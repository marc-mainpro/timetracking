-- Calendarios laborales (T70-03, RF-CAL-001..007, diseno §5.4 y §9.3).
--
-- Convencion de tipos temporales (RNF-011 / RNF-012): un calendario opera sobre
-- FECHAS LOCALES, no sobre instantes. "El 6 de enero es festivo" no es un punto
-- de la linea temporal, asi que la vigencia, los festivos y las jornadas
-- especiales usan DATE. Solo created_at/updated_at, que son marcas de auditoria
-- tecnica, son TIMESTAMPTZ (UTC). La zona horaria IANA del calendario se guarda
-- como texto y es la que convierte fecha local <-> instante en los bordes.

CREATE TABLE work_calendar (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    timezone VARCHAR(60) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_work_calendar PRIMARY KEY (id),
    CONSTRAINT fk_work_calendar_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT ck_work_calendar_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_work_calendar_period CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

-- Unicidad tenant-scoped del nombre: dos calendarios homonimos dentro de la
-- misma organizacion son indistinguibles en la UI de asignacion.
CREATE UNIQUE INDEX ux_work_calendar_tenant_name ON work_calendar (tenant_id, name);
CREATE INDEX ix_work_calendar_tenant_status ON work_calendar (tenant_id, status);

-- Reglas semanales (RF-CAL-002). Una fila por dia de la semana presente; los
-- dias ausentes se interpretan como no laborables.
CREATE TABLE calendar_day_rule (
    calendar_id UUID NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    working BOOLEAN NOT NULL,
    expected_minutes INTEGER NOT NULL,
    CONSTRAINT pk_calendar_day_rule PRIMARY KEY (calendar_id, day_of_week),
    CONSTRAINT fk_calendar_day_rule_calendar FOREIGN KEY (calendar_id)
        REFERENCES work_calendar (id) ON DELETE CASCADE,
    CONSTRAINT ck_calendar_day_rule_day CHECK (day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_calendar_day_rule_minutes CHECK (expected_minutes BETWEEN 0 AND 1440),
    -- Coherencia entre el flag y los minutos: un dia no laborable no puede
    -- esperar jornada, y uno laborable no puede esperar cero.
    CONSTRAINT ck_calendar_day_rule_coherent CHECK (
        (working AND expected_minutes > 0) OR (NOT working AND expected_minutes = 0))
);

-- Festivos (RF-CAL-003). La clave primaria (calendar_id, holiday_date) impone
-- una unica entrada por fecha sin necesidad de un id artificial: dentro de un
-- calendario, un festivo se identifica por su fecha.
CREATE TABLE calendar_holiday (
    calendar_id UUID NOT NULL,
    holiday_date DATE NOT NULL,
    name VARCHAR(120) NOT NULL,
    CONSTRAINT pk_calendar_holiday PRIMARY KEY (calendar_id, holiday_date),
    CONSTRAINT fk_calendar_holiday_calendar FOREIGN KEY (calendar_id)
        REFERENCES work_calendar (id) ON DELETE CASCADE
);

-- Jornadas especiales (RF-CAL-004): sustituyen la jornada esperada de una fecha
-- concreta. expected_minutes = 0 la convierte en no laborable.
CREATE TABLE calendar_special_day (
    calendar_id UUID NOT NULL,
    special_date DATE NOT NULL,
    name VARCHAR(120) NOT NULL,
    expected_minutes INTEGER NOT NULL,
    CONSTRAINT pk_calendar_special_day PRIMARY KEY (calendar_id, special_date),
    CONSTRAINT fk_calendar_special_day_calendar FOREIGN KEY (calendar_id)
        REFERENCES work_calendar (id) ON DELETE CASCADE,
    CONSTRAINT ck_calendar_special_day_minutes CHECK (expected_minutes BETWEEN 0 AND 1440)
);

-- Asignaciones (RF-CAL-006, T70-02). scope_target_id es NULL en ambito TENANT y
-- obligatorio en TEAM/EMPLOYEE. En ambito TEAM es un identificador OPACO: el
-- sistema no tiene gestion de equipos (ADR-0013), por eso no hay clave ajena
-- hacia ninguna tabla de equipos ni de empleados.
CREATE TABLE calendar_assignment (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    calendar_id UUID NOT NULL,
    scope VARCHAR(20) NOT NULL,
    scope_target_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_calendar_assignment PRIMARY KEY (id),
    CONSTRAINT fk_calendar_assignment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_calendar_assignment_calendar FOREIGN KEY (calendar_id)
        REFERENCES work_calendar (id) ON DELETE CASCADE,
    CONSTRAINT ck_calendar_assignment_scope CHECK (scope IN ('TENANT', 'TEAM', 'EMPLOYEE')),
    CONSTRAINT ck_calendar_assignment_target CHECK (
        (scope = 'TENANT' AND scope_target_id IS NULL)
        OR (scope IN ('TEAM', 'EMPLOYEE') AND scope_target_id IS NOT NULL))
);

-- Unicidad por ambito: una unica asignacion por (tenant, ambito, destinatario).
-- Dos asignaciones del mismo ambito harian ambigua la regla "la mas especifica
-- prevalece". Se necesitan dos indices parciales porque en PostgreSQL un UNIQUE
-- ordinario no deduplica los NULL del ambito TENANT.
CREATE UNIQUE INDEX ux_calendar_assignment_scoped
    ON calendar_assignment (tenant_id, scope, scope_target_id)
    WHERE scope_target_id IS NOT NULL;
CREATE UNIQUE INDEX ux_calendar_assignment_tenant_scope
    ON calendar_assignment (tenant_id)
    WHERE scope = 'TENANT';

-- Lectura de la resolucion del calendario efectivo: se filtra por tenant y se
-- ordena/filtra por ambito y destinatario.
CREATE INDEX ix_calendar_assignment_tenant_scope_target
    ON calendar_assignment (tenant_id, scope, scope_target_id);
CREATE INDEX ix_calendar_assignment_calendar ON calendar_assignment (calendar_id);

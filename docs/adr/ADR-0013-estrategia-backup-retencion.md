# ADR-0013: Estrategia de backup, retención y restauración de PostgreSQL

* Estado: accepted
* Fecha: 2026-08-04

## Contexto y problema

El sistema es la única fuente de verdad de los registros de jornada de todos
los tenants. Esos registros tienen valor legal (control horario obligatorio) y
contienen datos personales, así que perderlos no es un incidente recuperable
"rehaciendo el trabajo": es una pérdida irreversible con consecuencias
regulatorias.

A día de hoy no existe **ninguna** automatización. `docs/manuals/operations.md`
recoge dos comandos sueltos de `pg_dump`/`psql` y toda la persistencia vive en
el volumen `postgres_data` de Docker. Un `docker compose down -v` accidental, un
fallo del disco del host o un `DELETE` mal filtrado destruyen la base sin
posibilidad de recuperación. Los requisitos RO-005 (backups periódicos), RO-006
(política de retención), RO-007 (procedimiento de restauración documentado y
probado) y RT-008 (restauración validada y documentada) están sin cubrir.

El contexto acota mucho las opciones razonables:

* **RC-008**: no se diseña para alta disponibilidad. No hay réplica, ni
  standby, ni failover automático. El objetivo es *recuperar*, no *no caer*.
* El despliegue es un único `docker compose` sobre una máquina. No hay
  orquestador, ni operador de PostgreSQL, ni almacenamiento gestionado con
  snapshots.
* El equipo de operación es pequeño: un procedimiento que exija reconstruir
  WAL a mano no se ejecutará bien bajo presión.

La tentación es montar backup físico continuo con `pg_basebackup` + archivado
de WAL para tener recuperación punto en el tiempo (PITR). Es la opción técnica
superior, pero introduce un componente que hay que operar, monitorizar y probar,
y cuya restauración es notoriamente delicada. Un procedimiento complejo que
nadie ensaya es peor que uno simple que se ejecuta bien.

## Decisión

### Tipo de backup

**Backup lógico completo** con `pg_dump -Fc` (formato *custom*: comprimido, con
índice y apto para restauración selectiva con `pg_restore`). Implementado en
`scripts/backup/backup-postgres.sh`.

Se descarta PITR en esta versión (ver alternativas). La consecuencia asumida es
explícita: **la ventana de pérdida máxima es de 24 horas** (RPO = 24 h).

### Frecuencia

* **Diario**, a las 03:00 hora local del host, vía `cron` o `systemd timer` del
  anfitrión. El backup lógico de una base de este tamaño tarda segundos y no
  requiere ventana de parada.
* **Bajo demanda**, obligatorio *antes* de cualquier despliegue que incluya una
  migración Flyway destructiva (`DROP`, `ALTER ... TYPE`, borrado de columnas).

### Retención

Esquema *grandfather-father-son* reducido, implementado en la rotación del
script:

| Nivel | Se conserva | Variable |
|---|---|---|
| Diario | todos los backups de los últimos 7 días | `BACKUP_RETENTION_DAYS=7` |
| Semanal | el más antiguo de cada semana ISO, 4 semanas | `BACKUP_RETENTION_WEEKLY=4` |

Todo lo que no entra en ninguna de las dos ventanas se borra. En régimen
estacionario quedan ~11 backups.

El límite de 4 semanas no es arbitrario: es la ventana en la que un borrado
lógico no detectado (el escenario que el backup diario **no** cubre bien) sigue
siendo recuperable. Retener más tiempo copias completas con datos personales
choca con el principio de minimización del RGPD: un backup de hace seis meses
contiene datos de personas que pudieron ejercer su derecho de supresión, y no
hay forma práctica de aplicar ese derecho dentro de un volcado.

### Ubicación

1. **Copia local**: `${BACKUP_DIR}` (por defecto `./backups`, permisos `700`,
   ficheros `600`), en el disco del host, fuera del volumen de Docker. Esto es
   deliberado: si el backup viviera en `postgres_data`, un `down -v` se llevaría
   base y backups a la vez.
2. **Copia remota**: sincronización diaria del directorio a un almacenamiento
   externo al host (objeto o NAS) mediante `rsync`/`rclone` del operador. Sin
   esta segunda copia, un fallo del disco del host destruye base y backups.

La copia remota queda **fuera del alcance del script** a propósito: el destino
depende del entorno de despliegue de cada instalación y meterlo en el script
obligaría a incrustar credenciales de un proveedor concreto.

### Cifrado

Los volcados contienen datos personales de todos los tenants, así que se cifran
**en reposo** con GPG simétrico AES-256 (`BACKUP_PASSPHRASE`). Sin cifrado, un
fichero de backup mal ubicado equivale a una brecha completa de la base.

* La passphrase se pasa por *stdin* (`--passphrase-fd 0`), nunca por línea de
  comandos: los argumentos son visibles en `ps` y acabarían en los logs del
  shell.
* La passphrase **no** se guarda junto a los backups; vive en el gestor de
  secretos del entorno. Un backup cifrado con la clave al lado no está cifrado.
* Existe `--no-encrypt` para pruebas locales. El script lo marca como aviso en
  el log; no es admisible en producción.

### Validación

Tres niveles, de más barato a más caro:

1. **En cada backup**: el script verifica que el volcado es legible con
   `pg_restore --list` y calcula un `.sha256`. Detecta el fallo más común (un
   fichero truncado que parece correcto hasta que hace falta).
2. **En cada restauración**: `restore-postgres.sh` comprueba el SHA-256, que el
   esquema tiene tablas, que no hay migraciones Flyway marcadas como fallidas y
   que las tablas de negocio existen y tienen filas.
3. **Simulacro completo (RT-008)**: al menos **una vez por versión relevante y,
   como mínimo, trimestralmente**, se restaura un backup real en el entorno de
   Docker Compose y se ejecuta `scripts/smoke.sh`. La evidencia (fecha, backup,
   tiempo, resultado, incidencias) se registra en
   `docs/manuals/backup-restore.md`.

Un backup que no se ha restaurado nunca no es un backup: es un fichero.

### Responsables

| Responsabilidad | Rol |
|---|---|
| Programar el cron y vigilar que el backup diario termina en 0 | Operación |
| Custodiar `BACKUP_PASSPHRASE` y las credenciales de la copia remota | Operación |
| Ejecutar y firmar el simulacro trimestral de restauración | Operación, con validación técnica |
| Mantener los scripts y el procedimiento | Equipo de desarrollo |
| Decidir sobre RPO/RTO y aprobar excepciones | Responsable del producto |

**Objetivos declarados**: RPO 24 h, RTO 1 h (el simulacro de 2026-08-04 dio 13 s
de restauración de base sobre un conjunto de datos pequeño; el resto del RTO es
detección, decisión y arranque).

## Consecuencias

* Se cierra RO-005, RO-006, RO-007 y RT-008 con automatización real y evidencia
  de una prueba ejecutada, no con un procedimiento escrito sin ensayar.
* Se asume una ventana de pérdida de hasta 24 horas. Si el negocio no la acepta,
  la decisión a revisar es esta ADR (pasar a PITR), no la frecuencia del cron:
  bajar a backups horarios multiplica el volumen sin resolver el problema de
  fondo.
* El backup es **incompleto por diseño**: cubre PostgreSQL, no el fichero `.env`
  ni `BACKUP_PASSPHRASE`. Restaurar la base sin el `JWT_SECRET` original
  invalida todas las sesiones activas. Ambos secretos deben custodiarse aparte.
* La restauración es **destructiva** (`DROP DATABASE` + `CREATE`) y exige parar
  el backend. No hay restauración en caliente ni parcial por tenant.
* El script depende de `docker compose exec`: solo sirve para el despliegue por
  Compose. Un futuro despliegue gestionado necesitará revisar esta ADR.
* Aparece un secreto operativo nuevo (`BACKUP_PASSPHRASE`) cuya pérdida hace
  ilegibles todos los backups cifrados. Es un riesgo real y consciente:
  custodiarlo es responsabilidad explícita de Operación.

## Alternativas descartadas

* **PITR con `pg_basebackup` + archivado de WAL.** Reduce el RPO a segundos y es
  lo correcto para un sistema con datos legalmente relevantes. Se descarta *por
  ahora* porque exige almacenamiento de WAL, monitorización del archivado y un
  procedimiento de recuperación bastante más frágil, para un despliegue de una
  sola instancia sin equipo de operación dedicado (RC-008). Es la primera
  candidata a revisión cuando el sistema pase a producción con volumen real.
* **Snapshots del volumen Docker (`docker run --volumes-from`, `tar`).** Copia
  ficheros de PostgreSQL en caliente sin `pg_start_backup`, con riesgo de
  inconsistencia; y ata la restauración a la misma versión exacta del binario de
  PostgreSQL. Un volcado lógico es portable entre versiones mayores.
* **Snapshots del proveedor de infraestructura.** Sería la opción más simple,
  pero ata el procedimiento a un proveedor concreto y el MVP debe poder
  desplegarse en cualquier host con Docker.
* **`pg_dump` en texto plano (`-Fp`) redirigido a un `.sql`.** Es lo que había
  documentado. Se descarta: no comprime, no permite restauración selectiva y no
  ofrece forma barata de verificar la integridad del volcado (`pg_restore
  --list` no funciona sobre texto plano).
* **Cifrado GPG asimétrico con clave pública.** Más elegante (la máquina de
  backup no necesita la clave de descifrado), pero añade gestión de llaveros a
  un equipo pequeño. Se deja anotado como mejora si el backup pasa a ejecutarse
  en una máquina distinta a la de restauración.
* **Retención más larga (12 meses).** Descartada por minimización de datos
  (RGPD): copias completas antiguas contienen datos de personas que pueden haber
  ejercido su derecho de supresión.

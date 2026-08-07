-- T160-03 / T100-02: indices deducidos de planes de ejecucion reales, no de
-- suposiciones. Medidos sobre 100.000 jornadas repartidas en 20 tenants.
--
-- Criterio: solo se anade el indice cuyo plan se ha comprobado antes y despues.
-- El resto de claves ajenas sin indice propio se dejan como estan a proposito
-- (ver docs/reviews/performance-review.md): sus consultas van por indices
-- compuestos que ya empiezan por tenant_id, y no hay borrado fisico de padres
-- que obligue a recorrer la tabla hija.

-- break_entry.workday_id no tenia indice utilizable: el unico que existia es
-- parcial y cubre solo las pausas ABIERTAS (ux_break_entry_open ... WHERE
-- ended_at IS NULL), mientras que los informes leen justo las CERRADAS.
--
-- Efecto medido en el informe de un empleado y un mes: el plan recorria las
-- 100.023 pausas de TODOS los tenants para devolver 30 filas (17,7 ms, 941
-- buffers). Con el indice pasa a Nested Loop + Index Scan: 0,33 ms y 66
-- buffers. Lo relevante no es el factor sino que el coste deja de crecer con
-- el tamano total de la tabla: sin el, el informe de un tenant se degrada a
-- medida que otros tenants acumulan datos.
CREATE INDEX ix_break_entry_workday_id ON break_entry (workday_id);

-- correction_request se lista siempre por tenant y ordenado por fecha de
-- creacion descendente (con filtros opcionales de estado y solicitante), pero
-- solo tenia la clave primaria y un unico parcial sobre (workday_id,
-- requested_by) para las pendientes. El listado de un tenant recorria, por
-- tanto, las correcciones de todos los demas.
CREATE INDEX ix_correction_request_tenant_created_at
    ON correction_request (tenant_id, created_at DESC);

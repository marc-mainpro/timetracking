-- Volumen sintético para analizar planes de ejecución (T160-03).
--
-- Uso:
--   docker compose cp scripts/perf/seed-volume.sql postgres:/tmp/seed.sql
--   docker compose exec -T postgres psql -U timetracking -d timetracking -f /tmp/seed.sql
--
-- Reparte los datos entre VARIOS tenants a propósito: el problema que se busca
-- es el que hace que el coste de un tenant dependa de los datos de los demás, y
-- con un único tenant no se manifiesta.
--
-- No usar contra una base con datos reales: inserta tenants y usuarios ficticios.
DO $$
DECLARE t UUID; e UUID; w UUID; i INT; j INT; k INT; base TIMESTAMPTZ := now() - interval '400 days';
BEGIN
  FOR i IN 1..20 LOOP
    t := gen_random_uuid();
    INSERT INTO tenant (id, name, status, timezone, created_at, updated_at, activated_at)
      VALUES (t, 'Perf '||i, 'ACTIVE', 'Europe/Madrid', now(), now(), now());
    FOR j IN 1..25 LOOP
      e := gen_random_uuid();
      INSERT INTO app_user (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at)
        VALUES (e, t, 'perf'||i||'-'||j||'@acme.test', 'x', 'A', 'B', 'ACTIVE', now(), now());
      FOR k IN 1..200 LOOP
        w := gen_random_uuid();
        INSERT INTO workday (id, tenant_id, employee_id, status, started_at, ended_at, version, created_at, updated_at)
          VALUES (w, t, e, 'CLOSED', base + (k || ' days')::interval,
                  base + (k || ' days')::interval + interval '8 hours', 0, now(), now());
        INSERT INTO break_entry (id, workday_id, started_at, ended_at)
          VALUES (gen_random_uuid(), w, base + (k || ' days')::interval + interval '4 hours',
                  base + (k || ' days')::interval + interval '4 hours 30 minutes');
      END LOOP;
    END LOOP;
  END LOOP;
END $$;
ANALYZE workday; ANALYZE break_entry; ANALYZE app_user;

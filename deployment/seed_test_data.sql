-- =====================================================================
-- COMPIRA — Datos de prueba (seed) para M2–M5
-- =====================================================================
-- Objetivo: poblar escenarios de tareas, equipos, historial, observaciones,
-- notificaciones, indicadores (HU-27) y reportes (HU-29/HU-38) SIN crear
-- usuarios en Cognito. Los usuarios semilla existen SOLO en Postgres: sirven
-- como responsables/coordinadores de datos; NO pueden iniciar sesión.
--
-- Características:
--   * Idempotente: se puede re-ejecutar; limpia primero sus propias filas.
--   * Reversible: todo lo semilla usa IDs fijos (prefijo 'aaaa...'/'bbbb...'),
--     correos '*@compira.test' y títulos con prefijo '[SEED]'.
--   * No toca a los usuarios reales de Cognito ni sus datos.
--
-- Para revertir, ver seed_rollback al final (comentado).
-- =====================================================================

BEGIN;

-- IDs fijos de usuarios semilla (solo BD) ------------------------------
--  Admin extra, 2 coordinadores, 4 colaboradores
--  (cognito_sub con prefijo 'seed-' para distinguirlos de los reales)

-- ---------- Limpieza previa (idempotencia) ----------
-- Borra únicamente lo creado por este seed, respetando FKs.
DELETE FROM task_notifications WHERE task_id IN (SELECT id FROM tasks WHERE title LIKE '[SEED]%');
DELETE FROM task_history       WHERE task_id IN (SELECT id FROM tasks WHERE title LIKE '[SEED]%');
DELETE FROM task_observations  WHERE task_id IN (SELECT id FROM tasks WHERE title LIKE '[SEED]%');
DELETE FROM task_teams         WHERE task_id IN (SELECT id FROM tasks WHERE title LIKE '[SEED]%');
DELETE FROM tasks              WHERE title LIKE '[SEED]%';
DELETE FROM team_members       WHERE team_id IN (SELECT id FROM teams WHERE name LIKE '[SEED]%');
DELETE FROM team_members       WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@compira.test');
DELETE FROM task_teams         WHERE team_id IN (SELECT id FROM teams WHERE name LIKE '[SEED]%');
DELETE FROM teams              WHERE name LIKE '[SEED]%';
DELETE FROM user_roles         WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@compira.test');
DELETE FROM users              WHERE email LIKE '%@compira.test';

-- ---------- Usuarios semilla (solo BD, no Cognito) ----------
INSERT INTO users (id, cognito_sub, email, first_name, last_name, phone_number, preferred_mfa_channel, status, last_login_at) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'seed-admin-1',  'seed.admin@compira.test',  'Admin',  'Semilla',  '+573000000001', 'EMAIL', 'ACTIVE', now() - interval '1 day'),
  ('a0000000-0000-4000-8000-000000000002', 'seed-coord-1',  'seed.coord1@compira.test', 'Carla',  'Coord',    '+573000000002', 'EMAIL', 'ACTIVE', now() - interval '2 day'),
  ('a0000000-0000-4000-8000-000000000003', 'seed-coord-2',  'seed.coord2@compira.test', 'Camilo', 'Coord',    '+573000000003', 'EMAIL', 'ACTIVE', now() - interval '3 day'),
  ('a0000000-0000-4000-8000-000000000004', 'seed-collab-1', 'seed.colab1@compira.test', 'Lucía',  'Colab',    '+573000000004', 'EMAIL', 'ACTIVE', now() - interval '1 day'),
  ('a0000000-0000-4000-8000-000000000005', 'seed-collab-2', 'seed.colab2@compira.test', 'Mateo',  'Colab',    '+573000000005', 'EMAIL', 'ACTIVE', now() - interval '5 day'),
  ('a0000000-0000-4000-8000-000000000006', 'seed-collab-3', 'seed.colab3@compira.test', 'Sara',   'Colab',    '+573000000006', 'EMAIL', 'ACTIVE', NULL),
  ('a0000000-0000-4000-8000-000000000007', 'seed-collab-4', 'seed.colab4@compira.test', 'Diego',  'Colab',    '+573000000007', 'EMAIL', 'DISABLED', now() - interval '30 day');

-- ---------- Roles de los usuarios semilla ----------
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'ADMINISTRATOR'
WHERE u.email = 'seed.admin@compira.test';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'COORDINATOR'
WHERE u.email IN ('seed.coord1@compira.test', 'seed.coord2@compira.test');
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'COLLABORATOR'
WHERE u.email IN ('seed.colab1@compira.test', 'seed.colab2@compira.test', 'seed.colab3@compira.test', 'seed.colab4@compira.test');
-- Un usuario multi-rol (DEC-004): coord1 también es colaborador
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'COLLABORATOR'
WHERE u.email = 'seed.coord1@compira.test';

-- ---------- Equipos ----------
INSERT INTO teams (id, name, coordinator_user_id) VALUES
  ('b0000000-0000-4000-8000-000000000001', '[SEED] Operaciones', 'a0000000-0000-4000-8000-000000000002'),
  ('b0000000-0000-4000-8000-000000000002', '[SEED] Soporte',     'a0000000-0000-4000-8000-000000000003');

-- ---------- Miembros de equipo (un colaborador = un equipo) ----------
INSERT INTO team_members (user_id, team_id) VALUES
  ('a0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000001'),  -- Lucía -> Operaciones
  ('a0000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000001'),  -- Mateo -> Operaciones
  ('a0000000-0000-4000-8000-000000000006', 'b0000000-0000-4000-8000-000000000002');  -- Sara  -> Soporte
-- Diego (colab4, DISABLED) queda sin equipo a propósito (escenario borde).

-- ---------- Tareas (todos los estados + vencidas + próxima a vencer) ----------
-- Coordinador creador = coordinador del equipo correspondiente.
-- Operaciones (coord1), Soporte (coord2).
INSERT INTO tasks (id, title, description, due_date, status, responsible_user_id, created_by_user_id, created_at, updated_at) VALUES
  -- Operaciones
  ('c0000000-0000-4000-8000-000000000001', '[SEED] Pendiente a futuro',          'Tarea pendiente con vencimiento lejano',       now() + interval '10 day', 'PENDING',     'a0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000002', now() - interval '2 day',  now() - interval '2 day'),
  ('c0000000-0000-4000-8000-000000000002', '[SEED] Proxima a vencer (12h)',       'Debe contarse como proxima a vencer (<24h)',   now() + interval '12 hour', 'IN_PROGRESS', 'a0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000002', now() - interval '3 day',  now() - interval '1 day'),
  ('c0000000-0000-4000-8000-000000000003', '[SEED] Vencida (deriva DELAYED)',     'Pendiente vencida: la app la deriva a DELAYED', now() - interval '2 day',  'PENDING',     'a0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000002', now() - interval '6 day',  now() - interval '4 day'),
  ('c0000000-0000-4000-8000-000000000004', '[SEED] En progreso vencida',          'En progreso vencida: tambien deriva DELAYED',   now() - interval '1 day',  'IN_PROGRESS', 'a0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000002', now() - interval '7 day',  now() - interval '2 day'),
  ('c0000000-0000-4000-8000-000000000005', '[SEED] Completada por revisar',       'Completada esperando aprobacion del coordinador', now() + interval '1 day',  'COMPLETED',   'a0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000002', now() - interval '5 day',  now() - interval '6 hour'),
  ('c0000000-0000-4000-8000-000000000006', '[SEED] Cerrada a tiempo',             'Cerrada antes del vencimiento (cumplimiento OK)', now() - interval '1 day',  'CLOSED',      'a0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000002', now() - interval '10 day', now() - interval '2 day'),
  ('c0000000-0000-4000-8000-000000000007', '[SEED] Cerrada tarde',                'Cerrada despues del vencimiento (incumplimiento)', now() - interval '5 day',  'CLOSED',      'a0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000002', now() - interval '12 day', now() - interval '1 day'),
  ('c0000000-0000-4000-8000-000000000008', '[SEED] Cancelada',                    'Tarea cancelada (excluida de cumplimiento)',    now() + interval '3 day',  'CANCELLED',   'a0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000002', now() - interval '8 day',  now() - interval '3 day'),
  -- Soporte
  ('c0000000-0000-4000-8000-000000000009', '[SEED] Soporte pendiente',            'Pendiente del equipo de soporte',               now() + interval '4 day',  'PENDING',     'a0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000003', now() - interval '2 day',  now() - interval '2 day'),
  ('c0000000-0000-4000-8000-00000000000a', '[SEED] Soporte cerrada a tiempo',     'Cerrada a tiempo en soporte',                   now() - interval '2 day',  'CLOSED',      'a0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000003', now() - interval '9 day',  now() - interval '3 day'),
  ('c0000000-0000-4000-8000-00000000000b', '[SEED] Soporte sin responsable',      'Tarea sin responsable asignado (escenario borde)', now() + interval '6 day', 'PENDING',    NULL,                                   'a0000000-0000-4000-8000-000000000003', now() - interval '1 day',  now() - interval '1 day');

-- ---------- Vinculación tarea-equipo (alcance por rol) ----------
INSERT INTO task_teams (task_id, team_id) VALUES
  ('c0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000006', 'b0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000007', 'b0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000008', 'b0000000-0000-4000-8000-000000000001'),
  ('c0000000-0000-4000-8000-000000000009', 'b0000000-0000-4000-8000-000000000002'),
  ('c0000000-0000-4000-8000-00000000000a', 'b0000000-0000-4000-8000-000000000002'),
  ('c0000000-0000-4000-8000-00000000000b', 'b0000000-0000-4000-8000-000000000002');

-- ---------- Observaciones ----------
INSERT INTO task_observations (task_id, author_user_id, content, created_at) VALUES
  ('c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000004', 'Avance del 60%, falta validacion final.', now() - interval '20 hour'),
  ('c0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000005', 'Bloqueado por dependencia externa.',       now() - interval '2 day'),
  ('c0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000004', 'Entregable listo para revision.',          now() - interval '6 hour');

-- ---------- Historial / trazabilidad ----------
INSERT INTO task_history (task_id, event, actor_user_id, previous_value, new_value, detail, created_at) VALUES
  ('c0000000-0000-4000-8000-000000000006', 'CREATED',        'a0000000-0000-4000-8000-000000000002', NULL,          NULL,       'Tarea creada',                 now() - interval '10 day'),
  ('c0000000-0000-4000-8000-000000000006', 'ASSIGNED',       'a0000000-0000-4000-8000-000000000002', NULL,          'colab1',   'Responsable asignado',         now() - interval '10 day'),
  ('c0000000-0000-4000-8000-000000000006', 'STATUS_CHANGED', 'a0000000-0000-4000-8000-000000000004', 'PENDING',     'IN_PROGRESS','Inicio de ejecucion',        now() - interval '7 day'),
  ('c0000000-0000-4000-8000-000000000006', 'STATUS_CHANGED', 'a0000000-0000-4000-8000-000000000004', 'IN_PROGRESS', 'COMPLETED','Trabajo terminado',           now() - interval '3 day'),
  ('c0000000-0000-4000-8000-000000000006', 'CLOSED',         'a0000000-0000-4000-8000-000000000002', 'COMPLETED',   'CLOSED',   'Aprobada y cerrada',           now() - interval '2 day'),
  ('c0000000-0000-4000-8000-000000000007', 'REASSIGNED',     'a0000000-0000-4000-8000-000000000002', 'colab1',      'colab2',   'Reasignacion de responsable',  now() - interval '8 day'),
  ('c0000000-0000-4000-8000-000000000008', 'CANCELLED',      'a0000000-0000-4000-8000-000000000002', 'PENDING',     'CANCELLED','Cancelada por el coordinador', now() - interval '3 day');

-- ---------- Notificaciones (los 4 tipos; algunas entregables) ----------
INSERT INTO task_notifications (task_id, recipient_id, task_title, type, event_key, deliverable, created_at) VALUES
  ('c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000004', '[SEED] Pendiente a futuro',      'ASSIGNED',   'seed-evt-assigned-1',   TRUE,  now() - interval '2 day'),
  ('c0000000-0000-4000-8000-000000000007', 'a0000000-0000-4000-8000-000000000005', '[SEED] Cerrada tarde',           'REASSIGNED', 'seed-evt-reassigned-1', TRUE,  now() - interval '8 day'),
  ('c0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000004', '[SEED] Proxima a vencer (12h)',  'DUE_SOON',   'seed-evt-duesoon-1',    TRUE,  now() - interval '6 hour'),
  ('c0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000005', '[SEED] Vencida (deriva DELAYED)','OVERDUE',    'seed-evt-overdue-1',    TRUE,  now() - interval '1 day'),
  ('c0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000005', '[SEED] En progreso vencida',     'OVERDUE',    'seed-evt-overdue-2',    FALSE, now() - interval '12 hour');

-- ---------- Configuración de la organización ----------
UPDATE organization_settings SET notifications_enabled = TRUE, time_zone = 'America/Bogota' WHERE id = 1;

COMMIT;

-- =====================================================================
-- ROLLBACK (ejecutar manualmente si se desea limpiar el seed):
-- =====================================================================
-- BEGIN;
-- DELETE FROM task_notifications WHERE task_id IN (SELECT id FROM tasks WHERE title LIKE '[SEED]%');
-- DELETE FROM task_history       WHERE task_id IN (SELECT id FROM tasks WHERE title LIKE '[SEED]%');
-- DELETE FROM task_observations  WHERE task_id IN (SELECT id FROM tasks WHERE title LIKE '[SEED]%');
-- DELETE FROM task_teams         WHERE task_id IN (SELECT id FROM tasks WHERE title LIKE '[SEED]%');
-- DELETE FROM tasks              WHERE title LIKE '[SEED]%';
-- DELETE FROM team_members       WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@compira.test');
-- DELETE FROM teams              WHERE name LIKE '[SEED]%';
-- DELETE FROM user_roles         WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@compira.test');
-- DELETE FROM users              WHERE email LIKE '%@compira.test';
-- COMMIT;

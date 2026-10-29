# Compira Back

Backend base de COMPIRA construido con:

- Java 21
- Spring Boot WebFlux
- Bancolombia Scaffold + Clean Architecture
- PostgreSQL reactivo con R2DBC
- Amazon Cognito para registro, login, MFA y recuperación de contraseña
- Terraform para aprovisionar Cognito

## Módulos principales

- `domain/model` → entidades, comandos, respuestas y gateways
- `domain/usecase` → casos de uso de autenticación y compañías
- `infrastructure/entry-points/reactive-web` → endpoints WebFlux y OpenAPI
- `infrastructure/driven-adapters/r2dbc-postgresql` → persistencia local de usuarios, roles y compañías
- `infrastructure/driven-adapters/cognito-identity-provider` → integración reactiva con Cognito usando AWS SDK async
- `deployment/terraform/cognito` → infraestructura Cognito con Terraform

## Endpoints de autenticación

Todos existen en `/api/v1` y `/api/v2`.

- `POST /auth/register`
- `POST /auth/register/confirm`
- `POST /auth/login`
- `POST /auth/login/challenge`
- `POST /auth/password-recovery`
- `POST /auth/password-recovery/confirm`

## Variables requeridas para Cognito

- `COGNITO_REGION`
- `COGNITO_USER_POOL_ID`
- `COGNITO_CLIENT_ID`

## Variables locales de base de datos

- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=compira`
- `DB_SCHEMA=public`
- `DB_USER=postgres`
- `DB_PASSWORD=postgres`

## Base de datos local

Levantar PostgreSQL:

```bash
docker compose -f deployment/docker-compose.yml up -d
```

El esquema se inicializa con Liquibase desde
`infrastructure/driven-adapters/r2dbc-postgresql/src/main/resources/db/db.changelog-master.yaml`
cuando `LIQUIBASE_ENABLED=true`.

## Terraform Cognito

Ubicación:

- `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform/cognito`

Comandos base:

```bash
cd deployment/terraform/cognito
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
terraform apply
```

## Ejecutar backend

```bash
./gradlew bootRun
```

## Validar

```bash
./gradlew test
```


## Avisos al coordinador y trazabilidad

- El coordinador vigente del equipo recibe `DUE_SOON` cuando una tarea activa está
  dentro de las 24 horas previas a su vencimiento (incluida la frontera exacta).
  Se conserva el aviso al responsable; una tarea sin responsable también avisa al
  coordinador. Al llegar al vencimiento se conserva `OVERDUE`.
- Una transición válida del responsable a `COMPLETED` genera `TASK_COMPLETED`.
  Una observación autorizada genera `OBSERVATION_ADDED`, incluso si la escribe el
  propio coordinador. Cada aviso de actividad usa el ID del evento de historial;
  repetir la consulta no repite avisos y observaciones distintas no se confunden.
- Los destinatarios son los del equipo actual, no el creador de la tarea.
  Persisten las exclusiones de tareas terminadas/canceladas, el interruptor
  global, los controles de acceso y la persistencia para usuarios desconectados.
- Estado/observación, historial y aviso se confirman o revierten juntos. No se
  agregan correos ni nuevas conexiones persistentes: el frontend consulta cada 30s.
- Las respuestas de observaciones incluyen `authorName` y `authorEmail`; las de
  historial incluyen `actorName` y `actorEmail`. Los IDs originales permanecen.
  Se resuelve el perfil actual mediante una unión SQL, también para registros
  anteriores y usuarios deshabilitados; no es una fotografía del nombre en el
  momento histórico. Actor nulo significa acción automática del sistema.
- El listado administrativo de usuarios muestra tanto equipos coordinados como
  equipos de los que el usuario es miembro, sin duplicar equipos ni usuarios.
  `teamName` contiene nombres ordenados; `teamId` conserva un equipo representativo
  para compatibilidad, no debe interpretarse como la lista completa.

### Publicación compatible

La migración incremental `add_coordinator_notification_types.yaml` amplía la
restricción de tipos; no modifica cambios Liquibase anteriores ni elimina datos.
Publicar **primero frontend** (acepta los nuevos tipos y campos) y esperar su CD
verde; luego publicar backend (aplica la migración y genera avisos). Recargar las
pestañas antiguas para tomar el cliente nuevo. Volver al cliente anterior mientras
existan tipos nuevos no es compatible con su validación estricta.

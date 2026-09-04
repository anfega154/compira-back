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

Scripts de inicialización:

- `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/postgres/init/01-create-companies.sql`
- `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/postgres/init/02-create-auth-schema.sql`

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

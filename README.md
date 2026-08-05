# Compira Back

Proyecto base backend con:

- Java 21
- Spring Boot + WebFlux
- Scaffold Clean Architecture de Bancolombia
- Driven adapter reactivo para PostgreSQL con R2DBC
- API versionada en `/api/v1` y `/api/v2`

## Estructura

- `domain/model` → modelo de dominio y puertos
- `domain/usecase` → casos de uso
- `infrastructure/entry-points/reactive-web` → endpoints WebFlux
- `infrastructure/driven-adapters/r2dbc-postgresql` → adapter reactivo para Postgres
- `applications/app-service` → ensamblado e inicio de la app

## Endpoints base

- `GET /api/v1/companies`
- `GET /api/v1/companies/{id}`
- `POST /api/v1/companies`

Los mismos endpoints también existen en `/api/v2`.

### Ejemplo de creación

```bash
curl --request POST 'http://localhost:8080/api/v1/companies' \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "Compira SAS",
    "email": "contacto@compira.co"
  }'
```

## Levantar PostgreSQL local

Desde `/Users/andresganan/Desktop/COMPIRA/compira-back`:

```bash
docker compose -f deployment/docker-compose.yml up -d
```

La tabla `companies` se crea automáticamente con el script:

- `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/postgres/init/01-create-companies.sql`

## Variables de conexión

La aplicación usa estas variables, con defaults locales:

- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=compira`
- `DB_SCHEMA=public`
- `DB_USER=postgres`
- `DB_PASSWORD=postgres`

## Ejecutar

```bash
./gradlew bootRun
```

## Validación

```bash
./gradlew test
```

## Repositorio remoto

Este proyecto quedó conectado a:

- [anfega154/compira-back](https://github.com/anfega154/compira-back)

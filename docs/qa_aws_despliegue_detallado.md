# COMPIRA — guía detallada para QA en AWS

> **Actualización2026-10-29 — evolución de QA:** la infraestructura base ya está
> desplegada; las fases antiguas se conservan como referencia histórica, no como
> instrucciones para recrear recursos existentes. Se añade notificación WebSocket
> administrada, respaldo de versiones y retorno seguro. Seguir el
> [runbook de tiempo real](notificaciones_tiempo_real_qa.md) para el canal nuevo,
> variables, permisos, pruebas y reversión. No hacer pública Aurora.


Fecha de revisión: 2026-09-04

## 1. Resumen ejecutivo

Se revisaron los proyectos:

- Frontend: `/Users/andresganan/Desktop/COMPIRA/compira-front`
- Backend: `/Users/andresganan/Desktop/COMPIRA/compira-back`

Estado actual relevante para despliegue:

1. El frontend es una SPA React + Vite.
2. El backend es Spring Boot WebFlux + PostgreSQL reactivo + Liquibase + Cognito.
3. El backend ya tiene Terraform, pero solo para Cognito en `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform/cognito`.
4. No existe todavía una infraestructura completa para QA en AWS: faltan VPC, subredes, Aurora, ECS/ECR/ALB, S3/CloudFront, Route53 y secretos centralizados.
5. El flujo ideal para QA es:
   - frontend estático en S3 + CloudFront
   - backend en ECS Fargate detrás de un ALB
   - base de datos en Aurora PostgreSQL
   - Terraform para aprovisionamiento
   - GitHub Actions para CI/CD
6. Hallazgo funcional importante: el frontend sigue usando `/api/v1/companies`, pero el backend prueba explícitamente que `/api/v1/companies` debe responder `404`. Mientras eso no se corrija, el ambiente QA no quedará completamente funcional al entrar a la ruta raíz del frontend.

---

## 2. Hallazgos de la revisión del código

### 2.1 Frontend

Repositorio:

- remoto: `https://github.com/anfega154/compira-front`
- rama local actual: `dev`

Hallazgos:

1. El frontend usa `VITE_API_URL` como URL base del backend.
2. La página raíz usa `CompaniesPage`, que consume `/companies`.
3. No existía configuración completa de coverage para pipeline.
4. El build TypeScript fallaba por mezclar configuración de pruebas con Vite.

Cambios aplicados para dejarlo más listo para CI:

- `/Users/andresganan/Desktop/COMPIRA/compira-front/package.json`
- `/Users/andresganan/Desktop/COMPIRA/compira-front/package-lock.json`
- `/Users/andresganan/Desktop/COMPIRA/compira-front/tsconfig.app.json`
- `/Users/andresganan/Desktop/COMPIRA/compira-front/vite.config.ts`
- `/Users/andresganan/Desktop/COMPIRA/compira-front/vitest.config.ts`

Resultado validado:

- `npm run build` ✅
- `npm run test:coverage` ✅
- cobertura validada sobre el alcance activo configurado ✅

Cobertura obtenida en la última validación local:

- statements: 94.71%
- branches: 86.28%
- functions: 89.47%
- lines: 94.71%

### 2.2 Backend

Repositorio:

- remoto: `https://github.com/anfega154/compira-back`
- rama local actual: `dev`

Hallazgos:

1. El backend usa PostgreSQL compatible con Aurora PostgreSQL.
2. Usa Liquibase, lo cual facilita la primera carga de esquema en Aurora.
3. La configuración CORS estaba limitada a `GET`, `POST`, `OPTIONS`, pero el frontend usa `DELETE` para eliminar usuarios.
4. El Dockerfile existente no era seguro para pipeline porque declaraba `USER appuser` sin crear ese usuario.
5. El backend ya genera reporte Jacoco consolidado.
6. Solo existe Terraform para Cognito; no existe módulo completo de infraestructura QA.
7. Se encontraron archivos sensibles y de estado que no deben mantenerse en git:
   - `terraform.tfstate`
   - `terraform.tfstate.backup`
   - archivos locales con credenciales AWS

Cambios aplicados:

- `/Users/andresganan/Desktop/COMPIRA/compira-back/applications/app-service/src/main/resources/application.yaml`
- `/Users/andresganan/Desktop/COMPIRA/compira-back/infrastructure/entry-points/reactive-web/src/main/java/co/com/compira/api/config/CorsConfig.java`
- `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/Dockerfile`
- `/Users/andresganan/Desktop/COMPIRA/compira-back/.gitignore`

Resultado validado:

- `./gradlew test jacocoMergedReport` ✅

Cobertura backend medida en el reporte Jacoco consolidado:

- line: 89.03%
- instruction: 88.47%
- branch: 62.09%
- method: 89.01%

---

## 3. Decisión de arquitectura recomendada para QA

## Nivel recomendado

Para QA, la arquitectura más balanceada entre costo, mantenimiento y claridad operativa es:

1. **Frontend**
   - build en GitHub Actions
   - publicación estática en **S3**
   - distribución por **CloudFront**

2. **Backend**
   - contenedor Docker construido por GitHub Actions
   - imagen publicada en **ECR**
   - despliegue en **ECS Fargate**
   - exposición por **Application Load Balancer**

3. **Base de datos**
   - **Aurora PostgreSQL Serverless v2**
   - en subredes privadas de datos

4. **Configuración y secretos**
   - secretos de runtime en **AWS Secrets Manager**
   - parámetros no sensibles en **SSM Parameter Store** o variables del servicio ECS
   - secretos temporales de CI en **GitHub Environments**

5. **Infraestructura**
   - **Terraform** dentro de `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform`
   - estado remoto en bucket S3 + locking con DynamoDB

6. **Autenticación de GitHub Actions a AWS**
   - **OIDC**
   - evitar llaves AWS fijas dentro de GitHub

---

## 4. Qué debe existir en AWS

## 4.1 Red

Crear:

1. 1 VPC para QA
2. 2 subredes públicas
3. 2 subredes privadas de aplicación
4. 2 subredes privadas de datos
5. 1 Internet Gateway
6. 1 o 2 NAT Gateway
7. tablas de rutas

## 4.2 Seguridad

Crear:

1. security group para ALB
2. security group para ECS
3. security group para Aurora

Reglas:

- Internet → ALB: 443
- ALB → ECS: 8080
- ECS → Aurora: 5432

## 4.3 Backend

Crear:

1. repositorio ECR
2. cluster ECS
3. task definition
4. ECS service
5. target group
6. ALB listener HTTPS
7. ACM certificate

## 4.4 Frontend

Crear:

1. bucket S3 para hosting
2. bucket S3 de logs opcional
3. distribución CloudFront
4. certificado ACM en `us-east-1` si se usará dominio custom

## 4.5 Base de datos

Crear:

1. subnet group para RDS/Aurora
2. cluster Aurora PostgreSQL
3. instancia writer
4. Secrets Manager con usuario y password master
5. backup retention
6. parámetros de cluster si se necesitan ajustes

## 4.6 Observabilidad mínima

Crear:

1. log groups de CloudWatch
2. alarmas básicas:
   - ECS service unhealthy
   - ALB 5xx
   - CPU alta
   - Aurora connections altas

---

## 5. Estructura Terraform recomendada

Ubicación sugerida:

- `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform`

Estructura:

```text
deployment/terraform/
  bootstrap/
    main.tf
    variables.tf
    outputs.tf
  qa/
    backend.hcl
    main.tf
    providers.tf
    variables.tf
    outputs.tf
    terraform.tfvars.example
  modules/
    networking/
    security/
    ecr/
    ecs-cluster/
    ecs-service/
    alb/
    aurora-postgresql/
    frontend-hosting/
    secrets/
    github-oidc/
    cognito/
```

### 5.1 Bootstrap

El bootstrap crea:

1. bucket S3 de estado Terraform
2. tabla DynamoDB para lock

Esto se ejecuta una sola vez.

### 5.2 Stack QA

El stack `qa/` crea:

1. VPC
2. subredes
3. security groups
4. ECR
5. ECS cluster
6. ECS task execution role
7. ECS task role
8. ALB
9. target group
10. ECS service
11. Aurora PostgreSQL
12. bucket S3 frontend
13. CloudFront
14. secretos
15. Cognito, si se decide mover el módulo actual a esta misma estructura

---

## 6. Variables: dónde deben ir

## 6.1 Terraform variables

Usar para parámetros de infraestructura, por ejemplo:

- región
- nombres de recursos
- CIDR
- tamaño mínimo/máximo de Aurora Serverless
- CPU y memoria ECS
- dominio QA

Guardar en:

- `terraform.tfvars` local solo para trabajo local
- en pipeline: `-var` o `TF_VAR_*`
- nunca commitear `terraform.tfvars` con secretos reales

## 6.2 GitHub Secrets

Usar solo para secretos de CI/CD que GitHub necesite leer:

- `AWS_ROLE_TO_ASSUME_QA`
- `AWS_REGION`
- `TF_STATE_BUCKET`
- `TF_LOCK_TABLE`
- `QA_DB_SECRET_ARN`
- `QA_ECS_TASK_EXECUTION_ROLE_ARN` si algún paso externo lo requiere

## 6.3 GitHub Variables

Usar para parámetros no sensibles:

- `AWS_REGION`
- `ECR_REPOSITORY`
- `ECS_CLUSTER_NAME`
- `ECS_SERVICE_NAME`
- `ECS_TASK_FAMILY`
- `CONTAINER_NAME`
- `S3_BUCKET_QA`
- `CLOUDFRONT_DISTRIBUTION_ID`
- `VITE_API_URL_QA`

## 6.4 AWS Secrets Manager

Usar para secretos de runtime del backend:

- usuario/password de Aurora
- `COGNITO_USER_POOL_ID`
- `COGNITO_CLIENT_ID`
- cualquier API key futura

## 6.5 Variables ECS runtime

Como variables de entorno del contenedor backend:

- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_SCHEMA`
- `LIQUIBASE_ENABLED`
- `COGNITO_REGION`
- `CORS_ALLOWED_ORIGINS`

Como secretos inyectados desde Secrets Manager:

- `DB_USER`
- `DB_PASSWORD`
- `COGNITO_USER_POOL_ID` si se decide tratarlo como secreto
- `COGNITO_CLIENT_ID` si se decide tratarlo como secreto

## 6.6 Variables del frontend

Se resuelven en build time:

- `VITE_API_URL`
- `VITE_OTP_RESEND_COOLDOWN_SECONDS`

Para QA deben definirse en GitHub Actions antes de `npm run build`.

---

## 7. Docker: qué hacer y qué no hacer

## Recomendación

1. **Backend sí debe usar Docker**
2. **Frontend no necesita Docker**

Razón:

- backend necesita empaquetado reproducible para ECS
- frontend es estático; subir el build a S3 es más simple y barato

Estado actual:

- Dockerfile backend listo para pipeline en `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/Dockerfile`

Pipeline ideal backend:

1. correr tests
2. generar jar
3. construir imagen Docker
4. taggear con SHA
5. push a ECR
6. actualizar task definition ECS
7. desplegar servicio

Pipeline ideal frontend:

1. correr lint
2. correr tests
3. validar coverage
4. build
5. sync `dist/` a S3
6. invalidar CloudFront

---

## 8. Pipeline recomendado en GitHub Actions

## 8.1 Estrategia de ramas

Hoy no existe rama `qa` en los dos repositorios.

Se debe crear en:

- `compira-front`
- `compira-back`

Flujo recomendado:

1. ramas feature → PR a `dev`
2. `dev` estabilizado → PR a `qa`
3. merge a `qa` → despliegue automático a QA
4. `qa` aprobado → PR a `main`

## 8.2 Workflows mínimos por repositorio

### Frontend

**Workflow 1: validación de PR a qa**

Trigger:

- `pull_request` hacia `qa`

Pasos:

1. checkout
2. setup node
3. npm ci
4. lint
5. tests
6. coverage
7. build

**Workflow 2: deploy al hacer merge a qa**

Trigger:

- `push` a `qa`

Pasos:

1. checkout
2. setup node
3. npm ci
4. lint
5. test:coverage
6. build con `VITE_API_URL`
7. asumir rol AWS por OIDC
8. sync `dist/` a S3
9. invalidar CloudFront

### Backend

**Workflow 1: validación de PR a qa**

Trigger:

- `pull_request` hacia `qa`

Pasos:

1. checkout
2. setup java 21
3. cache gradle
4. `./gradlew test jacocoMergedReport`
5. publicar reporte de coverage si quieren
6. construir imagen Docker para validar Dockerfile

**Workflow 2: deploy al hacer merge a qa**

Trigger:

- `push` a `qa`

Pasos:

1. checkout
2. setup java 21
3. tests + jacoco
4. setup docker buildx
5. asumir rol AWS por OIDC
6. login a ECR
7. build image
8. push image con tag `${GITHUB_SHA}`
9. aplicar Terraform si hay cambios de infraestructura
10. render de task definition
11. deploy a ECS
12. esperar estabilidad

---

## 9. Orden correcto de implementación

## Fase 1 — saneamiento antes de tocar AWS

1. Crear rama `qa` en ambos repositorios.
2. Proteger la rama `qa`.
3. Exigir PR para merge.
4. Exigir checks obligatorios.
5. Rotar inmediatamente credenciales AWS locales expuestas en archivos de contexto.
6. Sacar de git:
   - `terraform.tfstate`
   - `terraform.tfstate.backup`
   - cualquier archivo `.tfvars` real
7. Confirmar si la home del frontend seguirá usando empresas o si se cambiará a otro módulo.

## Fase 2 — Terraform base

1. Crear bootstrap remoto de Terraform.
2. Configurar backend remoto S3 + DynamoDB lock.
3. Crear stack QA con:
   - red
   - seguridad
   - Aurora
   - ECR
   - ECS
   - ALB
   - S3 frontend
   - CloudFront
   - secretos

## Fase 3 — backend deployable

1. Confirmar task definition
2. configurar variables backend
3. apuntar `DB_HOST` al writer endpoint de Aurora
4. validar Liquibase
5. validar health endpoint `/actuator/health`

## Fase 4 — frontend deployable

1. definir `VITE_API_URL` de QA
2. configurar CloudFront
3. habilitar CORS QA en backend
4. validar login contra backend QA

## Fase 5 — pipelines

1. workflow PR frontend
2. workflow deploy frontend
3. workflow PR backend
4. workflow deploy backend
5. proteger qa con checks obligatorios

---

## 10. Paso a paso detallado de la primera vez

## 10.1 En GitHub

Hacer en ambos repositorios:

1. crear rama `qa` desde `dev`
2. ir a **Settings → Branches**
3. crear regla de protección para `qa`
4. exigir:
   - pull request antes de merge
   - al menos 1 aprobación
   - status checks obligatorios
   - bloqueo de force push

## 10.2 En AWS IAM para GitHub Actions

1. Crear provider OIDC de GitHub si no existe:
   - issuer: `https://token.actions.githubusercontent.com`
2. Crear un rol IAM por repositorio o uno compartido para QA:
   - `compira-front-gha-qa-role`
   - `compira-back-gha-qa-role`
3. Configurar trust policy restringida por:
   - repositorio
   - branch `refs/heads/qa`
4. Dar permisos mínimos:
   - frontend: S3 + CloudFront
   - backend: ECR + ECS + IAM PassRole + CloudWatch + Terraform targets

## 10.3 Bootstrap Terraform

En `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform/bootstrap`:

1. crear bucket S3 de state
2. crear tabla DynamoDB de locks
3. aplicar bootstrap manualmente una sola vez

Comandos:

```bash
cd /Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform/bootstrap
terraform init
terraform plan
terraform apply
```

## 10.4 Configurar backend remoto Terraform

En `/Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform/qa/backend.hcl`:

Definir:

- bucket
- key
- region
- dynamodb_table
- encrypt

Luego:

```bash
cd /Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform/qa
terraform init -backend-config=backend.hcl
```

## 10.5 Crear infraestructura QA

Aplicar en este orden lógico:

1. red
2. seguridad
3. secretos
4. Aurora
5. ECR
6. ECS cluster
7. ALB
8. frontend S3 + CloudFront
9. Cognito si corresponde

## 10.6 Crear secretos reales

Crear en Secrets Manager:

1. `compira/qa/database`
2. `compira/qa/cognito`

Ejemplo conceptual de `compira/qa/database`:

```json
{
  "username": "compira_app",
  "password": "REEMPLAZAR",
  "dbname": "compira",
  "schema": "public"
}
```

## 10.7 Preparar backend para Aurora

Definir para el servicio ECS:

- `DB_HOST=<writer-endpoint-aurora>`
- `DB_PORT=5432`
- `DB_NAME=compira`
- `DB_SCHEMA=public`
- `LIQUIBASE_ENABLED=true`
- `COGNITO_REGION=us-east-1`
- `CORS_ALLOWED_ORIGINS=https://qa.tudominio.com,https://dxxxxxxxx.cloudfront.net`

## 10.8 Primer despliegue backend

1. construir imagen
2. push a ECR
3. crear task definition
4. desplegar service
5. validar:
   - health
   - logs
   - conexión DB
   - Liquibase ejecutado

## 10.9 Primer despliegue frontend

1. definir `VITE_API_URL=https://api-qa.tudominio.com/api/v1`
2. ejecutar build
3. subir `dist/` a S3
4. invalidar CloudFront
5. validar en navegador:
   - carga inicial
   - login
   - flujo OTP
   - registro usuario
   - eliminación usuario

## 10.10 Validación end-to-end

Validar:

1. frontend carga en HTTPS
2. backend responde en HTTPS
3. login funciona
4. MFA funciona
5. password recovery funciona
6. registrar usuario funciona
7. eliminar usuario funciona
8. revisar comportamiento de la ruta raíz por el problema de `/companies`

---

## 11. Paso a paso de despliegues posteriores a QA

Cada vez que se lleven cambios a QA:

1. desarrollar en feature branch
2. abrir PR a `qa`
3. GitHub Actions debe ejecutar:
   - lint
   - tests
   - coverage
   - build
4. revisar PR
5. aprobar y hacer merge
6. el `push` a `qa` dispara deploy

### 11.1 Si cambia frontend

El pipeline debe:

1. compilar
2. ejecutar tests/coverage
3. construir `dist/`
4. sincronizar bucket S3
5. invalidar CloudFront

### 11.2 Si cambia backend

El pipeline debe:

1. ejecutar tests/Jacoco
2. construir imagen
3. push a ECR
4. actualizar task definition con nuevo tag
5. desplegar ECS service
6. esperar health green

### 11.3 Si cambia infraestructura

El pipeline backend debe:

1. ejecutar `terraform fmt -check`
2. ejecutar `terraform validate`
3. ejecutar `terraform plan`
4. en `push` a `qa`, ejecutar `terraform apply`
5. luego desplegar backend si aplica

---

## 12. Manual operativo detallado

## 12.1 Qué hacer si falla el backend al arrancar

Revisar en este orden:

1. logs de CloudWatch
2. variables ECS
3. secretos inyectados
4. security group de Aurora
5. endpoint writer de Aurora
6. salida de Liquibase
7. `/actuator/health`

## 12.2 Qué hacer si el frontend carga pero no consume backend

Revisar:

1. `VITE_API_URL` usado en el build
2. CORS
3. DNS del backend
4. certificado HTTPS
5. rutas `/api/v1/...`
6. consola del navegador

## 12.3 Qué hacer si falla Terraform

Revisar:

1. state lock en DynamoDB
2. permisos del rol OIDC
3. variables obligatorias
4. dependencias entre módulos
5. recursos existentes fuera de Terraform

## 12.4 Qué hacer si falla el deploy ECS

Revisar:

1. task definition renderizada
2. imagen existe en ECR
3. tag coincide con SHA
4. execution role
5. task role
6. target group health checks
7. puertos 8080/443/5432

---

## 13. Riesgos y bloqueadores actuales

### Bloqueador 1 — home del frontend

El frontend abre `CompaniesPage`, pero el backend valida que `/api/v1/companies` ya no existe.

Acción obligatoria:

- o se restaura el endpoint
- o se cambia la home del frontend a un módulo vigente

### Bloqueador 2 — secretos y state

Hay evidencia de material sensible local y state Terraform en el repositorio del backend.

Acción obligatoria:

1. rotar credenciales
2. remover state del repo
3. usar backend remoto S3 + DynamoDB

### Bloqueador 3 — falta infraestructura QA

Solo existe Terraform Cognito.

Acción obligatoria:

- crear módulos completos de infraestructura

### Bloqueador 4 — rama qa inexistente

Acción obligatoria:

- crear y proteger `qa`

---

## 14. Checklist final de salida a QA

- [ ] rama `qa` creada en front y back
- [ ] protección de rama `qa`
- [ ] workflows PR activos
- [ ] workflows deploy activos
- [ ] OIDC GitHub→AWS configurado
- [ ] bucket de state Terraform creado
- [ ] lock DynamoDB creado
- [ ] VPC/subredes creadas
- [ ] security groups creados
- [ ] Aurora creado
- [ ] secretos en Secrets Manager
- [ ] ECR creado
- [ ] ECS cluster/service creados
- [ ] ALB + HTTPS configurados
- [ ] S3 frontend creado
- [ ] CloudFront creado
- [ ] `VITE_API_URL` configurado
- [ ] `CORS_ALLOWED_ORIGINS` configurado
- [ ] frontend build/test/coverage pasando
- [ ] backend test/jacoco pasando
- [ ] issue de `/companies` resuelto

---

## 15. Recomendación final

La ruta más sólida para COMPIRA QA es:

1. usar Aurora PostgreSQL Serverless v2
2. mantener backend en ECS Fargate con Docker construido en pipeline
3. desplegar frontend como sitio estático en S3 + CloudFront
4. usar Terraform para todo recurso persistente
5. usar GitHub Actions con OIDC y ambientes `qa`
6. resolver antes del go-live QA el desacople actual entre la home del frontend y el backend

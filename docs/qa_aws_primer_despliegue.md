# COMPIRA QA: primer despliegue y operación continua

## Alcance y arquitectura

Cognito ya existe. Este procedimiento crea desde cero: backend remoto Terraform (S3/DynamoDB), rol OIDC de GitHub, VPC de dos zonas, subredes públicas/privadas, NAT Gateway, API Gateway HTTP público, ALB interno, ECS Fargate, ECR, Aurora PostgreSQL Serverless v2, CloudWatch, S3 privado y CloudFront para el frontend.

Los roots Terraform están centralizados en el backend:

- `deployment/terraform/bootstrap`: state remoto y OIDC.
- `deployment/terraform/cognito`: recursos Cognito ya existentes; se migra su state, no se recrea.
- `deployment/terraform/service`: API, red y Aurora.
- `deployment/terraform/frontend`: hosting web.

## Precondiciones

1. AWS CLI autenticado con un usuario/rol administrador **solo para el bootstrap**.
2. Terraform >= 1.6, Docker, Java 21, Node 22 y Git instalados.
3. Tener el ID del User Pool y Client ID existentes de Cognito.
4. Los repositorios `compira-back` y `compira-front` deben tener la rama protegida `qa`.
5. Elegir una región. Los ejemplos usan `us-east-1`.

## Fase 1: crear state remoto y OIDC

> Esta es la única fase ejecutada localmente con credenciales administrativas. No subas `terraform.tfvars`, `backend.hcl` ni archivos state.

```bash
cd /Users/andresganan/Desktop/COMPIRA/compira-back/deployment/terraform/bootstrap
cp terraform.tfvars.example terraform.tfvars
```

Edita `terraform.tfvars`. El bucket debe ser globalmente único; reemplaza `REPLACE_WITH_ACCOUNT_ID` por el ID de la cuenta. Confirma los repositorios autorizados.

```bash
terraform init
terraform fmt -check
terraform validate
terraform plan -out bootstrap.tfplan
terraform apply bootstrap.tfplan
```

Obtén los valores:

```bash
terraform output terraform_state_bucket
terraform output terraform_lock_table
terraform output github_actions_role_arn
```

Crea el cambio local de backend **solo después** de que el bucket y la tabla existan; no se versiona:

```bash
cp backend.tf.example backend.tf
cp backend.hcl.example backend.hcl
# editar bucket y dynamodb_table
terraform init -migrate-state -backend-config=backend.hcl
terraform state list
terraform plan
```

El último plan debe indicar que no hay cambios.

## Fase 2: migrar state de Cognito sin recrearlo

```bash
cd ../cognito
terraform state list
terraform plan
cp backend.hcl.example backend.hcl
# editar bucket y dynamodb_table: los creados en Fase 1
terraform init -migrate-state -backend-config=backend.hcl
terraform state list
terraform plan
```

El plan debe ser `No changes`. Si propone crear, reemplazar o destruir un User Pool, detente. Solo entonces ejecuta:

```bash
git rm --cached terraform.tfstate terraform.tfstate.backup
git add backend.tf .gitignore
git commit -m "chore(terraform): migrate Cognito state to S3"
```

## Fase 3: configurar GitHub Environment `qa`

En **cada repositorio**: Settings → Environments → New environment → `qa`. Limita deployments a la rama protegida `qa`.

### Secret en ambos repositorios

| Nombre | Valor |
|---|---|
| `AWS_QA_DEPLOY_ROLE_ARN` | output `github_actions_role_arn` de bootstrap |

### Secret adicional en frontend

| Nombre | Valor |
|---|---|
| `COMPIRA_BACK_REPOSITORY_TOKEN` | Fine-grained PAT con permiso `Contents: Read` sobre `anfega154/compira-back`; se usa para consultar Terraform centralizado. |

### Variables en ambos repositorios

| Nombre | Valor |
|---|---|
| `AWS_REGION` | `us-east-1` |
| `QA_TF_STATE_BUCKET` | output `terraform_state_bucket` |
| `QA_TF_LOCK_TABLE` | output `terraform_lock_table` |
| `COGNITO_USER_POOL_ID` | User Pool existente |
| `COGNITO_CLIENT_ID` | Client ID existente |
| `QA_PUBLIC_SUBNET_CIDRS` | `["10.20.1.0/24","10.20.2.0/24"]` |
| `QA_PRIVATE_SUBNET_CIDRS` | `["10.20.11.0/24","10.20.12.0/24"]` |

Después de Fase 5 agrega también `QA_FRONTEND_URL`. Después de Fase 4 agrega `QA_API_URL` al frontend.

## Configuración de variables por servicio

### Backend: variables de ejecución ECS

| Variable | Origen en QA | ¿Se configura manualmente en GitHub? |
|---|---|---|
| `DB_HOST` | Output `aws_rds_cluster.aurora.endpoint` desde Terraform | No |
| `DB_PORT` | Terraform: `5432` | No |
| `DB_NAME`, `DB_SCHEMA`, `DB_USER` | Terraform: `compira`, `public`, `compira_admin` | No |
| `DB_PASSWORD` | Secret administrado por RDS en AWS Secrets Manager, inyectado a ECS | No; nunca se expone |
| `LIQUIBASE_ENABLED` | Terraform: `true` | No |
| `COGNITO_REGION` | Variable GitHub `AWS_REGION` | Sí, una vez |
| `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID` | GitHub Environment variables | Sí, una vez |
| `CORS_ALLOWED_ORIGINS` | GitHub Environment variable `QA_FRONTEND_URL` | Sí, después de crear CloudFront |

Terraform transfiere los valores no secretos a la definición de tarea ECS. El password nunca pasa por GitHub ni se escribe en `terraform.tfvars`.

### Frontend: variables de compilación Vite

| Variable | Origen en QA | ¿Es secreto? |
|---|---|---|
| `VITE_API_URL` | GitHub Environment variable `QA_API_URL`, URL de API Gateway | No |
| `VITE_OTP_RESEND_COOLDOWN_SECONDS` | GitHub Environment variable opcional; valor predeterminado `120` | No |

Vite incorpora `VITE_*` en el JavaScript durante `npm run build`. Por ello nunca se debe usar este prefijo para passwords, claves AWS, secretos Cognito o credenciales de base de datos.

## Fase 4: primer backend y Aurora

Como desarrollador no debes ejecutar `terraform apply` dentro de `service` para el primer despliegue: ese root lo ejecuta el pipeline. Crear un `backend.hcl` o `terraform.tfvars` local de `service` es **opcional** y sirve únicamente si quieres revisar el plan antes del merge; esos archivos nunca se versionan.

Tu flujo de ramas es: **rama actual → `dev` → `qa`**. Haz el pull request de la rama actual hacia `dev`; valida allí el código. Después crea el pull request de `dev` hacia `qa`. Ese PR ejecuta solo validaciones; el merge resultante a `qa` activa el despliegue.

Para evitar crear ECS sin imagen, el workflow primero aplica con `desired_count=0`, crea ECR, Aurora y red; luego compila, publica la imagen SHA y pasa a `desired_count=1`.

En GitHub Actions, el workflow **QA backend** ejecuta automáticamente este orden:

1. `./gradlew test jacocoMergedReport`.
2. `docker build` de validación.
3. Terraform `apply` con ECS detenido.
4. `docker build` usando `deployment/Dockerfile`.
5. `docker push` a ECR con tag igual a SHA del commit.
6. Segundo Terraform `apply` con ECS en 1 réplica.

Espera la creación de Aurora. Puede tardar varios minutos. Verifica:

```bash
aws ecs describe-services --cluster compira-qa-cluster --services compira-qa-service --region us-east-1
aws logs tail /ecs/compira-qa --follow --region us-east-1
```

Obtén la URL del API:

```bash
cd deployment/terraform/service
terraform output api_gateway_url
```

Guárdala como variable `QA_API_URL` del Environment `qa` del frontend.

## Fase 5: primer frontend

Después de registrar `QA_API_URL`, crea el pull request de `dev` hacia `qa` en frontend. El workflow **QA frontend**:

1. Ejecuta lint, Vitest y coverage.
2. Obtiene el Terraform central desde `compira-back`.
3. Crea S3/CloudFront si aún no existen.
4. Compila Vite con `VITE_API_URL=$QA_API_URL`.
5. Publica `dist/` en S3.
6. Invalida CloudFront.

Después, en el backend agrega el output `frontend_url` como variable `QA_FRONTEND_URL` y ejecuta nuevamente el pipeline backend para que `CORS_ALLOWED_ORIGINS` quede restringido al dominio QA.

## Verificación funcional

1. Abre `frontend_url` y comprueba que carga por HTTPS.
2. Abre `${QA_API_URL}/actuator/health`; debe devolver HTTP 200.
3. Prueba login Cognito, flujo protegido y solicitudes API desde la interfaz.
4. Revisa Network del navegador: no debe existir error CORS.
5. Revisa target health de ALB, tarea ECS y CloudWatch logs si falla una solicitud.

## Despliegues posteriores

### Cambio backend

1. Crea PR hacia `qa`.
2. El workflow valida pruebas, cobertura y Docker build.
3. Aprueba y mergea.
4. Push a `qa` construye una imagen con SHA nuevo, la publica en ECR y actualiza ECS.
5. Comprueba health endpoint y logs.

### Cambio frontend

1. Crea PR hacia `qa`.
2. El workflow ejecuta lint, pruebas y coverage.
3. Aprueba y mergea.
4. Push a `qa` recompila, sincroniza S3 e invalida CloudFront.
5. Fuerza recarga del navegador y valida la funcionalidad.

## No hacer

- No ejecutar `terraform apply` para Cognito antes de validar su state migrado.
- No guardar AWS keys, passwords, `terraform.tfvars`, `backend.hcl` ni `terraform.tfstate` en Git.
- No reutilizar el tag `latest`; el pipeline usa el SHA para rollback trazable.
- No borrar el bucket de state ni la tabla de lock mientras exista infraestructura administrada por Terraform.

# QA: archivos que se versionan y archivos locales

## Se versionan en `compira-back`

- `.github/workflows/qa.yml`: validación, coverage, Docker, ECR y ECS.
- `deployment/Dockerfile`: imagen reproducible del backend.
- `deployment/terraform/bootstrap/**`: crea backend remoto y OIDC; incluye `.terraform.lock.hcl`.
- `deployment/terraform/service/**`: VPC, Aurora, ECR, ECS, ALB/API Gateway; incluye `.terraform.lock.hcl`.
- `deployment/terraform/frontend/**`: S3 y CloudFront; incluye `.terraform.lock.hcl`.
- `deployment/terraform/cognito/backend.tf` y `backend.hcl.example`: contrato de backend remoto tras migrar el state.
- Cambios de CORS en `application.yaml` y `CorsConfig.java`.

## Se versionan en `compira-front`

- `.github/workflows/qa.yml`.
- `vitest.config.ts`, `package.json`, `package-lock.json` y `tsconfig.app.json`: necesarios para tests, coverage y build de CI.

## Nunca se versionan

- `backend.hcl`: valores locales de backend Terraform.
- `terraform.tfvars`: configuración local o por ambiente.
- `*.tfstate`, `*.tfstate.*`, `.terraform/`.
- `*.tfplan` como `bootstrap.tfplan`.
- `.env` y el directorio `context/`.
- `coverage/`, `dist/` y `build/`.

`backend.tf.example` sí se versiona: es una plantilla. `backend.tf` de bootstrap se crea solo localmente después de crear el bucket y está ignorado.

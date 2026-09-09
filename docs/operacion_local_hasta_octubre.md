# Operación local temporal (hasta mediados de octubre)

Durante esta pausa, **Cognito se conserva en AWS**. La base de datos, el API y
el frontend se ejecutan en cada computador de desarrollo. GitHub Actions queda
como integración continua (tests, cobertura, lint y builds): un runner de
GitHub no puede llegar al `localhost` de los desarrolladores, por lo que no
puede desplegar allí.

## 1. Bajar la aplicación AWS sin tocar Cognito

> Ejecute una sola vez con el usuario autorizado de AWS y desde el repositorio
> `compira-back`. No ejecute `destroy` en `deployment/terraform/cognito`.

1. Confirme que está en la cuenta correcta:
   ```bash
   aws sts get-caller-identity
   ```
   Debe mostrar la cuenta `380093117501`.
2. En `deployment/terraform/service`, cree `backend.hcl` (es local e ignorado):
   ```hcl
   bucket         = "compira-qa-terraform-state-380093117501"
   key            = "qa/service/terraform.tfstate"
   region         = "us-east-1"
   dynamodb_table = "compira-qa-terraform-lock"
   encrypt        = true
   ```
3. Cree `terraform.tfvars` desde `terraform.tfvars.example`. Complete Cognito
   con los valores actuales y los demás parámetros usados en el workflow:
   ```hcl
   app_port=8080
   desired_count=0
   container_cpu=512
   container_memory=1024
   health_check_path="/actuator/health"
   api_gateway_stage_name="$default"
   vpc_cidr="10.20.0.0/16"
   public_subnet_cidrs=["10.20.1.0/24","10.20.2.0/24"]
   private_subnet_cidrs=["10.20.11.0/24","10.20.12.0/24"]
   log_retention_days=30
   db_name="compira"
   db_master_username="compira_admin"
   aurora_min_acu=0.5
   aurora_max_acu=2
   ```
4. Inicialice, revise y aplique exclusivamente el plan de destrucción:
   ```bash
   cd deployment/terraform/service
   terraform init -reconfigure -backend-config=backend.hcl
   terraform plan -destroy -out=destroy-service.tfplan
   terraform apply destroy-service.tfplan
   ```
   El plan debe destruir ECS, ECR, API Gateway, ALB, VPC/NAT, Aurora,
   CloudWatch y el bucket de logs. Aurora genera un *final snapshot* para no
   perder datos; elimínelo después desde RDS si confirma que ya no lo necesita.
5. El frontend dejó un bucket parcial. En `deployment/terraform/frontend`, use
   otro `backend.hcl` cambiando solo `key` a `qa/frontend/terraform.tfstate`, y
   ejecute:
   ```bash
   terraform init -reconfigure -backend-config=backend.hcl
   terraform plan -destroy -out=destroy-frontend.tfplan
   terraform apply destroy-frontend.tfplan
   ```
6. Verifique:
   ```bash
   aws ecs list-clusters --region us-east-1
   aws rds describe-db-clusters --region us-east-1
   aws ec2 describe-nat-gateways --region us-east-1 \
     --filter Name=state,Values=available,pending
   ```

Conserve el bucket de estado Terraform, tabla DynamoDB y rol OIDC del
`terraform/bootstrap`. Su costo es muy bajo y permiten reactivar AWS sin perder
el control de infraestructura. Cognito tampoco se modifica.

## 2. Backend y PostgreSQL local

1. Instale y abra Docker Desktop.
2. Cree el archivo privado de configuración:
   ```bash
   cd compira-back
   cp deployment/.env.local.example deployment/.env.local
   ```
3. Edite `deployment/.env.local` con `COGNITO_REGION`,
   `COGNITO_USER_POOL_ID` y `COGNITO_CLIENT_ID` existentes. No agregue llaves
   AWS: estas operaciones de Cognito no requieren `AWS_ACCESS_KEY_ID` ni
   `AWS_SECRET_ACCESS_KEY` en la aplicación.
4. Arranque PostgreSQL, las migraciones Liquibase y el API:
   ```bash
   docker compose --env-file deployment/.env.local \
     -f deployment/docker-compose.yml up --build -d
   docker compose --env-file deployment/.env.local \
     -f deployment/docker-compose.yml logs -f api
   ```
 ```bash
docker compose --env-file deployment/.env.local -f deployment/docker-compose.yml up --build -d
  ```
5. Verifique el API:
   ```bash
   curl http://localhost:8080/actuator/health
   ```
6. Para apagar y conservar datos: `docker compose -f deployment/docker-compose.yml down`.
   Para borrar la base local: añada `-v`.

## 3. Frontend local

En una segunda terminal:
```bash
cd compira-front
cp .env.example .env
npm ci
npm run dev
```

Verifique que `.env` tenga `VITE_API_URL=http://localhost:8080/api/v1`; abra la
URL de Vite, normalmente `http://localhost:5173`.

## 4. Flujo de ramas durante la pausa

1. Desarrolle en una rama de funcionalidad.
2. Abra PR a `dev`: los workflows hacen validación CI.
3. Promueva `dev` a `qa`: se repiten tests, cobertura, lint y build, pero no se
   crea ni modifica AWS.
4. Para probar integración, cada desarrollador actualiza sus ramas y ejecuta
   los pasos locales anteriores.

## 5. Reactivar AWS

A mediados de octubre, restaure los jobs `deploy` desde el historial Git,
complete nuevamente las variables del Environment `qa` y ejecute primero el
workflow backend. No borre `bootstrap` ni el estado remoto durante la pausa.

# Notificaciones en tiempo real — operación y retorno seguro

Actualización: 2026-10-29. Complementa las guías históricas del primer despliegue.

## Estado y alcance

Se conserva React/Vite en S3+CloudFront y Spring WebFlux en ECS Fargate,
Aurora PostgreSQL privada y Cognito. Se añade una **API WebSocket independiente**:
API Gateway mantiene conexiones; Lambda Python procesa conexión, desconexión,
latidos y entrega de eventos recibidos de SQS. DynamoDB registra conexiones y
tickets efímeros; no sustituye PostgreSQL ni contiene cuerpos de observaciones.
El frontend obtiene los avisos por la API autenticada al abrir, reconectar o
recibir `notifications.changed`. No consulta cada 30s mientras el canal está sano.
La consulta cada30s permanece únicamente como respaldo cuando no está habilitado
el canal o falla la conexión. No se reabre SSE a través de la entrada HTTP.

## Seguridad y contrato

- `POST /api/v1/notifications/realtime-ticket` exige Cognito y cuenta activa con rol
  COORDINATOR/COLLABORATOR. No acepta userId, destinatario ni token en el cuerpo.
- Respuesta200: `{ "url": "wss://.../qa?ticket=<aleatorio>", "expiresAt": "<ISO8601>" }`;
  Cache-Control:no-store. El ticket tiene32bytes aleatorios, sólo su SHA256 se
  guarda; se consume atómicamente una vez y caduca a60s o antes si vence la sesión.
- La conexión queda ligada al usuario resuelto por el backend. Se valida Origin
  exacto al conectar. No usar el JWT de Cognito en parámetros de URL.
- La autorización no se prolonga más allá de la expiración JWT ni2h. Se comprueba
  caducidad al entregar y al recibir ping, independientemente del borrado TTL.
- Cliente sólo envía `{ "action": "ping" }`; recibe pong o invalidación. No existe
  ruta para elegir destinatario, suscribirse a otro usuario ni emitir avisos.
- Eventos contienen type e identificador, no correo, nombre ni texto de tarea.
  Contenido/lecturas siguen protegidos por los permisos HTTP existentes.
- Logs de Gateway omiten URL/query, cabeceras y cuerpos; no registrar tickets.
- Reconexión progresiva1–30s, latido60s y espera de respuesta10s. Al vencer la
  sesión se obtiene una nueva autorización HTTP o se requiere iniciar sesión.
- Lambda atiende `$disconnect` best-effort y elimina conexiones con respuesta410.
  Se conservan varias pestañas/dispositivos; no se promete entrega exactamenteuna.

## Entrega fiable

Liquibase agrega `notification_outbox` sin cambiar ni eliminar avisos anteriores.
La inserción de aviso y outbox es una única sentencia y participa en la misma
transacción de la acción. Sólo se encolan avisos deliverable. Un trabajador cada
1s ve únicamente registros confirmados, toma hasta20con FOR UPDATE SKIP LOCKED,
envía a SQS y confirma entrega al transporte. Ante fallo aplica reintento5–300s.
SQS/Lambda tiene reintentos y respuestas parciales; tras5recepciones fallidas pasa
a DLQ(retención14d). El aviso sigue consultable en PostgreSQL aunque falle push.
Duplicados de transporte sólo producen relecturas idempotentes, no avisos nuevos.
Un usuario desconectado recupera pendientes al volver. Los avisos programados
siguen dependiendo de su evaluador; WebSocket no convierte los vencimientos en
acciones generadas por el navegador. No se garantiza latencia cero.

## Infraestructura separada y despliegue

Módulo `deployment/terraform/realtime`, estado `qa/realtime/terraform.tfstate` en
el bucket/tabla de bloqueo ya existentes. La cuenta QA tiene10ejecuciones Lambda concurrentes y exige conservar10sin
reservar: usar concurrencia no reservada(-1), con SQS limitado a5. Los logs de
WebSocket requieren además un rol regional de API Gateway hacia CloudWatch. Se
verificó que el ajuste estaba vacío antes de administrarlo; el rol usa las siete
acciones CloudWatch del permiso estándar AmazonAPIGatewayPushToCloudWatchLogs
(incluye crear el grupo de validación), a nivel regional, y tiene protección
prevent_destroy. No permite administrar IAM ni acceder a datos de aplicación.
Referencia: https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonAPIGatewayPushToCloudWatchLogs.html. Si otra pila ya posee
ese ajuste, reutilizar/importar su gestión en vez de sobrescribirlo. Este ajuste
es regional/compartido: nunca eliminarlo como parte de una reversión de aplicación.

No aplicar ni destruir los estados
service/cognito/frontend para incorporar este módulo.

1. Confirmar tag remoto de retorno en ambos repos y guardar task definition,
   digest ECR y versión frontend anteriores (ver sección siguiente).
2. Pasar pruebas Java/PostgreSQL, Lambda, frontend, contrato y escenarios reales
   HTTP/WebSocket; integrar por PR feature→dev→qa con CI verde.
3. Preparar backend.hcl a partir del ejemplo, tfvars con región, proyecto,
   ambiente, nombre del task role ECS existente y lista exacta de orígenes HTTPS.
4. `terraform init -backend-config=backend.hcl`; `terraform plan -out=realtime.tfplan`.
   Revisar que sólo crea recursos del canal y adjunta una policy mínima al task
   role, sin reemplazar ECS, Aurora, Cognito ni publicar la red de la base.
5. Aplicar únicamente el plan revisado. Anotar outputs. IAM backend sólo PutItem
   para claves TICKET#* y SendMessage a su cola; Lambda sólo registro, su cola,
   logs y ManageConnections para su API/stage.
6. BackendQA: variables de GitHub environment qa:
   NOTIFICATIONS_REALTIME_ENABLED=true, NOTIFICATIONS_REALTIME_URL(outputwss),
   NOTIFICATIONS_REALTIME_TABLE(outputtabla), NOTIFICATIONS_REALTIME_QUEUE_URL.
   qa-backend.sh conserva configuración existente y añade estos valores explícitos.
   Desplegar backend y verificar salud, migración y digest correcto.
7. FrontendQA: VITE_NOTIFICATIONS_REALTIME_ENABLED=true; desplegar después del
   endpoint listo. La URL WebSocket proviene del ticket, no se incrusta un JWT.
8. Verificar conexión autorizada, rechazo sin ticket/origen ajeno/replay,
   entrega entre usuarios aislados, recuperación tras desconexión y ausencia de
   consultas30s durante conexión sana. Refrescar pestañas antiguas.

Los pipelines validan Terraform y pruebas Lambda. Terraform apply sigue siendo
una operación de infraestructura explícita: el pipeline de la aplicación no
adquiere permisos amplios para crear IAM/API/DynamoDB. Las variables anteriores
no son secretos; las credenciales AWS permanecen en OIDC/roles y gestor local.

## Punto de retorno y cómo volver

Tag anotado **respaldo/qa-antes-websocket-20261029**, publicado en ambos remotos:
- backend QA6c751414c95bd8433d8ce2efe3c4e19a245cedce;
  task definition compira-qa-task:11;
  digest sha256:3780310ce9cb1925d5cf845a15590b12503c6f5079757dff15e5789a78b7c107.
- frontend QA3e7f6e718225c81b83dc7e51487a4b3f2a344c38;
  bundle /assets/index-mCfN5ram.js.

Un tag no revierte AWS ni la base de datos. Primero se puede **apagar el canal**:
NOTIFICATIONS_REALTIME_ENABLED=false en backend, VITE_NOTIFICATIONS_REALTIME_ENABLED=false
en frontend y volver a desplegar QA por workflow_dispatch sobre qa. El frontend
regresa a consultas30s y los avisos almacenados permanecen intactos. Durante el
apagado backend el cliente ya activo hará fallback y reintentará conectar.
Para dejar de aceptar conexiones existentes deshabilitar el event source mapping
SQS y cerrar conexiones registradas con ManageConnections; no borrar las colas.

Si es necesario volver al código: crear ramas de reversión desde dev, restaurar
por commits los cambios de esta entrega tomando como referencia ambos tags,
PRs→dev→qa y desplegar con controles verdes. No force-push, reset remoto ni push
al tag para disparar una publicación accidental. Recuperación operativa urgente:
volver al task definition11 usando procedimiento ECS aprobado y restaurar HTML
frontend del artefacto correspondiente; después reconciliar Git mediante PR.
Mantener la tabla outbox: es aditiva y el código previo la ignora; no ejecutar
rollback Liquibase destructivo ni eliminar avisos. Conservar colas/DLQ y revisar
mensajes pendientes antes de cualquier limpieza. Al reactivar pueden producirse
invalidaciones atrasadas pero las lecturas siguen siendo idempotentes.

## Operación y costos

API Gateway factura mensajes/minutos conectados, Lambda ejecuciones, DynamoDB
lecturas/escrituras, SQS solicitudes y CloudWatch logs. Hay un latido/minuto por
conexión; varias pestañas cuentan por separado. Revisar el consumo real antes de
prometer ahorro frente a polling; no se fija una cifra sin concurrencia/horas.
AlarmaCloudWatch sobre DLQ>0(no tiene destinatario SNS configurado): conectar un
canal operativo si se requieren avisos externos. Revisar antigüedad de cola,
errores/throttlingLambda, outboxpendiente/attempts y fallos de conexiones.
Retenciónlogs14d y DLQ14d. El historial de avisos y outbox entregada se conserva;
una política de depuración requiere revisión aparte, no se borra información aquí.

## Validaciones y evidencias

Las pruebas de Lambda simulan SDK, no certifican AWS. Las de backend usan
PostgreSQL real/Testcontainers; la prueba HTTP SDK con Postman y auditoría de
navegador separan el contrato local de la nube. Registrar resultados reales de
pipelines, plan aplicado, endpoint, digest, conexión y límites pendientes en el
informe de entrega. Nunca incluir JWT, tickets activos ni contraseñas.

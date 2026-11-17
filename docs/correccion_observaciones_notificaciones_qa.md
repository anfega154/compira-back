# Observaciones del coordinador y notificaciones en QA

Fecha de diagnóstico: 28 de octubre de 2026.

## Contrato de observaciones

`POST /api/v1/tasks/{taskId}/observations`

- Requiere token de acceso válido y cuenta activa.
- Coordinador del equipo de la tarea y colaborador responsable pueden registrar
  observaciones. No se habilita escritura para administradores ni usuarios ajenos.
- El cuerpo sigue siendo `{"content":"observacion"}`: texto obligatorio, máximo
  2000 caracteres. La respuesta de creación sigue siendo `201` con la observación.
- La observación conserva al autor autenticado y genera historial.
- `POST /api/v1/tasks/{taskId}/status` permanece exclusivo del colaborador.

La regla HTTP agrupaba observaciones con cambios de estado y exigía exclusivamente
COLLABORATOR. El caso de uso ya autorizaba al coordinador del equipo. Se separan
las reglas sin retirar los controles de acceso a la tarea del router/caso de uso.

## Evidencia de la corrección

- Antes del cambio: 35 casos de ResourceServerConfigTest, solo el nuevo caso de
  coordinador agregando observación falló con `403`.
- Después: `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --offline`,
  compilación exitosa y 334 pruebas sin fallos ni omisiones en los reportes XML
  de usecase, reactive-web, r2dbc-postgresql y cognito-identity-provider.
- La nueva matriz comprueba coordinador/colaborador autorizados para observación,
  administrador/anónimo bloqueados y cambio de estado restringido al colaborador.
- Las pruebas de AddTaskObservationAccessTest conservan el rechazo de colaborador
  ajeno y antiguo coordinador, sin escribir observación ni historial.
- Son pruebas locales; no certifican todavía el arreglo desplegado en AWS.

## Diagnóstico de notificaciones

Consultas AWS de solo lectura confirmaron:

- API `8cj0cthh07`, nombre `compira-qa-http-api`, protocolo `HTTP`.
- Integración `HTTP_PROXY` mediante `VPC_LINK`, límite de 30000 ms.
- ECS `compira-qa-task:9` activo con una tarea ejecutándose.
- Registros `/aws/apigateway/compira-qa`: múltiples respuestas `503` para
  `GET /api/v1/notifications/stream`, entre ellas a las 19:24:11 y 19:50:40 UTC.

El handler mantiene una respuesta SSE abierta hasta expirar el token, mientras que
HTTP API no soporta response streaming. El cliente interpreta el fallo como una
interrupción y reintenta. Esto explica el síntoma observado; no se ha certificado
todavía que se estén generando todos los avisos de negocio esperados.

Fuentes oficiales:

- https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-vs-rest.html
- https://docs.aws.amazon.com/apigateway/latest/developerguide/response-transfer-mode.html

### Opciones pendientes de aprobación

1. Cambio mínimo: el cliente consulta periódicamente `GET /api/v1/notifications`.
   Ya existe este contrato JSON, con destinatario autenticado, paginación y
   `Cache-Control: no-store`. No requiere reemplazar infraestructura; cambia
   actualización continua por actualización periódica y requiere ajuste frontend.
2. Mantener SSE: diseñar una entrada compatible con streaming (por ejemplo,
   REST API configurada para STREAM). Requiere revisar infraestructura, soporte
   Terraform, costes, seguridad y despliegue. Aumentar el timeout o añadir
   heartbeats al handler no habilita streaming en HTTP API.

No se cambió el frontend ni infraestructura AWS en esta corrección. No se guardó
el token del reporte, no se repitió una escritura contra la tarea real y no se
fusionaron ramas ni se promovió QA. Para validar en AWS tras publicar se necesita
una sesión vigente y registrar una observación controlada del coordinador.

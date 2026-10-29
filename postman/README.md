# Contratos HTTP auxiliares de tiempo real

`realtime-aws` modela PutItem y SendMessage a partir de los contratos del SDK AWS.
Es un fixture propio para operaciones precisas; no se reutilizó un mock externo.
No requiere cuenta Postman ni credenciales AWS reales.

```bash
postman simulation run postman/simulations/realtime-aws.sim.yaml --port 4952 --no-history
# En otra terminal con Java21:
./gradlew :aws-realtime:realtimeSimulationProbe -PrealtimeSimulationUrl=http://127.0.0.1:4952/aws
```

El SDK firma solicitudes locales con credenciales ficticias. Se ejercitan ticket
con hash y expiración, envío de envelope/MD5 y errores de tabla y cola. Revisar
los request events de Postman:200,200,400,400. El probe rechaza hosts no loopback.
Esto no prueba IAM, Cognito ni entrega AWS; ver runbook y evidencia cloud final.

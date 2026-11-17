FROM gradle:9.5.1-jdk21-alpine AS build

WORKDIR /workspace

COPY . .

RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/applications/app-service/build/libs/compira-back.jar /app/compira-back.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"

USER appuser

ENTRYPOINT ["/bin/sh", "-c", "java $JAVA_OPTS -jar /app/compira-back.jar"]

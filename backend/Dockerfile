# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw \
    && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache wget \
    && addgroup -S rutafija \
    && adduser -S -G rutafija -h /app rutafija

WORKDIR /app
COPY --from=build --chown=rutafija:rutafija /workspace/target/ruta-fija-backend-*.jar app.jar

USER rutafija
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=210s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

# syntax=docker/dockerfile:1.7
# One parameterised, multi-stage Dockerfile for every service:
#   docker build -f docker/Dockerfile --build-arg SERVICE=booking-service -t booking-service .
# Stage 1 builds only the requested module (+ the modules it depends on), stage 2 is a small JRE image.

ARG JAVA_VERSION=21

FROM maven:3.9.9-eclipse-temurin-${JAVA_VERSION} AS build
ARG SERVICE
WORKDIR /workspace

# 1) poms first => dependency layer is cached until a pom changes
COPY pom.xml ./
COPY common-lib/pom.xml common-lib/
COPY grpc-api/pom.xml grpc-api/
COPY discovery-server/pom.xml discovery-server/
COPY api-gateway/pom.xml api-gateway/
COPY catalog-service/pom.xml catalog-service/
COPY seat-inventory-service/pom.xml seat-inventory-service/
COPY booking-service/pom.xml booking-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${SERVICE} -am dependency:go-offline -DskipTests || true

# 2) sources
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl ${SERVICE} -am package -DskipTests \
 && cp ${SERVICE}/target/${SERVICE}-*.jar /workspace/app.jar \
 && java -Djarmode=tools -jar /workspace/app.jar extract --layers --launcher --destination /workspace/extracted

# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre-noble AS runtime
ARG SERVICE
LABEL org.opencontainers.image.title="bmtickets-${SERVICE}" \
      org.opencontainers.image.vendor="BookMyTickets" \
      org.opencontainers.image.source="https://github.com/your-org/ticket-booking-platform"

RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app

# Spring Boot layers: least -> most frequently changing (better layer reuse on push/pull)
COPY --from=build --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/application/ ./

USER 10001

# Container-aware JVM: heap sized from the cgroup memory limit, fail fast on OOM so Kubernetes restarts the pod.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

EXPOSE 8080 9090
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

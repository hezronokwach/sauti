# The backend image. Build from this folder: docker build -t sauti-hms .
# Base images are pinned by version and digest; bump them deliberately.

# Stage 1: build the jar. Dependencies come first so Docker can reuse that layer.
FROM eclipse-temurin:21.0.13.1_2-jdk-alpine-3.24@sha256:c07bb82e004a166b5d3d63b84ad70e2e822f228505e2e0c0b8dfdc2890d22f13 AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw .mvn/jvm.config && chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/main/ src/main/
# Tests run in CI, where they have a database. This stage only packages.
RUN ./mvnw -B -ntp package -Dmaven.test.skip=true

# Stage 2: only a Java runtime and the jar, run as a user without a login or a shell profile.
FROM eclipse-temurin:21.0.13.1_2-jre-alpine-3.24@sha256:5d7689f47d670aca766b9503a70bccc2d73b4eb9221ae8f714ae958313c4ce70
RUN addgroup -S -g 10001 sauti && adduser -S -D -H -u 10001 -G sauti sauti
WORKDIR /app
COPY --from=build --chown=root:root --chmod=444 /build/target/sauti-hms-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
# A slow first start runs the database migrations on a small instance.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1
# Java may use 70% of the container's memory; if it runs out, exit so the host restarts it.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

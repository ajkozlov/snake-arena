# ── Build stage ───────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies before copying source
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build application (skip tests — run them in CI before building the image)
COPY src ./src
RUN mvn package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /build/target/quarkus-app /app/quarkus-app

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/quarkus-app/quarkus-run.jar"]

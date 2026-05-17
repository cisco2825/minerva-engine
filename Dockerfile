# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom first so dependency layer is cached
COPY pom.xml .

# Pre-fetch dependencies (cached unless pom changes)
RUN mvn dependency:go-offline -q

# Copy source
COPY ruleengine-v2/src ./ruleengine-v2/src

# Build, skip tests (tests need a running DB)
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# UseContainerSupport lets the JVM respect cgroup memory limits on Render's free tier
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

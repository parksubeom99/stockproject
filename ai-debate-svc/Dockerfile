# ─── Stage 1: Build ───────────────────────────────────
FROM gradle:8.6-jdk17 AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts* ./
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ─── Stage 2: Run ─────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-jar", "app.jar"]

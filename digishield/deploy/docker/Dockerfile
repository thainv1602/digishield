# syntax=docker/dockerfile:1

# ---- Build stage ---------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy Gradle wrapper + build scripts first for better layer caching.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc

# Copy sources.
COPY contracts contracts
COPY shared shared
COPY modules modules
COPY boot boot

RUN chmod +x ./gradlew \
    && ./gradlew :boot:app:bootJar --no-daemon

RUN cp "$(ls boot/app/build/libs/*.jar | head -n 1)" /workspace/app.jar

# ---- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Non-root user.
RUN useradd -r -u 1001 -g root digishield

# Layered jar - copied as a single artifact, run with java -jar.
COPY --from=build /workspace/app.jar app.jar

USER 1001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--spring.profiles.active=api"]

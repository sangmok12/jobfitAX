FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace
COPY backend/ ./
RUN chmod +x gradlew && ./gradlew bootJar playwrightInstall --no-daemon

FROM mcr.microsoft.com/playwright/java:v1.63.0-noble

WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
COPY --from=builder /root/.cache/ms-playwright /ms-playwright

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

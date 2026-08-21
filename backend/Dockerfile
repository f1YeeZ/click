# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,id=clicker-maven,target=/root/.m2/repository \
    mvn -B -ntp -U -s /root/.m2/settings.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl && addgroup -S clicker && adduser -S clicker -G clicker
WORKDIR /app
COPY --from=build /workspace/target/mouse-hub-api-*.jar app.jar
RUN mkdir -p /data/mouse-images && chown -R clicker:clicker /app /data
USER clicker
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=5 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]

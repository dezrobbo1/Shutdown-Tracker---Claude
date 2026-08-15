# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY services/api/pom.xml services/api/pom.xml
COPY services/project-worker/pom.xml services/project-worker/pom.xml
COPY services/api/src services/api/src

RUN mvn -pl services/api -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=review
ENV PORT=8080

COPY --from=build /workspace/services/api/target/api-*.jar /app/shutdown-tracker-api.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/shutdown-tracker-api.jar"]

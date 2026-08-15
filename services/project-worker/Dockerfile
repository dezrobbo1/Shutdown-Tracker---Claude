# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY packages/project-import-contract/pom.xml packages/project-import-contract/pom.xml
COPY packages/project-export-contract/pom.xml packages/project-export-contract/pom.xml
COPY services/api/pom.xml services/api/pom.xml
COPY services/project-worker/pom.xml services/project-worker/pom.xml

COPY packages/project-import-contract/src packages/project-import-contract/src
COPY packages/project-export-contract/src packages/project-export-contract/src
COPY services/project-worker/src services/project-worker/src

RUN mvn -B -pl services/project-worker -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV PORT=8081

COPY --from=build /workspace/services/project-worker/target/project-worker-*.jar /app/shutdown-tracker-project-worker.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/shutdown-tracker-project-worker.jar"]

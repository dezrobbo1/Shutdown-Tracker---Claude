# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# The root pom declares every module, so the Maven reactor needs each module pom
# present before -pl/-am can select a subset. Copying poms before source keeps
# dependency resolution in an earlier layer than application code changes.
COPY pom.xml .
COPY packages/project-import-contract/pom.xml packages/project-import-contract/pom.xml
COPY packages/project-export-contract/pom.xml packages/project-export-contract/pom.xml
COPY services/api/pom.xml services/api/pom.xml
COPY services/project-worker/pom.xml services/project-worker/pom.xml

# The worker depends on both shared contract modules, so their sources build with it.
COPY packages/project-import-contract/src packages/project-import-contract/src
COPY packages/project-export-contract/src packages/project-export-contract/src
COPY services/project-worker/src services/project-worker/src

RUN mvn -B -pl services/project-worker -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV PORT=8081

COPY --from=build /workspace/services/project-worker/target/project-worker-*.jar /app/shutdown-tracker-project-worker.jar

# Worker handoff authentication fails closed. Supply the shared secret at run time
# through SHUTDOWN_TRACKER_WORKER_AUTH_SHARED_SECRET; never bake it into the image.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/shutdown-tracker-project-worker.jar"]

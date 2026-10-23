FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:8-jre-alpine
RUN apk add --no-cache ffmpeg curl
WORKDIR /app
COPY --from=build /workspace/target/disp-ems-server-*.jar /app/disp-ems-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/disp-ems-server.jar"]

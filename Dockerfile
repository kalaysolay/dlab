FROM gradle:8.11-jdk21 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/damulab-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

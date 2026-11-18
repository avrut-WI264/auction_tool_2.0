FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY . .

RUN mvn --batch-mode verify

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:InitialRAMPercentage=25", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

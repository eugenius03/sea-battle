FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /usr/src/app

COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /usr/src/app/target/*.jar app.jar

# Default profile inside container (can be overridden at runtime)

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
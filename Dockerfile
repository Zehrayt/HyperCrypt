# 1. Aşama: Build (Resmi Java 21 Maven İmajı)
FROM maven:3.9.6-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# 2. Aşama: Run (Java 21 Runtime)
FROM eclipse-temurin:21-jdk-alpine
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xmx300m","-Xms256m","-jar","/app.jar"]
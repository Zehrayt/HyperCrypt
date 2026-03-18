FROM maven:3.8.5-openjdk-17 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:17-jdk-slim
COPY --from=build /target/*.jar app.jar
EXPOSE 8080
# Render'ın kısıtlı RAM'i (512MB) için Java'ya sınır koyuyoruz
ENTRYPOINT ["java","-Xmx300m","-Xms256m","-jar","/app.jar"]
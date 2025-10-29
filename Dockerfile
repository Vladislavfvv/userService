#FROM openjdk:21-jdk-slim
#WORKDIR /app
#COPY target/userService-0.0.1-SNAPSHOT.jar app.jar
#EXPOSE 8080
#ENTRYPOINT ["java", "-jar", "app.jar"]

# 2 вариант запуска через докер(автоматом распакует):

# === Этап 1: Сборка приложения ===
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# === Этап 2: Запуск приложения ===
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# 3 вариант запуска через докер(автоматом распакует):

## === Этап 1: Сборка приложения ===
#FROM maven:3.9.6-eclipse-temurin-21 AS build
#WORKDIR /app
#
## Скопируем pom.xml и скачаем зависимости
#COPY pom.xml .
#RUN mvn dependency:go-offline
#
## Копируем исходники и собираем jar
#COPY src ./src
#RUN mvn clean package -DskipTests
#
## === Этап 2: Запуск приложения ===
#FROM eclipse-temurin:21-jdk
#WORKDIR /app
#
## Копируем готовый jar из предыдущего этапа
#COPY --from=build /app/target/*.jar app.jar
#
#EXPOSE 8080
#ENTRYPOINT ["java", "-jar", "app.jar"]

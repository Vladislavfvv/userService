# === Этап 1: Сборка приложения ===
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Копирование pom.xml для кэширования зависимостей
COPY pom.xml .
RUN mvn -B dependency:go-offline || true

# Копирование исходного кода
COPY src ./src

# Сборка проекта (пропускаем тесты для ускорения сборки образа)
RUN mvn -B clean package -DskipTests

# === Этап 2: Запуск приложения ===
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Копирование JAR файла из этапа сборки
COPY --from=build /app/target/*.jar app.jar

# Порт для User Service (8080 согласно application.properties)
EXPOSE 8080

# Переменная окружения для профиля
ENV SPRING_PROFILES_ACTIVE=docker

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]

# по шагам:
# 1. Скачает базовый образ openjdk:21
# 2. Скопирует мой JAR-файл в образ как app.jar
# 3. Установит точку входа (команду запуска)
# 4. Сохранит результат как user-service:latest

# После сборки образа можно:
# Запустить контейнер из этого образа:
# docker run -d -p 8080:8080 user-service:latest
# Просмотреть все образы:
# docker images
# Запушить в реестр (Docker Hub, GitLab Registry и т.д.):
#     docker tag authentication-service:latest ваш-логин/authentication-service:latest
#     docker push ваш-логин/authentication-service:latest 
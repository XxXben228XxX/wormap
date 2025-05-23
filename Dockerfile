# Етап 1: Збірка
FROM maven:3.9.4-eclipse-temurin-21 AS build
WORKDIR /app
COPY IdeaProjects/demo1/pom.xml pom.xml
COPY IdeaProjects/demo1/src src/
RUN mvn clean install -DskipTests

# Етап 2: Запуск
FROM eclipse-temurin:21-jdk-jammy
ENV JAVA_OPTS="-Xmx512m -Xms256m"
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT} -jar app.jar"]

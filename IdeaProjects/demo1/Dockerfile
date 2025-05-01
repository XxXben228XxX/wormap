FROM openjdk:21-jdk-slim

ENV JAVA_OPTS="-Xmx512m -Xms256m"

COPY target/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

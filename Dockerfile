FROM eclipse-temurin:17.0.10_7-jre

WORKDIR /app

COPY ./transformation-framework/target/transformation-framework-1.0-SNAPSHOT.jar .

ENTRYPOINT ["java", "-jar", "transformation-framework-1.0-SNAPSHOT.jar"]

EXPOSE 9094
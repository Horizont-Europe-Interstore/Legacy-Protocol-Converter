FROM eclipse-temurin:17.0.10_7-jre

WORKDIR /app

COPY ./transformation-framework/target/transformation-framework-1.0.jar .
COPY ./log-config/log4j2.xml ./log-config/log4j2.xml

ENV KUMULUZEE_LOGS_CONFIGFILELOCATION=./log-config/log4j2.xml

ENTRYPOINT ["java", "-jar", "transformation-framework-1.0.jar"]

EXPOSE 9094
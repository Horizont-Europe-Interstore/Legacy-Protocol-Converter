FROM eclipse-temurin:17-jre

WORKDIR /app

COPY ./transformation-framework/target/legacy-protocol-converter.jar .
COPY ./log-config/log4j2.xml ./log-config/log4j2.xml

ARG API_KEY
ENV KUMULUZEE_LOGS_CONFIGFILELOCATION=./log-config/log4j2.xml API-KEY=$API_KEY

ENTRYPOINT ["java", "-jar", "legacy-protocol-converter.jar"]

EXPOSE 9094
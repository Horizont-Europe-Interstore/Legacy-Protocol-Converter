FROM eclipse-temurin:17-jre

WORKDIR /app

# Install Python and pip
RUN apt-get update && \
    apt-get install -y python3 python3-venv python3-pip && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Create a virtual environment and install dependencies
RUN python3 -m venv /app/venv && \
    /app/venv/bin/pip install --upgrade pip && \
    /app/venv/bin/pip install pymodbus fastapi uvicorn

# Ensure Python uses the virtual environment
ENV PATH="/app/venv/bin:$PATH"

COPY ./transformation-framework/target/legacy-protocol-converter.jar .
COPY ./log-config/log4j2.xml ./log-config/log4j2.xml
COPY ./pymodbus_script.py /app/pymodbus_script.py

ARG API-KEY
ENV KUMULUZEE_LOGS_CONFIGFILELOCATION=./log-config/log4j2.xml API-KEY=$API-KEY

ENTRYPOINT ["java", "-jar", "legacy-protocol-converter.jar"]

EXPOSE 9094
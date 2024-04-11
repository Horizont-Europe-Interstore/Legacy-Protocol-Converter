# Legacy Systems Protocol Converter (LPC)

The Legacy Systems Protocol Converter, initially developed within the Horizon Europe Interstore project, acts as a
middleware, allowing devices that use different communication protocols to exchange data with EMS systems that use the
IEEE2030.5 standard. It supports:

- IEEE2030.5 communication: This is the primary function of the Legacy Protocol Converter. It can handle IEEE2030.5
  messages in both JSON and XML formats.
- Next-generation NATS messaging: This is a new messaging protocol that the converter uses to communicate with devices
  and EMS systems. It is designed to be more efficient and scalable than traditional protocols like REST over HTTP.
- MQTT and Modbus protocols: These are common protocols used by many devices. The Legacy Protocol Converter can
  translate messages from these protocols into the IEEE2030.5 format for use with EMS systems.

Key features of Legacy Protocol Converter:

- Built-in transformation framework: This framework allows users to define how incoming messages should be transformed
  into the outgoing IEEE2030.5 format. This is important because different devices and systems may use different message
  formats.
- Configuration file: The converter uses a configuration file to specify connection details for NATS, MQTT, and Modbus
  devices. Users can also define transformations within the configuration file.
- Flexibility: The converter can support multiple transformations, each with different incoming and outgoing
  connections, message formats, and structures. This allows for a high degree of flexibility in how the converter is
  used.

The Legacy Protocol Converter can be deployed and run using:

- A Docker container. Pre-built Docker images are available on Docker Hub, a custom Docker image can be build.
- Using a Java JAR file and execute it on any computer with OpenJDK Java Runtime Environment.
- Compile and build the project out of source code.
- It is possible to integrate LPC in custom projects by including packages.

LPC can be executed on-premise or in the cloud. It supports a variety of environments, including Kubernetes, Docker and
classical virtual machines, as well as bare-metal.

Overall, the Legacy Protocol Converter is a tool for enabling communication between devices and EMS systems that use
different communication protocols. It supports the latest IEEE2030.5 standard and provides a flexible and efficient way
to translate messages between different protocols.

## Running

### Running LPC with pre-built JAR

JRE 17 is required to run the JAR file.

JAR file is located in the official GitHub repository of LPC
here: https://github.com/Horizont-Europe-Interstore/Legacy-Protocol-Converter/blob/master/lpc-1.0.jar

You can download the JAR file and then run the following command to start the LPC:

```bash
java -jar lpc-1.0.jar
```

This will take the configuration files from ```./conf``` folder. If you want to specify a different folder, you can do
so by providing the path as an argument:

```bash
java -DCONFIGURATION=/path/to/config -jar lpc-1.0.jar
```

This will take the configuration files from ```/path/to/config``` folder.

### Running LPC with pre-built Docker image

Docker is required to run the Docker image.

Docker image is located on Docker Hub here: https://hub.docker.com/r/interstore/legacy-protocol-converter

When using Docker to run the LPC, configuration folder must be mounted to the container.
You can run the Docker image with the following command:

```bash
docker run -v /path/to/config:/app/conf interstore/legacy-protocol-converter
```

This will mount the configuration folder `/path/to/config` to the container and LPC will read the configuration files
from that folder.

### How to start NATS server in Docker

NATS image is available on Docker Hub: https://hub.docker.com/_/nats

To start NATS server in Docker, you can use the following command:

```bash
docker run -d --name nats-main -p 4222:4222 -p 6222:6222 -p 8222:8222 nats:latest
```

Then in order for the LPC to connect to the NATS server, you must configure the two containers to use the same network.

## Configuration
### Transforming from JSON to XML

We will be transforming JSON structure of IncomingEvent to
XML structure of OutgoingEvent. These two structures are used just as examples.

JSON IncomingEvent:

```json
{
  "datetime": "28-08-2023 12:00:35",
  "status": "active",
  "start": "28-08-2023",
  "duration": 900
}
```

XML IEEE2030.5 Event:

```xml

<Event>
    <creationTime>1702909917932</creationTime>
    <EventStatus>
        <currentStatus>1</currentStatus>
        <dateTime>1693216835000</dateTime>
        <potentiallySuperseded>false</potentiallySuperseded>
    </EventStatus>
    <interval>
        <duration>900</duration>
        <start>1693216835000</start>
    </interval>
</Event>
```

```yaml
connections:
  - name: NATS-connection
    type: NATS
    host: nats://localhost
    port: 4222
    reconnect: true
  - name: MQTT-connection
    type: MQTT
    ssl:
      default: true
    host: localhost
    port: 8883
    username: username
    password: password
transformations:
  - name: JSON IncomingEvent to XML IEEE2030.5 Event
    description: Example showing transformation of messages from JSON to XML
    connections:
      incoming-connection:
        - MQTT-connection
      incoming-topic: topic1
      incoming-format: JSON
      outgoing-connection:
        - NATS-connection
      outgoing-topic: event/listen
      outgoing-format: XML
    to-outgoing:
      to-topic: event/send
      message: '<Event> 
          <creationTime>$timestamp</creationTime> 
          <EventStatus>
            <currentStatus>
              <lpc:mapping>
                <path type="integer">/status</path>
                <values>["scheduled", "active", "cancelled", "cancelled_with_r", "superseded"]</values>
              </lpc:mapping>
            </currentStatus>
            <dateTime>
              <lpc:mapping>
                <path type="datetime">datetime</path>
                <pattern>dd-MM-yyyy HH:mm:ss</pattern>
              </lpc:mapping>
            </dateTime>
            <potentiallySuperseded>false</potentiallySuperseded>
          </EventStatus>
          <interval> 
            <duration>
              <lpc:mapping>
                <path type="integer">duration</path>
              </lpc:mapping>
            </duration>
            <start>
              <lpc:mapping>
                <path type="date">/start</path>
                <pattern>dd-MM-yyyy</pattern>
              </lpc:mapping>
            </start>
          </interval>
        </Event>
        '
```

With this transformation we are specifying that the MQTT-connection will subscribe to topic ```topic1``` and LPC will
transform the message to XML
structure, and it will send the transformed message using NATS-connection to topic ```event/send```.

We can see that the mapping is done with the help of XML tag ```<lpc:mapping>```.

For setting the value of ```OutgoingEvent/currentStatus```, we are converting value ```"active"``` to integer ```1```.
Because the ```IncomingEvent/status``` is string, and we have provided ```values``` options, this means we will map
to the integer based on which index is the value of ```IncomingEvent/status```. So ```"active"``` maps to ```1```.

For setting the value of ```OutgoingEvent/datetime```, we are converting from ```datetime``` to ```long```.
Because ```type``` is ```datetime```, we need to provide the pattern of the incoming value, so LPC know how to parse the
value, hence ```dd-MM-yyyy HH:mm:ss```. Also note that the leading ```/``` is not needed.
Same applies for setting the value of ```OutgoingEvent/interval/start```, but here we are using ```date```.

For setting the value of ```OutgoingEvent/interval/duration``` we just need to specify ```type```, because mapping is
done 1 on 1.

There is also reserved keyword ```$timestamp``` which is used to set the value of ```OutgoingEvent/creationTime``` to
the current time in milliseconds.

### Transforming from XML to JSON

For showcasing this we will use the example above but IncomingEvent in XML format and IEEE2030.5 Event in JSON format.

XML IncomingEvent:

```xml

<IncomingEvent>
    <datetime>28-08-2023 12:00:35</datetime>
    <status>active</status>
    <start>28-08-2023</start>
    <duration>900</duration>
</IncomingEvent>
```

JSON IEEE2030.5 Event:

```json
{
  "creationTime": 1702909917932,
  "eventStatus": {
    "currentStatus": 1,
    "dateTime": 1693216835000,
    "potentiallySuperseded": false
  },
  "interval": {
    "duration": 900,
    "start": 1693216835000
  }
}
```

```yaml
connections:
  - name: NATS-connection
    type: NATS
    host: nats://localhost
    port: 4222
    reconnect: true
  - name: MQTT-connection
    type: MQTT
    ssl:
      default: true
    host: localhost
    port: 8883
    username: username
    password: password
transformations:
  - name: XML IncomingEvent to JSON IEEE2030.5 Event
    description: Example showing transformation of messages from XML to JSON
    connections:
      incoming-connection:
        - MQTT-connection
      incoming-topic: topic1
      incoming-format: XML
      outgoing-connection:
        - NATS-connection
      outgoing-topic: event/listen
      outgoing-format: JSON
    to-outgoing:
      to-topic: event/send
      message: '{
            "creationTime": $timestamp,
            "eventStatus": {
                "currentStatus": {
                  "lpc:mapping": {
                    "path": "/IncomingEvent/status",
                    "type": "integer",
                    "values": ["scheduled", "active", "cancelled", "cancelled_with_r", "superseded"]
                  }
                },
                "dateTime": {
                  "lpc:mapping": {
                    "path": "/IncomingEvent/datetime",
                    "type": "datetime",
                    "pattern": "dd-MM-yyyy HH:mm:ss"
                  }
                },
                "potentiallySuperseded": false
              },
              "interval": {
                "duration": {
                  "lpc:mapping": {
                    "path": "/IncomingEvent/duration",
                    "type": "integer"
                  }
                },
                "start": {
                  "lpc:mapping": {
                    "path": "/IncomingEvent/start",
                    "type": "date",
                    "pattern": "dd-MM-yyyy"
                  }
                }
              }
            }
        '
```

Here we see the mapping is similar as in the example above, but because it is in JSON
format, we are using ```lpc:mapping``` key.

### Transforming from Modbus to JSON

For this example, we will use the Modbus connection to read data from the device and transform it to JSON IEEE2030.5
Event.

IncomingEvent structure in Modbus:

- register address 31000: datetime
- register address 31010: status
- register address 31020: start
- register address 31030: duration

JSON IEEE2030.5 Event:

```json
{
  "creationTime": 1702909917932,
  "eventStatus": {
    "currentStatus": 1,
    "dateTime": 1693216835000,
    "potentiallySuperseded": false
  },
  "interval": {
    "duration": 900,
    "start": 1693216835000
  }
}
```

```yaml
connections:
  - name: NATS-connection
    type: NATS
    host: nats://localhost
    port: 4222
    reconnect: true
  - name: Modbus-connection
    type: Modbus
    device: /dev/ttya
    baud-rate: 9600
    data-bits: 8
transformations:
  - name: XML IncomingEvent to JSON IEEE2030.5 Event
    description: Example showing transformation of messages from XML to JSON
    connections:
      incoming-connection:
        - Modbus-connection
      outgoing-connection:
        - NATS-connection
      outgoing-topic: event/listen
      outgoing-format: JSON
    to-outgoing:
      to-topic: event/send
      message: '{
            "creationTime": $timestamp,
            "EventStatus": {
                "currentStatus": {
                  "lpc:mapping": {
                    "path": "31010",
                    "type": "int8"
                  }
                },
                "dateTime": {
                  "lpc:mapping": {
                    "path": "31000",
                    "type": "int64"
                  }
                },
                "potentiallySuperseded": false
              },
              "interval": {
                "duration": {
                  "lpc:mapping": {
                    "path": "31030",
                    "type": "int32"
                  }
                },
                "start": {
                  "lpc:mapping": {
                    "path": "31020",
                    "type": "int64"
                  }
                }
              }
            }
        '
    interval-request:
      interval: 10000
      request:
        modbus-function-code: 3
        modbus-device-id: 1
        modbus-registers:
          - register-address: 31000
            type: int64
          - register-address: 31010
            type: int8
          - register-address: 31020
            type: int64
          - register-address: 31030
            type: int32
```

Here we see that the mapping is done with register addresses, and we are specifying the type of the value at the
register address.

Here we have specified that LPC will send a request to the device every 10 seconds to read the data from the registers.
LPC will for each register send new request with function code specified at ```modbus-function-code``` to specified
device at```modbus-device-id```.

## Deployment

LPC can be deployed as a JAR or as a Docker container.
When deploying, path to the configuration file must be provided either as an argument or mounted as a volume.
Default path to the configuration folder is ```./conf```.
In this folder, multiple configuration files can be placed, and LPC will read all of them.

### Configuration of logging

Configuration of logging is done with the help of Log4j library. So for configuring logging,
one should create file log4j.xml and config logging per
Log4j [documentation](https://logging.apache.org/log4j/2.x/manual/configuration.html).

Default configuration is following:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration name="kumuluzee">
    <Appenders>
        <Console name="console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d %p -- %c -- %marker %m %X %ex %n"/>
        </Console>

        <File name="file_debug_app" fileName="logs/debug_app.log">
            <PatternLayout pattern="%d %p -- %c -- %marker %m %X %ex %n"/>
        </File>
        <File name="file_info_app" fileName="logs/app.log">
            <PatternLayout pattern="%d %p -- %c -- %marker %m %X %ex %n"/>
        </File>

        <File name="file_lpc" fileName="logs/lpc.log">
            <PatternLayout pattern="%d %p -- %c -- %marker %m %X %ex %n"/>
        </File>
    </Appenders>

    <Loggers>
        <Root level="debug">
            <AppenderRef ref="console" level="info"/>
            <AppenderRef ref="file_info_app" level="info"/>
            <AppenderRef ref="file_debug_app" level="debug"/>
        </Root>

        <Logger name="si.sunesis.interoperability" level="debug">
            <AppenderRef ref="file_lpc"/>
        </Logger>
    </Loggers>
</Configuration>
```

This will print out logs of level INFO or above in the console and in the file `app.log`.
This will print out logs of level DEBUG or above in the file `debug_app.log`.
Separate log file for Legacy Protocol Converter is created in order for easier troubleshooting of LPC at `lpc.log`.

If using different configuration, one should specify the location of the configuration.

### Building and running the LPC using JAR

Build the JAR:

```bash
mvn clean package
```

**OPTIONAL** If using the custom configuration file for the logging,
then the environment variable with the path to the file must be
set: `KUMULUZEE_LOGS_CONFIGFILELOCATION=path/to/file/log4j2.xml`

Deploy the application:

```bash
java -jar transformation-framework/target/transformation-framework-1.0-SNAPSHOT.jar
```

This will take the configuration files from ```./conf``` folder. If you want to specify a different folder, you can do
so by providing the path as an argument:

```bash
java -DCONFIGURATION=/path/to/config -jar transformation-framework/target/transformation-framework-1.0-SNAPSHOT.jar
```

This will take the configuration files from ```/path/to/config``` folder.

### Building and running the LPC using Docker

Build the JAR:

```bash
mvn clean package
```

Build the Docker image:

```bash
docker build -t lpc:latest .
```

**OPTIONAL** If using the custom configuration file for the logging, then this file must be mounted to the container
before running it.
It must be mounted to `/app/log-config/log4j2.xml` like this:

```bash
docker run -v /path/to/log4j2.xml:/app/log-config/log4j2.xml lpc:latest
```

Run the Docker container and mount configuration folder:

```bash
docker run -v /path/to/config:/app/conf lpc:latest
```

Pre-built Docker images are available here: https://hub.docker.com/r/interstore/legacy-protocol-converter
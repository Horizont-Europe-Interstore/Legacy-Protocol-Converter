# Legacy Protocol Converter (LPC)

Legacy Protocol Converter is a framework designed to convert messages 
from one protocol to another. Currently, it supports Modbus, MQTT and NATS connections.
LPC can be configured through configuration file in YAML format.

General format of the configuration file is following:
```yaml
lpc:
  connections:
    -
    -
  transformations:
    -
    -
```
## Connections
Each incoming/outgoing connection must be configured in the list of connections so the LPC 
knows how to connect.
Possible options for each connection are following:
```yaml
lpc:
  connections:
    - name: string
      type: NATS/MQTT/Modbus
      host: string
      port: integer
      ssl:
        default: true/false
      username: string
      password: string
      reconnect: true/false
      device: string
      baud-rate: integer
      data-bits: integer
      parity: none/even/odd/space/mark
      stop-bits: integer
  transformations:
    -...
```
**name** and **type** are required keys. 
### NATS
Currently supported parameters for connection with NATS are **host**, 
**port**, **username**, **password** and **reconnect**.

Example of configuration for NATS:
```yaml
lpc:
  connections:
    - name: NATS-connection
      type: NATS
      host: nats://localhost
      port: 4222
      username: userTest
      password: testUser
      reconnect: true
  transformations:
    -...
```

### MQTT
Currently supported parameters for connection with MQTT are **host**,
**port**, **ssl**, **username**, **password** and **reconnect**.

Example of configuration for MQTT:
```yaml
lpc:
  connections:
    - name: MQTT-connection
      type: MQTT
      host: localhost
      port: 8883
      ssl:
        default: true
      username: userTest
      password: testUser
      reconnect: false
  transformations:
    -...
```

### Modbus
Configuration for Modbus depends on connection type, LPC supports serial connection or TCP connection.
For TCP connection parameters **host** and **port** are required.
For serial connection parameters **device** is required, optional parameters are 
**baud-rate**, **data-bits**, **parity** and **stop-bits**.

Example of configuration for serial Modbus connection:
```yaml
lpc:
  connections:
    - name: Modbus-connection
      type: Modbus
      device: /dev/ttymxc2
      baud-rate: 115200
      data-bits: 8
      parity: none
  transformations:
    -...
```

Example of configuration for TCP Modbus connection:
```yaml
lpc:
  connections:
    - name: Modbus-connection
      type: Modbus
      host: localhost
      port: 502
  transformations:
    -...
```

## Transformations
In the transformations section, each transformation is described, from which topics to listen on to 
mapping incoming/outgoing messages to specified format and structure.

Possible options for each transformation are following:
```yaml
lpc:
  connections:
    -
    -
  transformations:
    - name: string
      description: string
      connections:
        incoming-connection:
          -
          -
        incoming-topic: string
        incoming-format: XML/JSON
        modbus-function-code: integer
        modbus-device-id: integer
        outgoing-connection:
          -
          -
        outgoing-topic: string
        outgoing-format: XML/JSON
      to-outgoing: string
      to-incoming: string
      or
      to-incoming:
        modbus-registers:
          - register-address: integer
            path: string
            type: int8/int16/int32/int64/float32/float64
            pattern: string
            values: array
```

Options:
- **name:** Short name of the transformation
- **description:** Description of the transformation
- **connections.incoming-connection:** List of connection names, this connections will be used for sending/receiving the data from clients. If using Modbus, only Modbus connections must be listed here. Must match the name in the **connections**.
- **connections.incoming-topic:** On which topic MQTT/NATS client will send the incoming messages or listen for messages from devices. If using Modbus, this must be omitted.
- **connections.incoming-format:** Format of the incoming messages.
- **connections.modbus-function-code:** If using Modbus, it tells the LPC which function code to use when requesting/writing data.
- **connections.modbus-device-id:** If using Modbus, it tells the LPC to which device it will send the data.
- **connections.outgoing-connection:** List of connection names, this connections will be used for sending/receiving the data from server. Must match the name in the **connections**.
- **connections.outgoing-topic:** On which topic MQTT/NATS client will send the outgoing messages or listen for message from server. 
- **connections.outgoing-format:** Format of the outgoing messages.
- **to-outgoing:** Structure of the outgoing message with defined mappings.
- **to-incoming:** Structure of the incoming message with defined mappings. If this using Modbus, this must be omitted.
- **to-incoming.modbus-registers:** List of definitions of modbus registers used for writing/reading the data.

If **to-outgoing** message structure is present, then LPC will listen/request messages and send the transformed message to the **outgoing-topic**.
When using **to-outgoing** in combination with Modbus as  **connections.incoming-connection**, LPC will request data from 
Modbus at client id **connections.modbus-device-id** using function code **connections.modbus-function-code** for each defined mapping.

If **to-incoming** message structure is present, then LPC will listen/request messages and send the transformed message to the **incoming-topic**, or it will request/write data to appropriate Modbus registers.

### Mapping definitions
Transforming messages from one structure to another is done with the help of a mapper. Each mapper has the following variables that are configurable:
- type
- path
- pattern
- values

**type** specifies the type to which value must be converted to. Possible values are: *integer*, *float*, *double*, *date*, *datetime* and *string*.
When using Modbus, number of bits is required, so *integer8* (or *int8*), *int16*, *int32*, *int64* and also *float32* and *float64*;
*int64* is converted to **long** and *float64* is converted to **double**. *date* and *datetime* are converted to long representing the number of milliseconds since January 1, 1970, 00:00:00 GMT.

**path** specifies path to the value that will be used in new message structure. For XML/JSON this is done using XPath or JSON Pointer (e.g. /OutgoingEvent/currentStatus). For Modbus messages, register address must be provided.

**pattern** is needed only when type is *datetime* or *date* as it specifies the format of the provided temporal value at path.

**values** is needed only when value must be converted to the index of an array or index to some value from the array.

For easier explanation of options, we will use examples.

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
lpc:
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
      host: 37aad5450fca492297d7d3bc3329f4ba.s2.eu.hivemq.cloud
      port: 8883
      username: lpc-user
      password: zv.NaiixwWZ7wCC
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
        outgoing-topic: event/myevent
        outgoing-format: XML
      to-outgoing: 
        '<Event> 
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
With this transformation we are specifying that the MQTT-connection will subscribe to topic ```topic1``` and LPC will transform the message to XML
structure, and it will send the transformed message using NATS-connection to topic ```event/myevent```.

We can see that the mapping is done with the help of XML tag ```<lpc:mapping>```.

For setting the value of ```OutgoingEvent/currentStatus```, we are converting value ```"active"``` to integer ```1```.
Because the ```IncomingEvent/status``` is string, and we have provided ```values``` options, this means we will map
to the integer based on which index is the value of ```IncomingEvent/status```. So ```"active"``` maps to ```1```.

For setting the value of ```OutgoingEvent/datetime```, we are converting from ```datetime``` to ```long```.
Because ```type``` is ```datetime```, we need to provide the pattern of the incoming value, so LPC know how to parse the value, hence ```dd-MM-yyyy HH:mm:ss```. Also note that the leading ```/``` is not needed.
Same applies for setting the value of ```OutgoingEvent/interval/start```, but here we are using ```date```.

For setting the value of ```OutgoingEvent/interval/duration``` we just need to specify ```type```, because mapping is done 1 on 1.

There is also reserved keyword ```$timestamp``` which is used to set the value of ```OutgoingEvent/creationTime``` to the current time in milliseconds.

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
lpc:
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
      host: 37aad5450fca492297d7d3bc3329f4ba.s2.eu.hivemq.cloud
      port: 8883
      username: lpc-user
      password: zv.NaiixwWZ7wCC
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
        outgoing-topic: event/myevent
        outgoing-format: JSON
      to-outgoing: 
        '{
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
For this example, we will use the Modbus connection to read data from the device and transform it to JSON IEEE2030.5 Event.

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
lpc:
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
        modbus-function-code: 3
        modbus-device-id: 1
        outgoing-connection:
          - NATS-connection
        outgoing-topic: event/myevent
        outgoing-format: JSON
      to-outgoing: 
        '{
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
```

Here we see that the mapping is done with register addresses, and we are specifying the type of the value at the register address.
LPC will for each register send new request with function code specified at ```modbus-function-code``` to specified device at```modbus-device-id```.
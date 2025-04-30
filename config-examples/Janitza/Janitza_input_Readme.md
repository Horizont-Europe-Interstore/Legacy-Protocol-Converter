Shows a generic ecample on how one can use LPC to interface a Modbus-based Janitza power quality meter with an MQTT EMS.

# Connections

- NATS server, needed for the InterSTORE implementation of IEEE2030.5
- MQTT-broker, connection point of the EMS
- Janitza-Modbus, specifies the Modbus settings of the Janitza device

# Transformations

## Janitza-input- Modbus to NATS

Implemented to read Modbus messages from the Janitza meter and convert it into IEEE2030.5 over NATS

### Connections

#### Incoming connection

must be name of the Modbus connection (Janitza-Modbus)

#### Outgoing-connection

must be name of the NATS connection (NATS-Server)

#### outgoing-topic

is the topic where you would like to publish the IEEE2030.5 message on the NATS server (janitza.Input)

### To outgoing

#### to-topic

is the topic where you would like to publish the IEEE2030.5 message on the NATS server (same as "outgoing-topic" in
the "Connections", thus janitza.Input)

#### message:

The structure of the message must follow the indication reported in the standard.

In order to map a quantity (like "value") with its corresponging Modbus register, one needs to indicate the "lpc:
mapping" as follows:

```
"value": {
  "lpc:mapping": {
    "path": "51",
    "type": "int16"
  }
}
```

where:

- path, denotes the Modbus register from which the mapping value must be read (e.g., reguster 51)
- type, denotes the format type of the Modbus register (e.g., int16)

In this case, only the MirrorMeterReading resource is used.

##### mirrorMeterReadingList

This has the following structure:

```
message: '{
"mirrorMeterReadingList": {  
  "mirrorMeterReading": [
    {
      "description": {...},
      "lastUpdateTime": {...},
      "reading": {...},
      "readingType": {...},
        "powerOfTenMultiplier": {...},
        "uom": {...}
      },
      "mrid": {...}
    },
    {...}
  ],
  "all": 2,
  "results": 2
}
```

where:

- description, can be used to describe the reading
- lastUpdateTime, indicates the time when the reading occurred
- reading, indicated the actual reading value
- readingType, provides additional info on the reading type
    - powerOfTenMultiplier, indicates the power of ten multiplier for the attribute.
    - uom, Indicates the measurement type for the UOM for the readings of this type.
- mrid, is the global identifier of the object.

#### interval-requests

Here settings for the interval request for the Modbus registers are indicated. It should look like:

```
interval-request: 
  interval: {...}
  request: 
    modbus-function-code: {...}
    modbus-device-id: {...}
    endianness: {...}
    modbus-registers: 
      - register-address: {...}    
        type: {...} 
```

where:

- interval, interval request occurrence in milliseconds
- request:
    - modbus-function-code, is the function code for reading holding registers (i.e., 3)
    - modbus-device-id, device ID of the Modbus slave
    - endianness, to specify the modbus register endianess (e.g., big-swap)
    - modbus-registers, here all modbus registers of interest must be listed
        - register-address, address of the modbus register of interest (e.g., 800)
        - type, format type of the modbus register of interest (e.g., float32)

## Janitza-input - NATS to MQTT

Implemented to read IEEE2030.5 over NATS and convert it into MQTT

### Connections

#### Incoming connection

must be name of the NATS connection (e.g., NATS-Server)

#### incoming-topic

is the topic where you would like to publish the IEEE2030.5 message on the NATS server (janitza.Input)

#### Outgoing-connection

must be name of the MQTT connection (e.g., MQTT-broker)

### To outgoing

#### to-topic

is the topic where you would like to publish the IEEE2030.5 message on the MQTT broker (e.g., janitza/input)

#### message:

The structure of the message depends on the structure of the MQTT topics in the broker. For example:

```
message: '{
   "frequency": {
      "value": {
      }
    },
    "voltage": {
      "ph1": {
        "value": {
        }
      },
      "ph2": {
        "value": {
        }
      },
      "ph3": {
        "value": {
        }
      }
    },
 },
 {...}
 '
```

This allows to have the following structure in the MQTT broker:

- frequency
- voltage
    - ph1
    - ph2
    - ph3
- current
    - ph1
    - ph2
    - ph3
- power
    - Active
        - ph1
        - ph2
        - ph3
    - Reactive_fund
        - ph1
        - ph2
        - ph3
    - Apparent
        - ph1
        - ph2
        - ph3
    - Active_fund
        - ph1
        - ph2
        - ph3
- thd
    - voltage
        - ph1
        - ph2
        - ph3
    - current
        - ph1
        - ph2
        - ph3

Also in this case, the mapping of the values is obtained using "lpc_mapping". To get the value of reading from the
second (indexing starts from 0) mirrorMeterReading

  ```
  "value": {
    "lpc:mapping": {
      "path": "mirrorMeterReadingList/mirrorMeterReading/1/reading/value",
      "type": "float32"
     }
  }
  ```
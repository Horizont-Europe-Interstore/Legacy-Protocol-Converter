# InterSTORE explaination LPC

**BATTERY_EXAMPLE**
Shows a generic ecample on how one can use LPC to interface a Modbus-based BESS with an MQTT EMS.

# Connections

- NATS server, needed for the InterSTORE implementation of IEEE2030.5
- MQTT-broker, connection point of the EMS
- Riello-INV1-NetMan, specifies the Modbus settings of the BESS

# Transformations

## Riello-INV1-NetMan - Modbus to NATS

Implemented to read Modbus messages from the BESS and convert it into IEEE2030.5 over NATS

### Connections

#### Incoming connection

must be name of the Modbus connection (Riello-INV1-NetMan)

#### Outgoing-connection

must be name of the NATS connection (NATS-Server)

#### outgoing-topic

is the topic where you would like to publish the IEEE2030.5 message on the NATS server (riello.inv1.netman)

### To outgoing

#### to-topic

is the topic where you would like to publish the IEEE2030.5 message on the NATS server (same as "outgoing-topic" in
the "Connections", thus riello.inv1.netman)

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

##### DER

As mentioned in the standard, (10.10.4.4 DER resources) one needs to list DER resources which are resources of each DER.

```
DER{
  description{...},
  DERCapability{
    type{...},  
    modesSupported{...}, 
    rtgMaxW{...}, 
  },
  Resource1{...},
  Resource2{...}
}
```

###### DERCapability (10.10.4.4.2)

Is a subfield of "DER". Each unique DER instance SHALL link to a ```DERCapability``` instance that SHALL contain the
following attributes:

- ```type```
    - 80, "Other storage system" from ```DERType object```
- ```modesSupported``` look for the valued in table 54 (Modes and attributes for storage type DERs)
    - ```dERControlType```
        - 0: Charge mode. This requires as mandatory attributes:
            - ```rtgMaxChargeRateW```/```setMaxChargeRateW``` or ```rtgMaxW``` /```setMaxW```. ```rtgMaxChargeRateW```
              is mandatory if Discharge mode is supported
            - ```rtgMaxWh``` or ```rtgMaxAh```. For storage capacity
        - 1: Discharge mode. This requires as mandatory attributes:
            - ```rtgMaxChargeRateW```/```setMaxChargeRateW```
            - ```rtgMaxDischargeRateW```/```setMaxDischargeRateW``` or ```rtgMaxW```/```setMaxW```.
              ```rtgMaxDischargeRateW``` is mandatory if combined generator/storage
- ```rtgMaxW```
    - modesSupported: 20: Maximum Active Power. Continuous active power output (includes maximum discharge rate if
      combined generator/storage type)

Where:

- ```rtgMaxChargeRateW``` Maximum rate of energy transfer received by the storage DER in Watts.
- ```setMaxChargeRateW``` Maximum rate of energy transfer received by the storage device in Watts)
- ```rtgMaxW``` Maximum continuous active power output capability in watts
- ```setMaxW``` Limit for maximum active power capability of the DER in W)
- ```rtgMaxWh``` Maximum energy storage capacity in WattHours
- ```rtgMaxAh``` Usable energy storage capacity in AmpHours
- ```setMaxWh```: Maximum energy storage capacity of the DER in WattHours
- ```rtgMaxDischargeRateW``` Maximum rate of energy transfer delivered by the storage DER in Watts
- ```setMaxDischargeRateW``` Maximum rate of energy transfer delivered by the storage device in Watts

Thus, it should look like:

```
DERCapability{
  "DERType": {
    "value": 80
  },
  "modesSupported": {
    "dERControlType": {
      "value": [
        0,
        1
      ]
    }
  }, 
  rtgMaxW{...}, 
  rtgMaxWh{...}, 
  rtgMaxChargeRateW{...},
  rtgMaxDischargeRateW{...},
}
```

One can also add ```rtgMaxVA``` or ```rtgMaxVAR```

###### State of charge status

This is an example of a DER resource that one might want to add to indicate the state (percent data type). It should
look like:

```
"StateOfChargeStatusType": {
  "dateTime":{...},
  "value":{...}
  }
```

##### mirrorMeterReadingList

This function set can be used to provide additional meter data.
This has the following structure:

```
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
    - endianness, to specify the modbus register endianess (e.g., big/little)
    - modbus-registers, here all modbus registers of interest must be listed
        - register-address, address of the modbus register of interest (e.g., 11)
        - type, format type of the modbus register of interest (e.g., int16)

## Riello-INV1-NetMan - NATS to MQTT

Implemented to read IEEE2030.5 over NATS and convert it into MQTT

### Connections

#### Incoming connection

must be name of the NATS connection (e.g., NATS-Server)

#### incoming-topic

is the topic where you would like to publish the IEEE2030.5 message on the NATS server (riello.inv1.netman)

#### Outgoing-connection

must be name of the MQTT connection (e.g., MQTT-broker)

### To outgoing

#### to-topic

is the topic where you would like to publish the IEEE2030.5 message on the MQTT broker (e.g., Riello/INV1/Netman)

#### message:

The structure of the message depends on the structure of the MQTT topics in the broker. For example:

```
message: '{
   "stateUPS": {
     "state1": {...},
     "state2": {...},
     "state3": {...}
   },
   "voltage": {
     "nominal": {
     },
     "input": {
       "ph1": {...},
       "ph2": {...},
       "ph3": {...}
     },
     "output": {
       "ph1": {...},
       "ph2": {...},
       "ph3": {...}
     },
     "bypass": {
       "ph1": {...},
       "ph2": {...},
       "ph3": {...}
     }
   },
   "current": {
     "input": {
       "ph1": {...},
       "ph2": {...},
       "ph3": {...}
     },
     "output": {
       "ph1": {...},
       "ph2": {...},
       "ph3": {...}
     }
   },
   "power": {
     "nominal": {
       "active": {...},
       "reactive": {...},
       "apparent": {...},
       "minPF": {...}
     },
     "outputActive": {
       "ph1": {...},
       "ph2": {...},
       "ph3": {...}
     }
   },
   "frequency": {
     "input": {...},
     "output": {...},
     "bypass": {...}
   },
   "temperature": {
     "ups": {
     },
     "sensor1": {...},
     "sensor2": {...}
   },
   "soc": {...}
   }
 }
 '
```

Also in this case, the mapping of the values is obtained using "lpc_mapping":

- To get the value of rtgMaxW from DERCapability
  ```
  "value": {
    "lpc:mapping": {
      "path": "DER/0/DERCapability/rtgMaxW/value",
      "type": "int32"
     }
  }
  ```

- To get the value of reading from the second (indexing starts from 0) mirrorMeterReading
  ```
  "value": {
    "lpc:mapping": {
      "path": "mirrorMeterReadingList/mirrorMeterReading/1/reading/value",
      "type": "int32"
     }
  }
  ```
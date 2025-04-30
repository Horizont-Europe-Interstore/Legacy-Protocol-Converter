# Description of the Configuration

## 1. Connections

The connections section defines the communication endpoints used by
the LPC.

NATS Connection:

Name: NATS-connection

Type: NATS (a lightweight messaging system)

Host: localhost

Port: 4222

Reconnect: true (enables automatic reconnection if the connection is
lost)

Modbus Connection:

Name: Modbus-connection

Type: Modbus (a serial communication protocol)

Host: 192.168.1.211

Port: 502

## 2. Transformations

The transformations section defines how data is transformed and routed
between connections.

Transformation Name: Modbus to JSON IEEE2030.5 Event

Description: Transforms Modbus messages into JSON format compliant
with IEEE 2030.5 standards.

Validation: validate-ieee2030-5: true (validation of IEEE 2030.5
compliance).

### Connections:

Incoming Connection: Modbus-connection (data is received from Modbus).

Outgoing Connection: NATS-connection (transformed data is sent to
NATS).

Outgoing Topic: capwatts (the NATS topic where the data is published).

Outgoing Format: JSON (the format of the outgoing message).

### Outgoing Message:

The message field defines the structure of the JSON output. It
includes:

lastUpdateTime: A timestamp.

EventStatus: Contains mappings for:

Grid frequency: Mapped to Modbus register 27 (int16 type).

Output active power: Mapped to Modbus register 29 (int16 type).

interval: Contains mappings for:

Input 1 current: Mapped to Modbus register 32 (int16 type).

### Interval Request:

Interval: 3000 milliseconds (data is polled every 3 seconds).

Request:

Modbus Function Code: 4 (read input registers).

Modbus Device ID: 1.

Modbus Registers:

Register 27: int16 type.

Register 29: int16 type.

Register 32: int16 type.

## Summary

This YAML configuration file sets up the LPC to:

Connect to a Modbus device and a NATS messaging system.

Poll data from specific Modbus registers every 3 seconds.

Transform the Modbus data into a JSON format.

Publish the transformed data to the capwatts topic on NATS.

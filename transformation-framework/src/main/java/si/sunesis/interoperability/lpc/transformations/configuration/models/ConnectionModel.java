/*
 *  Copyright (c) 2023-2024 Sunesis and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package si.sunesis.interoperability.lpc.transformations.configuration.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a connection configuration for various protocol types.
 * Supports multiple protocols including NATS, MQTT, RabbitMQ, and Modbus.
 * Contains all settings needed to establish connections to external systems.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode
public class ConnectionModel {

    // Common connection parameters
    /**
     * Unique name identifying this connection
     */
    @EqualsAndHashCode.Exclude
    private String name;

    /**
     * Connection protocol type (e.g., NATS, MQTT, Modbus, RabbitMQ)
     */
    private String type;

    /**
     * Hostname or IP address of the remote server
     */
    private String host;

    /**
     * Port number for the connection
     */
    private Integer port;

    /**
     * Username for authentication
     */
    private String username;

    /**
     * Password for authentication
     */
    private String password;

    /**
     * SSL/TLS configuration for secure connections
     */
    private SslModel ssl = new SslModel();

    /**
     * Whether to automatically reconnect if connection is lost
     */
    @EqualsAndHashCode.Exclude
    private Boolean reconnect = false;

    // MQTT specific parameters
    /**
     * MQTT protocol version (3 or 5)
     */
    private Integer version = 5;

    // RabbitMQ specific parameters
    /**
     * RabbitMQ virtual host path
     */
    @JsonProperty("virtual-host")
    private String virtualHost = "/";

    /**
     * RabbitMQ exchange name
     */
    @JsonProperty("exchange-name")
    private String exchangeName;

    /**
     * RabbitMQ routing key for message routing
     */
    @JsonProperty("routing-key")
    private String routingKey = "";

    /**
     * RabbitMQ exchange type (direct, fanout, topic, headers)
     */
    @JsonProperty("exchange-type")
    private String exchangeType = "direct";

    // Modbus specific parameters
    /**
     * Serial device path for Modbus RTU connections
     */
    private String device;

    /**
     * Serial port baud rate for Modbus RTU
     */
    @JsonProperty("baud-rate")
    private Integer baudRate;

    /**
     * Number of data bits for serial communication
     */
    @JsonProperty("data-bits")
    private Integer dataBits;

    /**
     * Parity mode for serial communication (none, even, odd, mark, space)
     */
    private String parity;

    /**
     * Number of stop bits for serial communication
     */
    @JsonProperty("stop-bits")
    private Integer stopBits;
}

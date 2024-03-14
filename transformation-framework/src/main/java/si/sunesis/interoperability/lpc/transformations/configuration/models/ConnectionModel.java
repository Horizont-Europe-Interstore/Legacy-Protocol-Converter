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
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode
public class ConnectionModel {

    // Common
    @EqualsAndHashCode.Exclude
    private String name;

    private String type;

    private String host;

    private Integer port;

    private String username;

    private String password;

    private SslModel ssl = new SslModel();

    @EqualsAndHashCode.Exclude
    private Boolean reconnect = false;

    // MQTT
    private Integer version = 5;

    // RabbitMQ
    @JsonProperty("virtual-host")
    private String virtualHost = "/";

    @JsonProperty("exchange-name")
    private String exchangeName;

    @JsonProperty("routing-key")
    private String routingKey = "";

    @JsonProperty("exchange-type")
    private String exchangeType = "direct";

    // Modbus
    private String device;

    @JsonProperty("baud-rate")
    private Integer baudRate;

    @JsonProperty("data-bits")
    private Integer dataBits;

    private String parity;

    @JsonProperty("stop-bits")
    private Integer stopBits;
}

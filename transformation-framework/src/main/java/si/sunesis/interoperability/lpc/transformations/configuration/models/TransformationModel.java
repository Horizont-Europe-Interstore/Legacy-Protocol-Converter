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
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
public class TransformationModel {

    private String name;

    private String description;

    @JsonProperty("connections")
    private TransformationConnectionsModel connections;



    private String toIncoming;

    private List<ModbusModel> toModbus;

    @JsonSetter("to-incoming")
    private void unpackNested(Object toIncoming) {
        if (toIncoming instanceof String incoming) {
            this.toIncoming = incoming;
        } else if (toIncoming instanceof Map incoming) {
            this.toModbus = (List<ModbusModel>) incoming.get("modbus-registers");
        }
    }

    @JsonProperty("to-outgoing")
    private String toOutgoing;

    @JsonProperty("validate-schema")
    private Boolean validateSchema = false;

    @JsonRootName("to-incoming")
    private class ToIncomingModel {
        private String name;
        private Integer address;
        private Integer length;
        private String type;
    }
}

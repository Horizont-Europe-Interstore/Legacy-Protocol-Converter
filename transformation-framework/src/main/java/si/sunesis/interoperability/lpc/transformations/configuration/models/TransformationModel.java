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

/**
 * Defines a message transformation between different protocols and formats.
 * Contains configuration for incoming and outgoing message transformations,
 * connection details, and optional interval-based requests.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
public class TransformationModel {

    /**
     * Unique identifier for the transformation
     */
    private String name = null;

    /**
     * Human-readable description of the transformation's purpose
     */
    private String description;

    /**
     * Flag indicating whether to validate against IEEE 2030.5 standard
     */
    @JsonProperty("validate-ieee2030-5")
    private Boolean validateIEEE2030dot5 = false;

    /**
     * Connection settings for this transformation
     */
    @JsonProperty("connections")
    private TransformationConnectionsModel connections;

    /**
     * Message transformation configuration for incoming messages
     */
    @JsonProperty("to-incoming")
    private MessageModel toIncoming;

    /**
     * Configuration for periodic data polling requests
     */
    @JsonProperty("interval-request")
    private IntervalRequestModel intervalRequest;

    /**
     * Message transformation configuration for outgoing messages
     */
    @JsonProperty("to-outgoing")
    private MessageModel toOutgoing;
}

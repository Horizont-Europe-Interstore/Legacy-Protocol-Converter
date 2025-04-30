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

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration model that represents the entire application configuration.
 * Contains settings for logging, transformations, connections, and registration.
 * This class is deserialized from YAML configuration files.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
public class ConfigurationModel {

    /**
     * Configuration format version
     */
    private String version = "1.0.0";

    /**
     * Timestamp when the configuration was created or last modified
     */
    private String timestamp = null;

    /**
     * Logging configuration settings
     */
    private LoggingModel logging = new LoggingModel();

    /**
     * List of transformation definitions for converting between protocols
     */
    @JsonProperty("transformations")
    private List<TransformationModel> transformations = new ArrayList<>();

    /**
     * List of connection definitions for external systems
     */
    @JsonProperty("connections")
    private List<ConnectionModel> connections = new ArrayList<>();

    /**
     * Registration configuration for announcing to external systems
     */
    private RegistrationModel registration = new RegistrationModel();
}

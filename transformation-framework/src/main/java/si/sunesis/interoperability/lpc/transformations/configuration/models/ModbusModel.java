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
 * Represents a Modbus register configuration for data mapping.
 * Defines how to map between Modbus registers and message fields
 * including register address, data type, and optional value mappings.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
public class ModbusModel {

    /**
     * Modbus register address for reading/writing data
     */
    @JsonProperty("register-address")
    private Integer address;

    /**
     * Path to the data field in message for mapping
     */
    private String path;

    /**
     * Data type of the register (e.g., int, float, string)
     */
    private String type;

    /**
     * Format pattern for date/time values
     */
    private String pattern;

    /**
     * Array of possible values for enum-like mappings
     */
    private String[] values;

    /**
     * Default value to use when no mapping is found
     */
    @JsonProperty("default")
    private Float defaultValue;
}

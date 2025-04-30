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
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import si.sunesis.interoperability.lpc.transformations.enums.Endianness;

import java.util.ArrayList;
import java.util.List;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
public class MessageModel {

    /**
     * Destination topic for sending messages
     */
    @JsonProperty("to-topic")
    private String toTopic;

    /**
     * Topic for receiving reply messages
     */
    @JsonProperty("reply-from-topic")
    private String fromTopic;

    /**
     * Message template or content for transformation
     */
    private String message;

    /**
     * Modbus function code for Modbus protocol operations
     */
    @JsonProperty("modbus-function-code")
    private Integer functionCode;

    /**
     * Modbus device ID for addressing specific devices
     */
    @JsonProperty("modbus-device-id")
    private Integer deviceId;

    /**
     * List of Modbus register definitions for data mapping
     */
    @JsonProperty("modbus-registers")
    private List<ModbusModel> modbusRegisters = new ArrayList<>();

    /**
     * Byte order for Modbus data interpretation
     */
    private Endianness endianness = Endianness.BIG_ENDIAN;

    /**
     * Modbus library implementation to use ("java" or "python")
     */
    private String modbusLibrary = "java";

    /**
     * Sets the Modbus library implementation to use.
     * Supports either "java" or "python" as valid values.
     * If input is null, the current value is preserved.
     * Defaults to "java" if the input matches "java" (case-insensitive).
     * Sets to "python" for any other non-null value.
     *
     * @param modbusLibrary The name of the Modbus library to use
     */
    @JsonSetter("modbus-library")
    public void setModbusLibrary(String modbusLibrary) {
        if (modbusLibrary == null) {
            return;
        }

        if (modbusLibrary.equalsIgnoreCase("java")) {
            this.modbusLibrary = "java";
        } else {
            this.modbusLibrary = "python";
        }
    }

    /**
     * Sets the endianness format based on a string description.
     * Parses the input string to determine the appropriate Endianness enum value.
     * <p>
     * Supports four formats:
     * - Big endian (when string contains "big" but not "swap")
     * - Big endian with swapped bytes (when string contains both "big" and "swap")
     * - Little endian (when string doesn't contain "big" and doesn't contain "swap")
     * - Little endian with swapped bytes (when string doesn't contain "big" but contains "swap")
     * <p>
     * If input is null, the current value is preserved.
     *
     * @param endianness The string description of the endianness format
     */
    @JsonSetter
    public void setEndianness(String endianness) {
        if (endianness == null) {
            return;
        }

        if (endianness.toLowerCase().contains("big")) {
            if (endianness.toLowerCase().contains("swap")) {
                this.endianness = Endianness.BIG_ENDIAN_SWAP;
            } else {
                this.endianness = Endianness.BIG_ENDIAN;
            }
        } else {
            if (endianness.toLowerCase().contains("swap")) {
                this.endianness = Endianness.LITTLE_ENDIAN_SWAP;
            } else {
                this.endianness = Endianness.LITTLE_ENDIAN;
            }
        }
    }

    @JsonProperty("retry-count")
    private Integer retryCount = 0;
}

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
package si.sunesis.interoperability.lpc.transformations.transformation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.exception.IllegalDataAddressException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.msg.ModbusRequestBuilder;
import com.intelligt.modbus.jlibmodbus.msg.base.ModbusRequest;
import com.intelligt.modbus.jlibmodbus.msg.base.ModbusResponse;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadCoilsResponse;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadHoldingRegistersResponse;
import com.intelligt.modbus.jlibmodbus.utils.DataUtils;
import com.intelligt.modbus.jlibmodbus.utils.ModbusExceptionCode;
import com.intelligt.modbus.jlibmodbus.utils.ModbusFunctionCode;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ConnectionModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.MessageModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ModbusModel;
import si.sunesis.interoperability.lpc.transformations.enums.Endianness;

import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Utility class for handling Modbus protocol operations.
 * Provides methods for building Modbus requests, handling responses, and converting between
 * different data formats and endianness. Supports both Java and Python Modbus implementations.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
@ApplicationScoped
public class ModbusHandler {

    private ModbusHandler() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Builds register values for Modbus write operations based on the function code and message model.
     * Handles different Modbus function codes with appropriate register value formatting.
     *
     * @param msgToRegisterMap   Map of register addresses to their values
     * @param groupedModbusModel List of Modbus register models to write
     * @param messageModel       Message configuration containing function code and other settings
     * @return Array of integer register values ready for Modbus transmission
     */
    protected static int[] buildRegisters(Map<Integer, Float> msgToRegisterMap, List<ModbusModel> groupedModbusModel, MessageModel messageModel) {
        log.debug("Starting register address: {}", groupedModbusModel.get(0).getAddress());
        log.debug("Function code: {} value: {}", ModbusFunctionCode.get(messageModel.getFunctionCode()).name(), ModbusFunctionCode.get(messageModel.getFunctionCode()));

        switch (ModbusFunctionCode.get(messageModel.getFunctionCode())) {
            case WRITE_SINGLE_COIL, WRITE_MULTIPLE_COILS -> {
                boolean value = msgToRegisterMap.containsKey(groupedModbusModel.get(0).getAddress()) && msgToRegisterMap.get(groupedModbusModel.get(0).getAddress()) == 1;
                return new int[]{value ? 1 : 0};
            }
            case WRITE_SINGLE_REGISTER -> {
                int[] registers = prepareRegForWriting(messageModel, groupedModbusModel.get(0), msgToRegisterMap);
                log.debug("Writing single register: {}", registers);

                int value;
                if (registers.length > 1) {
                    log.warn("More than one register to write. Using only the first one.");
                    value = registers[0];
                } else if (registers.length == 0) {
                    log.warn("No registers to write. Using Float.floatToIntBits.");
                    value = Float.floatToIntBits(msgToRegisterMap.getOrDefault(groupedModbusModel.get(0).getAddress(), 0f));
                } else {
                    value = registers[0];
                }

                return new int[]{value};
            }
            case READ_WRITE_MULTIPLE_REGISTERS, WRITE_MULTIPLE_REGISTERS -> {
                ArrayList<int[]> registers = prepareRegsForWriting(messageModel, groupedModbusModel, msgToRegisterMap);
                log.debug("Writing registers: {}", registers);

                if (registers.isEmpty()) {
                    log.warn("No registers to write. Using default value.");
                    registers.add(new int[]{Float.floatToIntBits(msgToRegisterMap.getOrDefault(groupedModbusModel.get(0).getAddress(), 0f))});
                }

                return registers.stream().reduce(new int[0], (a, b) -> {
                    int[] result = new int[a.length + b.length];
                    System.arraycopy(a, 0, result, 0, a.length);
                    System.arraycopy(b, 0, result, a.length, b.length);
                    return result;
                });
            }
            default -> {
                return new int[]{};
            }
        }
    }

    /**
     * Builds a Modbus request object based on the function code and register values.
     * Creates the appropriate request type based on the Modbus function code in the message model.
     *
     * @param msgToRegisterMap   Map of register addresses to their values
     * @param groupedModbusModel List of Modbus register models to use in the request
     * @param messageModel       Message configuration containing function code and device ID
     * @param quantity           Number of registers to read/write
     * @return Configured ModbusRequest object ready for transmission
     * @throws ModbusNumberException If there is an error with register numbers or addressing
     */
    protected static ModbusRequest buildModbusRequest(Map<Integer, Float> msgToRegisterMap, List<ModbusModel> groupedModbusModel, MessageModel messageModel, int quantity) throws ModbusNumberException {
        ModbusRequest request = null;
        ModbusRequestBuilder requestBuilder = ModbusRequestBuilder.getInstance();

        while (!Modbus.checkEndAddress(groupedModbusModel.get(0).getAddress() + quantity)) {
            quantity--;
        }

        log.debug("Quantity: {}", quantity);

        int[] registers = buildRegisters(msgToRegisterMap, groupedModbusModel, messageModel);

        switch (ModbusFunctionCode.get(messageModel.getFunctionCode())) {
            case READ_COILS -> request = requestBuilder.buildReadCoils(messageModel.getDeviceId(),
                    groupedModbusModel.get(0).getAddress(),
                    quantity);
            case READ_DISCRETE_INPUTS -> request = requestBuilder.buildReadDiscreteInputs(messageModel.getDeviceId(),
                    groupedModbusModel.get(0).getAddress(),
                    quantity);
            case READ_HOLDING_REGISTERS ->
                    request = requestBuilder.buildReadHoldingRegisters(messageModel.getDeviceId(),
                            groupedModbusModel.get(0).getAddress(),
                            quantity);
            case READ_INPUT_REGISTERS -> request = requestBuilder.buildReadInputRegisters(messageModel.getDeviceId(),
                    groupedModbusModel.get(0).getAddress(),
                    quantity);
            case WRITE_SINGLE_COIL -> request = requestBuilder.buildWriteSingleCoil(messageModel.getDeviceId(),
                    groupedModbusModel.get(0).getAddress(),
                    registers[0] == 1);
            case WRITE_SINGLE_REGISTER -> request = requestBuilder.buildWriteSingleRegister(messageModel.getDeviceId(),
                    groupedModbusModel.get(0).getAddress(),
                    registers[0]);
            case READ_EXCEPTION_STATUS -> request = requestBuilder.buildReadExceptionStatus(messageModel.getDeviceId());
            case WRITE_MULTIPLE_COILS -> request = requestBuilder.buildWriteMultipleCoils(messageModel.getDeviceId(),
                    groupedModbusModel.get(0).getAddress(),
                    new boolean[]{registers[0] == 1});
            case WRITE_MULTIPLE_REGISTERS ->
                    request = requestBuilder.buildWriteMultipleRegisters(messageModel.getDeviceId(),
                            groupedModbusModel.get(0).getAddress(),
                            registers);
            case READ_WRITE_MULTIPLE_REGISTERS ->
                    request = requestBuilder.buildReadWriteMultipleRegisters(messageModel.getDeviceId(),
                            groupedModbusModel.get(0).getAddress(),
                            quantity,
                            groupedModbusModel.get(0).getAddress(),
                            registers);
            default -> log.warn("Function code not supported: {}", messageModel.getFunctionCode());
        }

        return request;
    }

    /**
     * Builds a Java implementation-specific Modbus request.
     * Calculates the appropriate quantity of registers based on data types in the model.
     *
     * @param msgToRegisterMap   Map of register addresses to their values
     * @param groupedModbusModel List of Modbus register models to use in the request
     * @param messageModel       Message configuration containing function code and device ID
     * @return Configured ModbusRequest object for the Java implementation
     * @throws ModbusNumberException If there is an error with register numbers or addressing
     */
    protected static ModbusRequest buildJavaModbusRequest(Map<Integer, Float> msgToRegisterMap, List<ModbusModel> groupedModbusModel, MessageModel messageModel) throws ModbusNumberException {
        int quantity = 0;
        for (ModbusModel model : groupedModbusModel) {
            quantity += getNumOfRegisters(model.getType());
        }

        return buildModbusRequest(msgToRegisterMap, groupedModbusModel, messageModel, quantity);
    }

    /**
     * Builds a Python implementation-specific Modbus request as a JSON object.
     * Creates a JSON structure that can be sent to the Python Modbus service.
     *
     * @param msgToRegisterMap   Map of register addresses to their values
     * @param groupedModbusModel List of Modbus register models to use in the request
     * @param messageModel       Message configuration containing function code and device ID
     * @param connectionModel    Connection details including host and port information
     * @return JSON object representing the request for the Python Modbus implementation
     */
    protected static javax.json.JsonObject buildPythonModbusRequest(Map<Integer, Float> msgToRegisterMap, List<ModbusModel> groupedModbusModel, MessageModel messageModel, ConnectionModel connectionModel) {
        int[] regs = ModbusHandler.buildRegisters(msgToRegisterMap, groupedModbusModel, messageModel);

        int count = groupedModbusModel.stream().map(i -> (int) Math.ceil(ModbusHandler.getNumOfRegisters(i.getType()) / 2.0)).reduce(0, Integer::sum);

        JsonArrayBuilder values = Json.createArrayBuilder();

        for (int reg : regs) {
            values.add(reg);
        }

        return Json.createObjectBuilder()
                .add("host", connectionModel.getHost())
                .add("port", connectionModel.getPort())
                .add("unit_id", messageModel.getDeviceId())
                .add("start_register", groupedModbusModel.get(0).getAddress())
                .add("function_code", messageModel.getFunctionCode())
                .add("values", values.build())
                .add("count", count)
                .build();
    }

    /**
     * Processes a Modbus response from the Java implementation.
     * Extracts register values from the response and stores them in the register map.
     *
     * @param response           The Modbus response to process
     * @param registerMap        Map to store extracted register values
     * @param groupedModbusModel List of Modbus register models used in the request
     * @param messageModel       Message configuration containing function code and endianness
     * @throws IllegalDataAddressException If the response contains an illegal data address
     */
    protected static void handleJavaModbusResponse(ModbusResponse response, Map<Integer, Object> registerMap, List<ModbusModel> groupedModbusModel, MessageModel messageModel) throws IllegalDataAddressException {
        if (response.getFunction() != messageModel.getFunctionCode()) {
            log.warn("Function code mismatch! Response: {}, message model: {}", response.getFunction(), messageModel.getFunctionCode());
            return;
        }

        if (response.getModbusExceptionCode() != null && response.getModbusExceptionCode() != ModbusExceptionCode.NO_EXCEPTION) {
            log.warn("Modbus exception code: {}", response.getModbusExceptionCode());
        }

        switch (ModbusFunctionCode.get(messageModel.getFunctionCode())) {
            case READ_DISCRETE_INPUTS, READ_COILS -> {
                if (!groupedModbusModel.get(0).getType().contains("bool")) {
                    throw new IllegalArgumentException("Wrong type");
                }

                ReadCoilsResponse coilsResponse = (ReadCoilsResponse) response;
                registerMap.put(groupedModbusModel.get(0).getAddress(), coilsResponse.getModbusCoils().get(0));
            }
            case READ_WRITE_MULTIPLE_REGISTERS, READ_INPUT_REGISTERS, READ_HOLDING_REGISTERS -> {
                ReadHoldingRegistersResponse holdingRegistersResponse = (ReadHoldingRegistersResponse) response;

                getValueFromJavaRegisters(holdingRegistersResponse, registerMap, groupedModbusModel, messageModel);
            }
            default ->
                    log.debug("Function code is write only: {}. So no data to read.", messageModel.getFunctionCode());
        }
    }

    /**
     * Extracts register values from a Java Modbus response with appropriate endianness conversion.
     * Takes the raw response data and converts it to proper register values based on the configured endianness.
     *
     * @param response           The Modbus response containing register values
     * @param registerMap        Map to store the extracted register values
     * @param groupedModbusModel List of Modbus models defining the registers
     * @param messageModel       Message configuration containing endianness settings
     */
    protected static void getValueFromJavaRegisters(ReadHoldingRegistersResponse response,
                                                    Map<Integer, Object> registerMap,
                                                    List<ModbusModel> groupedModbusModel,
                                                    MessageModel messageModel) {
        int[] registers;
        byte[] bytes = response.getHoldingRegisters().getBytes();

        if (messageModel.getEndianness() == Endianness.BIG_ENDIAN) {
            registers = DataUtils.BeToRegArray(bytes);
        } else if (messageModel.getEndianness() == Endianness.BIG_ENDIAN_SWAP) {
            bytes = ModbusHandler.beSwapToBe(bytes);
            registers = DataUtils.BeToRegArray(bytes);
        } else if (messageModel.getEndianness() == Endianness.LITTLE_ENDIAN_SWAP) {
            bytes = ModbusHandler.leSwapToLe(bytes);
            bytes = ModbusHandler.leToBe(bytes);
            registers = DataUtils.BeToRegArray(bytes);
        } else {
            bytes = ModbusHandler.leToBe(bytes);
            registers = DataUtils.BeToRegArray(bytes);
        }

        handleModbusRegisers(registers, bytes,
                registerMap, groupedModbusModel);
    }

    /**
     * Processes a Modbus response from the Python implementation.
     * Parses the JSON response and extracts register values into the register map.
     *
     * @param response           The JSON response string from the Python Modbus service
     * @param registerMap        Map to store extracted register values
     * @param groupedModbusModel List of Modbus register models used in the request
     * @param messageModel       Message configuration containing function code and endianness
     */
    protected static void handlePythonModbusResponse(String response, Map<Integer, Object> registerMap, List<ModbusModel> groupedModbusModel, MessageModel messageModel) {
        try {
            JsonObject jsonObject = new JsonParser().parse(response).getAsJsonObject();

            if (jsonObject.get("data").isJsonArray()) {
                // Output the JsonObject
                int[] data = new int[jsonObject.getAsJsonArray("data").size()];
                for (int i = 0; i < jsonObject.getAsJsonArray("data").size(); i++) {
                    data[i] = jsonObject.getAsJsonArray("data").get(i).getAsInt();  // Convert JsonElement to String
                }

                log.info("Data: {}", Arrays.toString(data));

                if (data.length > 0) {
                    switch (ModbusFunctionCode.get(messageModel.getFunctionCode())) {
                        case READ_DISCRETE_INPUTS, READ_COILS -> {
                            if (!groupedModbusModel.get(0).getType().contains("bool")) {
                                throw new IllegalArgumentException("Wrong type");
                            }

                            registerMap.put(groupedModbusModel.get(0).getAddress(), data[0]);
                        }
                        case READ_WRITE_MULTIPLE_REGISTERS, READ_INPUT_REGISTERS, READ_HOLDING_REGISTERS -> {
                            getValueFromPythonRegisters(data, registerMap, groupedModbusModel, messageModel);
                        }
                        default ->
                                log.debug("Function code is write only: {}. So no data to read.", messageModel.getFunctionCode());
                    }
                }
            }
        } catch (JsonSyntaxException err) {
            log.error("Json Syntax Exception: this is most likely due to failed Python response", err);
        } catch (Exception e) {
            log.error("Error while parsing Python response", e);
        }
    }

    /**
     * Extracts register values from a Python Modbus response with appropriate endianness conversion.
     * Converts integer data from Python response to proper register values based on endianness.
     *
     * @param data               Array of register values from Python response
     * @param registerMap        Map to store the extracted register values
     * @param groupedModbusModel List of Modbus models defining the registers
     * @param messageModel       Message configuration containing endianness settings
     */
    protected static void getValueFromPythonRegisters(int[] data,
                                                      Map<Integer, Object> registerMap,
                                                      List<ModbusModel> groupedModbusModel,
                                                      MessageModel messageModel) {
        int[] registers = data.clone();
        byte[] bytes = new byte[registers.length * 2]; // 2 bytes per register

        // Assuming big-endian (most significant register first)
        for (int i = 0; i < registers.length; i++) {
            // Extract high byte and low byte from each register
            bytes[i * 2] = (byte) ((registers[i] >> 8) & 0xFF);     // High byte
            bytes[i * 2 + 1] = (byte) (registers[i] & 0xFF);        // Low byte
        }

        if (messageModel.getEndianness() == Endianness.BIG_ENDIAN) {
            registers = DataUtils.BeToRegArray(bytes);
        } else if (messageModel.getEndianness() == Endianness.BIG_ENDIAN_SWAP) {
            bytes = ModbusHandler.beSwapToBe(bytes);
            registers = DataUtils.BeToRegArray(bytes);
        } else if (messageModel.getEndianness() == Endianness.LITTLE_ENDIAN_SWAP) {
            bytes = ModbusHandler.leSwapToLe(bytes);
            bytes = ModbusHandler.leToBe(bytes);
            registers = DataUtils.BeToRegArray(bytes);
        } else {
            bytes = ModbusHandler.leToBe(bytes);
            registers = DataUtils.BeToRegArray(bytes);
        }

        handleModbusRegisers(registers, bytes,
                registerMap, groupedModbusModel);
    }

    /**
     * Processes Modbus registers and extracts typed values based on register models.
     * Maps register values to their addresses according to data types specified in the models.
     * Handles different data types (int, uint, float, double) and various bit widths.
     *
     * @param registers          Array of integer register values
     * @param bytes              Raw byte representation of the register values
     * @param registerMap        Map to store the extracted and typed register values
     * @param groupedModbusModel List of register models containing type information
     */
    private static void handleModbusRegisers(int[] registers,
                                             byte[] bytes,
                                             Map<Integer, Object> registerMap,
                                             List<ModbusModel> groupedModbusModel) {
        log.debug("Bytes: {}", bytes);
        log.debug("Registers: {}", registers);

        for (ModbusModel modbusModel : groupedModbusModel) {
            String type = modbusModel.getType();

            Float factor = modbusModel.getFactor();

            int offset = modbusModel.getAddress() - groupedModbusModel.get(0).getAddress();

            if (type.contains("uint")) {
                // Handle unsigned integers
                if (type.contains("8")) {
                    // Extract low byte as unsigned 8-bit
                    int regValue = get(offset, registers);
                    int uint8Value = regValue & 0xFF;

                    uint8Value *= factor;

                    registerMap.put(modbusModel.getAddress(), uint8Value);
                } else if (type.contains("16")) {
                    int regValue = getUInt16At(offset, registers);
                    regValue *= factor;

                    registerMap.put(modbusModel.getAddress(), regValue);
                } else if (type.contains("64")) {
                    BigInteger uint64Value = getUInt64At(offset, registers);
                    uint64Value = uint64Value.multiply(BigInteger.valueOf(factor.longValue()));

                    registerMap.put(modbusModel.getAddress(), uint64Value);
                } else {
                    long uint32Value = getUInt32At(offset, registers);
                    uint32Value *= factor;

                    registerMap.put(modbusModel.getAddress(), uint32Value);
                }
            } else if (type.contains("int")) {
                // Handle signed integers
                if (type.contains("8")) {
                    byte regValue = DataUtils.byteLow(get(offset, registers));
                    regValue *= factor;

                    registerMap.put(modbusModel.getAddress(), regValue);
                } else if (type.contains("16")) {
                    int regValue = getInt16At(offset, registers);
                    regValue *= factor;

                    registerMap.put(modbusModel.getAddress(), regValue);
                } else if (type.contains("64")) {
                    long regValue = getInt64At(offset, registers);
                    regValue *= factor;

                    registerMap.put(modbusModel.getAddress(), regValue);
                } else {
                    int regValue = getInt32At(offset, registers);
                    regValue *= factor;

                    registerMap.put(modbusModel.getAddress(), regValue);
                }
            } else if (type.contains("long")) {
                long regValue = getInt64At(offset, registers);
                regValue *= factor;

                registerMap.put(modbusModel.getAddress(), regValue);
            } else if (type.contains("float")) {
                if (type.contains("64")) {
                    double regValue = getFloat64At(offset, registers);
                    regValue *= factor;

                    registerMap.put(modbusModel.getAddress(), regValue);
                } else {
                    float regValue = getFloat32At(offset, registers);
                    regValue *= factor;

                    registerMap.put(modbusModel.getAddress(), regValue);
                }
            } else if (type.contains("double")) {
                double regValue = getFloat64At(offset, registers);
                regValue *= factor;
                registerMap.put(modbusModel.getAddress(), regValue);
            } else {
                throw new IllegalArgumentException("Wrong type: " + type);
            }
        }

        log.debug("Register map: {}", registerMap);
    }

    // Existing methods remain but with corrected signed handling

    /**
     * Gets the register value at the specified offset.
     * Simple accessor method to safely retrieve a register value.
     *
     * @param offset    The index position in the registers array
     * @param registers The array of register values
     * @return The register value as an Integer
     */
    public static Integer get(int offset, int[] registers) {
        return registers[offset];
    }

    // Modified methods for signed integers

    /**
     * Gets a signed 16-bit integer from a register at the specified offset.
     * Performs casting to ensure proper handling of sign bit.
     *
     * @param offset    The index position in the registers array
     * @param registers The array of register values
     * @return The 16-bit signed integer value
     */
    public static int getInt16At(int offset, int[] registers) {
        return (short) registers[offset]; // Correctly returns signed 16-bit
    }

    /**
     * Gets a signed 32-bit integer from registers starting at the specified offset.
     * Handles cases where there are not enough registers available.
     *
     * @param offset    The starting index position in the registers array
     * @param registers The array of register values
     * @return The 32-bit signed integer value, or 16-bit if not enough registers
     */
    public static int getInt32At(int offset, int[] registers) {
        if (registers.length < offset + 2) {
            return getInt16At(offset, registers);
        }
        return (getInt16At(offset, registers) << 16) | (getInt16At(offset + 1, registers) & 0xFFFF);
    }

    /**
     * Gets a signed 64-bit integer from registers starting at the specified offset.
     * Combines two 32-bit integers into a 64-bit long value.
     *
     * @param offset    The starting index position in the registers array
     * @param registers The array of register values
     * @return The 64-bit signed integer value
     */
    public static long getInt64At(int offset, int[] registers) {
        long high = getInt32At(offset, registers);
        long low = getInt32At(offset + 2, registers) & 0xFFFFFFFFL;
        return (high << 32) | low;
    }

    /**
     * Gets an unsigned 16-bit integer from a register at the specified offset.
     * Masks the value to ensure it is treated as unsigned.
     *
     * @param offset    The index position in the registers array
     * @param registers The array of register values
     * @return The 16-bit unsigned integer value (0-65535)
     */
    public static int getUInt16At(int offset, int[] registers) {
        return registers[offset] & 0xFFFF;
    }

    /**
     * Gets an unsigned 32-bit integer from registers starting at the specified offset.
     * Combines two 16-bit registers with proper masking to ensure unsigned treatment.
     *
     * @param offset    The starting index position in the registers array
     * @param registers The array of register values
     * @return The 32-bit unsigned integer value as a long (0-4294967295)
     */
    public static long getUInt32At(int offset, int[] registers) {
        if (registers.length < offset + 2) {
            return getUInt16At(offset, registers);
        }
        return ((registers[offset] & 0xFFFFL) << 16) | (registers[offset + 1] & 0xFFFFL);
    }

    /**
     * Gets an unsigned 64-bit integer from registers starting at the specified offset.
     * Combines two 32-bit unsigned values into a BigInteger to handle the full unsigned range.
     *
     * @param offset    The starting index position in the registers array
     * @param registers The array of register values
     * @return The 64-bit unsigned integer value as a BigInteger (0-18446744073709551615)
     */
    public static BigInteger getUInt64At(int offset, int[] registers) {
        long highPart = getUInt32At(offset, registers);
        long lowPart = getUInt32At(offset + 2, registers);
        BigInteger bigHigh = BigInteger.valueOf(highPart).shiftLeft(32);
        BigInteger bigLow = BigInteger.valueOf(lowPart & 0xFFFFFFFFL);
        return bigHigh.add(bigLow); // Returns 0-18446744073709551615
    }

    /**
     * Gets a 32-bit floating point value from registers starting at the specified offset.
     * Converts the integer bits to the IEEE 754 floating point representation.
     *
     * @param offset    The starting index position in the registers array
     * @param registers The array of register values
     * @return The 32-bit floating point value
     */
    public static float getFloat32At(int offset, int[] registers) {
        return Float.intBitsToFloat(getInt32At(offset, registers));
    }

    /**
     * Gets a 64-bit floating point value from registers starting at the specified offset.
     * Converts the long bits to the IEEE 754 double precision representation.
     *
     * @param offset    The starting index position in the registers array
     * @param registers The array of register values
     * @return The 64-bit double precision floating point value
     */
    public static double getFloat64At(int offset, int[] registers) {
        return Double.longBitsToDouble(getInt64At(offset, registers));
    }

    /**
     * Reverses the order of bytes in a byte array.
     * Used for endianness conversion operations.
     *
     * @param bytes The byte array to reverse
     * @return A new byte array with the bytes in reverse order
     */
    private static byte[] reverseByteArray(byte[] bytes) {
        byte[] beBytes = Arrays.copyOf(bytes, bytes.length);

        // AB CD to DC BA
        if (beBytes.length == 1) {
            return beBytes;
        }

        // Reverse order of array
        for (int i = 0; i < beBytes.length / 2; i++) {
            byte temp = beBytes[i];
            beBytes[i] = beBytes[beBytes.length - 1 - i];
            beBytes[beBytes.length - 1 - i] = temp;
        }

        return beBytes;
    }

    /**
     * Swaps adjacent bytes in a byte array.
     * Used for handling different endianness formats where bytes are swapped but not fully reversed.
     *
     * @param bytes The byte array to process
     * @return A new byte array with adjacent bytes swapped
     */
    private static byte[] swapBytes(byte[] bytes) {
        byte[] swapBytes = Arrays.copyOf(bytes, bytes.length);

        // BA DC to AB CD
        if (swapBytes.length == 1) {
            return swapBytes;
        }

        for (int iii = 0; iii < swapBytes.length; iii += 2) {
            byte temp = swapBytes[iii];
            if (iii + 1 >= swapBytes.length) {
                break;
            }
            swapBytes[iii] = swapBytes[iii + 1];
            swapBytes[iii + 1] = temp;
        }

        return swapBytes;
    }

    /**
     * Converts byte array from big-endian to little-endian format.
     * Ensures the array has an even number of bytes for proper conversion.
     *
     * @param bytes The big-endian byte array to convert
     * @return The equivalent byte array in little-endian format
     */
    public static byte[] beToLe(byte[] bytes) {
        // Add 0 to beginning if nod divisible by 2
        if (bytes.length % 2 != 0) {
            byte[] newBytes = new byte[bytes.length + 1];
            newBytes[0] = 0;
            System.arraycopy(bytes, 0, newBytes, 1, bytes.length);
            bytes = newBytes;
        }

        return reverseByteArray(bytes);
    }

    /**
     * Swaps adjacent bytes in a big-endian byte array while maintaining big-endian ordering.
     * Used for handling the BIG_ENDIAN_SWAP endianness format.
     *
     * @param bytes The big-endian byte array to process
     * @return The big-endian byte array with adjacent bytes swapped
     */
    public static byte[] beSwapToBe(byte[] bytes) {
        return swapBytes(bytes);
    }

    /**
     * Swaps adjacent bytes in a little-endian byte array while maintaining little-endian ordering.
     * Used for handling the LITTLE_ENDIAN_SWAP endianness format.
     *
     * @param bytes The little-endian byte array to process
     * @return The little-endian byte array with adjacent bytes swapped
     */
    public static byte[] leSwapToLe(byte[] bytes) {
        return swapBytes(bytes);
    }

    /**
     * Converts a little-endian byte array to little-endian with adjacent bytes swapped.
     * Used for converting between different little-endian formats.
     *
     * @param bytes The standard little-endian byte array
     * @return The little-endian byte array with adjacent bytes swapped
     */
    public static byte[] leToLeSwap(byte[] bytes) {
        return swapBytes(bytes);
    }

    /**
     * Converts a little-endian byte array to big-endian with adjacent bytes swapped.
     * Combines reversal and swapping operations for complex endianness conversion.
     *
     * @param bytes The little-endian byte array
     * @return The big-endian byte array with adjacent bytes swapped
     */
    public static byte[] leToBeSwap(byte[] bytes) {
        return swapBytes(reverseByteArray(bytes));
    }

    /**
     * Converts byte array from little-endian to big-endian format.
     * Reverses the byte order to change endianness.
     *
     * @param bytes The little-endian byte array to convert
     * @return The equivalent byte array in big-endian format
     */
    public static byte[] leToBe(byte[] bytes) {
        return reverseByteArray(bytes);
    }

    /**
     * Calculates the number of Modbus registers needed for a specific data type.
     * Different data types require different numbers of registers (16-bit words).
     *
     * @param type The data type string (e.g., "int16", "float32", "int64")
     * @return The number of registers required for the specified data type
     * @throws IllegalArgumentException If the data type is not supported
     */
    public static int getNumOfRegisters(String type) {
        if (type.contains("int")) {
            if (type.contains("8")) {
                return 1;
            } else if (type.contains("16")) {
                return 1;
            } else if (type.contains("64")) {
                return 4;
            } else {
                return 2;
            }
        } else if (type.contains("long")) {
            return 4;
        } else if (type.contains("float")) {
            if (type.contains("64")) {
                return 4;
            } else {
                return 2;
            }
        } else if (type.contains("double")) {
            return 4;
        } else if (type.contains("short")) {
            return 1;
        } else if (type.contains("byte")) {
            return 1;
        } else {
            throw new IllegalArgumentException("Wrong type");
        }
    }

    /**
     * Converts a float value to byte array based on the data type specified in the Modbus model.
     * Handles different numeric types (int, float, long, short, byte) with appropriate conversions.
     *
     * @param value       The float value to convert
     * @param modbusModel The model containing the data type information
     * @return Byte array representation of the value according to the specified data type
     */
    private static byte[] toByteArray(Float value, ModbusModel modbusModel) {
        byte[] bytes = DataUtils.toByteArray(value);
        if (modbusModel.getType().contains("int")) {
            if (modbusModel.getType().contains("16")) {
                bytes = DataUtils.toByteArray(value.shortValue());
            } else if (modbusModel.getType().contains("8")) {
                bytes = DataUtils.toByteArray(value.byteValue());
            } else if (modbusModel.getType().contains("64")) {
                bytes = DataUtils.toByteArray(value.longValue());
            } else {
                bytes = DataUtils.toByteArray(value.intValue());
            }
        } else if (modbusModel.getType().contains("long")) {
            bytes = DataUtils.toByteArray(value.longValue());
        } else if (modbusModel.getType().contains("short")) {
            bytes = DataUtils.toByteArray(value.shortValue());
        } else if (modbusModel.getType().contains("byte")) {
            bytes = DataUtils.toByteArray(value.byteValue());
        }

        byte[] le = ModbusHandler.leSwapToLe(bytes);

        return ModbusHandler.leToBe(le);
    }

    /**
     * Prepares a register array for writing a single Modbus value.
     * Handles endianness conversions based on the message model settings.
     *
     * @param messageModel     The message configuration containing endianness settings
     * @param modbusModel      The model for the register to write
     * @param msgToRegisterMap Map of register addresses to values
     * @return Array of register values ready for Modbus transmission
     */
    private static int[] prepareRegForWriting(MessageModel messageModel, ModbusModel modbusModel, Map<Integer, Float> msgToRegisterMap) {
        log.debug("Preparing register {} for writing", modbusModel.getAddress());
        byte[] bytes = toByteArray(msgToRegisterMap.getOrDefault(modbusModel.getAddress(), 0f), modbusModel);

        if (messageModel.getEndianness().equals(Endianness.LITTLE_ENDIAN)) {
            bytes = beToLe(bytes);
        } else if (messageModel.getEndianness().equals(Endianness.BIG_ENDIAN_SWAP)) {
            bytes = beToLe(bytes);
            bytes = leToBeSwap(bytes);
        } else if (messageModel.getEndianness().equals(Endianness.LITTLE_ENDIAN_SWAP)) {
            bytes = beToLe(bytes);
            bytes = leToLeSwap(bytes);
        }

        int[] registers = DataUtils.BeToRegArray(bytes);

        log.debug("Register address {}, bytes: {}", modbusModel.getAddress(), bytes);
        log.debug("Register address {}, registers: {}", modbusModel.getAddress(), registers);

        return registers;
    }

    /**
     * Prepares register arrays for writing multiple Modbus values.
     * Processes each model in the list to create register arrays for all values.
     *
     * @param messageModel     The message configuration containing endianness settings
     * @param modbusModel      List of models for the registers to write
     * @param msgToRegisterMap Map of register addresses to values
     * @return List of register arrays ready for Modbus transmission
     */
    private static ArrayList<int[]> prepareRegsForWriting(MessageModel messageModel, List<ModbusModel> modbusModel, Map<Integer, Float> msgToRegisterMap) {
        log.debug("Preparing multiple registes for writing");
        ArrayList<int[]> registers = new ArrayList<>();

        for (ModbusModel model : modbusModel) {
            registers.add(prepareRegForWriting(messageModel, model, msgToRegisterMap));
        }

        log.debug("Prepared registers: {}", registers);

        return registers;
    }
}

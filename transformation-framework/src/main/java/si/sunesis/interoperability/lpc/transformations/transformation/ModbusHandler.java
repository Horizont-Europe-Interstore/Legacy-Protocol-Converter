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
import si.sunesis.interoperability.lpc.transformations.configuration.models.MessageModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ModbusModel;
import si.sunesis.interoperability.lpc.transformations.enums.Endianness;

import javax.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Map;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
@ApplicationScoped
public class ModbusHandler {

    private ModbusHandler() {
        throw new IllegalStateException("Utility class");
    }

    protected static ModbusRequest buildModbusRequest(Map<Integer, Float> msgToRegisterMap, ModbusModel modbusModel, MessageModel messageModel, int quantity) throws ModbusNumberException {
        ModbusRequest request = null;
        ModbusRequestBuilder requestBuilder = ModbusRequestBuilder.getInstance();

        while (!Modbus.checkEndAddress(modbusModel.getAddress() + quantity)) {
            quantity--;
        }

        log.debug("Starting register address: {}", modbusModel.getAddress());
        log.debug("Quantity: {}", quantity);
        log.debug("Function code: {} value: {}", ModbusFunctionCode.get(messageModel.getFunctionCode()).name(), ModbusFunctionCode.get(messageModel.getFunctionCode()));

        switch (ModbusFunctionCode.get(messageModel.getFunctionCode())) {
            case READ_COILS -> request = requestBuilder.buildReadCoils(messageModel.getDeviceId(),
                    modbusModel.getAddress(),
                    quantity);
            case READ_DISCRETE_INPUTS -> request = requestBuilder.buildReadDiscreteInputs(messageModel.getDeviceId(),
                    modbusModel.getAddress(),
                    quantity);
            case READ_HOLDING_REGISTERS ->
                    request = requestBuilder.buildReadHoldingRegisters(messageModel.getDeviceId(),
                            modbusModel.getAddress(),
                            quantity);
            case READ_INPUT_REGISTERS -> request = requestBuilder.buildReadInputRegisters(messageModel.getDeviceId(),
                    modbusModel.getAddress(),
                    quantity);
            case WRITE_SINGLE_COIL -> {
                boolean value = msgToRegisterMap.containsKey(modbusModel.getAddress()) && msgToRegisterMap.get(modbusModel.getAddress()) == 1;
                request = requestBuilder.buildWriteSingleCoil(messageModel.getDeviceId(),
                        modbusModel.getAddress(),
                        value);
            }
            case WRITE_SINGLE_REGISTER -> {
                int[] registers = prepareRegsForWriting(messageModel, modbusModel, msgToRegisterMap);
                log.debug("Writing registers: {}", registers);

                int value;
                if (registers.length > 1) {
                    log.warn("More than one register to write. Using only the first one.");
                    value = registers[0];
                } else if (registers.length == 0) {
                    log.warn("No registers to write. Using Float.floatToIntBits.");
                    value = Float.floatToIntBits(msgToRegisterMap.getOrDefault(modbusModel.getAddress(), 0f));
                } else {
                    value = registers[0];
                }

                request = requestBuilder.buildWriteSingleRegister(messageModel.getDeviceId(),
                        modbusModel.getAddress(),
                        value);
            }
            case READ_EXCEPTION_STATUS -> request = requestBuilder.buildReadExceptionStatus(messageModel.getDeviceId());
            case WRITE_MULTIPLE_COILS -> {
                boolean value = msgToRegisterMap.containsKey(modbusModel.getAddress()) && msgToRegisterMap.get(modbusModel.getAddress()) == 1;
                request = requestBuilder.buildWriteMultipleCoils(messageModel.getDeviceId(),
                        modbusModel.getAddress(),
                        new boolean[]{value});
            }
            case WRITE_MULTIPLE_REGISTERS -> {
                int[] registers = prepareRegsForWriting(messageModel, modbusModel, msgToRegisterMap);
                log.debug("Writing registers: {}", registers);

                request = requestBuilder.buildWriteMultipleRegisters(messageModel.getDeviceId(),
                        modbusModel.getAddress(),
                        registers);
            }
            case READ_WRITE_MULTIPLE_REGISTERS -> {
                int[] registers = prepareRegsForWriting(messageModel, modbusModel, msgToRegisterMap);
                log.debug("Writing registers: {}", registers);

                request = requestBuilder.buildReadWriteMultipleRegisters(messageModel.getDeviceId(),
                        modbusModel.getAddress(),
                        quantity,
                        modbusModel.getAddress(),
                        registers);
            }
            default -> log.warn("Function code not supported: {}", messageModel.getFunctionCode());
        }

        return request;
    }

    protected static ModbusRequest buildModbusRequest(Map<Integer, Float> msgToRegisterMap, ModbusModel modbusModel, MessageModel messageModel) throws ModbusNumberException {
        int quantity = (int) Math.ceil(getNumOfRegisters(modbusModel.getType()) / 2.0);
        return buildModbusRequest(msgToRegisterMap, modbusModel, messageModel, quantity);
    }

    protected static void handleModbusResponse(ModbusResponse response, Map<Integer, Object> registerMap, ModbusModel modbusModel, MessageModel messageModel) throws IllegalDataAddressException {
        if (response.getFunction() != messageModel.getFunctionCode()) {
            log.warn("Function code mismatch! Response: {}, message model: {}", response.getFunction(), messageModel.getFunctionCode());
            return;
        }

        if (response.getModbusExceptionCode() != null && response.getModbusExceptionCode() != ModbusExceptionCode.NO_EXCEPTION) {
            log.warn("Modbus exception code: {}", response.getModbusExceptionCode());
        }

        switch (ModbusFunctionCode.get(messageModel.getFunctionCode())) {
            case READ_DISCRETE_INPUTS, READ_COILS -> {
                if (!modbusModel.getType().contains("bool")) {
                    throw new IllegalArgumentException("Wrong type");
                }

                ReadCoilsResponse coilsResponse = (ReadCoilsResponse) response;
                registerMap.put(modbusModel.getAddress(), coilsResponse.getModbusCoils().get(0));
            }
            case READ_WRITE_MULTIPLE_REGISTERS, READ_INPUT_REGISTERS, READ_HOLDING_REGISTERS -> {
                ReadHoldingRegistersResponse holdingRegistersResponse = (ReadHoldingRegistersResponse) response;

                getValueFromRegisters(holdingRegistersResponse, registerMap, modbusModel, messageModel);
            }
            default ->
                    log.debug("Function code is write only: {}. So no data to read.", messageModel.getFunctionCode());
        }
    }

    protected static void getValueFromRegisters(ReadHoldingRegistersResponse response,
                                                Map<Integer, Object> registerMap,
                                                ModbusModel modbusModel,
                                                MessageModel messageModel) {
        int[] registers = response.getHoldingRegisters().getRegisters();
        byte[] bytes = response.getHoldingRegisters().getBytes();

        if (messageModel.getEndianness() == Endianness.BIG_ENDIAN) {
            bytes = beToLe(response.getHoldingRegisters().getBytes());
            registers = DataUtils.BeToRegArray(bytes);
        } else if (messageModel.getEndianness() == Endianness.BIG_ENDIAN_SWAP) {
            bytes = beSwapToBe(response.getHoldingRegisters().getBytes());
            bytes = beToLe(bytes);
            registers = DataUtils.BeToRegArray(bytes);
        } else if (messageModel.getEndianness() == Endianness.LITTLE_ENDIAN_SWAP) {
            bytes = leSwapToLe(response.getHoldingRegisters().getBytes());
            registers = DataUtils.BeToRegArray(bytes);
        }

        log.debug("Bytes: {}", bytes);
        log.debug("Registers: {}", registers);

        if (modbusModel.getType().contains("int")) {
            if (modbusModel.getType().contains("8")) {
                registerMap.put(modbusModel.getAddress(), DataUtils.byteLow(get(0, registers)));
            } else if (modbusModel.getType().contains("16")) {
                registerMap.put(modbusModel.getAddress(), getInt16At(0, registers));
            } else if (modbusModel.getType().contains("64")) {
                registerMap.put(modbusModel.getAddress(), getInt64At(0, registers));
            } else {
                registerMap.put(modbusModel.getAddress(), getInt32At(0, registers));
            }
        } else if (modbusModel.getType().contains("long")) {
            registerMap.put(modbusModel.getAddress(), getInt64At(0, registers));
        } else if (modbusModel.getType().contains("float")) {
            if (modbusModel.getType().contains("64")) {
                registerMap.put(modbusModel.getAddress(), getFloat64At(0, registers));
            } else {
                registerMap.put(modbusModel.getAddress(), getFloat32At(0, registers));
            }
        } else if (modbusModel.getType().contains("double")) {
            registerMap.put(modbusModel.getAddress(), getFloat64At(0, registers));
        } else {
            throw new IllegalArgumentException("Wrong type");
        }
    }

    public static Integer get(int offset, int[] registers) {
        return registers[offset];
    }

    public static int getInt16At(int offset, int[] registers) {
        return get(offset, registers);
    }

    public static int getInt32At(int offset, int[] registers) {
        return getInt16At(offset, registers) & '\uffff' | (getInt16At(offset + 1, registers) & '\uffff') << 16;
    }

    public static long getInt64At(int offset, int[] registers) {
        return getInt32At(offset, registers) & 4294967295L | (getInt32At(offset + 2, registers) & 4294967295L) << 32;
    }

    public static float getFloat32At(int offset, int[] registers) {
        return Float.intBitsToFloat(getInt32At(offset, registers));
    }

    public static double getFloat64At(int offset, int[] registers) {
        return Double.longBitsToDouble(getInt64At(offset, registers));
    }

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

    private static byte[] swapBytes(byte[] bytes) {
        byte[] swapBytes = Arrays.copyOf(bytes, bytes.length);

        // BA DC to AB CD
        if (swapBytes.length == 1) {
            return swapBytes;
        }

        for (int iii = 0; iii < swapBytes.length; iii += 2) {
            byte temp = swapBytes[iii];
            swapBytes[iii] = swapBytes[iii + 1];
            swapBytes[iii + 1] = temp;
        }

        return swapBytes;
    }

    public static byte[] beToLe(byte[] bytes) {
        return reverseByteArray(bytes);
    }

    public static byte[] beSwapToBe(byte[] bytes) {
        return swapBytes(bytes);
    }

    public static byte[] leSwapToLe(byte[] bytes) {
        return swapBytes(bytes);
    }

    public static byte[] leToLeSwap(byte[] bytes) {
        return swapBytes(bytes);
    }

    public static byte[] leToBeSwap(byte[] bytes) {
        return swapBytes(reverseByteArray(bytes));
    }

    public static byte[] leToBe(byte[] bytes) {
        return reverseByteArray(bytes);
    }

    private static int getNumOfRegisters(String type) {
        if (type.contains("int")) {
            if (type.contains("8")) {
                return 1;
            } else if (type.contains("16")) {
                return 2;
            } else if (type.contains("64")) {
                return 8;
            } else {
                return 4;
            }
        } else if (type.contains("long")) {
            return 8;
        } else if (type.contains("float")) {
            if (type.contains("64")) {
                return 8;
            } else {
                return 4;
            }
        } else if (type.contains("double")) {
            return 8;
        } else {
            throw new IllegalArgumentException("Wrong type");
        }
    }

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

        return bytes;
    }

    private static int[] prepareRegsForWriting(MessageModel messageModel, ModbusModel modbusModel, Map<Integer, Float> msgToRegisterMap) {
        log.debug("Preparing registers for writing");
        byte[] bytes = toByteArray(msgToRegisterMap.getOrDefault(modbusModel.getAddress(), 0f), modbusModel);

        if (messageModel.getEndianness().equals(Endianness.BIG_ENDIAN)) {
            bytes = leToBe(bytes);
        } else if (messageModel.getEndianness().equals(Endianness.BIG_ENDIAN_SWAP)) {
            bytes = leToBeSwap(bytes);
        } else if (messageModel.getEndianness().equals(Endianness.LITTLE_ENDIAN_SWAP)) {
            bytes = leToLeSwap(bytes);
        }

        int[] registers = DataUtils.BeToRegArray(bytes);

        log.debug("Bytes: {}", bytes);
        log.debug("Registers: {}", registers);

        return registers;
    }
}

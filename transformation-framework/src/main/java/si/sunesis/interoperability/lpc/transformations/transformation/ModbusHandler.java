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
import com.intelligt.modbus.jlibmodbus.utils.ModbusExceptionCode;
import com.intelligt.modbus.jlibmodbus.utils.ModbusFunctionCode;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.lpc.transformations.configuration.models.MessageModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ModbusModel;

import javax.enterprise.context.ApplicationScoped;
import java.nio.ByteBuffer;
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

        log.debug("Reading/writing from/to {} registers", quantity);
        log.debug("Starting register address: {}", modbusModel.getAddress());
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
            case WRITE_SINGLE_REGISTER -> request = requestBuilder.buildWriteSingleRegister(messageModel.getDeviceId(),
                    modbusModel.getAddress(),
                    Float.floatToIntBits(msgToRegisterMap.getOrDefault(modbusModel.getAddress(), 0f)));
            case READ_EXCEPTION_STATUS -> request = requestBuilder.buildReadExceptionStatus(messageModel.getDeviceId());
            case WRITE_MULTIPLE_COILS -> {
                boolean value = msgToRegisterMap.containsKey(modbusModel.getAddress()) && msgToRegisterMap.get(modbusModel.getAddress()) == 1;
                request = requestBuilder.buildWriteMultipleCoils(messageModel.getDeviceId(),
                        modbusModel.getAddress(),
                        new boolean[]{value});
            }
            case WRITE_MULTIPLE_REGISTERS -> {
                int capacity = getNumOfRegisters(modbusModel.getType());
                byte[] bytes = ByteBuffer.allocate(capacity).putFloat(msgToRegisterMap.getOrDefault(modbusModel.getAddress(), 0f)).array();

                request = requestBuilder.buildWriteMultipleRegisters(messageModel.getDeviceId(),
                        modbusModel.getAddress(),
                        bytes);
            }
            case READ_WRITE_MULTIPLE_REGISTERS -> {
                int capacity = getNumOfRegisters(modbusModel.getType());
                byte[] bytes = ByteBuffer.allocate(capacity).putFloat(msgToRegisterMap.getOrDefault(modbusModel.getAddress(), 0f)).array();

                int[] intArray = new int[bytes.length / 2];

                for (int i = 0; i < bytes.length; i += 2) {
                    // Combine two bytes to form one int
                    intArray[i / 2] = ((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF);
                }

                request = requestBuilder.buildReadWriteMultipleRegisters(messageModel.getDeviceId(),
                        modbusModel.getAddress(),
                        quantity,
                        modbusModel.getAddress(),
                        intArray);
            }
            default -> log.warn("Function code not supported: {}", messageModel.getFunctionCode());
        }

        return request;
    }

    protected static ModbusRequest buildModbusRequest(Map<Integer, Float> msgToRegisterMap, ModbusModel modbusModel, MessageModel messageModel) throws ModbusNumberException {
        return buildModbusRequest(msgToRegisterMap, modbusModel, messageModel, 1);
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

                getValueFromRegisters(holdingRegistersResponse, registerMap, modbusModel);
            }
            default -> log.debug("Function code is write only: {}", messageModel.getFunctionCode());
        }
    }

    protected static void getValueFromRegisters(ReadHoldingRegistersResponse response, Map<Integer, Object> registerMap, ModbusModel modbusModel) throws IllegalDataAddressException {
        if (modbusModel.getType().contains("int")) {
            if (modbusModel.getType().contains("8")) {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt8At(0));
            } else if (modbusModel.getType().contains("16")) {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt16At(0));
            } else if (modbusModel.getType().contains("64")) {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt64At(0));
            } else {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt32At(0));
            }
        } else if (modbusModel.getType().contains("long")) {
            registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt64At(0));
        } else if (modbusModel.getType().contains("float")) {
            if (modbusModel.getType().contains("64")) {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getFloat64At(0));
            } else {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getFloat32At(0));
            }
        } else if (modbusModel.getType().contains("double")) {
            registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getFloat64At(0));
        } else {
            throw new IllegalArgumentException("Wrong type");
        }
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
}

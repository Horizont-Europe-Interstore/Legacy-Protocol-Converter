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

import com.intelligt.modbus.jlibmodbus.exception.IllegalDataAddressException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.msg.ModbusRequestBuilder;
import com.intelligt.modbus.jlibmodbus.msg.base.ModbusRequest;
import com.intelligt.modbus.jlibmodbus.msg.base.ModbusResponse;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadCoilsResponse;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadHoldingRegistersResponse;
import com.intelligt.modbus.jlibmodbus.utils.ModbusFunctionCode;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.common.interfaces.RequestHandler;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ModbusModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.TransformationModel;
import si.sunesis.interoperability.lpc.transformations.connections.Connections;
import si.sunesis.interoperability.modbus.ModbusClient;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
public class TransformationHandler {

    private final ObjectTransformer objectTransformer;

    private final Connections connections;

    private final TransformationModel transformation;

    public TransformationHandler(TransformationModel transformation, ObjectTransformer objectTransformer, Connections connections) {
        this.transformation = transformation;
        this.objectTransformer = objectTransformer;
        this.connections = connections;
    }

    public void handleConnections() throws ModbusNumberException {
        log.info("Transformation: {}", transformation.getName());

        String[] incomingConnectionNames = transformation.getConnections().getIncomingConnections();
        String[] outgoingConnectionNames = transformation.getConnections().getOutgoingConnections();

        List<RequestHandler> incomingConnections =
                new ArrayList<>(connections.getMqttConnections(incomingConnectionNames).values());
        incomingConnections.addAll(connections.getNatsConnections(incomingConnectionNames).values());

        List<RequestHandler> outgoingConnections =
                new ArrayList<>(connections.getMqttConnections(outgoingConnectionNames).values());
        outgoingConnections.addAll(connections.getNatsConnections(outgoingConnectionNames).values());

        if (!connections.getModbusConnections(outgoingConnectionNames).isEmpty()) {
            log.error("Modbus connections are not supported as outgoing connections");
        }

        for (ModbusClient client : connections.getModbusConnections(incomingConnectionNames).values()) {
            try {
                log.info("Modbus client connect: {}", incomingConnectionNames);
                client.getClient().connect();
                log.info("Modbus client: {}", client.getClient().isConnected());
            } catch (ModbusIOException ignored) {
            }
        }

        if (transformation.getToOutgoing() != null) {
            log.info("Incoming connections: {}", incomingConnections);
            for (RequestHandler incomingConnection : incomingConnections) {
                incomingConnection.subscribe(transformation.getConnections().getIncomingTopic(), message -> {
                    String msg = new String((byte[]) message);
                    log.info("Incoming message from device: \n{}", msg);

                    String transformedMessage = objectTransformer.transform(msg,
                            transformation.getToOutgoing(),
                            transformation.getConnections().getIncomingFormat(),
                            transformation.getConnections().getOutgoingFormat());
                    log.info("Transformed message: \n{}", transformedMessage);

                    for (RequestHandler outgoingConnection : outgoingConnections) {
                        outgoingConnection.publish(transformedMessage, transformation.getConnections().getOutgoingTopic());
                    }
                });
            }
        }

        log.info("To incoming modbus: {}", transformation.getToModbus());
        log.info("To incoming: {}", transformation.getToIncoming());

        if (transformation.getToIncoming() != null) {
            for (RequestHandler outgoingConnection : outgoingConnections) {
                outgoingConnection.subscribe(transformation.getConnections().getOutgoingTopic(), message -> {
                    String msg = new String((byte[]) message);
                    log.info("Incoming message from server: \n{}", msg);

                    String transformedMessage = objectTransformer.transform(msg,
                            transformation.getToIncoming(),
                            transformation.getConnections().getOutgoingFormat(),
                            transformation.getConnections().getIncomingFormat());
                    log.info("Transformed message: \n{}", transformedMessage);

                    for (RequestHandler incomingConnection : incomingConnections) {
                        incomingConnection.publish(transformedMessage, transformation.getConnections().getIncomingTopic());
                    }
                });
            }
        } else if (transformation.getToModbus() != null && !transformation.getToModbus().isEmpty()) {
            List<ModbusClient> incomingModbusConnections =
                    new ArrayList<>(connections.getModbusConnections(incomingConnectionNames).values());

            for (RequestHandler outgoingConnection : outgoingConnections) {
                outgoingConnection.subscribe(transformation.getConnections().getOutgoingTopic(), message -> {
                    String msg = new String((byte[]) message);
                    log.info("Incoming message from server for modbus: {}", msg);

                    try {
                        buildModbusRequests(msg, incomingModbusConnections, outgoingConnections);
                    } catch (ModbusNumberException | ParseException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private void buildModbusRequests(String message,
                                     List<ModbusClient> incomingModbusConnections,
                                     List<RequestHandler> outgoingConnections) throws ModbusNumberException, ParseException {
        Map<Integer, Long> msgToRegisterMap = objectTransformer.transformToModbus(transformation.getToModbus(), message, transformation.getConnections().getOutgoingFormat());

        log.info("Message to register map: {}", msgToRegisterMap);

        for (ModbusClient modbusClient : incomingModbusConnections) {
            HashMap<Integer, Object> registerMap = new HashMap<>();

            for (ModbusModel modbusModel : transformation.getToModbus()) {
                ModbusRequest request = buildModbusRequest(msgToRegisterMap, modbusModel);

                modbusClient.requestReply(request, String.valueOf(transformation.getConnections().getModbusDeviceID()), msg -> {
                    try {
                        handleModbusResponse(msg, registerMap, modbusModel);
                    } catch (IllegalDataAddressException e) {
                        log.error("Illegal data address", e);
                    }
                });
            }

            if (transformation.getToOutgoing() != null) {
                log.info("Registers: {}", registerMap);

                String transformedMessage = objectTransformer.transform(registerMap,
                        transformation.getToOutgoing(),
                        transformation.getConnections().getIncomingFormat(),
                        transformation.getConnections().getOutgoingFormat());
                log.info("Transformed message: {}", transformedMessage);

                for (RequestHandler outgoingConnection : outgoingConnections) {
                    outgoingConnection.publish(transformedMessage, transformation.getConnections().getIncomingTopic());
                }
            }
        }
    }

    private ModbusRequest buildModbusRequest(Map<Integer, Long> msgToRegisterMap, ModbusModel modbusModel) throws ModbusNumberException {
        ModbusRequest request;
        ModbusRequestBuilder requestBuilder = ModbusRequestBuilder.getInstance();

        switch (ModbusFunctionCode.get(transformation.getConnections().getModbusFunctionCode())) {
            case READ_COILS ->
                    request = requestBuilder.buildReadCoils(transformation.getConnections().getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1);
            case READ_DISCRETE_INPUTS ->
                    request = requestBuilder.buildReadDiscreteInputs(transformation.getConnections().getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1);
            case READ_HOLDING_REGISTERS ->
                    request = requestBuilder.buildReadHoldingRegisters(transformation.getConnections().getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1);
            case READ_INPUT_REGISTERS ->
                    request = requestBuilder.buildReadInputRegisters(transformation.getConnections().getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1);
            case WRITE_SINGLE_COIL -> {
                boolean value = msgToRegisterMap.containsKey(modbusModel.getAddress()) && msgToRegisterMap.get(modbusModel.getAddress()) == 1;
                request = requestBuilder.buildWriteSingleCoil(transformation.getConnections().getModbusDeviceID(),
                        modbusModel.getAddress(),
                        value);
            }
            case WRITE_SINGLE_REGISTER ->
                    request = requestBuilder.buildWriteSingleRegister(transformation.getConnections().getModbusDeviceID(),
                            modbusModel.getAddress(),
                            Math.toIntExact(msgToRegisterMap.get(modbusModel.getAddress())));
            case READ_EXCEPTION_STATUS ->
                    request = requestBuilder.buildReadExceptionStatus(transformation.getConnections().getModbusDeviceID());
            case WRITE_MULTIPLE_COILS -> {
                boolean value = msgToRegisterMap.containsKey(modbusModel.getAddress()) && msgToRegisterMap.get(modbusModel.getAddress()) == 1;
                request = requestBuilder.buildWriteMultipleCoils(transformation.getConnections().getModbusDeviceID(),
                        modbusModel.getAddress(),
                        new boolean[]{value});
            }
            case WRITE_MULTIPLE_REGISTERS ->
                    request = requestBuilder.buildWriteMultipleRegisters(transformation.getConnections().getModbusDeviceID(),
                            modbusModel.getAddress(),
                            new int[]{Math.toIntExact(msgToRegisterMap.get(modbusModel.getAddress()))});
            case READ_WRITE_MULTIPLE_REGISTERS ->
                    request = requestBuilder.buildReadWriteMultipleRegisters(transformation.getConnections().getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1,
                            modbusModel.getAddress(),
                            new int[]{Math.toIntExact(msgToRegisterMap.get(modbusModel.getAddress()))});
            default -> throw new IllegalArgumentException("Unknown function code");
        }

        return request;
    }

    private void handleModbusResponse(ModbusResponse response, Map<Integer, Object> registerMap, ModbusModel modbusModel) throws IllegalDataAddressException {
        switch (ModbusFunctionCode.get(transformation.getConnections().getModbusFunctionCode())) {
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
            default -> throw new IllegalArgumentException("Unknown function code");
        }
    }

    private void getValueFromRegisters(ReadHoldingRegistersResponse response, Map<Integer, Object> registerMap, ModbusModel modbusModel) throws IllegalDataAddressException {
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
}

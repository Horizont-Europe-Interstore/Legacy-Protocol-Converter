package si.sunesis.interoperability.transformations.transformation;

import com.intelligt.modbus.jlibmodbus.exception.IllegalDataAddressException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.msg.ModbusRequestBuilder;
import com.intelligt.modbus.jlibmodbus.msg.base.ModbusRequest;
import com.intelligt.modbus.jlibmodbus.msg.base.ModbusResponse;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadCoilsResponse;
import com.intelligt.modbus.jlibmodbus.msg.response.ReadHoldingRegistersResponse;
import com.intelligt.modbus.jlibmodbus.utils.ModbusFunctionCode;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.common.interfaces.RequestHandler;
import si.sunesis.interoperability.modbus.ModbusClient;
import si.sunesis.interoperability.transformations.connections.Connections;
import si.sunesis.interoperability.transformations.models.YamlModbusModel;
import si.sunesis.interoperability.transformations.models.YamlTransformationModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TransformationHandler {

    private final ObjectTransformer objectTransformer;

    private final Connections connections;

    private final YamlTransformationModel transformation;

    public TransformationHandler(YamlTransformationModel transformation, ObjectTransformer objectTransformer, Connections connections) {
        this.transformation = transformation;
        this.objectTransformer = objectTransformer;
        this.connections = connections;
    }

    public void handleConnections() throws ModbusNumberException {
        log.info("Transformation: {}", transformation.getName());

        String[] incomingConnectionNames = transformation.getIncomingConnections();
        String[] outgoingConnectionNames = transformation.getOutgoingConnections();

        List<RequestHandler> incomingConnections =
                new ArrayList<>(connections.getMqttConnections(incomingConnectionNames).values());
        incomingConnections.addAll(connections.getNatsConnections(incomingConnectionNames).values());

        List<RequestHandler> outgoingConnections =
                new ArrayList<>(connections.getMqttConnections(outgoingConnectionNames).values());
        outgoingConnections.addAll(connections.getNatsConnections(outgoingConnectionNames).values());

        if (!connections.getModbusConnections(outgoingConnectionNames).isEmpty()) {
            log.error("Modbus connections are not supported as outgoing connections");
        }

        if (transformation.getToOutgoing() != null) {
            log.info("Incoming connections: {}", incomingConnections);
            for (RequestHandler incomingConnection : incomingConnections) {
                incomingConnection.subscribe(transformation.getIncomingTopic(), message -> {
                    String msg = new String((byte[]) message);
                    log.info("Incoming message from device: \n{}", msg);

                    String transformedMessage = objectTransformer.transform(msg,
                            transformation.getToOutgoing(),
                            transformation.getIncomingFormat(),
                            transformation.getOutgoingFormat());
                    log.info("Transformed message: \n{}", transformedMessage);

                    for (RequestHandler outgoingConnection : outgoingConnections) {
                        outgoingConnection.publish(transformedMessage, transformation.getOutgoingTopic());
                    }
                });
            }
        }

        if (transformation.getToIncoming() != null) {
            for (RequestHandler outgoingConnection : outgoingConnections) {
                outgoingConnection.subscribe(transformation.getOutgoingTopic(), message -> {
                    String msg = new String((byte[]) message);
                    log.info("Incoming message from server: \n{}", msg);

                    String transformedMessage = objectTransformer.transform(msg,
                            transformation.getToIncoming(),
                            transformation.getOutgoingFormat(),
                            transformation.getIncomingFormat());
                    log.info("Transformed message: \n{}", transformedMessage);

                    for (RequestHandler incomingConnection : incomingConnections) {
                        incomingConnection.publish(transformedMessage, transformation.getIncomingTopic());
                    }
                });
            }
        } else if (transformation.getToModbus() != null && transformation.getToModbus().length > 0) {
            List<ModbusClient> incomingModbusConnections =
                    new ArrayList<>(connections.getModbusConnections(incomingConnectionNames).values());

            for (RequestHandler outgoingConnection : outgoingConnections) {
                outgoingConnection.subscribe(transformation.getOutgoingTopic(), message -> {
                    String msg = new String((byte[]) message);
                    log.info("Incoming message from server for modbus: {}", msg);

                    try {
                        buildModbusRequests(msg, incomingModbusConnections, outgoingConnections);
                    } catch (ModbusNumberException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private void buildModbusRequests(String message,
                                     List<ModbusClient> incomingModbusConnections,
                                     List<RequestHandler> outgoingConnections) throws ModbusNumberException {
        for (ModbusClient modbusClient : incomingModbusConnections) {
            HashMap<Integer, Object> registerMap = new HashMap<>();

            for (YamlModbusModel modbusModel : transformation.getToModbus()) {
                ModbusRequest request = buildModbusRequest(message, modbusModel);

                modbusClient.requestReply(request, String.valueOf(transformation.getModbusDeviceID()), msg -> {
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
                        transformation.getIncomingFormat(),
                        transformation.getOutgoingFormat());
                log.info("Transformed message: {}", transformedMessage);

                for (RequestHandler outgoingConnection : outgoingConnections) {
                    outgoingConnection.publish(transformedMessage, transformation.getIncomingTopic());
                }
            }
        }
    }

    private ModbusRequest buildModbusRequest(String message, YamlModbusModel modbusModel) throws ModbusNumberException {
        ModbusRequest request;
        ModbusRequestBuilder requestBuilder = ModbusRequestBuilder.getInstance();

        switch (ModbusFunctionCode.get(transformation.getModbusFunctionCode())) {
            case READ_COILS -> request = requestBuilder.buildReadCoils(transformation.getModbusDeviceID(),
                    modbusModel.getAddress(),
                    1);
            case READ_DISCRETE_INPUTS ->
                    request = requestBuilder.buildReadDiscreteInputs(transformation.getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1);
            case READ_HOLDING_REGISTERS ->
                    request = requestBuilder.buildReadHoldingRegisters(transformation.getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1);
            case READ_INPUT_REGISTERS ->
                    request = requestBuilder.buildReadInputRegisters(transformation.getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1);
            case WRITE_SINGLE_COIL -> request = requestBuilder.buildWriteSingleCoil(transformation.getModbusDeviceID(),
                    modbusModel.getAddress(),
                    true);
            case WRITE_SINGLE_REGISTER ->
                    request = requestBuilder.buildWriteSingleRegister(transformation.getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1);
            case READ_EXCEPTION_STATUS ->
                    request = requestBuilder.buildReadExceptionStatus(transformation.getModbusDeviceID());
            case WRITE_MULTIPLE_COILS ->
                    request = requestBuilder.buildWriteMultipleCoils(transformation.getModbusDeviceID(),
                            modbusModel.getAddress(),
                            new boolean[]{true});
            case WRITE_MULTIPLE_REGISTERS ->
                    request = requestBuilder.buildWriteMultipleRegisters(transformation.getModbusDeviceID(),
                            modbusModel.getAddress(),
                            new int[]{1});
            case READ_WRITE_MULTIPLE_REGISTERS ->
                    request = requestBuilder.buildReadWriteMultipleRegisters(transformation.getModbusDeviceID(),
                            modbusModel.getAddress(),
                            1,
                            modbusModel.getAddress(),
                            new int[]{1});
            default -> throw new IllegalArgumentException("Unknown function code");
        }

        return request;
    }

    private void handleModbusResponse(ModbusResponse response, Map<Integer, Object> registerMap, YamlModbusModel modbusModel) throws IllegalDataAddressException {
        switch (ModbusFunctionCode.get(transformation.getModbusFunctionCode())) {
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

    private void getValueFromRegisters(ReadHoldingRegistersResponse response, Map<Integer, Object> registerMap, YamlModbusModel modbusModel) throws IllegalDataAddressException {
        if (modbusModel.getType().contains("int")) {
            if (modbusModel.getType().contains("16")) {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt16At(0));
            } else if (modbusModel.getType().contains("32")) {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt32At(0));
            } else if (modbusModel.getType().contains("64")) {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt64At(0));
            } else {
                registerMap.put(modbusModel.getAddress(), response.getHoldingRegisters().getInt8At(0));
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

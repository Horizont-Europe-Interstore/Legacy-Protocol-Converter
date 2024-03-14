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
import com.intelligt.modbus.jlibmodbus.msg.base.ModbusRequest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.common.exceptions.HandlerException;
import si.sunesis.interoperability.common.interfaces.RequestHandler;
import si.sunesis.interoperability.lpc.transformations.configuration.models.MessageModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ModbusModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.TransformationModel;
import si.sunesis.interoperability.lpc.transformations.connections.Connections;
import si.sunesis.interoperability.modbus.ModbusClient;

import java.text.ParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
public class TransformationHandler {

    private final ObjectTransformer objectTransformer;

    private final Connections connections;

    private final TransformationModel transformation;

    private final List<RequestHandler> incomingConnections = new ArrayList<>();

    @Getter
    private final List<RequestHandler> outgoingConnections = new ArrayList<>();

    public TransformationHandler(TransformationModel transformation, ObjectTransformer objectTransformer, Connections connections) {
        this.transformation = transformation;
        this.objectTransformer = objectTransformer;
        this.connections = connections;

        log.debug("Transformation: {}", transformation.getName());
    }

    public void handle() {
        handleConnections();
        handleOutgoingTransformations();
        handleIncomingTransformations();
        handleIntervalRequests();
    }

    private void handleConnections() {
        incomingConnections.clear();
        outgoingConnections.clear();

        String[] incomingConnectionNames = transformation.getConnections().getIncomingConnections();
        String[] outgoingConnectionNames = transformation.getConnections().getOutgoingConnections();

        incomingConnections.addAll(connections.getMqttConnections(incomingConnectionNames).values());
        incomingConnections.addAll(connections.getNatsConnections(incomingConnectionNames).values());
        incomingConnections.addAll(connections.getRabbitMQConnections(incomingConnectionNames).values());

        outgoingConnections.addAll(connections.getMqttConnections(outgoingConnectionNames).values());
        outgoingConnections.addAll(connections.getNatsConnections(outgoingConnectionNames).values());
        outgoingConnections.addAll(connections.getRabbitMQConnections(outgoingConnectionNames).values());

        if (!connections.getModbusConnections(outgoingConnectionNames).isEmpty()) {
            log.error("Modbus connections are not supported as outgoing connections");
        }

        for (ModbusClient client : connections.getModbusConnections(incomingConnectionNames).values()) {
            try {
                client.getClient().connect();
                log.debug("Modbus client: {}", client.getClient().isConnected());
            } catch (ModbusIOException ignored) {
            }
        }
    }

    private void handleOutgoingTransformations() {
        if (transformation.getToOutgoing() != null) {
            log.debug("Incoming connections: {}", incomingConnections);
            for (RequestHandler incomingConnection : incomingConnections) {
                incomingConnection.subscribe(transformation.getConnections().getIncomingTopic(), message -> {
                    String msg = new String((byte[]) message);
                    log.debug("Incoming message from device: \n{}", msg);

                    String transformedMessage = objectTransformer.transform(msg,
                            transformation.getToOutgoing().getMessage(),
                            transformation.getConnections().getIncomingFormat(),
                            transformation.getConnections().getOutgoingFormat());
                    log.debug("Transformed message: \n{}", transformedMessage);

                    sendMessage(transformedMessage,
                            transformation.getToOutgoing().getToTopic(),
                            outgoingConnections,
                            transformation.getToOutgoing().getRetryCount());
                });
            }
        }
    }

    private void handleIncomingTransformations() {
        String[] incomingConnectionNames = transformation.getConnections().getIncomingConnections();

        if (transformation.getToIncoming() != null) {
            if (transformation.getToIncoming().getToTopic() != null) {
                for (RequestHandler outgoingConnection : outgoingConnections) {
                    outgoingConnection.subscribe(transformation.getConnections().getOutgoingTopic(), message -> {
                        String msg = new String((byte[]) message);
                        log.debug("Incoming message from server: \n{}", msg);

                        String transformedMessage = objectTransformer.transform(msg,
                                transformation.getToIncoming().getMessage(),
                                transformation.getConnections().getOutgoingFormat(),
                                transformation.getConnections().getIncomingFormat());
                        log.debug("Transformed message: \n{}", transformedMessage);

                        sendMessage(transformedMessage,
                                transformation.getToIncoming().getToTopic(),
                                incomingConnections,
                                transformation.getToIncoming().getRetryCount());
                    });
                }
            } else {
                List<ModbusClient> incomingModbusConnections =
                        new ArrayList<>(connections.getModbusConnections(incomingConnectionNames).values());

                MessageModel messageModel = transformation.getToIncoming();

                for (RequestHandler outgoingConnection : outgoingConnections) {
                    outgoingConnection.subscribe(transformation.getConnections().getOutgoingTopic(), message -> {
                        String msg = new String((byte[]) message);
                        log.debug("Incoming message from server for modbus: {}", msg);
                        try {
                            buildModbusRequests(msg, incomingModbusConnections, outgoingConnections, messageModel);
                        } catch (ModbusNumberException | ParseException e) {
                            log.error("Error building modbus requests", e);
                        }
                    });
                }
            }
        }
    }

    private void sendMessage(String message, String topic, List<RequestHandler> connections, int retryCount) {
        Map<RequestHandler, String[]> failed = new HashMap<>();

        for (RequestHandler connection : connections) {
            try {
                connection.publish(message, topic);
            } catch (HandlerException e) {
                log.error("Error publishing outgoing message", e);
                failed.put(connection, new String[]{message, topic});
            }
        }

        if (retryCount > 0) {
            for (int i = 0; i < retryCount; i++) {
                for (Map.Entry<RequestHandler, String[]> entry : failed.entrySet()) {
                    try {
                        log.debug("Retrying to publish message");
                        entry.getKey().publish(entry.getValue()[0], entry.getValue()[1]);
                        failed.remove(entry.getKey());
                    } catch (HandlerException e) {
                        log.error("Error publishing failed message", e);
                    }
                }
            }
        }
    }

    private void sendModbusRequest(ModbusClient modbusClient,
                                   Map<Integer, Long> msgToRegisterMap,
                                   Map<Integer, Object> registerMap,
                                   MessageModel messageModel) {
        Map<ModbusModel, ModbusRequest> failed = new HashMap<>();

        for (ModbusModel modbusModel : transformation.getToIncoming().getModbusRegisters()) {
            ModbusRequest[] request = new ModbusRequest[1];
            try {
                request[0] = ModbusHandler.buildModbusRequest(msgToRegisterMap, modbusModel, messageModel);
                modbusClient.requestReply(request[0], String.valueOf(transformation.getToIncoming().getDeviceId()), msg -> {
                    try {
                        ModbusHandler.handleModbusResponse(msg, registerMap, modbusModel, messageModel);
                        failed.put(modbusModel, request[0]);
                    } catch (IllegalDataAddressException e) {
                        log.error("Illegal data address", e);
                    }
                });
            } catch (HandlerException e) {
                failed.put(modbusModel, request[0]);
            } catch (ModbusNumberException e) {
                log.error("Error building modbus request", e);
            }
        }

        if (transformation.getToIncoming().getRetryCount() > 0) {
            for (int i = 0; i < transformation.getToIncoming().getRetryCount(); i++) {
                for (Map.Entry<ModbusModel, ModbusRequest> entry : failed.entrySet()) {
                    try {
                        log.debug("Retrying to Modbus request...");
                        modbusClient.requestReply(entry.getValue(), String.valueOf(transformation.getToIncoming().getDeviceId()), msg -> {
                            try {
                                ModbusHandler.handleModbusResponse(msg, registerMap, entry.getKey(), messageModel);
                                failed.remove(entry.getKey());
                            } catch (IllegalDataAddressException e) {
                                log.error("Illegal data address", e);
                            }
                        });

                        failed.remove(entry.getKey());
                    } catch (HandlerException e) {
                        log.error("Error handling modbus request", e);
                    }
                }
            }
        }
    }

    private void handleIntervalRequests() {
        if (transformation.getIntervalRequest() == null) {
            return;
        }

        if (transformation.getIntervalRequest().getRequest().getToTopic() == null) {
            handleModbusInterval();
        } else {
            handleInterval();
        }
    }

    private void handleInterval() {
        String fromTopic = transformation.getIntervalRequest().getRequest().getFromTopic();
        String toTopic = transformation.getToOutgoing().getToTopic();
        for (RequestHandler requestHandler : incomingConnections) {
            requestHandler.subscribe(fromTopic, message -> {
                String msg = new String((byte[]) message);
                log.debug("Incoming message from device: \n{}", msg);

                String transformedMessage = objectTransformer.transform(msg,
                        transformation.getToOutgoing().getMessage(),
                        transformation.getConnections().getIncomingFormat(),
                        transformation.getConnections().getOutgoingFormat());
                log.debug("Transformed message: \n{}", transformedMessage);

                sendMessage(transformedMessage,
                        toTopic,
                        outgoingConnections,
                        transformation.getToOutgoing().getRetryCount());
            });

            Integer interval = transformation.getIntervalRequest().getInterval();

            ScheduledExecutorService executorService = Executors
                    .newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(() -> {
                try {
                    log.debug("Publishing interval request");
                    String message = transformation.getIntervalRequest().getRequest().getMessage();

                    sendMessage(message,
                            transformation.getIntervalRequest().getRequest().getToTopic(),
                            Collections.singletonList(requestHandler),
                            transformation.getIntervalRequest().getRequest().getRetryCount());
                } catch (Exception e) {
                    log.error("Error publishing interval request", e);
                }
            }, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    private void handleModbusInterval() {
        // Modbus request
        List<ModbusClient> incomingModbusConnections =
                new ArrayList<>(connections.getModbusConnections(transformation.getConnections().getIncomingConnections()).values());

        for (ModbusClient modbusClient : incomingModbusConnections) {
            HashMap<Integer, Object> registerMap = new HashMap<>();

            Integer interval = transformation.getIntervalRequest().getInterval();

            ScheduledExecutorService executorService = Executors
                    .newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(() -> {
                MessageModel messageModel = transformation.getIntervalRequest().getRequest();
                sendModbusRequest(modbusClient, Collections.emptyMap(), registerMap, messageModel);
            }, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    protected void buildModbusRequests(String message,
                                       List<ModbusClient> incomingModbusConnections,
                                       List<RequestHandler> outgoingConnections,
                                       MessageModel messageModel) throws ModbusNumberException, ParseException {
        Map<Integer, Long> msgToRegisterMap =
                objectTransformer.transformToModbus(transformation.getToIncoming().getModbusRegisters(),
                        message,
                        transformation.getConnections().getOutgoingFormat());

        log.debug("Message to register map: {}", msgToRegisterMap);

        for (ModbusClient modbusClient : incomingModbusConnections) {
            HashMap<Integer, Object> registerMap = new HashMap<>();

            sendModbusRequest(modbusClient, msgToRegisterMap, registerMap, messageModel);

            if (transformation.getToOutgoing() != null) {
                log.debug("Registers: {}", registerMap);

                String transformedMessage = objectTransformer.transform(registerMap,
                        transformation.getToOutgoing().getMessage(),
                        transformation.getConnections().getIncomingFormat(),
                        transformation.getConnections().getOutgoingFormat());
                log.debug("Transformed message: {}", transformedMessage);

                sendMessage(transformedMessage,
                        transformation.getToOutgoing().getToTopic(),
                        outgoingConnections,
                        transformation.getToOutgoing().getRetryCount());
            }
        }
    }
}

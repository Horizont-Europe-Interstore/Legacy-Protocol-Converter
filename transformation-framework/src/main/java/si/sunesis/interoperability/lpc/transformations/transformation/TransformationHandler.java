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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intelligt.modbus.jlibmodbus.exception.IllegalDataAddressException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusIOException;
import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import com.intelligt.modbus.jlibmodbus.msg.base.ModbusRequest;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.common.exceptions.HandlerException;
import si.sunesis.interoperability.common.interfaces.RequestHandler;
import si.sunesis.interoperability.lpc.transformations.configuration.models.*;
import si.sunesis.interoperability.lpc.transformations.connections.Connections;
import si.sunesis.interoperability.lpc.transformations.enums.ValidateIEEE2030Dot5;
import si.sunesis.interoperability.lpc.transformations.exceptions.LPCException;
import si.sunesis.interoperability.lpc.transformations.utils.TimeUtils;
import si.sunesis.interoperability.modbus.ModbusClient;

import javax.json.JsonObject;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final List<RequestHandler> outgoingConnections = new ArrayList<>();

    ScheduledExecutorService executorService = Executors
            .newScheduledThreadPool(1);
    private ScheduledFuture<?> modbusScheduledFuture;
    private ScheduledFuture<?> scheduledFuture;

    private Map<String, String> mappingsCache = null;

    private RegistrationModel registration;

    private final String port = System.getenv("PYTHON_PORT") != null ? System.getenv("PYTHON_PORT") : "9093";

    // Use a connection pool configuration for the client
    private final javax.ws.rs.client.Client webClient;
    private final WebTarget webTarget;

    public TransformationHandler(TransformationModel transformation, ObjectTransformer objectTransformer, Connections connections, RegistrationModel registrationModel) {
        this.transformation = transformation;
        this.objectTransformer = objectTransformer;
        this.connections = connections;
        this.registration = registrationModel;

        log.info("Transformation: {}", transformation.getName());

        // Initialize client with connection pooling and retry configuration
        org.glassfish.jersey.client.ClientConfig clientConfig = new org.glassfish.jersey.client.ClientConfig();

        clientConfig.property("jersey.config.client.connectionPoolSize", 20);
        clientConfig.property("jersey.config.client.keepAlive", true);

        // Configure timeouts - more generous timeouts
        clientConfig.property("jersey.config.client.connectTimeout", 90000);  // 90 seconds
        clientConfig.property("jersey.config.client.readTimeout", 90000);     // 90 seconds

        // Register custom tracing filter
        clientConfig.register((ClientRequestFilter) requestContext -> log.debug("Sending HTTP request to Python Modbus service: {}",
                requestContext.getMethod() + " " + requestContext.getUri()));

        // Register response filter
        clientConfig.register((ClientResponseFilter) (requestContext, responseContext) -> log.debug("Received HTTP response from Python Modbus service: {} {}",
                responseContext.getStatus(),
                responseContext.getStatusInfo().getReasonPhrase()));

        // Create the client with the configuration
        webClient = ClientBuilder.newBuilder()
                .withConfig(clientConfig)
                .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        webTarget = webClient.target("http://localhost:" + port + "/").path("modbus");
    }

    /**
     * Main handler method that initializes connections and starts handling transformations.
     * This is the entry point for the transformation process.
     *
     * @throws LPCException If there is an error during handling
     */
    public void handle() throws LPCException {
        String incomingTopic = transformation.getConnections().getOutgoingTopic() != null ? transformation.getConnections().getOutgoingTopic() : null;
        Integer deviceId = transformation.getToIncoming() != null ? transformation.getToIncoming().getDeviceId() : null;

        for (String connectionName : registration.getOutgoingConnections()) {
            RequestHandler requestHandler = connections.getConnectionsMap().get(connectionName);
            if (requestHandler != null) {
                try {
                    String topic = replaceWithNatsId(registration.getTopic(), registration.getDeviceId());
                    requestHandler.publish(registration.getMessage(), topic);
                } catch (HandlerException e) {
                    log.error("Error publishing registration message.", e);
                }
            }
        }

        if (incomingTopic != null && deviceId != null) {
            incomingTopic = replaceWithNatsId(incomingTopic, deviceId);
            for (String connectionName : transformation.getConnections().getOutgoingConnections()) {
                RequestHandler requestHandler = connections.getConnectionsMap().get(connectionName);
                if (requestHandler != null) {
                    try {
                        String message = """
                                {
                                    "deviceId": %d,
                                    "topic": "%s"
                                }
                                """.formatted(deviceId, incomingTopic);
                        requestHandler.publish(message, incomingTopic);

                        log.info("Published registration message for incoming topic: {}", incomingTopic);
                    } catch (HandlerException e) {
                        log.error("Error publishing registration message.", e);
                    }
                }
            }
        }

        String outgoingTopic = transformation.getToOutgoing() != null ? transformation.getToOutgoing().getToTopic() : null;
        Integer outgoingDeviceId = transformation.getIntervalRequest() != null ? transformation.getIntervalRequest().getRequest().getDeviceId() : null;

        if (outgoingTopic != null && outgoingDeviceId != null) {
            outgoingTopic = replaceWithNatsId(outgoingTopic, outgoingDeviceId);
            for (String connectionName : transformation.getConnections().getOutgoingConnections()) {
                RequestHandler requestHandler = connections.getConnectionsMap().get(connectionName);
                if (requestHandler != null) {
                    try {
                        String message = """
                                {
                                    "deviceId": %d,
                                    "topic": "%s"
                                }
                                """.formatted(deviceId, outgoingTopic);
                        requestHandler.publish(message, outgoingTopic);

                        log.info("Published registration message for outgoing topic: {}", outgoingTopic);
                    } catch (HandlerException e) {
                        log.error("Error publishing registration message.", e);
                    }
                }
            }
        }

        handleConnections();
        handleOutgoingTransformations();
        handleIncomingTransformations();
        handleIntervalRequests();
    }

    /**
     * Cleans up resources used by this handler.
     * Cancels scheduled tasks, disconnects from all connections, and clears connection lists.
     * Also closes the HTTP client used for Modbus communication.
     */
    public void destroy() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        if (modbusScheduledFuture != null) {
            modbusScheduledFuture.cancel(false);
            modbusScheduledFuture = null;
        }

        for (Map.Entry<String, RequestHandler> entry : connections.getConnectionsMap().entrySet()) {
            entry.getValue().disconnect();
        }

        incomingConnections.clear();
        outgoingConnections.clear();

        // Close the HTTP client
        if (webClient != null) {
            webClient.close();
        }
    }

    /**
     * Sets up and initializes all connections required for the transformation.
     * Populates the incomingConnections and outgoingConnections lists based on configuration.
     * Validates that Modbus connections are not used as outgoing connections.
     * Attempts to connect to all Modbus clients.
     *
     * @throws LPCException If Modbus connections are configured as outgoing connections
     */
    private void handleConnections() throws LPCException {
        incomingConnections.clear();
        outgoingConnections.clear();

        String[] incomingConnectionNames = transformation.getConnections().getIncomingConnections();
        String[] outgoingConnectionNames = transformation.getConnections().getOutgoingConnections();

        for (String key : connections.getConnectionsMap().keySet()) {
            for (String incomingConnectionName : incomingConnectionNames) {
                if (key.equals(incomingConnectionName)) {
                    incomingConnections.add(connections.getConnectionsMap().get(key));
                    break;
                }
            }

            for (String outgoingConnectionName : outgoingConnectionNames) {
                if (key.equals(outgoingConnectionName)) {
                    outgoingConnections.add(connections.getConnectionsMap().get(key));
                    break;
                }
            }
        }

        if (!connections.getModbusConnections(outgoingConnectionNames).isEmpty()) {
            throw new LPCException("Modbus connections are not supported as outgoing connections");
        }

        for (ModbusClient modbusClient : connections.getModbusConnections(incomingConnectionNames).values()) {
            try {
                modbusClient.getClient().connect();
            } catch (ModbusIOException ignored) {
                log.warn("Modbus connection failed");
            }
        }
    }

    /**
     * Sets up transformation and routing for messages going from device to server (outgoing direction).
     * Subscribes to the incoming topic and transforms received messages according to the configuration.
     * Sends the transformed messages to the configured outgoing topic.
     */
    private void handleOutgoingTransformations() {
        if (transformation.getToOutgoing() != null && transformation.getConnections().getIncomingTopic() != null) {
            String incomingTopic = transformation.getConnections().getIncomingTopic();
            incomingTopic = replacePlaceholders(incomingTopic);

            log.debug("Subscribing to outgoing topic: {}", incomingTopic);

            for (RequestHandler incomingConnection : incomingConnections) {
                incomingConnection.subscribe(incomingTopic, message -> {
                    String msg = new String((byte[]) message);
                    log.info("Incoming message on topic {} from device: \n{}", transformation.getConnections().getIncomingTopic(), msg);

                    if (transformation.getValidateIEEE2030dot5() == ValidateIEEE2030Dot5.BOTH ||
                            transformation.getValidateIEEE2030dot5() == ValidateIEEE2030Dot5.INCOMING) {
                        try {
                            objectTransformer.validateTransform(msg, transformation.getValidateIEEE2030dot5());
                        } catch (Exception e) {
                            log.error("Error validating transformation received from incoming: {}. {}", transformation.getName(), e.getMessage());
                        }
                    }

                    String transformedMessage = objectTransformer.transform(msg,
                            transformation.getToOutgoing().getMessage(),
                            transformation.getConnections().getIncomingFormat(),
                            transformation.getConnections().getOutgoingFormat());
                    log.info("Transformed outgoing message: \n{}", transformedMessage);

                    String toTopic = transformation.getToOutgoing().getToTopic();

                    Integer deviceId = null;

                    if (transformation.getIntervalRequest() != null && transformation.getIntervalRequest().getRequest() != null) {
                        deviceId = transformation.getIntervalRequest().getRequest().getDeviceId();
                    } else if (transformation.getToIncoming() != null && transformation.getToIncoming().getDeviceId() != null) {
                        deviceId = transformation.getToIncoming().getDeviceId();
                    }

                    toTopic = replaceWithNatsId(toTopic,
                            deviceId);
                    toTopic = replacePlaceholders(toTopic);

                    sendMessage(transformedMessage,
                            toTopic,
                            outgoingConnections,
                            transformation.getToOutgoing().getRetryCount());
                });
            }
        }
    }

    /**
     * Sets up transformation and routing for messages going from server to device (incoming direction).
     * Handles both standard protocol transformations and Modbus-specific transformations.
     * For standard protocols: Subscribes to outgoing topic, transforms received messages, and sends to device.
     * For Modbus: Subscribes to outgoing topic and translates messages into Modbus requests.
     */
    private void handleIncomingTransformations() {
        boolean toIncoming = transformation.getToIncoming() != null &&
                ((transformation.getToIncoming().getToTopic() != null && !transformation.getToIncoming().getToTopic().isEmpty())
                        || (transformation.getToIncoming().getModbusRegisters() != null && !transformation.getToIncoming().getModbusRegisters().isEmpty()));

        if (toIncoming && transformation.getConnections().getOutgoingTopic() != null) {
            if (!isModbus()) {
                String outgoingTopic = transformation.getConnections().getOutgoingTopic();
                outgoingTopic = replacePlaceholders(outgoingTopic);

                log.debug("Subscribing to incoming topic for non Modbus: {}", outgoingTopic);

                for (RequestHandler outgoingConnection : outgoingConnections) {
                    outgoingConnection.subscribe(outgoingTopic, message -> {
                        String msg = new String((byte[]) message);
                        log.info("Incoming message from server: \n{}", msg);

                        if (transformation.getValidateIEEE2030dot5() == ValidateIEEE2030Dot5.BOTH ||
                                transformation.getValidateIEEE2030dot5() == ValidateIEEE2030Dot5.OUTGOING) {
                            try {
                                objectTransformer.validateTransform(msg, transformation.getValidateIEEE2030dot5());
                            } catch (Exception e) {
                                log.error("Error validating transformation received from outgoing: {}. {}", transformation.getName(), e.getMessage());
                            }
                        }

                        String transformedMessage = objectTransformer.transform(msg,
                                transformation.getToIncoming().getMessage(),
                                transformation.getConnections().getOutgoingFormat(),
                                transformation.getConnections().getIncomingFormat());
                        log.info("Transformed incoming message: \n{}", transformedMessage);

                        String toTopic = transformation.getToIncoming().getToTopic();
                        toTopic = replacePlaceholders(toTopic);

                        sendMessage(transformedMessage,
                                toTopic,
                                incomingConnections,
                                transformation.getToIncoming().getRetryCount());
                    });
                }
            } else if (isModbus()) {
                String[] incomingConnectionNames = transformation.getConnections().getIncomingConnections();

                Map<String, ModbusClient> incomingModbusConnections = connections.getModbusConnections(incomingConnectionNames);

                MessageModel messageModel = transformation.getToIncoming();

                String outgoingTopic = transformation.getConnections().getOutgoingTopic();
                outgoingTopic = replaceWithNatsId(outgoingTopic,
                        messageModel.getDeviceId());
                outgoingTopic = replacePlaceholders(outgoingTopic);

                log.debug("Subscribing to incoming topic for Modbus: {}", outgoingTopic);

                for (RequestHandler outgoingConnection : outgoingConnections) {
                    outgoingConnection.subscribe(outgoingTopic, message -> {
                        String msg = new String((byte[]) message);
                        log.info("Incoming message from server for modbus: {}", msg);

                        if (transformation.getValidateIEEE2030dot5() == ValidateIEEE2030Dot5.BOTH ||
                                transformation.getValidateIEEE2030dot5() == ValidateIEEE2030Dot5.INCOMING) {
                            try {
                                objectTransformer.validateTransform(msg, transformation.getValidateIEEE2030dot5());
                            } catch (Exception e) {
                                log.error("Error validating transformation for Modbus: {}. {}", transformation.getName(), e.getMessage());
                            }
                        }

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

    /**
     * Publishes a message to a specified topic through all provided connections.
     * Implements retry logic for failed publish attempts based on the configured retry count.
     *
     * @param message     The message content to publish
     * @param topic       The topic to publish the message to
     * @param connections List of connection handlers to publish through
     * @param retryCount  Number of retry attempts for failed publishes
     */
    private void sendMessage(String message, String topic, List<RequestHandler> connections, int retryCount) {
        Map<RequestHandler, String[]> failed = new HashMap<>();

        log.debug("Publishing message to topic: {} with message: {}", topic, message);

        if (transformation.getValidateIEEE2030dot5() != ValidateIEEE2030Dot5.NONE) {
            try {
                objectTransformer.validateTransform(message, transformation.getValidateIEEE2030dot5());
            } catch (Exception e) {
                log.error("Error validating transformation: {}. {}", transformation.getName(), e.getMessage());
            }
        }

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

    /**
     * Sends Modbus requests to a Modbus device using either Java or Python libraries.
     * Groups requests for efficiency, sends them in parallel, and implements retry logic for failed requests.
     * Supports both TCP/IP and serial connections.
     *
     * @param modbusClient     The Modbus client to use for sending requests
     * @param connectionModel  Connection configuration for the Modbus device
     * @param msgToRegisterMap Map of register addresses to values from the incoming message
     * @param registerMap      Map to store register values read from or written to the device
     * @param messageModel     Configuration for the Modbus message format
     */
    private void sendModbusRequest(ModbusClient modbusClient,
                                   ConnectionModel connectionModel,
                                   Map<Integer, Float> msgToRegisterMap,
                                   Map<Integer, Object> registerMap,
                                   MessageModel messageModel) {
        ArrayList<List<ModbusModel>> failed = new ArrayList<>();

        List<List<ModbusModel>> groups = getModbusGroups(messageModel, registerMap);

        CountDownLatch latch = new CountDownLatch(groups.size());

        if (groups.size() > 1) {
            log.info("Grouping Modbus requests");
            log.debug("Groups: {}", groups);
        } else {
            log.info("Sending Modbus request");
        }

        log.debug("Using library: {}", messageModel.getModbusLibrary());

        for (List<ModbusModel> group : groups) {
            try {
                // If Host is null, it means it is Serial connection
                if (messageModel.getModbusLibrary().equalsIgnoreCase("java") || connectionModel.getHost() == null) {
                    ModbusRequest request = ModbusHandler.buildJavaModbusRequest(msgToRegisterMap, group, messageModel);

                    modbusClient.requestReply(request, String.valueOf(messageModel.getDeviceId()), msg -> {
                        try {
                            ModbusHandler.handleJavaModbusResponse(msg, registerMap, group, messageModel);
                        } catch (IllegalDataAddressException e) {
                            log.error("Illegal data address in group response", e);
                        } finally {
                            latch.countDown();
                        }
                    });
                } else {
                    javax.json.JsonObject modbusRequest = ModbusHandler.buildPythonModbusRequest(msgToRegisterMap, group, messageModel, connectionModel);

                    log.debug("Request data: {}", modbusRequest);

                    int maxRetries = 3;
                    int retryCount = 0;
                    boolean success = false;

                    while (!success) {
                        success = sendPythonModbusRequest(modbusRequest, group, registerMap, messageModel, retryCount, maxRetries);
                    }

                    latch.countDown();
                }
            } catch (InterruptedException ie) {
                log.error("Error during retrying Modbus request", ie);
                latch.countDown();
                failed.add(group);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error building or sending grouped Modbus request", e);
                latch.countDown();
                failed.add(group);
            }
        }

        try {
            boolean await = latch.await(1100L * groups.size(), TimeUnit.MILLISECONDS);
            if (!await) {
                log.error("Timed out waiting for group response");
            }
        } catch (InterruptedException e) {
            log.error("Error waiting for latch", e);
            Thread.currentThread().interrupt();
        }

        // Retry logic for failed groups
        if (messageModel.getRetryCount() > 0) {
            for (int i = 0; i < messageModel.getRetryCount(); i++) {
                for (List<ModbusModel> group : failed) {
                    try {
                        if (messageModel.getModbusLibrary().equalsIgnoreCase("java") || connectionModel.getHost() == null) {
                            ModbusRequest request = ModbusHandler.buildJavaModbusRequest(msgToRegisterMap, group, messageModel);
                            modbusClient.requestReply(request, String.valueOf(messageModel.getDeviceId()), msg -> {
                                try {
                                    ModbusHandler.handleJavaModbusResponse(msg, registerMap, group, messageModel);
                                    failed.remove(group);
                                } catch (IllegalDataAddressException e) {
                                    log.error("Illegal data address in group response", e);
                                }
                            });
                        } else {
                            JsonObject modbusRequest = ModbusHandler.buildPythonModbusRequest(msgToRegisterMap, group, messageModel, connectionModel);

                            int maxRetries = 3;
                            int retryCount = 0;
                            boolean success = false;

                            while (!success) {
                                success = sendPythonModbusRequest(modbusRequest, group, registerMap, messageModel, retryCount, maxRetries);

                                if (success) {
                                    failed.remove(group);
                                }
                            }
                        }
                    } catch (InterruptedException ie) {
                        log.error("Error during retrying Modbus request", ie);
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("Error retrying Modbus request", e);
                    }
                }
            }
        }
    }

    /**
     * Sets up periodic request handling based on the configured interval.
     * Delegates to specific handlers for Modbus or standard protocol intervals.
     * This is used for polling-based communication patterns where the system needs to
     * periodically request data from devices.
     */
    private void handleIntervalRequests() {
        if (transformation.getIntervalRequest() == null) {
            return;
        }

        if (isModbus()) {
            handleModbusInterval();
        } else if (transformation.getIntervalRequest().getRequest().getToTopic() != null) {
            handleInterval();
        }
    }

    /**
     * Handles periodic requests for standard (non-Modbus) protocols.
     * Sets up a scheduled task that sends configured request messages at specified intervals.
     * Also subscribes to the response topic to process device responses to the interval requests.
     */
    private void handleInterval() {
        String fromTopic = transformation.getIntervalRequest().getRequest().getFromTopic();
        fromTopic = replacePlaceholders(fromTopic);

        Long delay = getIntervalDelay();

        for (RequestHandler requestHandler : incomingConnections) {
            requestHandler.subscribe(fromTopic, message -> {
                String msg = new String((byte[]) message);
                log.info("Incoming message from device: \n{}", msg);

                String transformedMessage = objectTransformer.transform(msg,
                        transformation.getToOutgoing().getMessage(),
                        transformation.getConnections().getIncomingFormat(),
                        transformation.getConnections().getOutgoingFormat());
                log.info("Transformed message: \n{}", transformedMessage);

                String toTopic = transformation.getToOutgoing().getToTopic();
                toTopic = replacePlaceholders(toTopic);

                sendMessage(transformedMessage,
                        toTopic,
                        outgoingConnections,
                        transformation.getToOutgoing().getRetryCount());
            });

            Integer interval = transformation.getIntervalRequest().getInterval();

            scheduledFuture = executorService.scheduleAtFixedRate(() -> {
                try {
                    log.info("Publishing interval request");
                    String message = transformation.getIntervalRequest().getRequest().getMessage();

                    String toTopic = transformation.getIntervalRequest().getRequest().getToTopic();
                    toTopic = replacePlaceholders(toTopic);

                    sendMessage(message,
                            toTopic,
                            Collections.singletonList(requestHandler),
                            transformation.getIntervalRequest().getRequest().getRetryCount());
                } catch (Exception e) {
                    log.error("Error publishing interval request", e);
                }
            }, delay, interval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Handles periodic requests for Modbus protocols.
     * Sets up a scheduled task that sends Modbus requests at specified intervals.
     * Unlike standard protocols, this directly initiates Modbus requests rather than publishing messages.
     */
    private void handleModbusInterval() {
        // Modbus request
        Map<String, ModbusClient> incomingModbusConnections = connections.getModbusConnections(transformation.getConnections().getIncomingConnections());

        Integer interval = transformation.getIntervalRequest().getInterval();

        Long delay = getIntervalDelay();

        modbusScheduledFuture = executorService.scheduleAtFixedRate(() -> {
            log.info("Publishing Modbus interval request");
            MessageModel messageModel = transformation.getIntervalRequest().getRequest();

            try {
                buildModbusRequests(null, incomingModbusConnections, outgoingConnections, messageModel);
            } catch (ModbusNumberException e) {
                log.error("Error building modbus requests", e);
            } catch (ParseException e) {
                log.error("Error parsing message", e);
            }
        }, delay, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Builds and sends Modbus requests based on received messages or interval triggers.
     * Transforms incoming message data to Modbus register values when applicable.
     * Sends the Modbus requests and processes responses, then transforms and publishes the results.
     *
     * @param message                   The incoming message to transform (can be null for interval-based requests)
     * @param incomingModbusConnections Map of Modbus client connections to use
     * @param outgoingConnections       List of outgoing connections for publishing responses
     * @param messageModel              Configuration for the Modbus message format
     * @throws ModbusNumberException If there's an error with Modbus number processing
     * @throws ParseException        If there's an error parsing the message
     */
    private void buildModbusRequests(String message,
                                     Map<String, ModbusClient> incomingModbusConnections,
                                     List<RequestHandler> outgoingConnections,
                                     MessageModel messageModel) throws ModbusNumberException, ParseException {

        Map<Integer, Float> msgToRegisterMap = Collections.emptyMap();
        if (message != null) {
            msgToRegisterMap =
                    objectTransformer.transformToModbus(transformation.getToIncoming().getModbusRegisters(),
                            message,
                            transformation.getConnections().getOutgoingFormat());
        }

        for (Map.Entry<String, ModbusClient> modbusName : incomingModbusConnections.entrySet()) {
            HashMap<Integer, Object> registerMap = new HashMap<>();

            ConnectionModel connectionModel = connections.getConnectionModelMap().get(modbusName.getKey());
            ModbusClient modbusClient = modbusName.getValue();
            sendModbusRequest(modbusClient, connectionModel, msgToRegisterMap, registerMap, messageModel);

            if (transformation.getToOutgoing() != null && !registerMap.isEmpty()) {
                String transformedMessage = objectTransformer.transform(registerMap,
                        transformation.getToOutgoing().getMessage(),
                        transformation.getConnections().getIncomingFormat(),
                        transformation.getConnections().getOutgoingFormat());
                log.info("Transformed message: {}", transformedMessage);

                String toTopic = transformation.getToOutgoing().getToTopic();
                toTopic = replaceWithNatsId(toTopic,
                        messageModel.getDeviceId());
                toTopic = replacePlaceholders(toTopic);

                sendMessage(transformedMessage,
                        toTopic,
                        outgoingConnections,
                        transformation.getToOutgoing().getRetryCount());
            }
        }
    }

    /**
     * Sends a Modbus request to the Python-based Modbus service over HTTP.
     * Processes the response and handles common errors with retry logic.
     *
     * @param request      The JSON object containing the Modbus request details
     * @param group        The group of Modbus registers to process in this request
     * @param registerMap  Map to store register values from the response
     * @param messageModel Configuration for the Modbus message format
     * @param retryCount   Current retry attempt counter
     * @param maxRetries   Maximum number of retries to attempt
     * @return True if the request was processed successfully, false otherwise
     * @throws InterruptedException If the thread is interrupted during retry delays
     */
    private Boolean sendPythonModbusRequest(JsonObject
                                                    request, List<ModbusModel> group, Map<Integer, Object> registerMap, MessageModel messageModel,
                                            int retryCount, int maxRetries) throws InterruptedException {
        Response response = null;
        boolean success = false;
        try {
            response = webTarget.request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(request));

            if (response.hasEntity()) {
                String responseString = response.readEntity(String.class);
                log.debug("Received python response: {}", responseString);
                ModbusHandler.handlePythonModbusResponse(responseString, registerMap, group, messageModel);
            }
            success = true;
        } catch (javax.ws.rs.ProcessingException e) {
            // Check if it's an EOFException or timeout
            if (e.getCause() instanceof java.util.concurrent.ExecutionException &&
                    e.getCause().getCause() instanceof java.io.EOFException) {
                log.warn("EOFException occurred during HTTP request, retrying ({}/{})", retryCount + 1, maxRetries);
                retryCount++;
                if (retryCount >= maxRetries) {
                    log.error("Max retries reached for HTTP request", e);
                    throw e;
                }
                // Brief pause before retry
                Thread.sleep(100L * retryCount);
            } else {
                // Other processing exception
                log.error("Error processing HTTP request", e);
                throw e;
            }
        } catch (Exception e) {
            log.error("Error sending Modbus request", e);
            throw e;
        } finally {
            if (response != null) {
                response.close();
            }
        }

        return success;
    }

    /**
     * Groups Modbus registers into consecutive blocks for more efficient communication.
     * Optimizes requests by grouping registers with consecutive addresses together.
     * Handles different function codes with appropriate grouping strategies.
     *
     * @param messageModel Configuration for the Modbus message format containing register definitions
     * @param registerMap  Map to store default register values defined in the configuration
     * @return List of register groups that can be processed in single Modbus transactions
     */
    private List<List<ModbusModel>> getModbusGroups(MessageModel messageModel, Map<Integer, Object> registerMap) {
        // Group ModbusModels into consecutive blocks
        List<ModbusModel> sortedModels = new ArrayList<>(messageModel.getModbusRegisters());
        sortedModels.sort(Comparator.comparingInt(ModbusModel::getAddress));

        List<List<ModbusModel>> groups = new ArrayList<>();
        List<ModbusModel> currentGroup = new ArrayList<>();

        for (ModbusModel model : sortedModels) {
            if (model.getDefaultValue() != null) {
                registerMap.put(model.getAddress(), model.getDefaultValue());
            }

            if ((messageModel.getFunctionCode() != 16 && messageModel.getFunctionCode() != 23 && messageModel.getFunctionCode() != 11)
                    && model.getDefaultValue() != null) {

                continue;
            }

            if (messageModel.getFunctionCode() != 16 && messageModel.getFunctionCode() != 23 && messageModel.getFunctionCode() != 11) {
                groups.add(new ArrayList<>(Collections.singleton(model)));
                continue;
            }

            if (currentGroup.isEmpty()) {
                currentGroup.add(model);
            } else {
                ModbusModel lastModel = currentGroup.get(currentGroup.size() - 1);
                int lastModelRegisters = ModbusHandler.getNumOfRegisters(lastModel.getType());
                int lastModelEndAddress = lastModel.getAddress() + lastModelRegisters;
                if (model.getAddress() == lastModelEndAddress) {
                    currentGroup.add(model);
                } else {
                    groups.add(new ArrayList<>(currentGroup));
                    currentGroup.clear();
                    currentGroup.add(model);
                }
            }
        }
        if (!currentGroup.isEmpty()) {
            groups.add(new ArrayList<>(currentGroup));
        }

        return groups;
    }

    /**
     * Replaces placeholders in topic strings with environment or system property values.
     * Placeholders are surrounded by curly braces, like {DEVICE_ID} or {TENANT}.
     * First checks environment variables, then falls back to system properties.
     *
     * @param topic The topic string containing placeholders
     * @return The topic string with placeholders replaced by actual values
     */
    private String replacePlaceholders(String topic) {
        if (topic == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("\\{(.*?)}");
        Matcher matcher = pattern.matcher(topic);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            // Get the value inside the curly braces
            String found = matcher.group(1);

            String value = System.getenv(found);

            if (value == null) {
                value = System.getProperty(found, "#");
            }

            // Replace the found value with the replacement string
            matcher.appendReplacement(sb, value);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Replaces the {natsId} placeholder in the topic with a unique NATS ID based on device ID and connection parameters.
     * Generates a UUID based on the combination of connection IP and device ID, and caches the mappings in a local file.
     * If the mapping already exists, it reuses the existing NATS ID.
     *
     * @return The topic string with {natsId} replaced by the actual NATS ID
     */
    private String replaceWithNatsId(String topic, Integer deviceId) {
        log.debug("Replacing NATS ID in topic: {} for device ID: {}", topic, deviceId);

        if (deviceId == null) return topic;
        if (!topic.contains("{natsId}")) return topic;

        try {
            String[] incomingConnectionNames = transformation.getConnections().getIncomingConnections();
            String conn = connections.getConnectionNameToIp().get(incomingConnectionNames[0]);

            log.info("Replacing with NATS ID for device ID: {} and connection parameters: {} the topic: {}", deviceId, conn, topic);

            // Initialize cache if not already done
            if (mappingsCache == null) {
                mappingsCache = new HashMap<>();
                File file = new File("./mappings.json");
                if (file.exists()) {
                    Type type = new TypeToken<Map<String, String>>() {
                    }.getType();
                    String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                    mappingsCache = new Gson().fromJson(content, type);
                    if (mappingsCache == null) {
                        mappingsCache = new HashMap<>();
                    }
                }
            }

            String key = conn + "_" + deviceId;
            if (!mappingsCache.containsKey(key)) {
                File file = new File("./mappings.json");
                Type type = new TypeToken<Map<String, String>>() {
                }.getType();
                String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
                Map<String, String> fileCache = new Gson().fromJson(content, type);
                if (fileCache == null) {
                    fileCache = new HashMap<>();
                }
                fileCache.putAll(mappingsCache);

                if (!fileCache.containsKey(key)) {
                    // Generate and save new mapping
                    String lpcIdKey = "lpcId";
                    fileCache.putIfAbsent(lpcIdKey, String.valueOf(UUID.randomUUID()));

                    String fromString = key + fileCache.get(lpcIdKey);
                    //fromString = fromString.substring(0, Math.min(fromString.length(), 36));

                    log.info("From string: {}", fromString);

                    UUID natsId = UUID.nameUUIDFromBytes(fromString.getBytes());
                    fileCache.put(key, String.valueOf(natsId));

                    // Save updated mappings to file
                    try {
                        Files.write(Paths.get("./mappings.json"), new Gson().toJson(fileCache).getBytes());
                    } catch (IOException e) {
                        log.error("Error writing mappings file", e);
                    }
                }

                mappingsCache.clear();
                mappingsCache.putAll(fileCache);
            }

            return topic.replace("{natsId}", mappingsCache.get(key));
        } catch (Exception e) {
            log.error("Error replacing with NATS ID", e);
            return topic;
        }
    }

    /**
     * Determines if the current transformation involves Modbus connections.
     * Checks if any of the incoming connections are Modbus clients.
     *
     * @return true if Modbus connections are present, false otherwise
     */
    private boolean isModbus() {
        Collection<ModbusClient> clients = connections.getModbusConnections(transformation.getConnections().getIncomingConnections()).values();

        return !clients.isEmpty();
    }

    /**
     * Calculates the delay for interval requests based on the configured cron expression or fixed interval.
     * If a cron expression is provided, it calculates the next execution time using NTP synchronization if NTP is provided.
     * If not, it uses the fixed interval value directly.
     *
     * @return The calculated delay in milliseconds
     */
    private Long getIntervalDelay() {
        String cron = transformation.getIntervalRequest().getCron();
        String ntpServer = transformation.getIntervalRequest().getNtpServer();

        Long delay = Long.valueOf(transformation.getIntervalRequest().getInterval());

        if (cron == null || cron.isEmpty()) {
            log.debug("Using fixed delay for interval request: {} ms", delay);
        } else {
            try {
                delay = TimeUtils.calculateDelay(cron, ntpServer);
            } catch (Exception e) {
                log.error("Error while calculating interval delay", e);
                delay = Long.valueOf(transformation.getIntervalRequest().getInterval());
            }
        }

        return delay;
    }
}

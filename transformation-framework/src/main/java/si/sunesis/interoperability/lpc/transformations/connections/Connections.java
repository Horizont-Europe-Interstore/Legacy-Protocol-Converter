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
package si.sunesis.interoperability.lpc.transformations.connections;

import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.serial.*;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.nats.client.Options;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import si.sunesis.interoperability.common.interfaces.RequestHandler;
import si.sunesis.interoperability.lpc.transformations.configuration.Configuration;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ConnectionModel;
import si.sunesis.interoperability.lpc.transformations.exceptions.LPCException;
import si.sunesis.interoperability.modbus.ModbusClient;
import si.sunesis.interoperability.mqtt.Mqtt3Client;
import si.sunesis.interoperability.mqtt.Mqtt5Client;
import si.sunesis.interoperability.nats.NatsConnection;
import si.sunesis.interoperability.nats.NatsRequestHandler;
import si.sunesis.interoperability.rabbitmq.ChannelHandler;
import si.sunesis.interoperability.rabbitmq.RabbitMQClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
public class Connections {

    private final Configuration configuration;

    @Getter
    private final Map<String, RequestHandler> connectionsMap = new HashMap<>();

    @Getter
    private final Map<String, ConnectionModel> connectionModelMap = new HashMap<>();

    public Connections(Configuration configuration) throws LPCException {
        this.configuration = configuration;
        init();
    }

    /**
     * Initializes all connections defined in the configuration files.
     * Clears existing connections, reads configuration, and creates appropriate clients based on connection type.
     * Supports NATS, MQTT (v3 and v5), Modbus, and RabbitMQ connection types.
     *
     * @throws LPCException If there is an error creating any connection
     */
    public void init() throws LPCException {
        connectionsMap.clear();
        connectionModelMap.clear();

        List<ConnectionModel> yamlConnections = configuration.getConfigurations().stream()
                .flatMap(item -> item.getConnections().stream())
                .toList();

        Map<ConnectionModel, RequestHandler> clientMap = new HashMap<>();

        log.debug("Found {} connections", yamlConnections.size());
        for (ConnectionModel connection : yamlConnections) {
            RequestHandler requestHandler = clientMap.get(connection);

            this.connectionModelMap.put(connection.getName(), connection);

            if (requestHandler != null) {
                log.debug("Connection {} already exists under different name", connection.getName());
                this.connectionsMap.put(connection.getName(), requestHandler);
                continue;
            }

            if (connection.getType().equalsIgnoreCase("NATS")) {
                NatsConnection client;
                try {
                    client = buildNatsClient(connection);
                    NatsRequestHandler natsRequestHandler = new NatsRequestHandler(client);
                    this.connectionsMap.put(connection.getName(), natsRequestHandler);
                    clientMap.put(connection, natsRequestHandler);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LPCException("Interrupted NATS client", e);
                } catch (Exception e) {
                    log.error("Error building NATS client", e);
                    throw new LPCException("Error building NATS client", e);
                }
            } else if (connection.getType().equalsIgnoreCase("MQTT")) {
                try {
                    if (connection.getVersion() == 3) {
                        Mqtt3Client client = buildMqtt3Client(connection);
                        this.connectionsMap.put(connection.getName(), client);
                        clientMap.put(connection, client);
                    } else if (connection.getVersion() == 5) {
                        Mqtt5Client client = buildMqtt5Client(connection);
                        this.connectionsMap.put(connection.getName(), client);
                        clientMap.put(connection, client);
                    }
                } catch (Exception e) {
                    throw new LPCException("Error building MQTT client", e);
                }
            } else if (connection.getType().equalsIgnoreCase("modbus")) {
                if (connection.getHost() == null && connection.getDevice() == null) {
                    throw new LPCException("Host or device is required for Modbus connection!");
                }

                try {
                    Modbus.setLogLevel(Modbus.LogLevel.LEVEL_DEBUG);
                    ModbusClient client = buildModbusClient(connection);
                    this.connectionsMap.put(connection.getName(), client);
                    clientMap.put(connection, client);
                } catch (UnknownHostException | SerialPortException e) {
                    throw new LPCException("Error building Modbus client", e);
                }
            } else if (connection.getType().equalsIgnoreCase("RabbitMQ")) {
                RabbitMQClient client;
                try {
                    client = buildRabbitMQClient(connection);
                    this.connectionsMap.put(connection.getName(), client);
                    clientMap.put(connection, client);
                } catch (IOException | TimeoutException e) {
                    throw new LPCException("Error building RabbitMQ client", e);
                }
            }
        }
    }

    /**
     * Retrieves all Modbus connections or a subset specified by name.
     *
     * @param connectionNames Optional names of specific Modbus connections to retrieve
     * @return Map of Modbus connection names to their client instances
     */
    public Map<String, ModbusClient> getModbusConnections(String... connectionNames) {
        HashMap<String, ModbusClient> modbusConnections = new HashMap<>();
        for (Map.Entry<String, RequestHandler> entry : connectionsMap.entrySet()) {
            if (entry.getValue() instanceof ModbusClient handler) {
                if (connectionNames != null && connectionNames.length > 0
                        && !Arrays.asList(connectionNames).contains(entry.getKey())) {
                    continue;
                }

                modbusConnections.put(entry.getKey(), handler);
            }
        }
        return modbusConnections;
    }

    /**
     * Builds a NATS client based on connection configuration.
     *
     * @param connection The connection model containing NATS configuration parameters
     * @return A configured and connected NatsConnection instance
     * @throws IOException          If there is an error establishing the connection
     * @throws InterruptedException If the thread is interrupted during connection
     */
    private NatsConnection buildNatsClient(ConnectionModel connection) throws IOException, InterruptedException {
        NatsConnection client = new NatsConnection();

        client.setReconnect(connection.getReconnect());

        Options.Builder optionsBuilder = new Options.Builder()
                .server(connection.getHost() + ":" + connection.getPort());

        if (connection.getUsername() != null && connection.getPassword() != null) {
            optionsBuilder = optionsBuilder.userInfo(connection.getUsername(), connection.getPassword());
        }

        client.connectSync(optionsBuilder.build(), connection.getReconnect());

        return client;
    }

    private Mqtt3Client buildMqtt3Client(ConnectionModel connection) throws LPCException, org.eclipse.paho.client.mqttv3.MqttException, KeyManagementException, NoSuchAlgorithmException {
        MqttConnectOptions options = new MqttConnectOptions();

        String serverURI = connection.getHost() + ":" + connection.getPort();

        if (Boolean.TRUE.equals(connection.getReconnect())) {
            options.setAutomaticReconnect(true);
        }

        if (connection.getUsername() != null && connection.getPassword() != null) {
            options.setUserName(connection.getUsername());
            options.setPassword(connection.getPassword().toCharArray());
        }

        if (connection.getSsl() != null) {
            if (connection.getSsl().getClientCertPath() != null && connection.getSsl().getCaCertPath() != null) {
                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(buildKeyManagerFactory(connection).getKeyManagers(), buildTrustManagerFactory(connection).getTrustManagers(), null);
                options.setSocketFactory(sslContext.getSocketFactory());

                serverURI = "ssl://" + serverURI;
            } else if (Boolean.TRUE.equals(connection.getSsl().getUseDefault())) {
                SSLContext sslContext = SSLContext.getDefault();
                options.setSocketFactory(sslContext.getSocketFactory());

                serverURI = "ssl://" + serverURI;
            } else {
                serverURI = "tcp://" + serverURI;
            }
        } else {
            serverURI = "tcp://" + serverURI;
        }

        org.eclipse.paho.client.mqttv3.MqttAsyncClient mqttAsyncClient = new org.eclipse.paho.client.mqttv3.MqttAsyncClient(serverURI, connection.getName());
        mqttAsyncClient.connect(options).waitForCompletion();

        return new Mqtt3Client(mqttAsyncClient);
    }

    private Mqtt5Client buildMqtt5Client(ConnectionModel connection) throws LPCException, MqttException, KeyManagementException, NoSuchAlgorithmException {
        MqttConnectionOptions options = new MqttConnectionOptions();

        String serverURI = connection.getHost() + ":" + connection.getPort();

        if (Boolean.TRUE.equals(connection.getReconnect())) {
            options.setAutomaticReconnect(true);
        }

        if (connection.getUsername() != null && connection.getPassword() != null) {
            options.setUserName(connection.getUsername());
            options.setPassword(connection.getPassword().getBytes());
        }

        if (connection.getSsl() != null) {
            if (connection.getSsl().getClientCertPath() != null && connection.getSsl().getCaCertPath() != null) {
                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(buildKeyManagerFactory(connection).getKeyManagers(), buildTrustManagerFactory(connection).getTrustManagers(), null);
                options.setSocketFactory(sslContext.getSocketFactory());

                serverURI = "ssl://" + serverURI;
            } else if (Boolean.TRUE.equals(connection.getSsl().getUseDefault())) {
                SSLContext sslContext = SSLContext.getDefault();
                options.setSocketFactory(sslContext.getSocketFactory());

                serverURI = "ssl://" + serverURI;
            } else {
                serverURI = "tcp://" + serverURI;
            }
        } else {
            serverURI = "tcp://" + serverURI;
        }

        MqttAsyncClient mqttAsyncClient = new MqttAsyncClient(serverURI, connection.getName());
        mqttAsyncClient.connect(options).waitForCompletion();

        return new Mqtt5Client(mqttAsyncClient);
    }

    private RabbitMQClient buildRabbitMQClient(ConnectionModel connection) throws IOException, TimeoutException {
        Connection connectionMQ = getRabbitMQConnection(connection);

        ChannelHandler channelHandler = new ChannelHandler.ChannelHandlerBuilder()
                .setConnection(connectionMQ)
                .setExchangeName(connection.getExchangeName())
                .setExchangeType(connection.getExchangeType())
                .setRoutingKey(connection.getRoutingKey())
                .build();

        return new RabbitMQClient(channelHandler);
    }

    private static Connection getRabbitMQConnection(ConnectionModel connection) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();

        factory.setHost(connection.getHost());

        if (connection.getPort() != null) {
            factory.setPort(connection.getPort());
        }

        factory.setVirtualHost(connection.getVirtualHost());

        if (Boolean.TRUE.equals(connection.getReconnect())) {
            factory.setAutomaticRecoveryEnabled(true);
        }

        if (connection.getUsername() != null && connection.getPassword() != null) {
            factory.setUsername(connection.getUsername());
            factory.setPassword(connection.getPassword());
        }

        return factory.newConnection();
    }

    private ModbusClient buildModbusClient(ConnectionModel connectionModel) throws SerialPortException, UnknownHostException, LPCException {
        if (connectionModel.getHost() != null && connectionModel.getDevice() != null) {
            TcpParameters tcpParameters = new TcpParameters();
            tcpParameters.setHost(InetAddress.getByName(connectionModel.getHost()));
            tcpParameters.setPort(connectionModel.getPort());
            SerialUtils.setSerialPortFactory(new SerialPortFactoryTcpServer(tcpParameters));

            openPort(connectionModel);

            SerialParameters serialParameters = getSerialParameters(connectionModel);

            return new ModbusClient(ModbusMasterFactory.createModbusMasterRTU(serialParameters));
        }

        if (connectionModel.getHost() != null) {
            // TCP client
            TcpParameters tcpParameters = new TcpParameters();
            tcpParameters.setHost(InetAddress.getByName(connectionModel.getHost()));
            tcpParameters.setPort(connectionModel.getPort());

            return new ModbusClient(ModbusMasterFactory.createModbusMasterTCP(tcpParameters));
        } else {
            // Serial client
            openPort(connectionModel);

            SerialParameters serialParameters = getSerialParameters(connectionModel);

            return new ModbusClient(ModbusMasterFactory.createModbusMasterRTU(serialParameters));
        }
    }

    private void openPort(ConnectionModel connectionModel) throws LPCException {
        try {
            jssc.SerialPort port = new jssc.SerialPort(connectionModel.getDevice());

            log.debug("Port {} is opened: {}", port.getPortName(), port.isOpened());

            if (!port.isOpened()) {
                log.info("Opening port: {}", port.getPortName());
                log.info("Opened: {}", port.openPort());
            }
        } catch (jssc.SerialPortException e) {
            throw new LPCException("Error opening serial port", e);
        }
    }

    private SerialParameters getSerialParameters(ConnectionModel connection) {
        SerialParameters serialParameters = new SerialParameters();
        if (connection.getDevice() == null) {
            throw new IllegalArgumentException("Device is required for serial connection");
        }
        serialParameters.setDevice(connection.getDevice());

        if (connection.getBaudRate() != null) {
            serialParameters.setBaudRate(SerialPort.BaudRate.getBaudRate(connection.getBaudRate()));
        }

        if (connection.getDataBits() != null) {
            serialParameters.setDataBits(connection.getDataBits());
        }

        if (connection.getParity() != null) {
            switch (connection.getParity()) {
                case "even":
                    serialParameters.setParity(SerialPort.Parity.EVEN);
                    break;
                case "odd":
                    serialParameters.setParity(SerialPort.Parity.ODD);
                    break;
                case "mark":
                    serialParameters.setParity(SerialPort.Parity.MARK);
                    break;
                case "space":
                    serialParameters.setParity(SerialPort.Parity.SPACE);
                    break;
                case "none":
                default:
                    serialParameters.setParity(SerialPort.Parity.NONE);
                    break;
            }
        }

        if (connection.getStopBits() != null) {
            serialParameters.setStopBits(connection.getStopBits());
        }
        return serialParameters;
    }

    private KeyManagerFactory buildKeyManagerFactory(ConnectionModel connection) throws LPCException {
        try (FileInputStream inKey = new FileInputStream(connection.getSsl().getClientCertPath())) {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            if (connection.getSsl().getClientCertPassword() == null) {
                keyStore.load(inKey, null);
            } else {
                keyStore.load(inKey, connection.getSsl().getClientCertPassword().toCharArray());
            }

            KeyManagerFactory kmf = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, connection.getSsl().getClientCertPassword().toCharArray());
            return kmf;
        } catch (Exception e) {
            throw new LPCException("Error building KeyManagerFactory", e);
        }
    }

    private TrustManagerFactory buildTrustManagerFactory(ConnectionModel connection) throws LPCException {
        try (FileInputStream in = new FileInputStream(connection.getSsl().getCaCertPath())) {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            java.security.cert.Certificate caCert = certFactory.generateCertificate(in);
            trustStore.setCertificateEntry("caCert", caCert);

            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf;
        } catch (Exception e) {
            throw new LPCException("Error building TrustManagerFactory", e);
        }
    }
}

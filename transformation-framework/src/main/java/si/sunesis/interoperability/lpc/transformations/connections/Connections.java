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

import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.intelligt.modbus.jlibmodbus.Modbus;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.serial.*;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.nats.client.Options;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.util.io.pem.PemReader;
import si.sunesis.interoperability.common.exceptions.HandlerException;
import si.sunesis.interoperability.common.interfaces.RequestHandler;
import si.sunesis.interoperability.lpc.certificate.management.CertificateManagement;
import si.sunesis.interoperability.lpc.transformations.certificates.CertificateRegistration;
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
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
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

    public Connections(Configuration configuration) throws LPCException {
        this.configuration = configuration;
        init();
    }

    public void init() throws LPCException {
        connectionsMap.clear();

        List<ConnectionModel> yamlConnections = configuration.getConfigurations().stream()
                .flatMap(item -> item.getConnections().stream())
                .toList();

        Map<ConnectionModel, RequestHandler> clientMap = new HashMap<>();

        log.debug("Found {} connections", yamlConnections.size());
        for (ConnectionModel connection : yamlConnections) {
            RequestHandler requestHandler = clientMap.get(connection);

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
                } catch (InterruptedException | IOException e) {
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

    private Mqtt3Client buildMqtt3Client(ConnectionModel connection) throws LPCException, IOException, HandlerException, InterruptedException {
        String generatedDeviceCertPath = generateCertificates(connection);

        Mqtt3ClientBuilder client = com.hivemq.client.mqtt.mqtt3.Mqtt3Client.builder()
                .identifier(connection.getName())
                .serverHost(connection.getHost())
                .serverPort(connection.getPort())
                .addDisconnectedListener(context -> log.debug("Disconnected from MQTT broker 3: {}", context.getCause()))
                .addConnectedListener(context -> log.debug("Connected to MQTT broker 3"));

        if (Boolean.TRUE.equals(connection.getReconnect())) {
            client = client.automaticReconnectWithDefaultConfig();
        }

        if (connection.getUsername() != null && connection.getPassword() != null) {
            client = client.simpleAuth()
                    .username(connection.getUsername())
                    .password(connection.getPassword().getBytes())
                    .applySimpleAuth();
        }

        if (connection.getSsl() != null) {
            if (connection.getSsl().getClientCertPath() != null && connection.getSsl().getCaCertPath() != null) {
                client = client.sslConfig()
                        .keyManagerFactory(buildKeyManagerFactory(connection))
                        .trustManagerFactory(buildTrustManagerFactory(connection))
                        .applySslConfig();
            } else if (connection.getSsl().getPreenrollmentCertPath() != null
                    && connection.getSsl().getClientKeyPath() != null
                    && Boolean.TRUE.equals(connection.getActivateCertificate())) {
                client = client.sslConfig()
                        .keyManagerFactory(buildKeyManagerFactoryUsingKey(connection, generatedDeviceCertPath))
                        .applySslConfig();
            } else if (Boolean.TRUE.equals(connection.getSsl().getUseDefault())) {
                client = client.sslWithDefaultConfig();
            }
        }

        Mqtt3BlockingClient client1 = client.buildBlocking();

        Mqtt3Client mqtt3Client;

        if (Boolean.TRUE.equals(connection.getActivateCertificate())) {
            X509CertificateHolder certificateHolder = CertificateManagement.getCertificateHolder(generatedDeviceCertPath);
            String commonName = CertificateManagement.getRDNValue(certificateHolder.getSubject(), BCStyle.CN);

            client1 = client.identifier(commonName).buildBlocking();

            // First connect to register the certificate, device will drop connection
            try {
                client1.connect();
            } catch (Exception e) {
                log.debug("Dropped MQTT connection, expected: {}", e.getMessage());
            }

            Thread.sleep(5000);
            client1.connect();

            mqtt3Client = new Mqtt3Client(client1.toAsync());

            // Register gateway
            String clientId = CertificateRegistration.registerGateway(generatedDeviceCertPath, mqtt3Client);

            client1 = client.identifier(clientId).buildBlocking();
            client1.connect();
            mqtt3Client = new Mqtt3Client(client1.toAsync());

            // Register thing
            CertificateRegistration.registerThing(generatedDeviceCertPath, mqtt3Client);
        } else {
            client1.connect();

            mqtt3Client = new Mqtt3Client(client1.toAsync());
        }

        return mqtt3Client;
    }

    private Mqtt5Client buildMqtt5Client(ConnectionModel connection) throws LPCException, HandlerException, IOException, InterruptedException {
        String generatedDeviceCertPath = generateCertificates(connection);

        Mqtt5ClientBuilder client = com.hivemq.client.mqtt.mqtt5.Mqtt5Client.builder()
                .identifier(connection.getName())
                .serverHost(connection.getHost())
                .serverPort(connection.getPort())
                .addDisconnectedListener(context -> log.debug("Disconnected from MQTT broker 5: {}", context.getCause()))
                .addConnectedListener(context -> log.debug("Connected to MQTT broker 5"));

        if (Boolean.TRUE.equals(connection.getReconnect())) {
            client = client.automaticReconnectWithDefaultConfig();
        }

        if (connection.getUsername() != null && connection.getPassword() != null) {
            client = client.simpleAuth()
                    .username(connection.getUsername())
                    .password(connection.getPassword().getBytes())
                    .applySimpleAuth();
        }

        if (connection.getSsl() != null) {
            if (connection.getSsl().getClientCertPath() != null && connection.getSsl().getCaCertPath() != null) {
                client = client.sslConfig()
                        .keyManagerFactory(buildKeyManagerFactory(connection))
                        .trustManagerFactory(buildTrustManagerFactory(connection))
                        .applySslConfig();
            } else if (connection.getSsl().getPreenrollmentCertPath() != null
                    && connection.getSsl().getClientKeyPath() != null
                    && Boolean.TRUE.equals(connection.getActivateCertificate())) {
                client = client.sslConfig()
                        .keyManagerFactory(buildKeyManagerFactoryUsingKey(connection, generatedDeviceCertPath))
                        .applySslConfig();
            } else if (Boolean.TRUE.equals(connection.getSsl().getUseDefault())) {
                client = client.sslWithDefaultConfig();
            }
        }

        Mqtt5BlockingClient client1 = client.buildBlocking();

        Mqtt5Client mqtt5Client;

        if (Boolean.TRUE.equals(connection.getActivateCertificate())) {
            X509CertificateHolder certificateHolder = CertificateManagement.getCertificateHolder(generatedDeviceCertPath);
            String commonName = CertificateManagement.getRDNValue(certificateHolder.getSubject(), BCStyle.CN);

            client1 = client.identifier(commonName).buildBlocking();

            // First connect to register the certificate, device will drop connection
            try {
                client1.connect();
            } catch (Exception e) {
                log.debug("Dropped MQTT connection, expected: {}", e.getMessage());
            }

            Thread.sleep(5000);
            client1.connect();

            mqtt5Client = new Mqtt5Client(client1.toAsync());

            // Register gateway
            String clientId = CertificateRegistration.registerGateway(generatedDeviceCertPath, mqtt5Client);

            client1 = client.identifier(clientId).buildBlocking();
            client1.connect();
            mqtt5Client = new Mqtt5Client(client1.toAsync());

            // Register thing
            CertificateRegistration.registerThing(generatedDeviceCertPath, mqtt5Client);
        } else {
            client1.connect();

            mqtt5Client = new Mqtt5Client(client1.toAsync());
        }

        return mqtt5Client;
    }

    private String generateCertificates(ConnectionModel connection) throws LPCException {
        if (Boolean.TRUE.equals(connection.getActivateCertificate())) {
            try {
                String certPath = connection.getSsl().getPreenrollmentCertPath();
                // Get folder
                String folder = certPath.substring(0, certPath.lastIndexOf("/"));
                String csrPath = folder + "/req-generated.p10";
                String deviceCertPath = folder + "/device-cert.p7";

                return CertificateManagement.getCertificate(connection.getSsl().getPreenrollmentCertPath(),
                        connection.getSsl().getPreenrollmentCertPassword(),
                        csrPath,
                        deviceCertPath,
                        connection.getSsl().getClientKeyPath(),
                        connection.getSsl().getSubject());
            } catch (Exception e) {
                throw new LPCException("Error getting certificate", e);
            }
        }

        return null;
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

    private static KeyManagerFactory buildKeyManagerFactoryUsingKey(ConnectionModel connection, String createdCrtPath) throws LPCException {
        log.info("Building KeyManagerFactory with client key: {}", connection.getSsl().getClientKeyPath());
        try (FileInputStream fis = new FileInputStream(createdCrtPath)) {
            // Load the client certificate
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            java.security.cert.Certificate clientCert = certFactory.generateCertificate(fis);

            PrivateKey privateKey = parseECPrivateKey(connection.getSsl().getClientKeyPath());

            // Create a KeyStore and add the client certificate and private key
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry("clientKey", privateKey, null,
                    new java.security.cert.Certificate[]{clientCert});

            // Initialize KeyManagerFactory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, null);

            return kmf;
        } catch (Exception e) {
            throw new LPCException("Error building KeyManagerFactory", e);
        }
    }

    public static PrivateKey parseECPrivateKey(String filePath) throws Exception {
        log.info("Parsing EC private key from file: {}", filePath);

        String lines = Files.readString(Paths.get(filePath));

        String output = lines.replaceAll("-----BEGIN EC PARAMETERS-----\n.*?\n-----END EC PARAMETERS-----\n", "");

        PemReader reader = new PemReader(new StringReader(output));
        ECPrivateKey key = ECPrivateKey.getInstance(reader.readPemObject().getContent());

        X9ECParameters ecParameters = ECNamedCurveTable.getByOID(ASN1ObjectIdentifier.getInstance(key.getParametersObject().toASN1Primitive()));
        ECParameterSpec ecSpec = new ECParameterSpec(
                ecParameters.getCurve(),
                ecParameters.getG(),
                ecParameters.getN(),
                ecParameters.getH());

        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(key.getKey(), ecSpec));
    }

    private KeyManagerFactory buildKeyManagerFactory(ConnectionModel connection) throws LPCException {
        try (FileInputStream inKey = new FileInputStream(connection.getSsl().getClientCertPath())) {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(inKey, connection.getSsl().getClientCertPassword().toCharArray());

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

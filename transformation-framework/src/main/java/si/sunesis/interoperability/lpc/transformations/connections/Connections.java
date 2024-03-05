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

import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.intelligt.modbus.jlibmodbus.master.ModbusMasterFactory;
import com.intelligt.modbus.jlibmodbus.serial.SerialParameters;
import com.intelligt.modbus.jlibmodbus.serial.SerialPort;
import com.intelligt.modbus.jlibmodbus.tcp.TcpParameters;
import io.nats.client.Options;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.common.AbstractRequestHandler;
import si.sunesis.interoperability.lpc.transformations.configuration.Configuration;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ConnectionModel;
import si.sunesis.interoperability.modbus.ModbusClient;
import si.sunesis.interoperability.mqtt.Mqtt3Client;
import si.sunesis.interoperability.mqtt.Mqtt5Client;
import si.sunesis.interoperability.nats.NatsConnection;
import si.sunesis.interoperability.nats.NatsRequestHandler;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
@ApplicationScoped
public class Connections {

    @Inject
    private Configuration configuration;

    @Getter
    private final Map<String, AbstractRequestHandler> connectionsMap = new HashMap<>();

    @SneakyThrows
    @PostConstruct
    public void init() {
        List<ConnectionModel> yamlConnections = configuration.getConnections();
        log.info("Found {} connections", yamlConnections.size());
        for (ConnectionModel connection : yamlConnections) {
            if (connection.getType().equalsIgnoreCase("NATS")) {
                NatsConnection client = buildNatsClient(connection);

                NatsRequestHandler requestHandler = new NatsRequestHandler(client);
                this.connectionsMap.put(connection.getName(), requestHandler);
            } else if (connection.getType().equalsIgnoreCase("MQTT")) {
                if (connection.getVersion() == 3) {
                    this.connectionsMap.put(connection.getName(), buildMqtt3Client(connection));
                } else if (connection.getVersion() == 5) {
                    this.connectionsMap.put(connection.getName(), buildMqtt5Client(connection));
                }
            } else if (connection.getType().equalsIgnoreCase("modbus")) {
                if (connection.getHost() == null && connection.getDevice() == null) {
                    throw new IllegalArgumentException("Host or device is required for modbus connection");
                }

                if (connection.getHost() != null) {
                    // TCP client
                    TcpParameters tcpParameters = new TcpParameters();
                    tcpParameters.setHost(InetAddress.getByName(connection.getHost()));
                    tcpParameters.setPort(connection.getPort());

                    ModbusClient client = new ModbusClient(ModbusMasterFactory.createModbusMasterTCP(tcpParameters));
                    this.connectionsMap.put(connection.getName(), client);
                } else {
                    // Serial client
                    SerialParameters serialParameters = getSerialParameters(connection);

                    ModbusClient client = new ModbusClient(ModbusMasterFactory.createModbusMasterRTU(serialParameters));
                    this.connectionsMap.put(connection.getName(), client);
                }
            }
        }
    }

    public Map<String, NatsRequestHandler> getNatsConnections(String... connectionNames) {
        HashMap<String, NatsRequestHandler> natsConnections = new HashMap<>();
        for (Map.Entry<String, AbstractRequestHandler> entry : connectionsMap.entrySet()) {
            if (entry.getValue() instanceof NatsRequestHandler handler) {
                if (connectionNames != null && connectionNames.length > 0
                        && !Arrays.asList(connectionNames).contains(entry.getKey())) {
                    continue;
                }

                natsConnections.put(entry.getKey(), handler);
            }
        }
        return natsConnections;
    }

    public Map<String, Mqtt5Client> getMqttConnections(String... connectionNames) {
        HashMap<String, Mqtt5Client> mqttConnections = new HashMap<>();
        for (Map.Entry<String, AbstractRequestHandler> entry : connectionsMap.entrySet()) {
            if (entry.getValue() instanceof Mqtt5Client handler) {
                if (connectionNames != null && connectionNames.length > 0
                        && !Arrays.asList(connectionNames).contains(entry.getKey())) {
                    continue;
                }

                mqttConnections.put(entry.getKey(), handler);
            }
        }
        return mqttConnections;
    }

    public Map<String, ModbusClient> getModbusConnections(String... connectionNames) {
        HashMap<String, ModbusClient> modbusConnections = new HashMap<>();
        for (Map.Entry<String, AbstractRequestHandler> entry : connectionsMap.entrySet()) {
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

    private Mqtt3Client buildMqtt3Client(ConnectionModel connection) {
        Mqtt3ClientBuilder client = com.hivemq.client.mqtt.mqtt3.Mqtt3Client.builder()
                .identifier(connection.getName())
                .serverHost(connection.getHost())
                .serverPort(connection.getPort());

        if (Boolean.TRUE.equals(connection.getReconnect())) {
            client = client.automaticReconnectWithDefaultConfig();
        }

        if (connection.getSsl() != null) {
            client = client.sslWithDefaultConfig();
        }

        if (connection.getUsername() != null && connection.getPassword() != null) {
            client = client.simpleAuth()
                    .username(connection.getUsername())
                    .password(connection.getPassword().getBytes())
                    .applySimpleAuth();
        }

        if (connection.getSsl().getTrustStore() != null) {
            client = client.sslConfig()
                    .trustManagerFactory(buildTrustManagerFactory(connection))
                    .applySslConfig();
        }

        if (connection.getSsl() != null) {
            if (connection.getSsl().getTrustStore() != null && connection.getSsl().getKeyStore() != null) {
                client = client.sslConfig()
                        .trustManagerFactory(buildTrustManagerFactory(connection))
                        .applySslConfig();

                client = client.sslConfig()
                        .keyManagerFactory(buildKeyManagerFactory(connection))
                        .applySslConfig();
            } else {
                client = client.sslWithDefaultConfig();
            }
        }

        return new Mqtt3Client(client.buildAsync());
    }

    private Mqtt5Client buildMqtt5Client(ConnectionModel connection) {
        Mqtt5ClientBuilder client = com.hivemq.client.mqtt.mqtt5.Mqtt5Client.builder()
                .identifier(connection.getName())
                .serverHost(connection.getHost())
                .serverPort(connection.getPort());

        if (Boolean.TRUE.equals(connection.getReconnect())) {
            client = client.automaticReconnectWithDefaultConfig();
        }

        if (connection.getSsl() != null) {
            client = client.sslWithDefaultConfig();
        }

        if (connection.getUsername() != null && connection.getPassword() != null) {
            client = client.simpleAuth()
                    .username(connection.getUsername())
                    .password(connection.getPassword().getBytes())
                    .applySimpleAuth();
        }

        if (connection.getSsl() != null) {
            if (connection.getSsl().getTrustStore() != null && connection.getSsl().getKeyStore() != null) {
                client = client.sslConfig()
                        .trustManagerFactory(buildTrustManagerFactory(connection))
                        .applySslConfig();

                client = client.sslConfig()
                        .keyManagerFactory(buildKeyManagerFactory(connection))
                        .applySslConfig();
            } else if (Boolean.TRUE.equals(connection.getSsl().getUseDefault())) {
                client = client.sslWithDefaultConfig();
            }
        }

        return new Mqtt5Client(client.buildAsync());
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

    private KeyManagerFactory buildKeyManagerFactory(ConnectionModel connection) {
        try {
            KeyStore keyStore = KeyStore.getInstance(connection.getSsl().getKeyStore().getType());
            InputStream inKey = new FileInputStream(connection.getSsl().getKeyStore().getPath());
            keyStore.load(inKey, connection.getSsl().getKeyStore().getStorePassword().toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, connection.getSsl().getKeyStore().getKeyPassword().toCharArray());
            return kmf;
        } catch (Exception e) {
            log.error("Error building KeyManagerFactory", e);
        }

        return null;
    }

    private TrustManagerFactory buildTrustManagerFactory(ConnectionModel connection) {
        try {
            KeyStore trustStore = KeyStore.getInstance(connection.getSsl().getTrustStore().getType());
            InputStream in = new FileInputStream(connection.getSsl().getTrustStore().getPath());
            trustStore.load(in, connection.getSsl().getTrustStore().getStorePassword().toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf;
        } catch (Exception e) {
            log.error("Error building TrustManagerFactory", e);
        }
        return null;
    }
}

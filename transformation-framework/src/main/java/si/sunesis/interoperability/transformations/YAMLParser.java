package si.sunesis.interoperability.transformations;

import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.transformations.connections.Connections;
import si.sunesis.interoperability.transformations.models.YamlTransformationModel;
import si.sunesis.interoperability.transformations.models.YamlTransformationsModel;
import si.sunesis.interoperability.transformations.transformation.ObjectTransformer;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;

@Slf4j
@ApplicationScoped
public class YAMLParser {

    @Inject
    private YamlTransformationsModel yamlTransformationsModel;

    @Inject
    private ObjectTransformer objectTransformer;

    @Inject
    private Connections connections;

    @PostConstruct
    private void init() {
        log.info("YAMLParser initialized");

        /*
        log.info("Connections: {}", connections.getConnectionsMap().size());
        log.info("MQTT Connection: {}", connections.getMqttConnections().get("MQTT-broker").getClient().getState());
        connections.getNatsConnections().get("NATS-conn").getClient().getConnection().ifPresent(connection -> log.info("NATS Connection: {}", connection.getServerInfo()));

        for (YamlTransformationModel transformation : yamlTransformationsModel.getTransformations()) {
            log.info("Transformation: {}", transformation.getName());

            String[] incomingConnectionNames = transformation.getIncomingConnections();
            String[] outgoingConnectionNames = transformation.getOutgoingConnections();

            List<RequestHandler> incomingConnections = new ArrayList<>();
            List<RequestHandler> outgoingConnections = new ArrayList<>();

            for (String connectionName : incomingConnectionNames) {
                log.info("Connection: {}", connectionName);

                if (connections.getMqttConnections().containsKey(connectionName)) {
                    log.info("Connection type: MQTT");
                    incomingConnections.add(connections.getMqttConnections().get(connectionName));
                } else if (connections.getNatsConnections().containsKey(connectionName)) {
                    log.info("Connection type: NATS");
                    incomingConnections.add(connections.getNatsConnections().get(connectionName));
                }
            }

            for (String connectionName : outgoingConnectionNames) {
                log.info("Connection: {}", connectionName);

                if (connections.getMqttConnections().containsKey(connectionName)) {
                    log.info("Connection type: MQTT");
                    outgoingConnections.add(connections.getMqttConnections().get(connectionName));
                } else if (connections.getNatsConnections().containsKey(connectionName)) {
                    log.info("Connection type: NATS");
                    outgoingConnections.add(connections.getNatsConnections().get(connectionName));
                }
            }

            if (transformation.getToServer() != null) {
                for (RequestHandler incomingConnection : incomingConnections) {
                    incomingConnection.subscribe(transformation.getDeviceTopic(), message -> {
                        String msg = new String((byte[]) message);
                        log.info("Received message: {}", msg);

                        String mapped = parseFromDevice(msg, transformation);

                        log.info("Mapped: {}", mapped);

                        for (RequestHandler outgoingConnection : outgoingConnections) {
                            outgoingConnection.publish(mapped, transformation.getServerTopic());
                        }
                    });
                }
            }

            if (transformation.getToDevice() != null) {
                for (RequestHandler outgoingConnection : outgoingConnections) {
                    outgoingConnection.subscribe(transformation.getServerTopic(), message -> {
                        String msg = new String((byte[]) message);
                        log.info("Received message: {}", msg);

                        String mapped = parseFromServer(msg, transformation);

                        for (RequestHandler incomingConnection : incomingConnections) {
                            incomingConnection.publish(mapped, transformation.getDeviceTopic());
                        }
                    });
                }
            }
        }*/
    }

    public String parseFromDevice(String fromDevice, YamlTransformationModel transformation) {
        return objectTransformer.transform(fromDevice,
                transformation.getToOutgoing(),
                transformation.getIncomingFormat(),
                transformation.getOutgoingFormat());
    }

    public String parseFromServer(String fromServer, YamlTransformationModel transformation) {
        return objectTransformer.transform(fromServer,
                transformation.getToIncoming(),
                transformation.getOutgoingFormat(),
                transformation.getIncomingFormat());
    }

    public Integer getConnections() {
        return connections.getConnectionsMap().size();
    }

    public void parseFromDevice(String fromDevice) {
        try {
            for (YamlTransformationModel transformation : yamlTransformationsModel.getTransformations()) {
                log.info("Transformation: {}", transformation.getName());

                log.info("Device format: {}", transformation.getIncomingFormat());
                log.info("Server format: {}", transformation.getOutgoingFormat());
                log.info("Server mapping: {}", transformation.getToOutgoing());
                log.info("Device mapping: {}", transformation.getToIncoming());

                log.info("Incoming connections: {}", Arrays.toString(transformation.getIncomingConnections()));
                log.info("Outgoing connections: {}", Arrays.toString(transformation.getOutgoingConnections()));

                String mapped = objectTransformer.transform(fromDevice,
                        transformation.getOutgoingTopic(),
                        transformation.getIncomingFormat(),
                        transformation.getOutgoingFormat());

                log.info("Mapped: {}", mapped);
            }
        } catch (Exception e) {
            log.error("Error parsing YAML", e);
        }
    }

    public void parseFromServer(String fromServer) {
        try {
            for (YamlTransformationModel transformation : yamlTransformationsModel.getTransformations()) {
                log.info("Transformation: {}", transformation.getName());

                log.info("Device format: {}", transformation.getIncomingFormat());
                log.info("Server format: {}", transformation.getOutgoingFormat());
                log.info("Server mapping: {}", transformation.getToOutgoing());
                log.info("Device mapping: {}", transformation.getToIncoming());

                String mapped = objectTransformer.transform(fromServer,
                        transformation.getToIncoming(),
                        transformation.getOutgoingFormat(),
                        transformation.getIncomingFormat());

                log.info("Mapped: {}", mapped);
            }
        } catch (Exception e) {
            log.error("Error parsing YAML", e);
        }
    }
}
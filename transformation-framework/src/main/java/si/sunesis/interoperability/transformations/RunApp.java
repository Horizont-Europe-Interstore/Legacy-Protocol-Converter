package si.sunesis.interoperability.transformations;

import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.transformations.connections.Connections;
import si.sunesis.interoperability.transformations.models.YamlTransformationModel;
import si.sunesis.interoperability.transformations.models.YamlTransformationsModel;
import si.sunesis.interoperability.transformations.transformation.ObjectTransformer;
import si.sunesis.interoperability.transformations.transformation.TransformationHandler;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@Slf4j
@ApplicationScoped
public class RunApp {

    private static final String deviceXMLInput = """
            <customevent>
            <datetime>28-08-2023 12:00:35</datetime>
            <status>active</status>
            <start>28-08-2023</start>
            <duration>900</duration>
            </customevent>
            """;

    private static final String deviceJsonInput = """
            {
            "datetime": "28-08-2023 12:00:35",
            "status": "active",
            "start": "28-08-2023",
            "duration": 900
            }
            """;

    private static final String serverXMLInput = """
            <event>
            	<creationtime>1702909917932</creationtime>
            	<eventstatus>
            		<currentstatus> 1 </currentstatus>
            		<datetime> 1693216835000 </datetime>
            		<potentiallysuperseded></potentiallysuperseded>
            	</eventstatus>
            	<interval>
            		<duration> 900 </duration>
            		<start> 1693216835000 </start>
            	</interval>
            </event>
            """;

    private static final String serverJsonInput = """
            {
              "creationTime": 1702909917932,
              "EventStatus": {
                "currentStatus": 1,
                "dateTime": 1693216835000,
                "potentiallySuperseded": null
              },
              "interval": {
                "duration": 900,
                "start": 1693216835000
              }
            }
            """;

    // TODO: add validation of xml and json
    // add ingoing/outgoing connections
    // add modbus

    private final YamlTransformationsModel yamlTransformationsModel;

    private final ObjectTransformer objectTransformer;

    private final Connections connections;

    @Inject
    private RunApp(YamlTransformationsModel yamlTransformationsModel,
                   ObjectTransformer objectTransformer,
                   Connections connections) {
        this.yamlTransformationsModel = yamlTransformationsModel;
        this.objectTransformer = objectTransformer;
        this.connections = connections;
    }

    private void begin(@Observes @Initialized(ApplicationScoped.class) Object init) {
        for (YamlTransformationModel yamlTransformationModel : yamlTransformationsModel.getTransformations()) {
            TransformationHandler handler = new TransformationHandler(yamlTransformationModel, objectTransformer, connections);
            try {
                handler.handleConnections();
            } catch (ModbusNumberException e) {
                log.error("Error handling connections", e);
            }
        }
    }
}

// registracija naprav
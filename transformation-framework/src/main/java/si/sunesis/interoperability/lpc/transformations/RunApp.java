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
package si.sunesis.interoperability.lpc.transformations;

import com.intelligt.modbus.jlibmodbus.exception.ModbusNumberException;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.lpc.transformations.configuration.Configuration;
import si.sunesis.interoperability.lpc.transformations.configuration.models.TransformationModel;
import si.sunesis.interoperability.lpc.transformations.connections.Connections;
import si.sunesis.interoperability.lpc.transformations.transformation.ObjectTransformer;
import si.sunesis.interoperability.lpc.transformations.transformation.TransformationHandler;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
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

    private final Configuration configuration;

    private final ObjectTransformer objectTransformer;

    private final Connections connections;

    @Inject
    private RunApp(Configuration configuration,
                   ObjectTransformer objectTransformer,
                   Connections connections) {
        this.configuration = configuration;
        this.objectTransformer = objectTransformer;
        this.connections = connections;
    }

    private void begin(@Observes @Initialized(ApplicationScoped.class) Object init) {
        for (TransformationModel transformationModel : configuration.getTransformations()) {
            TransformationHandler handler = new TransformationHandler(transformationModel, objectTransformer, connections);
            try {
                handler.handleConnections();
            } catch (ModbusNumberException e) {
                log.error("Error handling connections", e);
            }
        }
    }
}

// registracija naprav
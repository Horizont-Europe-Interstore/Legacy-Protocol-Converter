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

import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.common.exceptions.HandlerException;
import si.sunesis.interoperability.common.interfaces.RequestHandler;
import si.sunesis.interoperability.lpc.transformations.configuration.Configuration;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ConfigurationModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.RegistrationModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.TransformationModel;
import si.sunesis.interoperability.lpc.transformations.connections.Connections;
import si.sunesis.interoperability.lpc.transformations.exceptions.LPCException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;

/**
 * Main handler for managing all transformations in the application.
 * Coordinates the creation, initialization, and lifecycle of individual transformation handlers.
 * Handles configuration changes and system registration messages.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
@ApplicationScoped
public class TransformationsHandler {

    @Inject
    private Configuration configuration;

    @Inject
    private ObjectTransformer objectTransformer;

    private final ArrayList<TransformationHandler> transformationHandlers = new ArrayList<>();

    /**
     * Starts the transformation handling process.
     * Sets up a configuration change consumer and initializes all transformations.
     * This is the main entry point for starting the protocol conversion system.
     *
     * @throws LPCException If there is an error initializing transformations
     */
    public void startHandling() throws LPCException {
        configuration.setConsumer(this::restart);
        handleTransformations();
    }

    /**
     * Creates and initializes all transformation handlers from the current configuration.
     * Publishes registration messages to configured connections.
     * Creates a transformation handler for each transformation model and starts it.
     *
     * @throws LPCException If there is an error initializing connections or handlers
     */
    private void handleTransformations() throws LPCException {
        Connections connections = new Connections(configuration);
        for (ConfigurationModel configurationModel : configuration.getConfigurations()) {
            RegistrationModel registration = configurationModel.getRegistration();

            for (String connectionName : registration.getOutgoingConnections()) {
                RequestHandler<String, ?> requestHandler = connections.getConnectionsMap().get(connectionName);
                if (requestHandler != null) {
                    try {
                        requestHandler.publish(registration.getMessage(), registration.getTopic());
                    } catch (HandlerException e) {
                        log.error("Error publishing registration message.", e);
                    }
                }
            }

            for (TransformationModel transformationModel : configurationModel.getTransformations()) {
                TransformationHandler handler = new TransformationHandler(transformationModel, objectTransformer, connections);
                transformationHandlers.add(handler);
                handler.handle();
            }
        }
    }

    /**
     * Restarts all transformation handlers after a configuration change.
     * Destroys existing handlers, clears the list, and creates new ones based on updated configuration.
     *
     * @param b Flag indicating configuration change (not used but required for consumer interface)
     */
    private void restart(Boolean b) {
        for (TransformationHandler handler : transformationHandlers) {
            handler.destroy();
        }

        transformationHandlers.clear();
        try {
            handleTransformations();
        } catch (LPCException e) {
            log.error("Failed to restart handling transformations", e);
            System.exit(1);
        }
    }
}

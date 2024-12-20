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

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPrivateKeySpec;
import org.bouncycastle.util.io.pem.PemReader;
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
import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;

/**
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

    public void startHandling() throws LPCException {
        configuration.setConsumer(this::restart);
        handleTransformations();
    }

    private void handleTransformations() throws LPCException {
        Connections connections = new Connections(configuration);
        for (ConfigurationModel configurationModel : configuration.getConfigurations()) {
            RegistrationModel registration = configurationModel.getRegistration();

            for (String connectionName : registration.getOutgoingConnections()) {
                RequestHandler requestHandler = connections.getConnectionsMap().get(connectionName);
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

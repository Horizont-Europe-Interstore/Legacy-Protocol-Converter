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

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import si.sunesis.interoperability.lpc.transformations.transformation.TransformationsHandler;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import java.util.HashMap;
import java.util.Map;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.1
 */
@ApplicationPath("/")
public class LegacyProtocolConverterApplication extends ResourceConfig {

    @Inject
    private TransformationsHandler handler;

    public LegacyProtocolConverterApplication() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("jersey.config.server.wadl.disableWadl", "true");
        setProperties(properties);
        packages("si.sunesis.interoperability.lpc.transformations");
        register(MultiPartFeature.class);
    }

    @PostConstruct
    public void init() {
        this.handler.startHandling();
    }
}

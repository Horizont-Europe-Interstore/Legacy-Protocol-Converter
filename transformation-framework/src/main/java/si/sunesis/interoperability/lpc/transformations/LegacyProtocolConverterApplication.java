package si.sunesis.interoperability.lpc.transformations;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashMap;
import java.util.Map;

@ApplicationPath("/")
public class LegacyProtocolConverterApplication extends ResourceConfig {

    public LegacyProtocolConverterApplication() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("jersey.config.server.wadl.disableWadl", "true");
        setProperties(properties);
        packages("si.sunesis.interoperability.lpc.transformations");
        register(MultiPartFeature.class);
    }
}

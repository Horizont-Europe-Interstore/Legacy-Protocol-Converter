package si.sunesis.interoperability.transformations;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@ApplicationScoped
public class RunApp {

    @Inject
    private YAMLParser yamlParser;

    private void begin(@Observes @Initialized(ApplicationScoped.class) Object init) {
        try {
            yamlParser.parse();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


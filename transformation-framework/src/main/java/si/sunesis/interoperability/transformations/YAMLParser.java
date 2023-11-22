package si.sunesis.interoperability.transformations;

import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.transformations.models.YamlModel;
import si.sunesis.interoperability.transformations.models.YamlTransformationModel;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@Slf4j
@ApplicationScoped
public class YAMLParser {

    @Inject
    private YamlModel yamlModel;

    public void parse() {
        try {
            log.info("YAML model: {}", yamlModel);

            for (YamlTransformationModel transformation : yamlModel.getTransformations()) {
                log.info("Transformation: {}", transformation);
                log.info("Ingoing: {}", transformation.getIngoing());
            }
        } catch (Exception e) {
            log.error("Error parsing YAML", e);
        }
    }
}
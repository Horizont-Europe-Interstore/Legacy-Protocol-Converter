package si.sunesis.interoperability.transformations.models;

import com.kumuluz.ee.configuration.cdi.ConfigBundle;
import com.kumuluz.ee.configuration.cdi.ConfigValue;
import lombok.Data;

import javax.enterprise.context.ApplicationScoped;

@Data
@ConfigBundle("lpc")
@ApplicationScoped
public class YamlTransformationsModel {

    @ConfigValue("transformations")
    private YamlTransformationModel[] transformations;

    @ConfigValue("connections")
    private YamlConnectionModel[] connections;
}

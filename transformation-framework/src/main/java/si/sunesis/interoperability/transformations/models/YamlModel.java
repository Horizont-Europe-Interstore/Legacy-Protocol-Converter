package si.sunesis.interoperability.transformations.models;

import com.kumuluz.ee.configuration.cdi.ConfigBundle;
import com.kumuluz.ee.configuration.cdi.ConfigValue;
import lombok.Data;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;

@Data
@ConfigBundle("lpc")
@ApplicationScoped
public class YamlModel {

    @ConfigValue("transformations")
    private YamlTransformationModel[] transformations;
}

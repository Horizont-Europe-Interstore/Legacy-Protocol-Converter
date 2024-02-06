package si.sunesis.interoperability.transformations.models;

import com.kumuluz.ee.configuration.cdi.ConfigValue;
import lombok.Data;

@Data
public class YamlSslModel {

    @ConfigValue("trust-store")
    private YamlSslStoreModel trustStore;

    @ConfigValue("key-store")
    private YamlSslStoreModel keyStore;

    @ConfigValue("default")
    private Boolean useDefault = false;
}

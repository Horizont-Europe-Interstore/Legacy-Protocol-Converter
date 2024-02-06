package si.sunesis.interoperability.transformations.models;

import com.kumuluz.ee.configuration.cdi.ConfigValue;
import lombok.Data;

@Data
public class YamlSslStoreModel {

    @ConfigValue("type")
    private String type = "JKS";

    @ConfigValue("path")
    private String path;

    @ConfigValue("store-password")
    private String storePassword = "";

    @ConfigValue("key-password")
    private String keyPassword = "";
}

package si.sunesis.interoperability.transformations.models;

import com.kumuluz.ee.configuration.cdi.ConfigValue;
import lombok.Data;

@Data
public class YamlModbusModel {

    @ConfigValue("address")
    private Integer address;

    @ConfigValue("path")
    private String path;

    @ConfigValue("type")
    private String type;

    @ConfigValue("pattern")
    private String pattern;

    @ConfigValue
    private String[] values;
}

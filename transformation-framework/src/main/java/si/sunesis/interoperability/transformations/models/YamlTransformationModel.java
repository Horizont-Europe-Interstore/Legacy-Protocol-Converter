package si.sunesis.interoperability.transformations.models;

import com.kumuluz.ee.configuration.cdi.ConfigValue;
import lombok.Data;

@Data
public class YamlTransformationModel {

    @ConfigValue("name")
    private String name;

    @ConfigValue("description")
    private String description;

    @ConfigValue("deviceMqttTopic")
    private String deviceMqttTopic;

    @ConfigValue("natsSubject")
    private String natsSubject;

    @ConfigValue("fromDeviceFormat")
    private String fromDeviceFormat;

    @ConfigValue("forwardFormat")
    private String forwardFormat;

    @ConfigValue("outgoing")
    private String outgoing;

    @ConfigValue("ingoing")
    private String ingoing;
}

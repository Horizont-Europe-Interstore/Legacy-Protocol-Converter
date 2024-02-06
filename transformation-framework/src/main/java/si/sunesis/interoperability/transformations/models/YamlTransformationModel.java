package si.sunesis.interoperability.transformations.models;

import com.kumuluz.ee.configuration.cdi.ConfigValue;
import lombok.Data;

@Data
public class YamlTransformationModel {

    @ConfigValue("name")
    private String name;

    @ConfigValue("description")
    private String description;

    @ConfigValue("connections.incoming-topic")
    private String incomingTopic;

    @ConfigValue("connections.outgoing-topic")
    private String outgoingTopic;

    @ConfigValue("modbus-device-id")
    private Integer modbusDeviceID;

    @ConfigValue("modbus-function-code")
    private Integer modbusFunctionCode;

    @ConfigValue("connections.incoming-format")
    private String incomingFormat;

    @ConfigValue("connections.outgoing-format")
    private String outgoingFormat;

    @ConfigValue("to-incoming")
    private String toIncoming;

    @ConfigValue("to-incoming.modbus-registers")
    private YamlModbusModel[] toModbus;

    @ConfigValue("to-outgoing")
    private String toOutgoing;

    @ConfigValue("validateSchema")
    private Boolean validateSchema = false;

    @ConfigValue("connections.incoming-connection")
    private String[] incomingConnections;

    @ConfigValue("connections.outgoing-connection")
    private String[] outgoingConnections;
}

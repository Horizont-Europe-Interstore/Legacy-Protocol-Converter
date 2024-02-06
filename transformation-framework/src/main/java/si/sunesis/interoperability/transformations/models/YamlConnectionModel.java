package si.sunesis.interoperability.transformations.models;

import com.kumuluz.ee.configuration.cdi.ConfigValue;
import lombok.Data;

@Data
public class YamlConnectionModel {

    // Common
    @ConfigValue(value = "name")
    private String name;

    @ConfigValue("type")
    private String type;

    @ConfigValue("host")
    private String host;

    @ConfigValue("port")
    private Integer port;

    @ConfigValue("username")
    private String username;

    @ConfigValue("password")
    private String password;

    @ConfigValue("ssl")
    private YamlSslModel ssl;

    @ConfigValue("reconnect")
    private Boolean reconnect = false;

    // MQTT
    @ConfigValue("version")
    private Integer version = 5;

    // Modbus
    @ConfigValue("device")
    private String device;

    @ConfigValue("baud-rate")
    private Integer baudRate;

    @ConfigValue("data-bits")
    private Integer dataBits;

    @ConfigValue("parity")
    private String parity;

    @ConfigValue("stop-bits")
    private Integer stopBits;
}

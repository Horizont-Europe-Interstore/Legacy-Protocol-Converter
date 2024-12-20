package si.sunesis.interoperability.lpc.transformations.certificates;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import si.sunesis.interoperability.common.exceptions.HandlerException;
import si.sunesis.interoperability.common.interfaces.RequestHandler;
import si.sunesis.interoperability.lpc.certificate.management.CertificateManagement;

import java.io.IOException;

@Slf4j
public class CertificateRegistration {

    public static String registerGateway(String deviceCertPath, RequestHandler requestHandler) throws IOException, HandlerException {
        X509CertificateHolder certificateHolder = CertificateManagement.getCertificateHolder(deviceCertPath);

        // Extract information
        String commonName = CertificateManagement.getRDNValue(certificateHolder.getSubject(), BCStyle.CN);
        String organizationalUnit = CertificateManagement.getRDNValue(certificateHolder.getSubject(), BCStyle.OU);

        String gwRegistration = """
                {
                    "version": "1.0",
                    "command": "register-gateway",
                    "gateway_id": "%s",
                    "parameters": {
                        "device_type": "iot_light_gateway",
                        "management_mode": "jobs",
                        "maker": "Sunesis",
                        "model": "anyModel",
                        "serial_number": "SERIAL1",
                        "environment_prefix": "%s",
                        "geolocalization": "40.4518799,-3.6868177"
                    }
                }
                """;

        String gwRegistrationMessage = String.format(gwRegistration, commonName, organizationalUnit);

        requestHandler.publish(gwRegistrationMessage, "devices/registration");

        log.info("Gateway registration message published");

        return String.format("%s_%s_thing1", organizationalUnit, commonName);
    }

    public static void registerThing(String deviceCertPath, RequestHandler requestHandler) throws IOException, HandlerException {
        X509CertificateHolder certificateHolder = CertificateManagement.getCertificateHolder(deviceCertPath);

        // Extract information
        String commonName = CertificateManagement.getRDNValue(certificateHolder.getSubject(), BCStyle.CN);
        String organizationalUnit = CertificateManagement.getRDNValue(certificateHolder.getSubject(), BCStyle.OU);

        String thingId = commonName + "_thing1";

        String thingRegistration = """
                {
                  "version": "1.0",
                  "command": "register-thing",
                  "gateway_id": "%s",
                  "parameters": {
                    "thing_id": "%s",
                    "device_type": "iot_light_gateway",
                    "radio_type": "eth",
                    "model": "emulate_modbus",
                    "maker": "Fibaro",
                    "serial_number": "12345",
                    "authentication_mode": "gateway",
                    "environment_prefix": "%s",
                    "interaction_mode": "gateway",
                    "filter_tag": [
                      {
                        "id": 102000,
                        "period": 10,
                        "tag": "Pulse1"
                      }
                    ],
                    "outbound_communication_modes": [
                      "topic"
                    ],
                    "inbound_communication_mode": "topic"
                  }
                }
                """;

        String thingRegistrationMessage = String.format(thingRegistration, commonName, thingId, organizationalUnit);

        log.info("Thing registration message published");

        requestHandler.publish(thingRegistrationMessage, "devices/registration");
    }
}

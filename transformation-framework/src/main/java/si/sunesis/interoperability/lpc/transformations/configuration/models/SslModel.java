/*
 *  Copyright (c) 2023-2024 Sunesis and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package si.sunesis.interoperability.lpc.transformations.configuration.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Configuration model for SSL/TLS settings used in secure connections.
 * Contains paths to certificates, passwords, and configuration options
 * needed to establish secure connections with external systems.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
public class SslModel {

    /**
     * Path to the CA certificate file for SSL verification
     */
    @JsonProperty("ca-cert-path")
    private String caCertPath;

    /**
     * Path to the client certificate file for SSL authentication
     */
    @JsonProperty("client-cert-path")
    private String clientCertPath;

    /**
     * Password for the client certificate
     */
    @JsonProperty("client-cert-password")
    private String clientCertPassword;

    /**
     * Flag to use default system SSL configuration instead of custom certificates
     */
    @JsonProperty("default")
    private Boolean useDefault = false;
}

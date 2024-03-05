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
package si.sunesis.interoperability.lpc.transformations.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ConnectionModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.TransformationModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.TransformationsModel;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
@Getter
@ApplicationScoped
public class Configuration {

    private final List<ConnectionModel> connections = new ArrayList<>();

    private final List<TransformationModel> transformations = new ArrayList<>();

    @PostConstruct
    private void init() {
        try {
            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            objectMapper.findAndRegisterModules();

            String configuration = System.getenv("CONFIGURATION");

            if (configuration == null) {
                if (System.getProperty("CONFIGURATION") != null) {
                    configuration = System.getProperty("CONFIGURATION");
                } else {
                    configuration = "conf";
                }
            }

            File dir = new File(configuration);

            if (!dir.exists() || !dir.isDirectory()) {
                throw new RuntimeException("Configuration directory does not exist");
            }

            for (File file : Objects.requireNonNull(dir.listFiles())) {
                if (file.getName().endsWith(".yaml")) {
                    String fileContent = readFromInputStream(new FileInputStream(file));
                    TransformationsModel transformationsModel = objectMapper.readValue(
                            fileContent,
                            TransformationsModel.class);
                    this.connections.addAll(transformationsModel.getConnections());
                    this.transformations.addAll(transformationsModel.getTransformations());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readFromInputStream(InputStream inputStream)
            throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        try (BufferedReader br
                     = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }

        return resultStringBuilder.toString();
    }
}

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import si.sunesis.interoperability.common.exceptions.HandlerException;
import si.sunesis.interoperability.lpc.transformations.configuration.models.ConfigurationModel;
import si.sunesis.interoperability.lpc.transformations.configuration.models.TransformationModel;
import si.sunesis.interoperability.lpc.transformations.constants.Constants;
import si.sunesis.interoperability.lpc.transformations.exceptions.LPCException;
import si.sunesis.interoperability.lpc.transformations.transformation.ObjectTransformer;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Slf4j
@Getter
@ApplicationScoped
public class Configuration {

    private final List<ConfigurationModel> configurations = new ArrayList<>();

    private final HashMap<String, Long> lastModified = new HashMap<>();

    private final ObjectTransformer objectTransformer = new ObjectTransformer();

    @Setter
    private Consumer<Boolean> consumer;

    @PostConstruct
    private void init() {
        try {
            readConf();
        } catch (LPCException e) {
            log.error("Error reading configuration", e);
            System.exit(1);
        }

        scheduleRead();
    }

    public void readConf() throws LPCException {
        try {
            configurations.clear();

            ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
            objectMapper.findAndRegisterModules();

            File[] files = readFiles();

            for (File file : Objects.requireNonNull(files)) {
                String name = file.getName();
                if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                    log.info("Reading configuration: {}", name);

                    lastModified.put(name, file.lastModified());

                    String fileContent = readFromInputStream(new FileInputStream(file));
                    ConfigurationModel configurationModel = objectMapper.readValue(
                            fileContent,
                            ConfigurationModel.class);

                    validateTransformations(configurationModel);

                    this.configurations.add(configurationModel);
                }
            }
        } catch (IOException e) {
            log.error("Error reading configuration", e);
            System.exit(1);
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

    private File[] readFiles() throws LPCException {
        String configuration = getConfFolderName();

        File dir = new File(configuration);

        if (!dir.exists() || !dir.isDirectory()) {
            throw new LPCException("Configuration directory does not exist");
        }

        return dir.listFiles();
    }

    private String getConfFolderName() {
        String configuration = System.getenv(Constants.CONFIGURATION_FOLDER);

        if (configuration == null) {
            if (System.getProperty(Constants.CONFIGURATION_FOLDER) != null) {
                configuration = System.getProperty(Constants.CONFIGURATION_FOLDER);
            } else {
                configuration = "conf";
            }
        }

        return configuration;
    }

    private void scheduleRead() {
        String configuration = getConfFolderName();
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);
        executorService.scheduleAtFixedRate(() -> {
            log.info("Checking for changes in the configuration folder {}", configuration);
            try {
                File[] files = readFiles();

                boolean newConf = false;

                for (File file : Objects.requireNonNull(files)) {
                    String name = file.getName();
                    log.debug("Checking file: {}", name);
                    if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                        if (lastModified.containsKey(name)) {
                            if (lastModified.get(name) != file.lastModified()) {
                                newConf = true;
                                log.debug("Configuration file changed: {}", name);
                            }
                        } else {
                            newConf = true;
                            log.debug("New configuration file: {}", name);
                        }

                        lastModified.put(name, file.lastModified());
                        if (newConf) {
                            break;
                        }
                    }
                }

                if (files.length != lastModified.size()) {
                    newConf = true;

                    log.debug("Checking for any configuration that were removed");
                    // Delete keys from lastModified that are not in files array
                    for (String key : new ArrayList<>(lastModified.keySet())) {
                        boolean found = false;
                        for (File file : files) {
                            if (file.getName().equals(key)) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            log.debug("Configuration file removed: {}", key);
                            lastModified.remove(key);
                        }
                    }
                }

                if (newConf) {
                    log.debug("New configuration detected. Reloading configurations...");
                    readConf();
                    consumer.accept(true);
                }
            } catch (Exception e) {
                log.error("Error reading configuration", e);
                System.exit(1);
            }
        }, 60, 30, TimeUnit.SECONDS);
    }

    private void validateTransformations(ConfigurationModel configurationModel) {
        for (TransformationModel transformationModel : configurationModel.getTransformations()) {
            try {
                log.info("Validating messages for transformation: {}", transformationModel.getName());

                if (transformationModel.getToOutgoing() != null && transformationModel.getToOutgoing().getMessage() != null) {
                    objectTransformer.mockTransform(transformationModel.getToOutgoing().getMessage(), transformationModel.getValidateIEEE2030dot5());
                }

                if (transformationModel.getToIncoming() != null && transformationModel.getToIncoming().getMessage() != null) {
                    objectTransformer.mockTransform(transformationModel.getToIncoming().getMessage(), transformationModel.getValidateIEEE2030dot5());
                }
            } catch (JsonProcessingException ex) {
                log.error("Error validating transformation: {}", transformationModel.getName(), ex);
                log.error("Error processing JSON: {}", ex.getOriginalMessage());
                log.error("Json is not valid. At column: {} and line: {}", ex.getLocation().getColumnNr(), ex.getLocation().getLineNr());
                System.exit(1);
            } catch (HandlerException ex) {
                log.error("Error validating transformation: {}", transformationModel.getName(), ex);
                System.exit(1);
            } catch (Exception e) {
                log.error("Error validating transformation: {}.", transformationModel.getName(), e);
                System.exit(1);
            }
        }
    }
}

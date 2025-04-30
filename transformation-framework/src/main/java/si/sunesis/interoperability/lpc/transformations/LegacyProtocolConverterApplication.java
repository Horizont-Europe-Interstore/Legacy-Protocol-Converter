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
package si.sunesis.interoperability.lpc.transformations;

import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import si.sunesis.interoperability.lpc.transformations.exceptions.LPCException;
import si.sunesis.interoperability.lpc.transformations.transformation.TransformationsHandler;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Main application class for the Legacy Protocol Converter.
 * Initializes REST API endpoints, manages Python process for Modbus operations,
 * and starts the transformation handling service.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.1
 */
@Slf4j
@ApplicationPath("/")
public class LegacyProtocolConverterApplication extends ResourceConfig {

    private Process pythonProcess = null;

    @Inject
    private TransformationsHandler handler;

    /**
     * Constructor that initializes the REST API.
     * Configures Jersey settings, sets up resource packages, and registers features.
     */
    public LegacyProtocolConverterApplication() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jersey.config.server.wadl.disableWadl", "true");
        setProperties(properties);
        packages("si.sunesis.interoperability.lpc.transformations");
        register(MultiPartFeature.class);
    }

    /**
     * Initialization method that runs after dependency injection is complete.
     * Starts the Python Modbus service and initializes transformation handling.
     */
    @PostConstruct
    public void init() {
        pythonHandler();

        try {
            this.handler.startHandling();
        } catch (LPCException e) {
            log.error("Failed to start handling transformations", e);
            System.exit(1);
        }
    }

    /**
     * Manages the Python process used for Modbus operations.
     * Launches a Python script in a separate process, monitors its output,
     * and automatically restarts it if it fails.
     */
    private void pythonHandler() {
        Thread thread = new Thread(() -> {
            String port = System.getenv("PYTHON_PORT") != null ? System.getenv("PYTHON_PORT") : "9093";
            String[] cmd = {"python3", "pymodbus_script.py", "--api", "--api_port", port};

            ProcessBuilder processBuilder = new ProcessBuilder(cmd);

            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        log.info("Starting Python API server...");
                        pythonProcess = processBuilder.start();

                        StreamGobbler outputGobbler = new StreamGobbler(pythonProcess.getInputStream(),
                                s -> log.debug("Python API InputStream output: {}", s));
                        StreamGobbler errorGobbler = new StreamGobbler(pythonProcess.getErrorStream(),
                                s -> log.debug("Python API ErrorStream output: {}", s));

                        Executors.newSingleThreadExecutor().submit(outputGobbler);
                        Executors.newSingleThreadExecutor().submit(errorGobbler);

                        // Wait for the process to exit
                        int exitCode = pythonProcess.waitFor();
                        log.error("Python API process exited with code: {}", exitCode);

                        // Restart only if the process failed
                        Thread.sleep(5000); // Wait before restart
                    } catch (InterruptedException e) {
                        log.error("Python API process interrupted: ", e);
                        Thread.currentThread().interrupt(); // Restore the interrupt flag
                    } catch (Exception e) {
                        log.error("Error starting Python API: ", e);
                    }
                }
            } finally {
                // Clean shutdown
                if (pythonProcess != null && pythonProcess.isAlive()) {
                    pythonProcess.destroy();
                }
                log.info("Python API supervisor thread exited.");
            }
        });

        thread.setDaemon(false); // Ensure it runs as a non-daemon thread
        thread.start();

        // Register JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Stopping Python supervisor thread...");
            thread.interrupt();
            try {
                thread.join(); // Optional: wait for it to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    /**
     * Utility class for consuming output streams from subprocess.
     * Captures and processes output from Python process streams.
     */
    private record StreamGobbler(InputStream inputStream, Consumer<String> consumer) implements Runnable {
        /**
         * Reads input streamline by line and processes each line with the consumer.
         */
        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }
}

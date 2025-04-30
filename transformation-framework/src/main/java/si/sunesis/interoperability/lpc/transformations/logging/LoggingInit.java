package si.sunesis.interoperability.lpc.transformations.logging;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import si.sunesis.interoperability.lpc.transformations.configuration.models.LoggingModel;

import java.nio.file.Paths;

@Slf4j
public class LoggingInit {

    private static final String LOGS_DIR = "logs";

    private static volatile boolean initialized = false;

    private LoggingInit() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Initializes the logging system based on the provided configuration model.
     * This method is synchronized to ensure thread-safety during initialization.
     * Initializes logging only once, using a static flag to prevent duplicate initialization.
     *
     * @param loggingModel The logging configuration model containing appenders, loggers, and logging settings
     */
    public static synchronized void init(LoggingModel loggingModel) {
        if (!initialized) {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);

            Configuration configuration = createConfiguration(loggingModel);
            context.setConfiguration(configuration);

            initialized = true;
            log.info("Logging initialized application-wide");
        }
    }

    /**
     * Creates a Log4j2 configuration object based on the provided logging model.
     * Sets up the logger hierarchy, including the root logger and named loggers.
     * Each logger is configured with appropriate appenders and log levels.
     *
     * @param loggingModel The model containing logging configuration parameters
     * @return A fully configured Log4j2 Configuration object
     */
    private static Configuration createConfiguration(LoggingModel loggingModel) {
        final ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.toLevel(loggingModel.getStatus(), Level.DEBUG));
        builder.setConfigurationName(loggingModel.getConfigurationName());

        if (loggingModel.getAppenders() != null) {
            // Create appenders dynamically based on the LoggingModel
            for (LoggingModel.Appender appender : loggingModel.getAppenders()) {
                builder.add(createAppender(builder, appender));
            }
        }

        // Configure root logger
        LoggingModel.RootLogger rootLoggerConfig = loggingModel.getRootLogger();
        if (rootLoggerConfig != null) {
            RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.toLevel(rootLoggerConfig.getLevel(), Level.DEBUG));
            for (LoggingModel.AppenderRef appenderRef : rootLoggerConfig.getAppenderRefs()) {
                rootLogger.add(builder.newAppenderRef(appenderRef.getRef()).addAttribute("level", appenderRef.getLevel()));
            }
            builder.add(rootLogger);
        }

        if (loggingModel.getLoggers() != null) {
            // Configure named loggers
            for (LoggingModel.LoggerConfig loggerConfig : loggingModel.getLoggers()) {
                LoggerComponentBuilder logger = builder.newLogger(loggerConfig.getName(), Level.toLevel(loggerConfig.getLevel(), Level.DEBUG));
                for (LoggingModel.AppenderRef appenderRef : loggerConfig.getAppenderRefs()) {
                    logger.add(builder.newAppenderRef(appenderRef.getRef()).addAttribute("level", appenderRef.getLevel()));
                }
                logger.addAttribute("additivity", true);
                builder.add(logger);
            }
        }

        return builder.build();
    }

    private static AppenderComponentBuilder createAppender(ConfigurationBuilder<BuiltConfiguration> builder, LoggingModel.Appender appender) {
        if ("Console".equalsIgnoreCase(appender.getType())) {
            return builder.newAppender(appender.getName(), "CONSOLE")
                    .addAttribute("target", appender.getTarget())
                    .add(builder.newLayout("PatternLayout")
                            .addAttribute("pattern", appender.getPattern()));
        } else if ("RollingFile".equalsIgnoreCase(appender.getType())) {
            return createRollingFileAppender(builder, appender);
        }
        return null;
    }

    private static AppenderComponentBuilder createRollingFileAppender(ConfigurationBuilder<BuiltConfiguration> builder, LoggingModel.Appender appender) {
        AppenderComponentBuilder componentBuilder = builder.newAppender(appender.getName(), "RollingFile")
                .addAttribute("fileName", Paths.get(LOGS_DIR, appender.getFileName()).toString())
                .addAttribute("filePattern", Paths.get(LOGS_DIR, appender.getFilePattern()).toString())
                .add(builder.newLayout("PatternLayout").addAttribute("pattern", appender.getPattern()));

        if (appender.getPolicies() != null && !appender.getPolicies().isEmpty()) {
            ComponentBuilder<?> policies = builder.newComponent("Policies");
            for (LoggingModel.Policies policy : appender.getPolicies()) {
                if (policy.getType() == TriggeringPolicy.TIME_BASED) {
                    policies.addComponent(builder.newComponent(policy.getType().getPolicy())
                            .addAttribute("interval", policy.getInterval())
                            .addAttribute("modulate", policy.isModulate()));
                } else if (policy.getType() == TriggeringPolicy.CRON_BASED) {
                    policies.addComponent(builder.newComponent(policy.getType().getPolicy())
                            .addAttribute("cron", policy.getCron()));
                } else if (policy.getType() == TriggeringPolicy.SIZE_BASED) {
                    policies.addComponent(builder.newComponent(policy.getType().getPolicy())
                            .addAttribute("size", policy.getSize()));
                } else if (policy.getType() == TriggeringPolicy.ON_START) {
                    policies.addComponent(builder.newComponent(policy.getType().getPolicy())
                            .addAttribute("minSize", policy.getMinSize()));
                }
            }

            componentBuilder.addComponent(policies);
        }

        if (appender.getRolloverStrategy() != null &&
                (appender.getRolloverStrategy().getDeleteAge() != null ||
                        appender.getRolloverStrategy().getDeleteFilePattern() != null)) {
            ComponentBuilder<?> component = builder.newComponent(appender.getRolloverStrategy().getType())
                    .addAttribute("basePath", LOGS_DIR)
                    .addAttribute("maxDepth", 1);

            if (appender.getRolloverStrategy().getDeleteFilePattern() != null) {
                component.addComponent(builder.newComponent("IfFileName")
                        .addAttribute("glob", appender.getRolloverStrategy().getDeleteFilePattern()));
            }

            if (appender.getRolloverStrategy().getDeleteAge() != null) {
                component.addComponent(builder.newComponent("IfLastModified")
                        .addAttribute("age", appender.getRolloverStrategy().getDeleteAge()));
            }

            ComponentBuilder<?> rolloverStrategy = builder.newComponent("DefaultRolloverStrategy")
                    .addComponent(component);
            componentBuilder.addComponent(rolloverStrategy);
        }

        return componentBuilder;
    }
}
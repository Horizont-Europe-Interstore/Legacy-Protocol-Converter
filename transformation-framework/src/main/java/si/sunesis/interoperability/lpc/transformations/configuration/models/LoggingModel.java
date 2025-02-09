package si.sunesis.interoperability.lpc.transformations.configuration.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import si.sunesis.interoperability.lpc.transformations.logging.TriggeringPolicy;

import java.util.List;

@Data
public class LoggingModel {

    private static final String LOG_PATTERN = "%d %p -- %c -- %marker %m %X %ex %n";

    @JsonProperty("configuration-name")
    private String configurationName = "lpc";
    private String status = "info";

    private List<Appender> appenders = List.of(new Appender(), Appender.getConsoleAppender());

    private List<LoggerConfig> loggers = List.of(new LoggerConfig());

    @JsonProperty("root-logger")
    private RootLogger rootLogger = new RootLogger();

    @Data
    public static class Appender {
        private String name = "file_lpc";
        private String type = "RollingFile";
        private String target;
        private String fileName = "lpc.log";
        private String filePattern = "lpc-%d{yyyy-MM-dd}.log";
        private String pattern = LOG_PATTERN;
        private List<Policies> policies = List.of(new Policies());

        @JsonProperty("default-rollover-strategy")
        private RolloverStrategy rolloverStrategy = new RolloverStrategy();

        public static Appender getConsoleAppender() {
            Appender appender = new Appender();
            appender.setName("console");
            appender.setType("Console");
            appender.setTarget("SYSTEM_OUT");
            return appender;
        }
    }

    @Data
    public static class RolloverStrategy {
        private String type = "Delete";
        private String deleteFilePattern = "lpc-*.log";
        private String deleteAge = "1d";
    }

    @Data
    public static class Policies {
        private TriggeringPolicy type = TriggeringPolicy.TIME_BASED;

        @JsonSetter("type")
        public void setType(String type) {
            if (type == null) {
                this.type = TriggeringPolicy.TIME_BASED;
                return;
            }
            this.type = TriggeringPolicy.fromString(type);
        }

        private String cron;
        private String size;
        private String minSize;

        private int interval = 1;

        private boolean modulate = true;
    }

    @Data
    public static class LoggerConfig {
        private String name = "si.sunesis.interoperability.lpc";
        private String level = System.getenv("LOG_LEVEL") == null ? "debug" : System.getenv("LOG_LEVEL");

        @JsonProperty("appender-refs")
        private List<AppenderRef> appenderRefs = List.of(new AppenderRef());
    }

    @Data
    public static class RootLogger {
        private String level = System.getenv("ROOT_LOG_LEVEL") == null ? "info" : System.getenv("ROOT_LOG_LEVEL");

        @JsonProperty("appender-refs")
        private List<AppenderRef> appenderRefs = List.of(new AppenderRef("console", level));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AppenderRef {
        private String ref = "file_lpc";
        private String level = System.getenv("LOG_LEVEL") == null ? "debug" : System.getenv("LOG_LEVEL");
    }
}

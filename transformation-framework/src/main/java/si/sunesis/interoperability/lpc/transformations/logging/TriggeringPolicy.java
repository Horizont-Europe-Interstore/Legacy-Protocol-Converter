package si.sunesis.interoperability.lpc.transformations.logging;

import lombok.Getter;

public enum TriggeringPolicy {
    SIZE_BASED("SizeBasedTriggeringPolicy"),

    TIME_BASED("TimeBasedTriggeringPolicy"),

    CRON_BASED("CronBasedTriggeringPolicy"),

    ON_START("OnStartupTriggeringPolicy");

    @Getter
    private final String policy;

    TriggeringPolicy(String policy) {
        this.policy = policy;
    }

    public static TriggeringPolicy fromString(String policy) {
        for (TriggeringPolicy triggeringPolicy : TriggeringPolicy.values()) {
            if (triggeringPolicy.getPolicy().equalsIgnoreCase(policy)) {
                return triggeringPolicy;
            }
        }
        throw new IllegalArgumentException("Unknown triggering policy: " + policy);
    }
}

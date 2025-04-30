package si.sunesis.interoperability.lpc.transformations.logging;

import lombok.Getter;

@Getter
public enum TriggeringPolicy {
    SIZE_BASED("SizeBasedTriggeringPolicy"),

    TIME_BASED("TimeBasedTriggeringPolicy"),

    CRON_BASED("CronBasedTriggeringPolicy"),

    ON_START("OnStartupTriggeringPolicy");

    private final String policy;

    TriggeringPolicy(String policy) {
        this.policy = policy;
    }

    /**
     * Converts a string representation of a policy to its corresponding TriggeringPolicy enum value.
     * The comparison is case-insensitive.
     *
     * @param policy The string representation of the triggering policy
     * @return The matching TriggeringPolicy enum value
     * @throws IllegalArgumentException If no matching policy is found
     */
    public static TriggeringPolicy fromString(String policy) {
        for (TriggeringPolicy triggeringPolicy : TriggeringPolicy.values()) {
            if (triggeringPolicy.getPolicy().equalsIgnoreCase(policy)) {
                return triggeringPolicy;
            }
        }
        throw new IllegalArgumentException("Unknown triggering policy: " + policy);
    }
}

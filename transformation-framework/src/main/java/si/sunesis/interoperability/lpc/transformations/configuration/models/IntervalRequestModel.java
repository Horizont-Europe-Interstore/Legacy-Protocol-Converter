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
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

/**
 * Defines an interval-based request configuration for polling data periodically.
 * Contains the interval period and the message model that defines the request.
 * Used for scenarios where data needs to be fetched at regular intervals.
 *
 * @author David Trafela, Sunesis
 * @since 1.0.0
 */
@Data
public class IntervalRequestModel {

    /**
     * Time interval in seconds between periodic requests
     */
    private Integer interval;

    /**
     * Cron expression for scheduling the start of the periodic request.
     * If set, it triggers interval requests based on the cron expression. Further requests will be made based on the interval.
     */
    private String cron;

    @JsonSetter("cron")
    public void setCron(String cronExpression) {
        if (cronExpression != null && !cronExpression.isEmpty()) {
            if (cronExpression.equalsIgnoreCase("05") || cronExpression.equalsIgnoreCase("5")) {
                this.cron = "*/5 * * * *"; // Every 5 minutes
            } else if (cronExpression.equalsIgnoreCase("10")) {
                this.cron = "*/10 * * * *";
            } else if (cronExpression.equalsIgnoreCase("15")) {
                this.cron = "*/15 * * * *"; // Every 15 minutes
            } else if (cronExpression.equalsIgnoreCase("20")) {
                this.cron = "*/20 * * * *"; // Every 20 minutes
            } else if (cronExpression.equalsIgnoreCase("30")) {
                this.cron = "*/30 * * * *"; // Every 30 minutes
            } else {
                this.cron = "* * * * *"; // Every minute by default
            }
        }
    }

    /**
     * NTP server address for time synchronization
     * Used to ensure consistent timestamps
     */
    @JsonProperty("ntp-server")
    private String ntpServer = null;

    /**
     * Message configuration for the periodic request
     */
    private MessageModel request;
}

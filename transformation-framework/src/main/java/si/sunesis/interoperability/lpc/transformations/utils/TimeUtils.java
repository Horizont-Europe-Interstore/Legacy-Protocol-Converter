package si.sunesis.interoperability.lpc.transformations.utils;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.cronutils.model.CronType.UNIX;

public class TimeUtils {

    public static long calculateDelay(String cronExpression, String ntpServer) throws Exception {
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(UNIX));
        Cron cron = parser.parse(cronExpression);
        cron.validate();

        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        ZonedDateTime now = getCurrentNtpTime(ntpServer);

        return executionTime.nextExecution(now)
                .map(next -> Duration.between(now, next).toMillis())
                .orElseThrow(() -> new RuntimeException("Could not calculate next execution time"));
    }

    public static ZonedDateTime getCurrentNtpTime(String ntpServer) throws Exception {
        if (ntpServer == null) {
            return ZonedDateTime.now();
        }

        NTPUDPClient client = new NTPUDPClient();
        client.setDefaultTimeout(3000);
        InetAddress hostAddr = InetAddress.getByName(ntpServer);
        TimeInfo info = client.getTime(hostAddr);
        info.computeDetails();
        long now = info.getMessage().getTransmitTimeStamp().getTime();
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault());
    }
}

package org.tanzu.timemcp.time;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TimeService {

    private static final String[] NTP_SERVERS = {
        "pool.ntp.org",
        "time.nist.gov",
        "0.pool.ntp.org",
        "1.pool.ntp.org"
    };

    @Tool(description = "Get the current time from authoritative NTP servers")
    public static String getCurrentDateTime() {
        return getCurrentDateTimeWithTimezone(ZoneId.systemDefault().getId());
    }

    @Tool(description = "Get the current time for a specific timezone from authoritative NTP servers")
    public static String getCurrentDateTimeWithTimezone(String timezoneId) {
        try {
            ZoneId zone = ZoneId.of(timezoneId);
            ZonedDateTime ntpTime = getNTPTime(zone);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            return ntpTime.format(formatter);
        } catch (Exception e) {
            // Fallback to system time if NTP fails
            ZonedDateTime fallbackTime = ZonedDateTime.now(ZoneId.of(timezoneId));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            return fallbackTime.format(formatter) + " (fallback - NTP unavailable: " + e.getMessage() + ")";
        }
    }

    private static ZonedDateTime getNTPTime(ZoneId zone) throws Exception {
        NTPUDPClient timeClient = new NTPUDPClient();
        timeClient.setDefaultTimeout(5000); // 5 second timeout
        
        Exception lastException = null;
        
        // Try multiple NTP servers for reliability
        for (String ntpServer : NTP_SERVERS) {
            try {
                timeClient.open();
                InetAddress hostAddr = InetAddress.getByName(ntpServer);
                TimeInfo timeInfo = timeClient.getTime(hostAddr);
                timeClient.close();
                
                // Get the NTP time and convert to ZonedDateTime
                long ntpTime = timeInfo.getMessage().getTransmitTimeStamp().getTime();
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ntpTime), zone);
                
            } catch (Exception e) {
                lastException = e;
                try {
                    timeClient.close();
                } catch (Exception ignored) {
                    // Ignore close errors
                }
                // Continue to next server
            }
        }
        
        // If all servers failed, throw the last exception
        throw new RuntimeException("All NTP servers failed", lastException);
    }
}

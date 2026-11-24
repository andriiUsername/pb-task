package privatbank.task.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    private static final Duration DATABASE_TIMESTAMP_PRECISION = Duration.ofNanos(1_000);

    @Bean
    public Clock clock(@Value("${app.time-zone}") ZoneId zone) {
        return Clock.tick(Clock.system(zone), DATABASE_TIMESTAMP_PRECISION);
    }
}

package co.com.compira.config;

import co.com.compira.model.task.gateways.TaskClockGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;

@Configuration
public class TaskClockConfig {
    @Bean
    public TaskClockGateway taskClockGateway() {
        return OffsetDateTime::now;
    }
}

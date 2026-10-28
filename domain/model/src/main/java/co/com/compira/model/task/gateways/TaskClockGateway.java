package co.com.compira.model.task.gateways;

import java.time.OffsetDateTime;

public interface TaskClockGateway {
    OffsetDateTime now();
}

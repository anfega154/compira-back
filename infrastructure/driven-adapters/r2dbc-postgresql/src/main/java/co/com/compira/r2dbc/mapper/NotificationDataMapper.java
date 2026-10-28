package co.com.compira.r2dbc.mapper;

import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.TaskNotification;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationDataMapper {
    public TaskNotification toNotification(Map<String, Object> row) {
        return new TaskNotification(((Number) row.get("id")).longValue(), (UUID) row.get("task_id"),
                (String) row.get("task_title"), NotificationType.valueOf((String) row.get("type")),
                (OffsetDateTime) row.get("created_at"), (OffsetDateTime) row.get("read_at"));
    }
}

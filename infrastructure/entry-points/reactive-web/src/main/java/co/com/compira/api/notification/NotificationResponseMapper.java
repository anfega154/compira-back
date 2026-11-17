package co.com.compira.api.notification;

import co.com.compira.model.notification.TaskNotification;
import org.springframework.stereotype.Component;

@Component
public class NotificationResponseMapper {
    public NotificationResponse toResponse(TaskNotification notification) {
        return new NotificationResponse(Long.toString(notification.id()), notification.taskId(), notification.taskTitle(),
                notification.type().name(), notification.createdAt(), notification.readAt());
    }
}

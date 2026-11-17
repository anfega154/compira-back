package co.com.compira.model.notification;
import java.util.UUID;
public record NotificationDispatch(long id, UUID recipientId) { }

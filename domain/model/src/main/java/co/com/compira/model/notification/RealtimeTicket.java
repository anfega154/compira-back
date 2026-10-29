package co.com.compira.model.notification;
import java.time.Instant;
public record RealtimeTicket(String url, Instant expiresAt) { }

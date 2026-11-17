package co.com.compira.api.notification;
import co.com.compira.usecase.notifications.DispatchNotificationsUseCase;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
@Component
public class NotificationOutboxScheduler {
    private final DispatchNotificationsUseCase dispatch;
    private final TransactionalOperator transactions;
    public NotificationOutboxScheduler(DispatchNotificationsUseCase dispatch, TransactionalOperator transactions) {
        this.dispatch = dispatch; this.transactions = transactions;
    }
    @Scheduled(fixedDelay = 1000)
    public Mono<Void> deliver() { return Mono.defer(dispatch::execute).as(transactions::transactional); }
}

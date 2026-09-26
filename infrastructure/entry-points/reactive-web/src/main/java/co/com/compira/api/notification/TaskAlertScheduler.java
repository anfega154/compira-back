package co.com.compira.api.notification;

import co.com.compira.usecase.taskalerts.EvaluateTaskAlertsUseCase;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
@EnableScheduling
public class TaskAlertScheduler {
    private final EvaluateTaskAlertsUseCase alerts;
    private final TransactionalOperator transactions;

    public TaskAlertScheduler(EvaluateTaskAlertsUseCase alerts, TransactionalOperator transactions) {
        this.alerts = alerts;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelay = 1000)
    public Mono<Void> evaluate() {
        return Mono.defer(alerts::evaluate).as(transactions::transactional);
    }
}

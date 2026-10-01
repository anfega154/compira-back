package co.com.compira.usecase.gettaskindicators;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskIndicators;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GetTaskIndicatorsUseCase {
    private static final Duration DUE_SOON_WINDOW = Duration.ofHours(24);

    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskUserDirectoryGateway taskUserDirectoryGateway;
    private final TaskClockGateway taskClockGateway;
    private final TaskAuthorization taskAuthorization;

    public GetTaskIndicatorsUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                    TaskUserDirectoryGateway taskUserDirectoryGateway,
                                    TaskClockGateway taskClockGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskUserDirectoryGateway = taskUserDirectoryGateway;
        this.taskClockGateway = taskClockGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<TaskIndicators> execute(String actorEmail) {
        return taskAuthorization.requireActor(actorEmail)
                .flatMapMany(this::resolveScope)
                .map(this::withDerivedStatus)
                .collectList()
                .flatMap(this::buildIndicators);
    }

    private Flux<Task> resolveScope(TaskUser actor) {
        if (actor.hasRole(RoleCode.ADMINISTRATOR.name())) {
            return taskRepositoryGateway.findAll();
        }
        if (actor.hasRole(RoleCode.COORDINATOR.name())) {
            return taskRepositoryGateway.findByCoordinator(actor.id());
        }
        return Flux.error(new CompiraException(
                TaskErrorCode.ACTOR_NOT_COORDINATOR,
                TaskMessage.ACTOR_NOT_COORDINATOR,
                ErrorCategory.FORBIDDEN));
    }

    private Task withDerivedStatus(Task task) {
        if (task.isOverdue(taskClockGateway.now())) {
            return new Task(task.id(), task.title(), task.description(), task.dueDate(), TaskStatus.DELAYED,
                    task.responsibleUserId(), task.createdByUserId(), task.createdAt(), task.updatedAt());
        }
        return task;
    }

    private Mono<TaskIndicators> buildIndicators(List<Task> tasks) {
        OffsetDateTime now = taskClockGateway.now();
        OffsetDateTime dueSoonLimit = now.plus(DUE_SOON_WINDOW);
        long overdueCount = tasks.stream().filter(task -> task.status() == TaskStatus.DELAYED).count();
        long dueSoonCount = tasks.stream().filter(task -> isDueSoon(task, now, dueSoonLimit)).count();
        long closedCount = tasks.stream().filter(task -> task.status() == TaskStatus.CLOSED).count();
        long closedOnTimeCount = tasks.stream().filter(this::isClosedOnTime).count();
        Integer compliancePercentage = closedCount == 0
                ? null
                : Math.toIntExact(Math.round(closedOnTimeCount * 100.0 / closedCount));
        Map<UUID, Long> workloadByAssignee = tasks.stream()
                .filter(task -> task.status().isActive() && task.responsibleUserId() != null)
                .collect(Collectors.groupingBy(Task::responsibleUserId, Collectors.counting()));
        return resolveWorkload(workloadByAssignee)
                .map(workload -> new TaskIndicators(tasks.size(), overdueCount, dueSoonCount,
                        closedCount, closedOnTimeCount, compliancePercentage, workload));
    }

    private Mono<List<TaskIndicators.AssigneeWorkload>> resolveWorkload(Map<UUID, Long> workloadByAssignee) {
        return taskUserDirectoryGateway.findByIds(workloadByAssignee.keySet())
                .collectMap(TaskUser::id, assignee -> assignee)
                .map(assignees -> workloadByAssignee.entrySet().stream()
                        .map(entry -> toWorkload(entry.getKey(), entry.getValue(), assignees.get(entry.getKey())))
                        .sorted((left, right) -> Long.compare(right.taskCount(), left.taskCount()))
                        .toList());
    }

    private TaskIndicators.AssigneeWorkload toWorkload(UUID assigneeId, long taskCount, TaskUser assignee) {
        String name = assignee == null ? null : (safe(assignee.firstName()) + " " + safe(assignee.lastName())).trim();
        String email = assignee == null ? null : assignee.email();
        return new TaskIndicators.AssigneeWorkload(assigneeId, name == null || name.isBlank() ? null : name, email, taskCount);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isDueSoon(Task task, OffsetDateTime now, OffsetDateTime dueSoonLimit) {
        return task.status().isActive()
                && task.status() != TaskStatus.DELAYED
                && task.dueDate() != null
                && task.dueDate().isAfter(now)
                && !task.dueDate().isAfter(dueSoonLimit);
    }

    private boolean isClosedOnTime(Task task) {
        return task.status() == TaskStatus.CLOSED
                && task.dueDate() != null
                && task.updatedAt() != null
                && !task.updatedAt().isAfter(task.dueDate());
    }
}

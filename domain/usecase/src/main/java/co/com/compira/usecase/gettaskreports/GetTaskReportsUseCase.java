package co.com.compira.usecase.gettaskreports;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskReport;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GetTaskReportsUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskUserDirectoryGateway taskUserDirectoryGateway;
    private final TaskClockGateway taskClockGateway;
    private final TaskAuthorization taskAuthorization;

    public GetTaskReportsUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                 TaskUserDirectoryGateway taskUserDirectoryGateway,
                                 TaskClockGateway taskClockGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskUserDirectoryGateway = taskUserDirectoryGateway;
        this.taskClockGateway = taskClockGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<TaskReport> execute(String actorEmail) {
        return taskAuthorization.requireActor(actorEmail)
                .flatMapMany(this::resolveScope)
                .map(this::withDerivedStatus)
                .filter(task -> task.responsibleUserId() != null)
                .collectMultimap(Task::responsibleUserId)
                .flatMap(this::buildReport);
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

    private Mono<TaskReport> buildReport(Map<UUID, java.util.Collection<Task>> tasksByAssignee) {
        return taskUserDirectoryGateway.findByIds(tasksByAssignee.keySet())
                .collectMap(TaskUser::id, assignee -> assignee)
                .map(assignees -> new TaskReport(tasksByAssignee.entrySet().stream()
                        .map(entry -> buildRow(entry.getKey(), entry.getValue(), assignees.get(entry.getKey())))
                        .sorted((left, right) -> Long.compare(right.totalTasks(), left.totalTasks()))
                        .toList()));
    }

    private TaskReport.AssigneeReportRow buildRow(UUID assigneeId, java.util.Collection<Task> tasks, TaskUser assignee) {
        long total = tasks.size();
        long active = tasks.stream().filter(task -> task.status().isActive()).count();
        long closed = tasks.stream().filter(task -> task.status() == TaskStatus.CLOSED).count();
        long closedOnTime = tasks.stream().filter(this::isClosedOnTime).count();
        long overdue = tasks.stream().filter(task -> task.status() == TaskStatus.DELAYED).count();
        Integer compliance = closed == 0 ? null : Math.toIntExact(Math.round(closedOnTime * 100.0 / closed));
        Double averageClosureHours = averageClosureHours(tasks);
        String name = assignee == null ? null : (safe(assignee.firstName()) + " " + safe(assignee.lastName())).trim();
        String email = assignee == null ? null : assignee.email();
        return new TaskReport.AssigneeReportRow(assigneeId, name == null || name.isBlank() ? null : name, email,
                total, active, closed, closedOnTime, overdue, compliance, averageClosureHours);
    }

    private Double averageClosureHours(java.util.Collection<Task> tasks) {
        List<Double> durations = tasks.stream()
                .filter(task -> task.status() == TaskStatus.CLOSED && task.createdAt() != null && task.updatedAt() != null)
                .map(task -> Duration.between(task.createdAt(), task.updatedAt()).toMinutes() / 60.0)
                .filter(hours -> hours >= 0)
                .toList();
        if (durations.isEmpty()) {
            return null;
        }
        double average = durations.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.round(average * 10.0) / 10.0;
    }

    private boolean isClosedOnTime(Task task) {
        return task.status() == TaskStatus.CLOSED
                && task.dueDate() != null
                && task.updatedAt() != null
                && !task.updatedAt().isAfter(task.dueDate());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

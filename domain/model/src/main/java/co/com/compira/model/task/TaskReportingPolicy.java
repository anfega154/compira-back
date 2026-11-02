package co.com.compira.model.task;

import java.time.Duration;

/** Definiciones compartidas: sólo cierres verificables y con fecha límite entran en puntualidad. */
public final class TaskReportingPolicy {
    private TaskReportingPolicy() { }
    public static boolean hasValidClosure(Task task) {
        return task.status() == TaskStatus.CLOSED && task.closedAt() != null && task.createdAt() != null
                && !task.closedAt().isBefore(task.createdAt());
    }
    public static boolean isEvaluable(Task task) { return hasValidClosure(task) && task.dueDate() != null; }
    public static boolean isOnTime(Task task) { return isEvaluable(task) && !task.closedAt().isAfter(task.dueDate()); }
    public static double closureHours(Task task) { return Duration.between(task.createdAt(), task.closedAt()).toMillis() / 3600000.0; }
}

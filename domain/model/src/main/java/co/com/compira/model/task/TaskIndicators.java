package co.com.compira.model.task;

import java.util.List;
import java.util.UUID;

public record TaskIndicators(
        long totalTasks,
        long overdueCount,
        long dueSoonCount,
        long closedCount,
        long closedOnTimeCount,
        Integer compliancePercentage,
        List<AssigneeWorkload> workloadByAssignee,
        List<Assignee> assignees) {

    public record AssigneeWorkload(
            UUID assigneeId,
            String assigneeName,
            String assigneeEmail,
            long taskCount) {
    }

    public record Assignee(
            UUID id,
            String name,
            String email) {
    }
}

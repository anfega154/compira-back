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
        List<AssigneeWorkload> workloadByAssignee) {

    public record AssigneeWorkload(
            UUID assigneeId,
            String assigneeName,
            String assigneeEmail,
            long taskCount) {
    }
}

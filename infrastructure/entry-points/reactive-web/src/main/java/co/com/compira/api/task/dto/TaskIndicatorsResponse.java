package co.com.compira.api.task.dto;

import java.util.List;
import java.util.UUID;

public record TaskIndicatorsResponse(
        long totalTasks,
        long overdueCount,
        long dueSoonCount,
        long closedCount,
        long closedOnTimeCount,
        Integer compliancePercentage,
        List<AssigneeWorkloadResponse> workloadByAssignee) {

    public record AssigneeWorkloadResponse(
            UUID assigneeId,
            String assigneeName,
            String assigneeEmail,
            long taskCount) {
    }
}

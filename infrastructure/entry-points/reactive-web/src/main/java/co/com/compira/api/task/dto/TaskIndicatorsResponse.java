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
        List<AssigneeWorkloadResponse> workloadByAssignee,
        List<AssigneeResponse> assignees,
        long evaluableClosedCount) {
    public TaskIndicatorsResponse(long totalTasks, long overdueCount, long dueSoonCount, long closedCount, long closedOnTimeCount,
            Integer compliancePercentage, List<AssigneeWorkloadResponse> workloadByAssignee, List<AssigneeResponse> assignees) {
        this(totalTasks, overdueCount, dueSoonCount, closedCount, closedOnTimeCount, compliancePercentage, workloadByAssignee, assignees, closedCount);
    }

    public record AssigneeWorkloadResponse(
            UUID assigneeId,
            String assigneeName,
            String assigneeEmail,
            long taskCount) {
    }

    public record AssigneeResponse(
            UUID id,
            String name,
            String email) {
    }
}

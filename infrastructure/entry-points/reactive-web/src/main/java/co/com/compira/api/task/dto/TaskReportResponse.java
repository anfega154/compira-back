package co.com.compira.api.task.dto;

import java.util.List;
import java.util.UUID;

public record TaskReportResponse(List<AssigneeReportRowResponse> rows) {

    public record AssigneeReportRowResponse(
            UUID assigneeId,
            String assigneeName,
            String assigneeEmail,
            long totalTasks,
            long activeTasks,
            long closedTasks,
            long closedOnTimeTasks,
            long overdueTasks,
            Integer compliancePercentage,
            Double averageClosureHours) {
    }
}

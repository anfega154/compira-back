package co.com.compira.model.task;

import java.util.List;
import java.util.UUID;

public record TaskReport(List<AssigneeReportRow> rows) {

    public record AssigneeReportRow(
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

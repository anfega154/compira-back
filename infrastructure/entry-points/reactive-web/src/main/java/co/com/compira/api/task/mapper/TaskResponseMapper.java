package co.com.compira.api.task.mapper;

import co.com.compira.api.task.dto.TaskHistoryEntryResponse;
import co.com.compira.api.task.dto.TaskIndicatorsResponse;
import co.com.compira.api.task.dto.TaskObservationResponse;
import co.com.compira.api.task.dto.TaskReportResponse;
import co.com.compira.api.task.dto.TaskResponse;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskIndicators;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.TaskReport;
import org.springframework.stereotype.Component;

@Component
public class TaskResponseMapper {
    public TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.id(),
                task.title(),
                task.description(),
                task.dueDate(),
                task.status().name(),
                task.responsibleUserId(),
                task.createdByUserId(),
                task.createdAt(),
                task.updatedAt());
    }

    public TaskObservationResponse toResponse(TaskObservation observation) {
        return new TaskObservationResponse(
                observation.id(),
                observation.taskId(),
                observation.authorUserId(),
                observation.content(),
                observation.createdAt());
    }

    public TaskHistoryEntryResponse toResponse(TaskHistoryEntry entry) {
        return new TaskHistoryEntryResponse(
                entry.id(),
                entry.taskId(),
                entry.event().name(),
                entry.actorUserId(),
                entry.previousValue(),
                entry.newValue(),
                entry.detail(),
                entry.createdAt());
    }

    public TaskIndicatorsResponse toResponse(TaskIndicators indicators) {
        return new TaskIndicatorsResponse(
                indicators.totalTasks(),
                indicators.overdueCount(),
                indicators.dueSoonCount(),
                indicators.closedCount(),
                indicators.closedOnTimeCount(),
                indicators.compliancePercentage(),
                indicators.workloadByAssignee().stream()
                        .map(workload -> new TaskIndicatorsResponse.AssigneeWorkloadResponse(
                                workload.assigneeId(), workload.assigneeName(), workload.assigneeEmail(), workload.taskCount()))
                        .toList(),
                indicators.assignees().stream()
                        .map(assignee -> new TaskIndicatorsResponse.AssigneeResponse(
                                assignee.id(), assignee.name(), assignee.email()))
                        .toList());
    }

    public TaskReportResponse toResponse(TaskReport report) {
        return new TaskReportResponse(report.rows().stream()
                .map(row -> new TaskReportResponse.AssigneeReportRowResponse(
                        row.assigneeId(), row.assigneeName(), row.assigneeEmail(),
                        row.totalTasks(), row.activeTasks(), row.closedTasks(), row.closedOnTimeTasks(),
                        row.overdueTasks(), row.compliancePercentage(), row.averageClosureHours()))
                .toList());
    }
}

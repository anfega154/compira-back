package co.com.compira.model.report;

import co.com.compira.model.task.Task;
import java.time.OffsetDateTime;

public record ReportTask(Task task, String responsibleName, String responsibleEmail,
                         OffsetDateTime lastActivityAt, long observations) { }

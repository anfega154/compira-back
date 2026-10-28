package co.com.compira.model.task;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskMessage;

import java.util.Arrays;

public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    DELAYED,
    COMPLETED,
    CLOSED,
    CANCELLED;

    public static TaskStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new CompiraException(
                        TaskErrorCode.INVALID_TASK_STATUS,
                        TaskMessage.INVALID_TASK_STATUS,
                        ErrorCategory.BAD_REQUEST));
    }

    public boolean isTerminal() {
        return this == CLOSED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == PENDING || this == IN_PROGRESS || this == DELAYED;
    }
}

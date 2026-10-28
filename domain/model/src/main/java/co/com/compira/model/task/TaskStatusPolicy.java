package co.com.compira.model.task;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TaskStatusPolicy {
    private static final Map<TaskStatus, Set<TaskStatus>> COLLABORATOR_TRANSITIONS = Map.of(
            TaskStatus.PENDING, EnumSet.of(TaskStatus.IN_PROGRESS),
            TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.PENDING, TaskStatus.COMPLETED),
            TaskStatus.DELAYED, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED),
            TaskStatus.COMPLETED, EnumSet.of(TaskStatus.IN_PROGRESS));

    private TaskStatusPolicy() {
    }

    public static boolean isCollaboratorTransitionAllowed(TaskStatus current, TaskStatus target) {
        if (target == TaskStatus.CLOSED || target == TaskStatus.CANCELLED || target == TaskStatus.DELAYED) {
            return false;
        }
        return COLLABORATOR_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(TaskStatus.class)).contains(target);
    }

    public static boolean isCancellable(TaskStatus current) {
        return current != TaskStatus.CLOSED && current != TaskStatus.CANCELLED;
    }

    public static boolean isReassignable(TaskStatus current) {
        return current != TaskStatus.CLOSED && current != TaskStatus.CANCELLED;
    }

    public static boolean isClosable(TaskStatus current) {
        return current == TaskStatus.COMPLETED;
    }
}

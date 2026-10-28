package co.com.compira.usecase.task;

import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskStatusPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusPolicyTest {
    @Test
    void shouldAllowCollaboratorForwardTransitions() {
        assertTrue(TaskStatusPolicy.isCollaboratorTransitionAllowed(TaskStatus.PENDING, TaskStatus.IN_PROGRESS));
        assertTrue(TaskStatusPolicy.isCollaboratorTransitionAllowed(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED));
        assertTrue(TaskStatusPolicy.isCollaboratorTransitionAllowed(TaskStatus.DELAYED, TaskStatus.COMPLETED));
    }

    @Test
    void shouldRejectCollaboratorForbiddenTransitions() {
        assertFalse(TaskStatusPolicy.isCollaboratorTransitionAllowed(TaskStatus.PENDING, TaskStatus.CLOSED));
        assertFalse(TaskStatusPolicy.isCollaboratorTransitionAllowed(TaskStatus.PENDING, TaskStatus.CANCELLED));
        assertFalse(TaskStatusPolicy.isCollaboratorTransitionAllowed(TaskStatus.PENDING, TaskStatus.DELAYED));
        assertFalse(TaskStatusPolicy.isCollaboratorTransitionAllowed(TaskStatus.PENDING, TaskStatus.COMPLETED));
        assertFalse(TaskStatusPolicy.isCollaboratorTransitionAllowed(TaskStatus.CLOSED, TaskStatus.IN_PROGRESS));
    }

    @Test
    void shouldEvaluateCancellable() {
        assertTrue(TaskStatusPolicy.isCancellable(TaskStatus.PENDING));
        assertTrue(TaskStatusPolicy.isCancellable(TaskStatus.DELAYED));
        assertFalse(TaskStatusPolicy.isCancellable(TaskStatus.CLOSED));
        assertFalse(TaskStatusPolicy.isCancellable(TaskStatus.CANCELLED));
    }

    @Test
    void shouldEvaluateReassignable() {
        assertTrue(TaskStatusPolicy.isReassignable(TaskStatus.IN_PROGRESS));
        assertFalse(TaskStatusPolicy.isReassignable(TaskStatus.CLOSED));
        assertFalse(TaskStatusPolicy.isReassignable(TaskStatus.CANCELLED));
    }

    @Test
    void shouldEvaluateClosable() {
        assertTrue(TaskStatusPolicy.isClosable(TaskStatus.COMPLETED));
        assertFalse(TaskStatusPolicy.isClosable(TaskStatus.IN_PROGRESS));
    }

    @Test
    void shouldParseStatusFromValue() {
        assertTrue(TaskStatus.fromValue("in_progress") == TaskStatus.IN_PROGRESS);
        assertTrue(TaskStatus.PENDING.isActive());
        assertTrue(TaskStatus.CLOSED.isTerminal());
    }
}

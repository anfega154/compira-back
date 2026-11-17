package co.com.compira.model.task;

public final class TaskErrorCode {
    public static final String TASK_NOT_FOUND = "TASK_001";
    public static final String INVALID_TASK_STATUS = "TASK_002";
    public static final String INVALID_STATUS_TRANSITION = "TASK_003";
    public static final String RESPONSIBLE_NOT_FOUND = "TASK_004";
    public static final String RESPONSIBLE_NOT_COLLABORATOR = "TASK_005";
    public static final String ACTOR_NOT_FOUND = "TASK_006";
    public static final String ACTOR_NOT_COORDINATOR = "TASK_007";
    public static final String ACTOR_NOT_RESPONSIBLE = "TASK_008";
    public static final String TASK_ALREADY_CLOSED = "TASK_009";
    public static final String TASK_NOT_COMPLETED = "TASK_010";
    public static final String DUE_DATE_IN_PAST = "TASK_011";
    public static final String CLOSE_NOT_ALLOWED_FOR_COLLABORATOR = "TASK_012";
    public static final String INVALID_TASK_REQUEST = "TASK_013";

    private TaskErrorCode() {
    }
}

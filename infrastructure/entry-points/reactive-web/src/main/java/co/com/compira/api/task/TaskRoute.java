package co.com.compira.api.task;

public final class TaskRoute {
    public static final String API_V1 = "/api/v1";
    public static final String TASKS_BASE = "/tasks";
    public static final String CREATE = "";
    public static final String MANAGED = "";
    public static final String ASSIGNED = "/assigned";
    public static final String BY_ID = "/{taskId}";
    public static final String ASSIGN = "/{taskId}/assign";
    public static final String REASSIGN = "/{taskId}/reassign";
    public static final String STATUS = "/{taskId}/status";
    public static final String CANCEL = "/{taskId}/cancel";
    public static final String APPROVE = "/{taskId}/approve";
    public static final String OBSERVATIONS = "/{taskId}/observations";
    public static final String HISTORY = "/{taskId}/history";
    public static final String TASK_ID_VARIABLE = "taskId";
    public static final String ACTOR_EMAIL_HEADER = "X-Actor-Email";

    private TaskRoute() {
    }
}

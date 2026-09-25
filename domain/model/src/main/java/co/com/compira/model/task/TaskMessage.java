package co.com.compira.model.task;

public final class TaskMessage {
    public static final String TASK_NOT_FOUND = "No se encontró la tarea solicitada";
    public static final String INVALID_TASK_STATUS = "El estado de la tarea no es válido";
    public static final String INVALID_STATUS_TRANSITION = "La transición de estado solicitada no está permitida";
    public static final String RESPONSIBLE_NOT_FOUND = "No se encontró el colaborador responsable indicado";
    public static final String RESPONSIBLE_NOT_COLLABORATOR = "El responsable de una tarea debe ser un colaborador";
    public static final String ACTOR_NOT_FOUND = "No se encontró el usuario que realiza la operación";
    public static final String ACTOR_NOT_COORDINATOR = "Esta operación solo puede realizarla un coordinador";
    public static final String ACTOR_NOT_RESPONSIBLE = "Solo el responsable vigente puede actualizar la tarea";
    public static final String TASK_ALREADY_CLOSED = "Una tarea cerrada no admite esta operación";
    public static final String TASK_NOT_COMPLETED = "Solo puede cerrarse una tarea que está completada";
    public static final String DUE_DATE_IN_PAST = "La fecha límite no puede ser anterior al momento actual";
    public static final String CLOSE_NOT_ALLOWED_FOR_COLLABORATOR = "El colaborador no puede cerrar la tarea; el cierre lo realiza el coordinador";
    public static final String INVALID_TASK_REQUEST = "La solicitud contiene datos inválidos o incompletos";

    private TaskMessage() {
    }
}

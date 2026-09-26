package co.com.compira.api.task;

public final class TaskValidationMessage {
    public static final String TEAM_REQUIRED = "El equipo es obligatorio";
    public static final String ACTOR_EMAIL_REQUIRED = "El correo del usuario que realiza la operación es obligatorio";
    public static final String TITLE_REQUIRED = "El título de la tarea es obligatorio";
    public static final String TITLE_LENGTH = "El título puede tener máximo 150 caracteres";
    public static final String DESCRIPTION_LENGTH = "La descripción puede tener máximo 2000 caracteres";
    public static final String RESPONSIBLE_EMAIL_REQUIRED = "El correo del responsable es obligatorio";
    public static final String RESPONSIBLE_EMAIL_INVALID = "El correo del responsable no tiene un formato válido";
    public static final String STATUS_REQUIRED = "El estado objetivo es obligatorio";
    public static final String STATUS_INVALID = "El estado objetivo no es válido";
    public static final String OBSERVATION_REQUIRED = "La observación es obligatoria";
    public static final String OBSERVATION_LENGTH = "La observación puede tener máximo 2000 caracteres";
    public static final String TASK_ID_INVALID = "El identificador de la tarea no es válido";

    private TaskValidationMessage() {
    }
}

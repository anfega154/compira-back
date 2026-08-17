package co.com.compira.api.auth;

public final class AuthenticationValidationMessage {
    public static final String EMAIL_REQUIRED = "El correo electrónico es obligatorio";
    public static final String EMAIL_INVALID = "El correo electrónico no tiene un formato válido";
    public static final String PASSWORD_REQUIRED = "La contraseña es obligatoria";
    public static final String PASSWORD_LENGTH = "La contraseña debe tener entre 8 y 128 caracteres";
    public static final String FIRST_NAME_REQUIRED = "El nombre es obligatorio";
    public static final String FIRST_NAME_LENGTH = "El nombre puede tener máximo 100 caracteres";
    public static final String LAST_NAME_REQUIRED = "El apellido es obligatorio";
    public static final String LAST_NAME_LENGTH = "El apellido puede tener máximo 100 caracteres";
    public static final String PHONE_NUMBER_REQUIRED = "El número de teléfono es obligatorio";
    public static final String PHONE_NUMBER_INVALID = "El número de teléfono debe estar en formato E.164";
    public static final String MFA_CHANNEL_REQUIRED = "El canal MFA preferido es obligatorio";
    public static final String MFA_CHANNEL_INVALID = "El canal MFA preferido debe ser EMAIL o SMS";
    public static final String SESSION_REQUIRED = "La sesión es obligatoria";
    public static final String CHALLENGE_NAME_REQUIRED = "El nombre del reto es obligatorio";
    public static final String CHALLENGE_NAME_INVALID = "El nombre del reto no es válido";
    public static final String CHALLENGE_CODE_REQUIRED = "El código de confirmación es obligatorio";
    public static final String NEW_PASSWORD_REQUIRED = "La nueva contraseña es obligatoria";
    public static final String NEW_PASSWORD_LENGTH = "La nueva contraseña debe tener entre 8 y 128 caracteres";
    public static final String DELETE_USER_SUCCESS = "Usuario eliminado correctamente";
    public static final String ACCESS_TOKEN_REQUIRED = "El access token es obligatorio";

    private AuthenticationValidationMessage() {
    }
}

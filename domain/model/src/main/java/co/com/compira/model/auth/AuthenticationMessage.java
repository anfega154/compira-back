package co.com.compira.model.auth;

public final class AuthenticationMessage {
    public static final String UNEXPECTED_ERROR = "Ocurrió un error interno inesperado";
    public static final String USER_ALREADY_EXISTS = "Ya existe una cuenta registrada con este correo electrónico";
    public static final String INVALID_PASSWORD = "La contraseña no cumple con la política definida en Cognito";
    public static final String INVALID_CONFIRMATION_CODE = "El código de confirmación es inválido";
    public static final String EXPIRED_CONFIRMATION_CODE = "El código de confirmación expiró";
    public static final String INVALID_CREDENTIALS = "Las credenciales ingresadas no son válidas";
    public static final String USER_NOT_CONFIRMED = "La cuenta aún no ha sido confirmada";
    public static final String USER_NOT_FOUND = "No se encontró una cuenta asociada al usuario enviado";
    public static final String PASSWORD_RESET_REQUIRED = "Debes restablecer la contraseña antes de iniciar sesión";
    public static final String LOCAL_USER_NOT_FOUND = "No se encontró el perfil local del usuario";
    public static final String LOCAL_USER_PERSISTENCE_ERROR = "No fue posible guardar el perfil local del usuario";
    public static final String INVALID_CHALLENGE_REQUEST = "La solicitud del reto de autenticación no es válida";
    public static final String MFA_CHANNEL_REQUIRED = "Debes indicar el canal MFA cuando el reto es SELECT_MFA_TYPE";
    public static final String CHALLENGE_CODE_REQUIRED = "Debes enviar el código de verificación para completar el reto seleccionado";
    public static final String UNSUPPORTED_CHALLENGE = "El reto solicitado no está soportado por este backend";
    public static final String TOO_MANY_REQUESTS = "Se recibieron demasiadas solicitudes. Inténtalo nuevamente en unos minutos";
    public static final String INVALID_REQUEST = "La solicitud contiene datos inválidos o incompletos";
    public static final String IDENTITY_PROVIDER_CONFIGURATION_ERROR = "La configuración de Cognito no es válida para esta operación. Revisa SES, MFA y el cliente de la aplicación";
    public static final String GENERIC_AUTHENTICATION_ERROR = "Ocurrió un error inesperado durante el proceso de autenticación";

    private AuthenticationMessage() {
    }
}

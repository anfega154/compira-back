package co.com.compira.model.auth;

@SuppressWarnings("java:S2068")
public final class AuthenticationErrorCode {
    public static final String UNEXPECTED_ERROR = "AUTH_000";
    public static final String USER_ALREADY_EXISTS = "AUTH_001";
    public static final String INVALID_PASSWORD = "AUTH_002";
    public static final String INVALID_CONFIRMATION_CODE = "AUTH_003";
    public static final String EXPIRED_CONFIRMATION_CODE = "AUTH_004";
    public static final String INVALID_CREDENTIALS = "AUTH_005";
    public static final String USER_NOT_CONFIRMED = "AUTH_006";
    public static final String USER_NOT_FOUND = "AUTH_007";
    public static final String PASSWORD_RESET_REQUIRED = "AUTH_008";
    public static final String UNSUPPORTED_CHALLENGE = "AUTH_009";
    public static final String LOCAL_USER_NOT_FOUND = "AUTH_010";
    public static final String GENERIC_AUTHENTICATION_ERROR = "AUTH_011";
    public static final String INVALID_CHALLENGE_REQUEST = "AUTH_012";
    public static final String TOO_MANY_REQUESTS = "AUTH_013";
    public static final String INVALID_REQUEST = "AUTH_014";
    public static final String IDENTITY_PROVIDER_CONFIGURATION_ERROR = "AUTH_015";

    private AuthenticationErrorCode() {
    }
}

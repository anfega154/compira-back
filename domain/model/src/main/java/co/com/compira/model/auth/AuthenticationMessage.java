package co.com.compira.model.auth;

public final class AuthenticationMessage {
    public static final String USER_ALREADY_EXISTS = "An account with this email already exists";
    public static final String INVALID_PASSWORD = "Password does not meet the required policy";
    public static final String INVALID_CONFIRMATION_CODE = "The confirmation code is invalid";
    public static final String EXPIRED_CONFIRMATION_CODE = "The confirmation code has expired";
    public static final String INVALID_CREDENTIALS = "The provided credentials are invalid";
    public static final String USER_NOT_CONFIRMED = "The user account is not confirmed";
    public static final String USER_NOT_FOUND = "The user account was not found";
    public static final String PASSWORD_RESET_REQUIRED = "A password reset is required before signing in";
    public static final String LOCAL_USER_NOT_FOUND = "The local user profile was not found";
    public static final String INVALID_CHALLENGE_REQUEST = "The challenge request is invalid for the selected challenge";
    public static final String TOO_MANY_REQUESTS = "Too many requests were received. Please try again later";
    public static final String GENERIC_AUTHENTICATION_ERROR = "An unexpected authentication error occurred";

    private AuthenticationMessage() {
    }
}

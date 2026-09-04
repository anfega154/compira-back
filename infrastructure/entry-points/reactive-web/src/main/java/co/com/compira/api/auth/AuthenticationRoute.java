package co.com.compira.api.auth;

@SuppressWarnings("java:S2068")
public final class AuthenticationRoute {
    public static final String API_V1 = "/api/v1";
    public static final String AUTH_BASE = "/auth";
    public static final String REGISTER = "/register";
    public static final String LOGIN = "/login";
    public static final String LOGOUT = "/logout";
    public static final String LOGIN_CHALLENGE = "/login/challenge";
    public static final String RESEND_CONFIRMATION_CODE = "/login/resend-code";
    public static final String PASSWORD_RECOVERY = "/password-recovery";
    public static final String PASSWORD_RECOVERY_CONFIRMATION = "/password-recovery/confirm";
    public static final String USERS = "/users";

    private AuthenticationRoute() {
    }
}

package co.com.compira.model.auth;

public final class AuthenticationLogSanitizer {
    private static final String EMPTY_VALUE = "<vacío>";
    private static final String NULL_VALUE = "<nulo>";
    private static final int TOKEN_VISIBLE_EDGE = 4;

    private AuthenticationLogSanitizer() {
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return NULL_VALUE;
        }
        if (email.isBlank()) {
            return EMPTY_VALUE;
        }
        int separatorIndex = email.indexOf('@');
        if (separatorIndex <= 0 || separatorIndex == email.length() - 1) {
            return maskToken(email);
        }

        String localPart = email.substring(0, separatorIndex);
        String domainPart = email.substring(separatorIndex + 1);
        return maskEdge(localPart, 1) + "@" + maskEdge(domainPart, 1);
    }

    public static String maskSession(String session) {
        if (session == null) {
            return NULL_VALUE;
        }
        if (session.isBlank()) {
            return EMPTY_VALUE;
        }
        return maskToken(session);
    }

    public static String maskAccessToken(String accessToken) {
        if (accessToken == null) {
            return NULL_VALUE;
        }
        if (accessToken.isBlank()) {
            return EMPTY_VALUE;
        }
        return maskToken(accessToken);
    }

    private static String maskToken(String value) {
        if (value.length() <= TOKEN_VISIBLE_EDGE * 2) {
            return maskEdge(value, 1);
        }
        return value.substring(0, TOKEN_VISIBLE_EDGE)
                + "***"
                + value.substring(value.length() - TOKEN_VISIBLE_EDGE);
    }

    private static String maskEdge(String value, int visibleChars) {
        if (value.length() <= visibleChars) {
            return "*";
        }
        return value.substring(0, visibleChars) + "***";
    }
}

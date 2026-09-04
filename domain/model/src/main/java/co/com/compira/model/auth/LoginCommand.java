package co.com.compira.model.auth;

@SuppressWarnings("java:S2068")
public record LoginCommand(String username, String password) {
}

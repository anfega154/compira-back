package co.com.compira.model.auth;

@SuppressWarnings("java:S2068")
public record RegisterUserCommand(
        String email,
        String password,
        String firstName,
        String lastName,
        String phoneNumber,
        MfaChannel preferredMfaChannel,
        RoleCode roleCode) {
}

package co.com.compira.model.auth;

public record UserRegistrationResult(
        String cognitoSub,
        boolean userConfirmed,
        CodeDeliveryDetails codeDeliveryDetails) {
}

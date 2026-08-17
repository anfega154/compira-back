package co.com.compira.api.auth.dto;

public record UserRegistrationResponse(
        String cognitoSub,
        boolean userConfirmed,
        CodeDeliveryDetailsResponse codeDeliveryDetails) {
}

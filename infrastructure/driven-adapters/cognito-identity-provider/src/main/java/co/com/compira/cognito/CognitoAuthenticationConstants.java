package co.com.compira.cognito;

@SuppressWarnings("java:S2068")
public final class CognitoAuthenticationConstants {
    public static final String EMAIL_ATTRIBUTE = "email";
    public static final String EMAIL_VERIFIED_ATTRIBUTE = "email_verified";
    public static final String GIVEN_NAME_ATTRIBUTE = "given_name";
    public static final String FAMILY_NAME_ATTRIBUTE = "family_name";
    public static final String PHONE_NUMBER_ATTRIBUTE = "phone_number";
    public static final String SUB_ATTRIBUTE = "sub";
    public static final String USERNAME_PARAMETER = "USERNAME";
    public static final String PASSWORD_PARAMETER = "PASSWORD";
    public static final String ANSWER_PARAMETER = "ANSWER";
    public static final String EMAIL_OTP_CODE_PARAMETER = "EMAIL_OTP_CODE";
    public static final String SMS_MFA_CODE_PARAMETER = "SMS_MFA_CODE";
    public static final String SOFTWARE_TOKEN_MFA_CODE_PARAMETER = "SOFTWARE_TOKEN_MFA_CODE";
    public static final String NEW_PASSWORD_PARAMETER = "NEW_PASSWORD";
    public static final String CHALLENGE_DELIVERY_DESTINATION_PARAMETER = "CODE_DELIVERY_DESTINATION";
    public static final String CHALLENGE_DELIVERY_MEDIUM_PARAMETER = "CODE_DELIVERY_DELIVERY_MEDIUM";
    public static final String MFA_OPTIONS_PARAMETER = "MFAS_CAN_CHOOSE";

    private CognitoAuthenticationConstants() {
    }
}

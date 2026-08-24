package co.com.compira.model.auth;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;

import java.util.Arrays;

public enum AuthenticationChallengeName {
    EMAIL_OTP,
    SMS_MFA,
    SELECT_MFA_TYPE,
    SOFTWARE_TOKEN_MFA,
    NEW_PASSWORD_REQUIRED;

    public static AuthenticationChallengeName fromValue(String value) {
        return Arrays.stream(values())
                .filter(challenge -> challenge.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new CompiraException(
                        AuthenticationErrorCode.INVALID_REQUEST,
                        AuthenticationMessage.INVALID_REQUEST,
                        ErrorCategory.BAD_REQUEST));
    }
}

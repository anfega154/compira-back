package co.com.compira.model.auth;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;

import java.util.Arrays;

public enum MfaChannel {
    EMAIL(AuthenticationChallengeName.EMAIL_OTP),
    SMS(AuthenticationChallengeName.SMS_MFA);

    private final AuthenticationChallengeName challengeName;

    MfaChannel(AuthenticationChallengeName challengeName) {
        this.challengeName = challengeName;
    }

    public AuthenticationChallengeName getChallengeName() {
        return challengeName;
    }

    public static MfaChannel fromValue(String value) {
        return Arrays.stream(values())
                .filter(channel -> channel.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new CompiraException(
                        AuthenticationErrorCode.INVALID_REQUEST,
                        AuthenticationMessage.INVALID_REQUEST,
                        ErrorCategory.BAD_REQUEST));
    }
}

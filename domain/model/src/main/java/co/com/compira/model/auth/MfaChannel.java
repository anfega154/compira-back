package co.com.compira.model.auth;

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
                .orElseThrow(() -> new IllegalArgumentException(value + " is not a supported MFA channel"));
    }
}

package co.com.compira.api.auth.mapper;

import co.com.compira.api.auth.AuthenticationApiTestData;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.MfaChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationRequestMapperTest {
    private final AuthenticationRequestMapper mapper = new AuthenticationRequestMapper();

    @Test
    void shouldMapRegisterRequestToCommand() {
        var command = mapper.toCommand(AuthenticationApiTestData.registerUserRequest());

        assertEquals("john.doe@compira.co", command.email());
        assertEquals(MfaChannel.EMAIL, command.preferredMfaChannel());
    }

    @Test
    void shouldMapChallengeRequestToCommand() {
        var command = mapper.toCommand(AuthenticationApiTestData.respondChallengeRequest());

        assertEquals(AuthenticationChallengeName.EMAIL_OTP, command.challengeName());
        assertEquals("654321", command.code());
    }

    @Test
    void shouldMapDeleteUserRequestToCommand() {
        var command = mapper.toCommand(AuthenticationApiTestData.deleteUserRequest());

        assertEquals("john.doe@compira.co", command.email());
    }

    @Test
    void shouldMapLogoutRequestToCommand() {
        var command = mapper.toCommand(AuthenticationApiTestData.logoutRequest());

        assertEquals("access-token", command.accessToken());
    }
}

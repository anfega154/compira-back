package co.com.compira.api.auth.mapper;

import co.com.compira.api.auth.AuthenticationApiTestData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationResponseMapperTest {
    private final AuthenticationResponseMapper mapper = new AuthenticationResponseMapper();

    @Test
    void shouldMapAuthenticationResultToApiResponse() {
        var response = mapper.toResponse(AuthenticationApiTestData.authenticatedResult());

        assertEquals("AUTHENTICATED", response.status());
        assertEquals("john.doe@compira.co", response.user().email());
        assertEquals("access-token", response.tokens().accessToken());
    }

    @Test
    void shouldMapRegistrationResultToApiResponse() {
        var response = mapper.toResponse(AuthenticationApiTestData.userRegistrationResult());

        assertEquals("cognito-sub-123", response.cognitoSub());
        assertEquals("john.doe@compira.co", response.username());
        assertEquals("FORCE_CHANGE_PASSWORD", response.userStatus());
    }
}

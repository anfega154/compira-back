package co.com.compira.cognito;

import co.com.compira.cognito.config.CognitoIdentityProviderProperties;
import co.com.compira.cognito.mapper.CognitoAuthenticationResultMapper;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationStatus;
import co.com.compira.model.auth.AuthenticationTokens;
import co.com.compira.model.auth.ConfirmPasswordRecoveryCommand;
import co.com.compira.model.auth.LoginCommand;
import co.com.compira.model.auth.LogoutCommand;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.ResendConfirmationCodeCommand;
import co.com.compira.model.auth.RespondAuthenticationChallengeCommand;
import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.auth.StartPasswordRecoveryCommand;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderAsyncClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeDeliveryDetailsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CognitoAuthenticationGatewayAdapterTest {
    private final CognitoIdentityProviderAsyncClient cognitoClient = mock(CognitoIdentityProviderAsyncClient.class);
    private final CognitoIdentityProviderProperties properties = new CognitoIdentityProviderProperties("us-east-1", "us-east-1_pool123", "client-id-123");
    private final CognitoAuthenticationResultMapper mapper = new CognitoAuthenticationResultMapper();
    private CognitoAuthenticationGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CognitoAuthenticationGatewayAdapter(cognitoClient, properties, mapper);
    }

    @Test
    void shouldRegisterUserWithAdminCreateUser() {
        AdminCreateUserResponse response = AdminCreateUserResponse.builder()
                .user(UserType.builder()
                        .username("john@compira.co")
                        .userStatus("FORCE_CHANGE_PASSWORD")
                        .attributes(List.of(
                                AttributeType.builder().name("sub").value("cognito-sub-abc").build(),
                                AttributeType.builder().name("email").value("john@compira.co").build()))
                        .build())
                .build();
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));
        when(cognitoClient.adminSetUserMFAPreference(any(AdminSetUserMfaPreferenceRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(AdminSetUserMfaPreferenceResponse.builder().build()));

        RegisterUserCommand command = new RegisterUserCommand(
                "john@compira.co", "TempPass123!", "John", "Doe", "+573001112233", MfaChannel.EMAIL, RoleCode.COLLABORATOR);

        StepVerifier.create(adapter.registerUser(command))
                .assertNext(result -> {
                    assertEquals("cognito-sub-abc", result.cognitoSub());
                    assertEquals("john@compira.co", result.username());
                    assertEquals("FORCE_CHANGE_PASSWORD", result.userStatus());
                })
                .verifyComplete();

        verify(cognitoClient).adminCreateUser(any(AdminCreateUserRequest.class));
        verify(cognitoClient).adminSetUserMFAPreference(any(AdminSetUserMfaPreferenceRequest.class));
    }

    @Test
    void shouldMapUsernameExistsExceptionOnRegister() {
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(UsernameExistsException.builder().message("exists").build()));

        RegisterUserCommand command = new RegisterUserCommand(
                "john@compira.co", "TempPass123!", "John", "Doe", "+573001112233", MfaChannel.EMAIL, RoleCode.COLLABORATOR);

        StepVerifier.create(adapter.registerUser(command))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_001".equals(((CompiraException) error).getCode())
                        && ErrorCategory.CONFLICT.equals(((CompiraException) error).getErrorCategory()))
                .verify();
    }

    @Test
    void shouldMapInvalidPasswordExceptionOnRegister() {
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(InvalidPasswordException.builder().message("weak").build()));

        RegisterUserCommand command = new RegisterUserCommand(
                "john@compira.co", "weak", "John", "Doe", "+573001112233", MfaChannel.EMAIL, RoleCode.COLLABORATOR);

        StepVerifier.create(adapter.registerUser(command))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_002".equals(((CompiraException) error).getCode()))
                .verify();
    }

    @Test
    void shouldDeleteUser() {
        when(cognitoClient.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(AdminDeleteUserResponse.builder().build()));

        StepVerifier.create(adapter.deleteUser("john@compira.co"))
                .verifyComplete();

        verify(cognitoClient).adminDeleteUser(any(AdminDeleteUserRequest.class));
    }

    @Test
    void shouldMapUserNotFoundOnDelete() {
        when(cognitoClient.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(UserNotFoundException.builder().message("not found").build()));

        StepVerifier.create(adapter.deleteUser("john@compira.co"))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_007".equals(((CompiraException) error).getCode()))
                .verify();
    }

    @Test
    void shouldLoginSuccessfully() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .authenticationResult(software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType.builder()
                        .accessToken("access-token")
                        .idToken("id-token")
                        .refreshToken("refresh-token")
                        .expiresIn(3600)
                        .tokenType("Bearer")
                        .build())
                .build();
        when(cognitoClient.initiateAuth(any(software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(adapter.login(new LoginCommand("john@compira.co", "Pass123!")))
                .assertNext(result -> {
                    assertEquals(AuthenticationStatus.AUTHENTICATED, result.status());
                    assertNotNull(result.tokens());
                    assertEquals("access-token", result.tokens().accessToken());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnChallengeOnLogin() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .challengeName("EMAIL_OTP")
                .session("session-token")
                .challengeParameters(java.util.Map.of(
                        "CODE_DELIVERY_DESTINATION", "j***@c***.co",
                        "CODE_DELIVERY_DELIVERY_MEDIUM", "EMAIL"))
                .build();
        when(cognitoClient.initiateAuth(any(software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(adapter.login(new LoginCommand("john@compira.co", "Pass123!")))
                .assertNext(result -> {
                    assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status());
                    assertEquals(AuthenticationChallengeName.EMAIL_OTP, result.challenge().challengeName());
                    assertEquals("session-token", result.challenge().session());
                })
                .verifyComplete();
    }

    @Test
    void shouldMapNotAuthorizedOnLogin() {
        when(cognitoClient.initiateAuth(any(software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(NotAuthorizedException.builder().message("bad creds").build()));

        StepVerifier.create(adapter.login(new LoginCommand("john@compira.co", "wrong")))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_005".equals(((CompiraException) error).getCode())
                        && ErrorCategory.UNAUTHORIZED.equals(((CompiraException) error).getErrorCategory()))
                .verify();
    }

    @Test
    void shouldLogoutSuccessfully() {
        when(cognitoClient.globalSignOut(any(software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GlobalSignOutResponse.builder().build()));

        StepVerifier.create(adapter.logout(new LogoutCommand("access-token-123")))
                .verifyComplete();
    }

    @Test
    void shouldRespondToChallengeSuccessfully() {
        RespondToAuthChallengeResponse response = RespondToAuthChallengeResponse.builder()
                .authenticationResult(software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType.builder()
                        .accessToken("new-access-token")
                        .idToken("new-id-token")
                        .refreshToken("new-refresh-token")
                        .expiresIn(3600)
                        .tokenType("Bearer")
                        .build())
                .build();
        when(cognitoClient.respondToAuthChallenge(any(software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        RespondAuthenticationChallengeCommand command = new RespondAuthenticationChallengeCommand(
                "john@compira.co", "session", AuthenticationChallengeName.EMAIL_OTP, "123456", null, null);

        StepVerifier.create(adapter.respondToChallenge(command))
                .assertNext(result -> {
                    assertEquals(AuthenticationStatus.AUTHENTICATED, result.status());
                    assertEquals("new-access-token", result.tokens().accessToken());
                })
                .verifyComplete();
    }

    @Test
    void shouldRespondToNewPasswordChallenge() {
        RespondToAuthChallengeResponse response = RespondToAuthChallengeResponse.builder()
                .challengeName("EMAIL_OTP")
                .session("new-session")
                .challengeParameters(java.util.Map.of(
                        "CODE_DELIVERY_DESTINATION", "j***@c***.co",
                        "CODE_DELIVERY_DELIVERY_MEDIUM", "EMAIL"))
                .build();
        when(cognitoClient.respondToAuthChallenge(any(software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        RespondAuthenticationChallengeCommand command = new RespondAuthenticationChallengeCommand(
                "john@compira.co", "session", AuthenticationChallengeName.NEW_PASSWORD_REQUIRED, null, null, "NewPass123!");

        StepVerifier.create(adapter.respondToChallenge(command))
                .assertNext(result -> {
                    assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status());
                    assertEquals(AuthenticationChallengeName.EMAIL_OTP, result.challenge().challengeName());
                })
                .verifyComplete();
    }

    @Test
    void shouldMapCodeMismatchOnChallenge() {
        when(cognitoClient.respondToAuthChallenge(any(software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(CodeMismatchException.builder().message("wrong code").build()));

        RespondAuthenticationChallengeCommand command = new RespondAuthenticationChallengeCommand(
                "john@compira.co", "session", AuthenticationChallengeName.EMAIL_OTP, "000000", null, null);

        StepVerifier.create(adapter.respondToChallenge(command))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_003".equals(((CompiraException) error).getCode()))
                .verify();
    }

    @Test
    void shouldMapExpiredCodeOnChallenge() {
        when(cognitoClient.respondToAuthChallenge(any(software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(ExpiredCodeException.builder().message("expired").build()));

        RespondAuthenticationChallengeCommand command = new RespondAuthenticationChallengeCommand(
                "john@compira.co", "session", AuthenticationChallengeName.EMAIL_OTP, "123456", null, null);

        StepVerifier.create(adapter.respondToChallenge(command))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_004".equals(((CompiraException) error).getCode()))
                .verify();
    }

    @Test
    void shouldStartPasswordRecovery() {
        ForgotPasswordResponse response = ForgotPasswordResponse.builder()
                .codeDeliveryDetails(CodeDeliveryDetailsType.builder()
                        .destination("j***@c***.co")
                        .deliveryMedium("EMAIL")
                        .attributeName("email")
                        .build())
                .build();
        when(cognitoClient.forgotPassword(any(software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(adapter.startPasswordRecovery(new StartPasswordRecoveryCommand("john@compira.co")))
                .assertNext(result -> {
                    assertNotNull(result.codeDeliveryDetails());
                    assertEquals("EMAIL", result.codeDeliveryDetails().deliveryMedium());
                })
                .verifyComplete();
    }

    @Test
    void shouldConfirmPasswordRecovery() {
        when(cognitoClient.confirmForgotPassword(any(software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ConfirmForgotPasswordResponse.builder().build()));

        StepVerifier.create(adapter.confirmPasswordRecovery(new ConfirmPasswordRecoveryCommand("john@compira.co", "123456", "NewPass123!")))
                .verifyComplete();
    }

    @Test
    void shouldResendConfirmationCode() {
        ResendConfirmationCodeResponse response = ResendConfirmationCodeResponse.builder()
                .codeDeliveryDetails(CodeDeliveryDetailsType.builder()
                        .destination("j***@c***.co")
                        .deliveryMedium("EMAIL")
                        .attributeName("email")
                        .build())
                .build();
        when(cognitoClient.resendConfirmationCode(any(software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        StepVerifier.create(adapter.resendConfirmationCode(new ResendConfirmationCodeCommand("john@compira.co")))
                .assertNext(result -> {
                    assertNotNull(result.codeDeliveryDetails());
                    assertEquals("j***@c***.co", result.codeDeliveryDetails().destination());
                })
                .verifyComplete();
    }

    @Test
    void shouldMapTooManyRequestsException() {
        when(cognitoClient.initiateAuth(any(software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(TooManyRequestsException.builder().message("throttled").build()));

        StepVerifier.create(adapter.login(new LoginCommand("john@compira.co", "Pass123!")))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_013".equals(((CompiraException) error).getCode())
                        && ErrorCategory.TOO_MANY_REQUESTS.equals(((CompiraException) error).getErrorCategory()))
                .verify();
    }

    @Test
    void shouldMapInvalidParameterExceptionWithSesMessage() {
        when(cognitoClient.initiateAuth(any(software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(InvalidParameterException.builder().message("EmailSendingAccount not configured for SES").build()));

        StepVerifier.create(adapter.login(new LoginCommand("john@compira.co", "Pass123!")))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_015".equals(((CompiraException) error).getCode())
                        && ErrorCategory.INTERNAL_SERVER_ERROR.equals(((CompiraException) error).getErrorCategory()))
                .verify();
    }

    @Test
    void shouldMapInvalidParameterExceptionGeneric() {
        when(cognitoClient.initiateAuth(any(software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(InvalidParameterException.builder().message("some invalid param").build()));

        StepVerifier.create(adapter.login(new LoginCommand("john@compira.co", "Pass123!")))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_014".equals(((CompiraException) error).getCode())
                        && ErrorCategory.BAD_REQUEST.equals(((CompiraException) error).getErrorCategory()))
                .verify();
    }

    @Test
    void shouldMapGenericCognitoException() {
        when(cognitoClient.initiateAuth(any(software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("unexpected")));

        StepVerifier.create(adapter.login(new LoginCommand("john@compira.co", "Pass123!")))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_011".equals(((CompiraException) error).getCode())
                        && ErrorCategory.INTERNAL_SERVER_ERROR.equals(((CompiraException) error).getErrorCategory()))
                .verify();
    }

    @Test
    void shouldRespondToSelectMfaTypeChallenge() {
        RespondToAuthChallengeResponse response = RespondToAuthChallengeResponse.builder()
                .challengeName("EMAIL_OTP")
                .session("new-session")
                .challengeParameters(java.util.Map.of(
                        "CODE_DELIVERY_DESTINATION", "j***@c***.co",
                        "CODE_DELIVERY_DELIVERY_MEDIUM", "EMAIL"))
                .build();
        when(cognitoClient.respondToAuthChallenge(any(software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        RespondAuthenticationChallengeCommand command = new RespondAuthenticationChallengeCommand(
                "john@compira.co", "session", AuthenticationChallengeName.SELECT_MFA_TYPE, null, MfaChannel.EMAIL, null);

        StepVerifier.create(adapter.respondToChallenge(command))
                .assertNext(result -> assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status()))
                .verifyComplete();
    }

    @Test
    void shouldRespondToSmsMfaChallenge() {
        RespondToAuthChallengeResponse response = RespondToAuthChallengeResponse.builder()
                .authenticationResult(software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType.builder()
                        .accessToken("at").idToken("it").refreshToken("rt").expiresIn(3600).tokenType("Bearer").build())
                .build();
        when(cognitoClient.respondToAuthChallenge(any(software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        RespondAuthenticationChallengeCommand command = new RespondAuthenticationChallengeCommand(
                "john@compira.co", "session", AuthenticationChallengeName.SMS_MFA, "654321", null, null);

        StepVerifier.create(adapter.respondToChallenge(command))
                .assertNext(result -> assertEquals(AuthenticationStatus.AUTHENTICATED, result.status()))
                .verifyComplete();
    }

    @Test
    void shouldRespondToSoftwareTokenMfaChallenge() {
        RespondToAuthChallengeResponse response = RespondToAuthChallengeResponse.builder()
                .authenticationResult(software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType.builder()
                        .accessToken("at").idToken("it").refreshToken("rt").expiresIn(3600).tokenType("Bearer").build())
                .build();
        when(cognitoClient.respondToAuthChallenge(any(software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        RespondAuthenticationChallengeCommand command = new RespondAuthenticationChallengeCommand(
                "john@compira.co", "session", AuthenticationChallengeName.SOFTWARE_TOKEN_MFA, "111222", null, null);

        StepVerifier.create(adapter.respondToChallenge(command))
                .assertNext(result -> assertEquals(AuthenticationStatus.AUTHENTICATED, result.status()))
                .verifyComplete();
    }
}

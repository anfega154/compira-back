package co.com.compira.cognito;

import co.com.compira.cognito.config.CognitoIdentityProviderProperties;
import co.com.compira.cognito.mapper.CognitoAuthenticationResultMapper;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.CodeDeliveryDetails;
import co.com.compira.model.auth.ConfirmPasswordRecoveryCommand;
import co.com.compira.model.auth.ConfirmUserRegistrationCommand;
import co.com.compira.model.auth.LoginCommand;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.PasswordRecoveryResult;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.RespondAuthenticationChallengeCommand;
import co.com.compira.model.auth.StartPasswordRecoveryCommand;
import co.com.compira.model.auth.UserRegistrationResult;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderAsyncClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeDeliveryDetailsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.EmailMfaSettingsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordResetRequiredException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SMSMfaSettingsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Repository
public class CognitoAuthenticationGatewayAdapter implements AuthenticationGateway {
    private final CognitoIdentityProviderAsyncClient cognitoIdentityProviderAsyncClient;
    private final CognitoIdentityProviderProperties properties;
    private final CognitoAuthenticationResultMapper cognitoAuthenticationResultMapper;

    public CognitoAuthenticationGatewayAdapter(CognitoIdentityProviderAsyncClient cognitoIdentityProviderAsyncClient,
                                              CognitoIdentityProviderProperties properties,
                                              CognitoAuthenticationResultMapper cognitoAuthenticationResultMapper) {
        this.cognitoIdentityProviderAsyncClient = cognitoIdentityProviderAsyncClient;
        this.properties = properties;
        this.cognitoAuthenticationResultMapper = cognitoAuthenticationResultMapper;
    }

    @Override
    public Mono<UserRegistrationResult> registerUser(RegisterUserCommand command) {
        SignUpRequest request = SignUpRequest.builder()
                .clientId(properties.clientId())
                .username(command.email())
                .password(command.password())
                .userAttributes(buildUserAttributes(command))
                .build();

        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.signUp(request))
                .map(response -> new UserRegistrationResult(
                        response.userSub(),
                        response.userConfirmed(),
                        mapCodeDeliveryDetails(response.codeDeliveryDetails())))
                .onErrorMap(this::mapException);
    }

    @Override
    public Mono<Void> deleteUser(String username) {
        AdminDeleteUserRequest request = AdminDeleteUserRequest.builder()
                .userPoolId(properties.userPoolId())
                .username(username)
                .build();

        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.adminDeleteUser(request))
                .then()
                .onErrorMap(this::mapException);
    }

    @Override
    public Mono<Void> confirmUserRegistration(ConfirmUserRegistrationCommand command, MfaChannel preferredMfaChannel) {
        ConfirmSignUpRequest confirmSignUpRequest = ConfirmSignUpRequest.builder()
                .clientId(properties.clientId())
                .username(command.email())
                .confirmationCode(command.confirmationCode())
                .build();

        AdminSetUserMfaPreferenceRequest mfaPreferenceRequest = AdminSetUserMfaPreferenceRequest.builder()
                .userPoolId(properties.userPoolId())
                .username(command.email())
                .emailMfaSettings(EmailMfaSettingsType.builder()
                        .enabled(MfaChannel.EMAIL.equals(preferredMfaChannel))
                        .preferredMfa(MfaChannel.EMAIL.equals(preferredMfaChannel))
                        .build())
                .smsMfaSettings(SMSMfaSettingsType.builder()
                        .enabled(MfaChannel.SMS.equals(preferredMfaChannel))
                        .preferredMfa(MfaChannel.SMS.equals(preferredMfaChannel))
                        .build())
                .build();

        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.confirmSignUp(confirmSignUpRequest))
                .then(Mono.fromFuture(cognitoIdentityProviderAsyncClient.adminSetUserMFAPreference(mfaPreferenceRequest)))
                .then()
                .onErrorMap(this::mapException);
    }

    @Override
    public Mono<AuthenticationResult> login(LoginCommand command) {
        InitiateAuthRequest request = InitiateAuthRequest.builder()
                .clientId(properties.clientId())
                .authFlow("USER_PASSWORD_AUTH")
                .authParameters(Map.of(
                        CognitoAuthenticationConstants.USERNAME_PARAMETER, command.username(),
                        CognitoAuthenticationConstants.PASSWORD_PARAMETER, command.password()))
                .build();

        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.initiateAuth(request))
                .map(cognitoAuthenticationResultMapper::fromInitiateAuthResponse)
                .onErrorMap(this::mapException);
    }

    @Override
    public Mono<AuthenticationResult> respondToChallenge(RespondAuthenticationChallengeCommand command) {
        RespondToAuthChallengeRequest request = RespondToAuthChallengeRequest.builder()
                .clientId(properties.clientId())
                .challengeName(command.challengeName().name())
                .session(command.session())
                .challengeResponses(buildChallengeResponses(command))
                .build();

        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.respondToAuthChallenge(request))
                .map(cognitoAuthenticationResultMapper::fromRespondToChallengeResponse)
                .onErrorMap(this::mapException);
    }

    @Override
    public Mono<PasswordRecoveryResult> startPasswordRecovery(StartPasswordRecoveryCommand command) {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .clientId(properties.clientId())
                .username(command.email())
                .build();

        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.forgotPassword(request))
                .map(response -> new PasswordRecoveryResult(mapCodeDeliveryDetails(response.codeDeliveryDetails())))
                .onErrorMap(this::mapException);
    }

    @Override
    public Mono<Void> confirmPasswordRecovery(ConfirmPasswordRecoveryCommand command) {
        ConfirmForgotPasswordRequest request = ConfirmForgotPasswordRequest.builder()
                .clientId(properties.clientId())
                .username(command.email())
                .confirmationCode(command.confirmationCode())
                .password(command.newPassword())
                .build();

        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.confirmForgotPassword(request))
                .then()
                .onErrorMap(this::mapException);
    }

    private List<AttributeType> buildUserAttributes(RegisterUserCommand command) {
        return List.of(
                AttributeType.builder().name(CognitoAuthenticationConstants.EMAIL_ATTRIBUTE).value(command.email()).build(),
                AttributeType.builder().name(CognitoAuthenticationConstants.GIVEN_NAME_ATTRIBUTE).value(command.firstName()).build(),
                AttributeType.builder().name(CognitoAuthenticationConstants.FAMILY_NAME_ATTRIBUTE).value(command.lastName()).build(),
                AttributeType.builder().name(CognitoAuthenticationConstants.PHONE_NUMBER_ATTRIBUTE).value(command.phoneNumber()).build());
    }

    private Map<String, String> buildChallengeResponses(RespondAuthenticationChallengeCommand command) {
        return switch (command.challengeName()) {
            case SELECT_MFA_TYPE -> Map.of(
                    CognitoAuthenticationConstants.USERNAME_PARAMETER, command.username(),
                    CognitoAuthenticationConstants.ANSWER_PARAMETER, command.mfaChannel().getChallengeName().name());
            case EMAIL_OTP -> Map.of(
                    CognitoAuthenticationConstants.USERNAME_PARAMETER, command.username(),
                    CognitoAuthenticationConstants.EMAIL_OTP_CODE_PARAMETER, command.code());
            case SMS_MFA -> Map.of(
                    CognitoAuthenticationConstants.USERNAME_PARAMETER, command.username(),
                    CognitoAuthenticationConstants.SMS_MFA_CODE_PARAMETER, command.code());
            case SOFTWARE_TOKEN_MFA -> Map.of(
                    CognitoAuthenticationConstants.USERNAME_PARAMETER, command.username(),
                    CognitoAuthenticationConstants.SOFTWARE_TOKEN_MFA_CODE_PARAMETER, command.code());
            case NEW_PASSWORD_REQUIRED -> throw new CompiraException(
                    AuthenticationErrorCode.UNSUPPORTED_CHALLENGE,
                    AuthenticationMessage.INVALID_CHALLENGE_REQUEST,
                    ErrorCategory.BAD_REQUEST);
        };
    }

    private CodeDeliveryDetails mapCodeDeliveryDetails(CodeDeliveryDetailsType codeDeliveryDetailsType) {
        return codeDeliveryDetailsType == null
                ? null
                : new CodeDeliveryDetails(
                codeDeliveryDetailsType.destination(),
                codeDeliveryDetailsType.deliveryMediumAsString(),
                codeDeliveryDetailsType.attributeName());
    }

    private Throwable mapException(Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;

        if (cause instanceof CompiraException) {
            return cause;
        }
        if (cause instanceof UsernameExistsException) {
            return new CompiraException(AuthenticationErrorCode.USER_ALREADY_EXISTS, AuthenticationMessage.USER_ALREADY_EXISTS, ErrorCategory.CONFLICT);
        }
        if (cause instanceof InvalidPasswordException) {
            return new CompiraException(AuthenticationErrorCode.INVALID_PASSWORD, AuthenticationMessage.INVALID_PASSWORD, ErrorCategory.BAD_REQUEST);
        }
        if (cause instanceof CodeMismatchException) {
            return new CompiraException(AuthenticationErrorCode.INVALID_CONFIRMATION_CODE, AuthenticationMessage.INVALID_CONFIRMATION_CODE, ErrorCategory.BAD_REQUEST);
        }
        if (cause instanceof ExpiredCodeException) {
            return new CompiraException(AuthenticationErrorCode.EXPIRED_CONFIRMATION_CODE, AuthenticationMessage.EXPIRED_CONFIRMATION_CODE, ErrorCategory.BAD_REQUEST);
        }
        if (cause instanceof UserNotConfirmedException) {
            return new CompiraException(AuthenticationErrorCode.USER_NOT_CONFIRMED, AuthenticationMessage.USER_NOT_CONFIRMED, ErrorCategory.UNAUTHORIZED);
        }
        if (cause instanceof UserNotFoundException) {
            return new CompiraException(AuthenticationErrorCode.USER_NOT_FOUND, AuthenticationMessage.USER_NOT_FOUND, ErrorCategory.NOT_FOUND);
        }
        if (cause instanceof NotAuthorizedException) {
            return new CompiraException(AuthenticationErrorCode.INVALID_CREDENTIALS, AuthenticationMessage.INVALID_CREDENTIALS, ErrorCategory.UNAUTHORIZED);
        }
        if (cause instanceof PasswordResetRequiredException) {
            return new CompiraException(AuthenticationErrorCode.PASSWORD_RESET_REQUIRED, AuthenticationMessage.PASSWORD_RESET_REQUIRED, ErrorCategory.UNAUTHORIZED);
        }
        if (cause instanceof TooManyRequestsException || cause instanceof LimitExceededException) {
            return new CompiraException(AuthenticationErrorCode.TOO_MANY_REQUESTS, AuthenticationMessage.TOO_MANY_REQUESTS, ErrorCategory.TOO_MANY_REQUESTS);
        }
        if (cause instanceof InvalidParameterException) {
            return new CompiraException(AuthenticationErrorCode.INVALID_CHALLENGE_REQUEST, AuthenticationMessage.INVALID_CHALLENGE_REQUEST, ErrorCategory.BAD_REQUEST);
        }
        return new CompiraException(AuthenticationErrorCode.GENERIC_AUTHENTICATION_ERROR, AuthenticationMessage.GENERIC_AUTHENTICATION_ERROR, ErrorCategory.INTERNAL_SERVER_ERROR);
    }
}

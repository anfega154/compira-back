package co.com.compira.cognito;

import co.com.compira.cognito.config.CognitoIdentityProviderProperties;
import co.com.compira.cognito.mapper.CognitoAuthenticationResultMapper;
import co.com.compira.model.auth.AuthenticationLogSanitizer;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.CodeDeliveryDetails;
import co.com.compira.model.auth.ConfirmPasswordRecoveryCommand;
import co.com.compira.model.auth.LoginCommand;
import co.com.compira.model.auth.LogoutCommand;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.PasswordRecoveryResult;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.ResendConfirmationCodeCommand;
import co.com.compira.model.auth.ResendConfirmationCodeResult;
import co.com.compira.model.auth.RespondAuthenticationChallengeCommand;
import co.com.compira.model.auth.StartPasswordRecoveryCommand;
import co.com.compira.model.auth.UserRegistrationResult;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderAsyncClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserMfaPreferenceRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeDeliveryDetailsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.DeliveryMediumType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.EmailMfaSettingsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordResetRequiredException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SMSMfaSettingsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Repository
public class CognitoAuthenticationGatewayAdapter implements AuthenticationGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(CognitoAuthenticationGatewayAdapter.class);
    private static final String OPERATION_ADMIN_CREATE_USER = "cognito-admin-create-user";
    private static final String OPERATION_SET_MFA_PREFERENCE = "cognito-admin-set-mfa-preference";
    private static final String OPERATION_DELETE_USER = "cognito-admin-delete-user";
    private static final String OPERATION_LOGIN = "cognito-initiate-auth";
    private static final String OPERATION_RESPOND_CHALLENGE = "cognito-respond-to-auth-challenge";
    private static final String OPERATION_START_PASSWORD_RECOVERY = "cognito-forgot-password";
    private static final String OPERATION_CONFIRM_PASSWORD_RECOVERY = "cognito-confirm-forgot-password";
    private static final String OPERATION_RESEND_CONFIRMATION_CODE = "cognito-resend-confirmation-code";
    private static final String LOG_OPERATION_START = "Invocando operación Cognito. operation={} principal={} detalle={}";
    private static final String LOG_OPERATION_SUCCESS = "Operación Cognito completada. operation={} principal={} detalle={}";
    private static final String LOG_OPERATION_FAILURE = "Operación Cognito falló. operation={} principal={} awsType={} awsMessage={}";
    private static final String LOG_EXCEPTION_MAPPED = "Error Cognito mapeado. operation={} principal={} code={} category={}";

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
        String maskedEmail = AuthenticationLogSanitizer.maskEmail(command.email());
        AdminCreateUserRequest request = AdminCreateUserRequest.builder()
                .userPoolId(properties.userPoolId())
                .username(command.email())
                .temporaryPassword(command.password())
                .userAttributes(buildUserAttributes(command))
                .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
                .build();

        LOGGER.info(LOG_OPERATION_START, OPERATION_ADMIN_CREATE_USER, maskedEmail, command.preferredMfaChannel().name());
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.adminCreateUser(request))
                .map(response -> new UserRegistrationResult(
                        extractSubFromAttributes(response.user().attributes()),
                        response.user().username(),
                        response.user().userStatusAsString()))
                .flatMap(result -> setMfaPreference(command.email(), command.preferredMfaChannel())
                        .thenReturn(result))
                .doOnNext(result -> LOGGER.info(
                        LOG_OPERATION_SUCCESS,
                        OPERATION_ADMIN_CREATE_USER,
                        maskedEmail,
                        "status=" + result.userStatus()))
                .onErrorMap(error -> mapException(OPERATION_ADMIN_CREATE_USER, maskedEmail, error));
    }

    @Override
    public Mono<Void> deleteUser(String username) {
        String maskedEmail = AuthenticationLogSanitizer.maskEmail(username);
        AdminDeleteUserRequest request = AdminDeleteUserRequest.builder()
                .userPoolId(properties.userPoolId())
                .username(username)
                .build();

        LOGGER.info(LOG_OPERATION_START, OPERATION_DELETE_USER, maskedEmail, properties.userPoolId());
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.adminDeleteUser(request))
                .doOnSuccess(response -> LOGGER.info(LOG_OPERATION_SUCCESS, OPERATION_DELETE_USER, maskedEmail, properties.userPoolId()))
                .then()
                .onErrorMap(error -> mapException(OPERATION_DELETE_USER, maskedEmail, error));
    }

    @Override
    public Mono<AuthenticationResult> login(LoginCommand command) {
        String maskedEmail = AuthenticationLogSanitizer.maskEmail(command.username());
        InitiateAuthRequest request = InitiateAuthRequest.builder()
                .clientId(properties.clientId())
                .authFlow("USER_PASSWORD_AUTH")
                .authParameters(Map.of(
                        CognitoAuthenticationConstants.USERNAME_PARAMETER, command.username(),
                        CognitoAuthenticationConstants.PASSWORD_PARAMETER, command.password()))
                .build();

        LOGGER.info(LOG_OPERATION_START, OPERATION_LOGIN, maskedEmail, "USER_PASSWORD_AUTH");
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.initiateAuth(request))
                .map(cognitoAuthenticationResultMapper::fromInitiateAuthResponse)
                .doOnNext(result -> LOGGER.info(LOG_OPERATION_SUCCESS, OPERATION_LOGIN, maskedEmail, result.status().name()))
                .onErrorMap(error -> mapException(OPERATION_LOGIN, maskedEmail, error));
    }

    @Override
    public Mono<Void> logout(LogoutCommand command) {
        String maskedAccessToken = AuthenticationLogSanitizer.maskAccessToken(command.accessToken());
        GlobalSignOutRequest request = GlobalSignOutRequest.builder()
                .accessToken(command.accessToken())
                .build();

        LOGGER.info(LOG_OPERATION_START, "cognito-global-sign-out", maskedAccessToken, "logout");
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.globalSignOut(request))
                .doOnSuccess(response -> LOGGER.info(LOG_OPERATION_SUCCESS, "cognito-global-sign-out", maskedAccessToken, "logout"))
                .then()
                .onErrorMap(error -> mapException("cognito-global-sign-out", maskedAccessToken, error));
    }

    @Override
    public Mono<AuthenticationResult> respondToChallenge(RespondAuthenticationChallengeCommand command) {
        String maskedEmail = AuthenticationLogSanitizer.maskEmail(command.username());
        RespondToAuthChallengeRequest request = RespondToAuthChallengeRequest.builder()
                .clientId(properties.clientId())
                .challengeName(command.challengeName().name())
                .session(command.session())
                .challengeResponses(buildChallengeResponses(command))
                .build();

        LOGGER.info(
                LOG_OPERATION_START,
                OPERATION_RESPOND_CHALLENGE,
                maskedEmail,
                command.challengeName().name() + ":" + AuthenticationLogSanitizer.maskSession(command.session()));
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.respondToAuthChallenge(request))
                .map(cognitoAuthenticationResultMapper::fromRespondToChallengeResponse)
                .doOnNext(result -> LOGGER.info(
                        LOG_OPERATION_SUCCESS,
                        OPERATION_RESPOND_CHALLENGE,
                        maskedEmail,
                        result.status().name()))
                .onErrorMap(error -> mapException(OPERATION_RESPOND_CHALLENGE, maskedEmail, error));
    }

    @Override
    public Mono<PasswordRecoveryResult> startPasswordRecovery(StartPasswordRecoveryCommand command) {
        String maskedEmail = AuthenticationLogSanitizer.maskEmail(command.email());
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .clientId(properties.clientId())
                .username(command.email())
                .build();

        LOGGER.info(LOG_OPERATION_START, OPERATION_START_PASSWORD_RECOVERY, maskedEmail, properties.clientId());
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.forgotPassword(request))
                .map(response -> new PasswordRecoveryResult(mapCodeDeliveryDetails(response.codeDeliveryDetails())))
                .doOnNext(result -> LOGGER.info(
                        LOG_OPERATION_SUCCESS,
                        OPERATION_START_PASSWORD_RECOVERY,
                        maskedEmail,
                        result.codeDeliveryDetails() != null ? result.codeDeliveryDetails().deliveryMedium() : "<sin-medio>"))
                .onErrorMap(error -> mapException(OPERATION_START_PASSWORD_RECOVERY, maskedEmail, error));
    }

    @Override
    public Mono<Void> confirmPasswordRecovery(ConfirmPasswordRecoveryCommand command) {
        String maskedEmail = AuthenticationLogSanitizer.maskEmail(command.email());
        ConfirmForgotPasswordRequest request = ConfirmForgotPasswordRequest.builder()
                .clientId(properties.clientId())
                .username(command.email())
                .confirmationCode(command.confirmationCode())
                .password(command.newPassword())
                .build();

        LOGGER.info(LOG_OPERATION_START, OPERATION_CONFIRM_PASSWORD_RECOVERY, maskedEmail, properties.clientId());
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.confirmForgotPassword(request))
                .doOnSuccess(response -> LOGGER.info(LOG_OPERATION_SUCCESS, OPERATION_CONFIRM_PASSWORD_RECOVERY, maskedEmail, "confirmado"))
                .then()
                .onErrorMap(error -> mapException(OPERATION_CONFIRM_PASSWORD_RECOVERY, maskedEmail, error));
    }

    @Override
    public Mono<ResendConfirmationCodeResult> resendConfirmationCode(ResendConfirmationCodeCommand command) {
        String maskedEmail = AuthenticationLogSanitizer.maskEmail(command.email());
        ResendConfirmationCodeRequest request = ResendConfirmationCodeRequest.builder()
                .clientId(properties.clientId())
                .username(command.email())
                .build();

        LOGGER.info(LOG_OPERATION_START, OPERATION_RESEND_CONFIRMATION_CODE, maskedEmail, properties.clientId());
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.resendConfirmationCode(request))
                .map(response -> new ResendConfirmationCodeResult(mapCodeDeliveryDetails(response.codeDeliveryDetails())))
                .doOnNext(result -> LOGGER.info(
                        LOG_OPERATION_SUCCESS,
                        OPERATION_RESEND_CONFIRMATION_CODE,
                        maskedEmail,
                        result.codeDeliveryDetails() != null ? result.codeDeliveryDetails().deliveryMedium() : "<sin-medio>"))
                .onErrorMap(error -> mapException(OPERATION_RESEND_CONFIRMATION_CODE, maskedEmail, error));
    }

    private List<AttributeType> buildUserAttributes(RegisterUserCommand command) {
        return List.of(
                AttributeType.builder().name(CognitoAuthenticationConstants.EMAIL_ATTRIBUTE).value(command.email()).build(),
                AttributeType.builder().name(CognitoAuthenticationConstants.EMAIL_VERIFIED_ATTRIBUTE).value("true").build(),
                AttributeType.builder().name(CognitoAuthenticationConstants.GIVEN_NAME_ATTRIBUTE).value(command.firstName()).build(),
                AttributeType.builder().name(CognitoAuthenticationConstants.FAMILY_NAME_ATTRIBUTE).value(command.lastName()).build(),
                AttributeType.builder().name(CognitoAuthenticationConstants.PHONE_NUMBER_ATTRIBUTE).value(command.phoneNumber()).build());
    }

    private String extractSubFromAttributes(List<AttributeType> attributes) {
        return attributes.stream()
                .filter(attr -> CognitoAuthenticationConstants.SUB_ATTRIBUTE.equals(attr.name()))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);
    }

    private Mono<Void> setMfaPreference(String username, MfaChannel preferredMfaChannel) {
        String maskedEmail = AuthenticationLogSanitizer.maskEmail(username);
        AdminSetUserMfaPreferenceRequest mfaPreferenceRequest = AdminSetUserMfaPreferenceRequest.builder()
                .userPoolId(properties.userPoolId())
                .username(username)
                .emailMfaSettings(EmailMfaSettingsType.builder()
                        .enabled(MfaChannel.EMAIL.equals(preferredMfaChannel))
                        .preferredMfa(MfaChannel.EMAIL.equals(preferredMfaChannel))
                        .build())
                .smsMfaSettings(SMSMfaSettingsType.builder()
                        .enabled(MfaChannel.SMS.equals(preferredMfaChannel))
                        .preferredMfa(MfaChannel.SMS.equals(preferredMfaChannel))
                        .build())
                .build();

        LOGGER.info(LOG_OPERATION_START, OPERATION_SET_MFA_PREFERENCE, maskedEmail, preferredMfaChannel.name());
        return Mono.fromFuture(cognitoIdentityProviderAsyncClient.adminSetUserMFAPreference(mfaPreferenceRequest))
                .doOnSuccess(response -> LOGGER.info(
                        LOG_OPERATION_SUCCESS,
                        OPERATION_SET_MFA_PREFERENCE,
                        maskedEmail,
                        preferredMfaChannel.name()))
                .then()
                .onErrorMap(error -> mapException(OPERATION_SET_MFA_PREFERENCE, maskedEmail, error));
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
            case NEW_PASSWORD_REQUIRED -> Map.of(
                    CognitoAuthenticationConstants.USERNAME_PARAMETER, command.username(),
                    CognitoAuthenticationConstants.NEW_PASSWORD_PARAMETER, command.newPassword());
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

    private Throwable mapException(String operation, String principal, Throwable throwable) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;

        LOGGER.error(
                LOG_OPERATION_FAILURE,
                operation,
                principal,
                cause.getClass().getSimpleName(),
                cause.getMessage(),
                cause);
        if (cause instanceof CompiraException) {
            return cause;
        }
        CompiraException mappedException = cause instanceof UsernameExistsException
                ? new CompiraException(AuthenticationErrorCode.USER_ALREADY_EXISTS, AuthenticationMessage.USER_ALREADY_EXISTS, ErrorCategory.CONFLICT)
                : cause instanceof InvalidPasswordException
                ? new CompiraException(AuthenticationErrorCode.INVALID_PASSWORD, AuthenticationMessage.INVALID_PASSWORD, ErrorCategory.BAD_REQUEST)
                : cause instanceof CodeMismatchException
                ? new CompiraException(AuthenticationErrorCode.INVALID_CONFIRMATION_CODE, AuthenticationMessage.INVALID_CONFIRMATION_CODE, ErrorCategory.BAD_REQUEST)
                : cause instanceof ExpiredCodeException
                ? new CompiraException(AuthenticationErrorCode.EXPIRED_CONFIRMATION_CODE, AuthenticationMessage.EXPIRED_CONFIRMATION_CODE, ErrorCategory.BAD_REQUEST)
                : cause instanceof UserNotConfirmedException
                ? new CompiraException(AuthenticationErrorCode.USER_NOT_CONFIRMED, AuthenticationMessage.USER_NOT_CONFIRMED, ErrorCategory.UNAUTHORIZED)
                : cause instanceof UserNotFoundException
                ? new CompiraException(AuthenticationErrorCode.USER_NOT_FOUND, AuthenticationMessage.USER_NOT_FOUND, ErrorCategory.NOT_FOUND)
                : cause instanceof NotAuthorizedException
                ? new CompiraException(AuthenticationErrorCode.INVALID_CREDENTIALS, AuthenticationMessage.INVALID_CREDENTIALS, ErrorCategory.UNAUTHORIZED)
                : cause instanceof PasswordResetRequiredException
                ? new CompiraException(AuthenticationErrorCode.PASSWORD_RESET_REQUIRED, AuthenticationMessage.PASSWORD_RESET_REQUIRED, ErrorCategory.UNAUTHORIZED)
                : cause instanceof TooManyRequestsException || cause instanceof LimitExceededException
                ? new CompiraException(AuthenticationErrorCode.TOO_MANY_REQUESTS, AuthenticationMessage.TOO_MANY_REQUESTS, ErrorCategory.TOO_MANY_REQUESTS)
                : cause instanceof InvalidParameterException
                ? mapInvalidParameterException((InvalidParameterException) cause)
                : new CompiraException(AuthenticationErrorCode.GENERIC_AUTHENTICATION_ERROR, AuthenticationMessage.GENERIC_AUTHENTICATION_ERROR, ErrorCategory.INTERNAL_SERVER_ERROR);
        LOGGER.warn(
                LOG_EXCEPTION_MAPPED,
                operation,
                principal,
                mappedException.getCode(),
                mappedException.getErrorCategory());
        return mappedException;
    }

    private CompiraException mapInvalidParameterException(InvalidParameterException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return new CompiraException(
                    AuthenticationErrorCode.INVALID_REQUEST,
                    AuthenticationMessage.INVALID_REQUEST,
                    ErrorCategory.BAD_REQUEST);
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("emailsendingaccount")
                || normalizedMessage.contains("emailmfaconfiguration")
                || normalizedMessage.contains("ses")) {
            return new CompiraException(
                    AuthenticationErrorCode.IDENTITY_PROVIDER_CONFIGURATION_ERROR,
                    AuthenticationMessage.IDENTITY_PROVIDER_CONFIGURATION_ERROR,
                    ErrorCategory.INTERNAL_SERVER_ERROR);
        }

        return new CompiraException(
                AuthenticationErrorCode.INVALID_REQUEST,
                AuthenticationMessage.INVALID_REQUEST,
                ErrorCategory.BAD_REQUEST);
    }
}

package co.com.compira.api.auth;

import co.com.compira.api.auth.dto.ConfirmPasswordRecoveryRequest;
import co.com.compira.api.auth.dto.ConfirmUserRegistrationRequest;
import co.com.compira.api.auth.dto.DeleteUserRequest;
import co.com.compira.api.auth.dto.LoginRequest;
import co.com.compira.api.auth.dto.LogoutRequest;
import co.com.compira.api.auth.dto.RegisterUserRequest;
import co.com.compira.api.auth.dto.RespondAuthenticationChallengeRequest;
import co.com.compira.api.auth.dto.StartPasswordRecoveryRequest;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuthenticationRequestValidator {
    private static final String VALIDATION_MESSAGE_SEPARATOR = ", ";

    private final Validator validator;

    public AuthenticationRequestValidator(Validator validator) {
        this.validator = validator;
    }

    public Mono<RegisterUserRequest> validateRegisterUserRequest(RegisterUserRequest request) {
        return validate(request);
    }

    public Mono<ConfirmUserRegistrationRequest> validateConfirmRegistrationRequest(ConfirmUserRegistrationRequest request) {
        return validate(request);
    }

    public Mono<LoginRequest> validateLoginRequest(LoginRequest request) {
        return validate(request);
    }

    public Mono<LogoutRequest> validateLogoutRequest(LogoutRequest request) {
        return validate(request);
    }

    public Mono<RespondAuthenticationChallengeRequest> validateRespondChallengeRequest(RespondAuthenticationChallengeRequest request) {
        return validate(request)
                .flatMap(validRequest -> {
                    AuthenticationChallengeName challengeName = AuthenticationChallengeName.fromValue(validRequest.challengeName());
                    return switch (challengeName) {
                        case SELECT_MFA_TYPE -> validRequest.mfaChannel() == null
                                ? Mono.error(buildBadRequestException(
                                AuthenticationErrorCode.INVALID_CHALLENGE_REQUEST,
                                AuthenticationMessage.MFA_CHANNEL_REQUIRED))
                                : Mono.just(validRequest);
                        case EMAIL_OTP, SMS_MFA, SOFTWARE_TOKEN_MFA -> validRequest.code() == null || validRequest.code().isBlank()
                                ? Mono.error(buildBadRequestException(
                                AuthenticationErrorCode.INVALID_CHALLENGE_REQUEST,
                                AuthenticationMessage.CHALLENGE_CODE_REQUIRED))
                                : Mono.just(validRequest);
                        case NEW_PASSWORD_REQUIRED -> Mono.error(buildBadRequestException(
                                AuthenticationErrorCode.UNSUPPORTED_CHALLENGE,
                                AuthenticationMessage.UNSUPPORTED_CHALLENGE));
                    };
                });
    }

    public Mono<StartPasswordRecoveryRequest> validateStartPasswordRecoveryRequest(StartPasswordRecoveryRequest request) {
        return validate(request);
    }

    public Mono<ConfirmPasswordRecoveryRequest> validateConfirmPasswordRecoveryRequest(ConfirmPasswordRecoveryRequest request) {
        return validate(request);
    }

    public Mono<DeleteUserRequest> validateDeleteUserRequest(DeleteUserRequest request) {
        return validate(request);
    }

    private <T> Mono<T> validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return Mono.just(request);
        }

        return Mono.error(buildBadRequestException(
                AuthenticationErrorCode.INVALID_REQUEST,
                violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .sorted()
                        .collect(Collectors.joining(VALIDATION_MESSAGE_SEPARATOR))));
    }

    private CompiraException buildBadRequestException(String code, String message) {
        return new CompiraException(code, message, ErrorCategory.BAD_REQUEST);
    }
}

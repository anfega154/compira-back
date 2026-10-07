package co.com.compira.usecase.startpasswordrecovery;

import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationLogSanitizer;
import co.com.compira.model.auth.CodeDeliveryDetails;
import co.com.compira.model.auth.PasswordRecoveryResult;
import co.com.compira.model.auth.StartPasswordRecoveryCommand;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import reactor.core.publisher.Mono;

import java.util.Set;

public class StartPasswordRecoveryUseCase {
    /**
     * Códigos de error que delatan si una cuenta existe (o su estado). Para evitar la
     * enumeración de usuarios (R-01), la solicitud de recuperación responde siempre de
     * forma genérica cuando el proveedor de identidad devuelve uno de estos estados.
     */
    private static final Set<String> ACCOUNT_ENUMERATION_CODES = Set.of(
            AuthenticationErrorCode.USER_NOT_FOUND,
            AuthenticationErrorCode.LOCAL_USER_NOT_FOUND,
            AuthenticationErrorCode.USER_NOT_CONFIRMED,
            AuthenticationErrorCode.UNVERIFIED_RECOVERY_CONTACT);

    private static final String EMAIL_DELIVERY_MEDIUM = "EMAIL";
    private static final String EMAIL_ATTRIBUTE = "email";

    private final AuthenticationGateway authenticationGateway;

    public StartPasswordRecoveryUseCase(AuthenticationGateway authenticationGateway) {
        this.authenticationGateway = authenticationGateway;
    }

    public Mono<PasswordRecoveryResult> execute(StartPasswordRecoveryCommand command) {
        return authenticationGateway.startPasswordRecovery(command)
                .onErrorResume(error -> revealsAccountState(error)
                        ? Mono.just(genericResult(command.email()))
                        : Mono.error(error));
    }

    private boolean revealsAccountState(Throwable error) {
        return error instanceof CompiraException compiraException
                && ACCOUNT_ENUMERATION_CODES.contains(compiraException.getCode());
    }

    private PasswordRecoveryResult genericResult(String email) {
        return new PasswordRecoveryResult(new CodeDeliveryDetails(
                AuthenticationLogSanitizer.maskEmail(email),
                EMAIL_DELIVERY_MEDIUM,
                EMAIL_ATTRIBUTE));
    }
}

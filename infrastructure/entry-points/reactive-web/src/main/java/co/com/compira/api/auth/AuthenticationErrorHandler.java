package co.com.compira.api.auth;

import co.com.compira.api.auth.dto.AuthenticationErrorResponse;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationErrorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationErrorHandler.class);
    private static final String LOG_KNOWN_ERROR = "Se responde error controlado. code={} status={} tipo={} mensaje={}";
    private static final String LOG_BAD_REQUEST_ERROR = "Se responde error de solicitud inválida. code={} status={} tipo={} mensaje={}";
    private static final String LOG_UNEXPECTED_ERROR = "Se responde error inesperado. code={} status={} tipo={}";

    public Mono<ServerResponse> handle(Throwable throwable) {
        if (throwable instanceof CompiraException exception) {
            HttpStatus httpStatus = resolveStatus(exception.getErrorCategory());
            LOGGER.warn(
                    LOG_KNOWN_ERROR,
                    exception.getCode(),
                    httpStatus.value(),
                    throwable.getClass().getSimpleName(),
                    exception.getMessage());
            return ServerResponse.status(httpStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationErrorResponse(exception.getCode(), exception.getMessage()));
        }
        if (throwable instanceof IllegalArgumentException exception) {
            LOGGER.warn(
                    LOG_BAD_REQUEST_ERROR,
                    AuthenticationErrorCode.INVALID_REQUEST,
                    HttpStatus.BAD_REQUEST.value(),
                    throwable.getClass().getSimpleName(),
                    exception.getMessage());
            return ServerResponse.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationErrorResponse(
                            AuthenticationErrorCode.INVALID_REQUEST,
                            exception.getMessage()));
        }

        LOGGER.error(
                LOG_UNEXPECTED_ERROR,
                AuthenticationErrorCode.UNEXPECTED_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                throwable.getClass().getSimpleName(),
                throwable);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthenticationErrorResponse(
                        AuthenticationErrorCode.UNEXPECTED_ERROR,
                        AuthenticationMessage.UNEXPECTED_ERROR));
    }

    private HttpStatus resolveStatus(ErrorCategory errorCategory) {
        return switch (errorCategory) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

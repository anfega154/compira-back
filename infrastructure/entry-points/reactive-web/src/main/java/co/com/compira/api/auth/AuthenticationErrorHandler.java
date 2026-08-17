package co.com.compira.api.auth;

import co.com.compira.api.auth.dto.AuthenticationErrorResponse;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationErrorHandler {
    public Mono<ServerResponse> handle(Throwable throwable) {
        if (throwable instanceof CompiraException exception) {
            return ServerResponse.status(resolveStatus(exception.getErrorCategory()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationErrorResponse(exception.getCode(), exception.getMessage()));
        }
        if (throwable instanceof IllegalArgumentException exception) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new AuthenticationErrorResponse(
                            AuthenticationErrorCode.INVALID_REQUEST,
                            exception.getMessage()));
        }

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

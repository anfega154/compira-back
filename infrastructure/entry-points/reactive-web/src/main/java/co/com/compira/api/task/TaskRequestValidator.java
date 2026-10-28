package co.com.compira.api.task;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.TaskErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TaskRequestValidator {
    private static final String VALIDATION_MESSAGE_SEPARATOR = ", ";

    private final Validator validator;

    public TaskRequestValidator(Validator validator) {
        this.validator = validator;
    }

    public <T> Mono<T> validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return Mono.just(request);
        }

        return Mono.error(new CompiraException(
                TaskErrorCode.INVALID_TASK_REQUEST,
                violations.stream()
                        .map(ConstraintViolation::getMessage)
                        .sorted()
                        .collect(Collectors.joining(VALIDATION_MESSAGE_SEPARATOR)),
                ErrorCategory.BAD_REQUEST));
    }

    public Mono<String> requireActorEmail(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return Mono.error(new CompiraException(
                    TaskErrorCode.INVALID_TASK_REQUEST,
                    TaskValidationMessage.ACTOR_EMAIL_REQUIRED,
                    ErrorCategory.BAD_REQUEST));
        }
        return Mono.just(actorEmail);
    }
}

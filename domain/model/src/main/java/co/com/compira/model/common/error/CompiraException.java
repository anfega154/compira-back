package co.com.compira.model.common.error;

public class CompiraException extends RuntimeException {
    private final String code;
    private final ErrorCategory errorCategory;

    public CompiraException(String code, String message, ErrorCategory errorCategory) {
        super(message);
        this.code = code;
        this.errorCategory = errorCategory;
    }

    public String getCode() {
        return code;
    }

    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }
}

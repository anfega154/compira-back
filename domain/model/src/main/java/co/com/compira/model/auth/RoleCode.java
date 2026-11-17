package co.com.compira.model.auth;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;

import java.util.Arrays;

public enum RoleCode {
    ADMINISTRATOR,
    COORDINATOR,
    COLLABORATOR;

    public static RoleCode fromValue(String value) {
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new CompiraException(
                        AuthenticationErrorCode.INVALID_REQUEST,
                        AuthenticationMessage.INVALID_ROLE_CODE,
                        ErrorCategory.BAD_REQUEST));
    }
}

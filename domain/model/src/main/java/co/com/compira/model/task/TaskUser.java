package co.com.compira.model.task;

import java.util.List;
import java.util.UUID;

public record TaskUser(
        UUID id,
        String email,
        String firstName,
        String lastName,
        List<String> roles) {

    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }
}

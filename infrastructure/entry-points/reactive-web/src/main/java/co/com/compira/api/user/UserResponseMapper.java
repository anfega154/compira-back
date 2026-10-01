package co.com.compira.api.user;

import co.com.compira.model.user.OrganizationUser;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {
    public UserResponse toResponse(OrganizationUser user) {
        return new UserResponse(
                user.id(),
                user.email(),
                user.firstName(),
                user.lastName(),
                user.phoneNumber(),
                user.status(),
                user.roles(),
                user.teamId(),
                user.teamName(),
                user.createdAt(),
                user.lastLoginAt());
    }
}

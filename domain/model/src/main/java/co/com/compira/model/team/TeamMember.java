package co.com.compira.model.team;

import java.util.UUID;

public record TeamMember(UUID id, String email, String firstName, String lastName) {
}

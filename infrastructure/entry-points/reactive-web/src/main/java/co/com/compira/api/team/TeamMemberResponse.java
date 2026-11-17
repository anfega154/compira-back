package co.com.compira.api.team;

import java.util.UUID;

public record TeamMemberResponse(UUID id, String email, String firstName, String lastName) {
}

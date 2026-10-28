package co.com.compira.api.team;

import java.util.UUID;

public record TeamResponse(UUID id, String name, UUID coordinatorUserId, String coordinatorEmail) {
}

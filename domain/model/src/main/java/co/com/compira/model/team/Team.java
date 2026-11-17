package co.com.compira.model.team;

import java.util.UUID;

public record Team(UUID id, String name, UUID coordinatorUserId, String coordinatorEmail) {
}

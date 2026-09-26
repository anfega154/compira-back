package co.com.compira.api.team;

import co.com.compira.model.team.Team;
import org.springframework.stereotype.Component;

@Component
public class TeamResponseMapper {
    public TeamResponse toResponse(Team team) {
        return new TeamResponse(team.id(), team.name(), team.coordinatorUserId(), team.coordinatorEmail());
    }
}

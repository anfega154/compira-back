package co.com.compira.r2dbc.mapper;

import co.com.compira.model.team.Team;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

@Component
public class TeamDataMapper {
    public Team toTeam(Map<String, Object> row) {
        return new Team((UUID) row.get("id"), (String) row.get("name"),
                (UUID) row.get("coordinator_user_id"), (String) row.get("email"));
    }
}

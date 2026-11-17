package co.com.compira.r2dbc;

import co.com.compira.model.report.ExecutiveReport;
import co.com.compira.model.report.ReportTask;
import co.com.compira.model.report.gateways.ExecutiveReportGateway;
import co.com.compira.r2dbc.mapper.TaskDataMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Repository
public class ExecutiveReportAdapter implements ExecutiveReportGateway {
    private static final String STATUS = "CASE WHEN tasks.status IN ('PENDING','IN_PROGRESS','DELAYED') AND tasks.due_date <= :now THEN 'DELAYED' ELSE tasks.status END";
    private static final String SELECT = """
        SELECT tasks.*, t.id AS team_id, t.name AS team_name, activity.closed_at,
               NULLIF(TRIM(CONCAT_WS(' ',u.first_name,u.last_name)),'') AS responsible_name,
               u.email AS responsible_email, GREATEST(tasks.updated_at,activity.last_event) AS last_activity,
               (SELECT COUNT(*) FROM task_observations o WHERE o.task_id = tasks.id) AS observation_count
        FROM tasks
        LEFT JOIN task_teams tt ON tt.task_id = tasks.id
        LEFT JOIN teams t ON t.id = tt.team_id
        LEFT JOIN users u ON u.id = tasks.responsible_user_id
        LEFT JOIN LATERAL (SELECT MAX(h.created_at) FILTER (WHERE h.event='CLOSED') AS closed_at,
                                 MAX(h.created_at) AS last_event FROM task_history h WHERE h.task_id=tasks.id) activity ON TRUE
        WHERE 1=1
        """;
    private final DatabaseClient database;
    private final TaskDataMapper mapper;
    public ExecutiveReportAdapter(DatabaseClient database, TaskDataMapper mapper) { this.database=database; this.mapper=mapper; }

    @Override
    public Flux<ReportTask> find(UUID coordinatorId, ExecutiveReport.Filter filter, String zone, OffsetDateTime now, int limit) {
        String dateColumn = switch(filter.dateBasis()) { case CREATED -> "tasks.created_at"; case DUE -> "tasks.due_date"; case CLOSED -> "activity.closed_at"; };
        String sql = SELECT + (coordinatorId == null ? "" : " AND t.coordinator_user_id=:coordinator")
                + (filter.teamId() == null ? "" : " AND t.id=:team")
                + (filter.responsibleId() == null ? "" : " AND tasks.responsible_user_id=:responsible")
                + (filter.unassigned() ? " AND tasks.responsible_user_id IS NULL" : "")
                + (filter.status() == null ? "" : " AND (" + STATUS + ")=:status")
                + (filter.from() == null ? "" : " AND " + dateColumn + ">=:dateFrom")
                + (filter.to() == null ? "" : " AND " + dateColumn + "<:dateTo")
                + " ORDER BY tasks.created_at DESC,tasks.id LIMIT :maxRows";
        var query = database.sql(sql).bind("maxRows", limit);
        if (coordinatorId != null) query=query.bind("coordinator",coordinatorId);
        if (filter.teamId()!=null) query=query.bind("team",filter.teamId());
        if (filter.responsibleId()!=null) query=query.bind("responsible",filter.responsibleId());
        if (filter.status()!=null) query=query.bind("status",filter.status().name()).bind("now",now);
        if (filter.from()!=null) query=query.bind("dateFrom",filter.from().atStartOfDay(ZoneId.of(zone)).toOffsetDateTime());
        if (filter.to()!=null) query=query.bind("dateTo",filter.to().plusDays(1).atStartOfDay(ZoneId.of(zone)).toOffsetDateTime());
        return query.fetch().all().map(row -> new ReportTask(mapper.toTask(row),
                (String) row.get("responsible_name"), (String) row.get("responsible_email"),
                (OffsetDateTime) row.get("last_activity"), ((Number)row.get("observation_count")).longValue()));
    }
}

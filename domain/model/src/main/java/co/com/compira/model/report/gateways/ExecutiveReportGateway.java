package co.com.compira.model.report.gateways;

import co.com.compira.model.report.ExecutiveReport;
import co.com.compira.model.report.ReportTask;
import reactor.core.publisher.Flux;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface ExecutiveReportGateway {
    /** Una sentencia SQL, alcance y filtros antes del límite; coordinatorId nulo sólo para administrador autorizado. */
    Flux<ReportTask> find(UUID coordinatorId, ExecutiveReport.Filter filter, String zone, OffsetDateTime now, int limit);
}

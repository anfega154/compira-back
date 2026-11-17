package co.com.compira.api.report;

import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.model.report.ExecutiveReport;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.usecase.executivereport.GetExecutiveReportUseCase;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import java.time.LocalDate;
import java.time.Duration;
import java.util.*;

@Component
public class ExecutiveReportHandler {
    private static final Set<String> PARAMETERS=Set.of("from","to","dateBasis","teamId","responsibleId","unassigned","status");
    private final GetExecutiveReportUseCase reports;
    private final TaskErrorHandler errors;
    public ExecutiveReportHandler(GetExecutiveReportUseCase reports,TaskErrorHandler errors) { this.reports=reports;this.errors=errors; }
    public Mono<ServerResponse> generate(ServerRequest request) {
        return Mono.fromCallable(()->filters(request)).flatMap(filter->request.principal().flatMap(actor->reports.execute(actor.getName(),filter)))
            .timeout(Duration.ofSeconds(30))
            .flatMap(report->ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control","no-store").bodyValue(report))
            .onErrorResume(java.util.concurrent.TimeoutException.class, error -> ServerResponse.status(503).contentType(MediaType.APPLICATION_JSON).header("Cache-Control","no-store").bodyValue(new co.com.compira.api.task.dto.TaskErrorResponse("REPORT_TIMEOUT","La consulta superó el tiempo disponible. Reduzca el periodo y vuelva a intentar")))
            .onErrorResume(errors::handle);
    }
    private ExecutiveReport.Filter filters(ServerRequest r) {
        if(r.queryParams().entrySet().stream().anyMatch(e->!PARAMETERS.contains(e.getKey()) || e.getValue().size()!=1))
            throw new IllegalArgumentException("Parámetro desconocido o repetido en el informe");
        String unassigned=r.queryParam("unassigned").orElse("false");
        if(!unassigned.equals("true") && !unassigned.equals("false")) throw new IllegalArgumentException("unassigned debe ser true o false");
        try {
            return new ExecutiveReport.Filter(r.queryParam("from").map(LocalDate::parse).orElse(null),r.queryParam("to").map(LocalDate::parse).orElse(null),
                ExecutiveReport.DateBasis.valueOf(r.queryParam("dateBasis").orElse("CREATED")),
                r.queryParam("teamId").map(UUID::fromString).orElse(null),r.queryParam("responsibleId").map(UUID::fromString).orElse(null),
                Boolean.parseBoolean(unassigned),r.queryParam("status").map(TaskStatus::valueOf).orElse(null));
        } catch(java.time.DateTimeException | IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Filtros inválidos. Revise fechas ISO, rango, identificadores, base de fecha y estado",invalid);
        }
    }
}

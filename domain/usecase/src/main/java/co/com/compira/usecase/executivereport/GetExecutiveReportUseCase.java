package co.com.compira.usecase.executivereport;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.organization.gateways.OrganizationSettingsGateway;
import co.com.compira.model.report.ExecutiveReport;
import co.com.compira.model.report.ExecutiveReport.*;
import co.com.compira.model.report.ReportTask;
import co.com.compira.model.report.gateways.ExecutiveReportGateway;
import co.com.compira.model.task.*;
import co.com.compira.model.task.gateways.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GetExecutiveReportUseCase {
    public static final int MAX_TASKS=5000;
    private final ExecutiveReportGateway reports;
    private final TaskUserDirectoryGateway users;
    private final OrganizationSettingsGateway settings;
    private final TaskClockGateway clock;
    public GetExecutiveReportUseCase(ExecutiveReportGateway reports, TaskUserDirectoryGateway users,
            OrganizationSettingsGateway settings, TaskClockGateway clock) {
        this.reports=reports; this.users=users; this.settings=settings; this.clock=clock;
    }
    public Mono<ExecutiveReport> execute(String actorEmail, Filter filter) {
        return new TaskAuthorization(users).requireActor(actorEmail).flatMap(actor -> {
            boolean admin=actor.hasRole(RoleCode.ADMINISTRATOR.name());
            if (!admin && !actor.hasRole(RoleCode.COORDINATOR.name())) return Mono.error(new CompiraException(
                    "REPORT_FORBIDDEN","No tiene permiso para generar reportes",ErrorCategory.FORBIDDEN));
            return settings.get().flatMap(config -> {
                String zone=config.timeZone()==null ? "UTC" : config.timeZone();
                ZoneId.of(zone);
                OffsetDateTime now=clock.now();
                return reports.find(admin ? null : actor.id(),filter,zone,now,MAX_TASKS+1).take(MAX_TASKS+1L).collectList()
                    // Entropía UUID y cálculo acotado del informe fuera del hilo de red.
                    .publishOn(Schedulers.boundedElastic())
                    .map(rows -> {
                        if (rows.size()>MAX_TASKS) throw new CompiraException("REPORT_LIMIT_EXCEEDED",
                            "El informe supera 5000 tareas. Reduzca el periodo o seleccione equipo y responsable; no se exportaron datos parciales",ErrorCategory.BAD_REQUEST);
                        return build(actor,admin,filter,zone,now,rows);
                    });
            });
        });
    }
    private ExecutiveReport build(TaskUser actor, boolean admin, Filter filter, String zone, OffsetDateTime now, List<ReportTask> rows) {
        List<Item> items=rows.stream().map(row -> item(row,now)).toList();
        Metrics summary=metrics(items,now);
        Quality quality=new Quality(items.stream().filter(t->t.dueDate()==null).count(),
                items.stream().filter(t->t.teamId()==null).count(),
                items.stream().filter(t->t.status()==TaskStatus.CLOSED && t.closedAt()==null).count(),
                items.stream().filter(t->t.status()==TaskStatus.CLOSED && t.closedAt()!=null && t.closureHours()==null).count());
        List<String> findings=new ArrayList<>();
        findings.add("El corte contiene " + summary.total() + " tareas; " + summary.active() + " activas y " + summary.closed() + " cerradas.");
        findings.add(summary.overdue()+" tareas activas superaron el vencimiento y "+summary.dueSoon()+" vencen en las próximas 24 horas.");
        findings.add(summary.unassigned()+" tareas no tienen responsable; están incluidas en los totales y en el grupo Sin responsable.");
        findings.add("Puntualidad calculada sobre "+summary.evaluableClosed()+" cierres con historial y fecha límite verificables, de "+summary.closed()+" cierres totales.");
        List<String> recommendations=new ArrayList<>();
        if(summary.overdue()>0) recommendations.add("Priorizar las tareas vencidas del detalle y acordar acciones con sus responsables y coordinadores.");
        if(summary.dueSoon()>0) recommendations.add("Revisar capacidad y compromisos de las tareas que vencen en las próximas 24 horas.");
        if(items.stream().anyMatch(t->t.responsibleId()==null && t.status().isActive())) recommendations.add("Asignar responsable a las tareas activas que aún no lo tienen.");
        if(summary.completed()>0) recommendations.add("Revisar las tareas completadas pendientes de aprobación; completar no equivale a cerrar.");
        if(quality.missingDueDate()+quality.missingTeam()+quality.closedWithoutHistory()+quality.invalidClosureDates()>0)
            recommendations.add("Depurar los datos incompletos indicados en calidad antes de comparar resultados o tomar decisiones de desempeño.");
        if(recommendations.isEmpty()) recommendations.add("Mantener el seguimiento periódico. La ausencia de alertas no acredita calidad de entregables ni utilización de capacidad.");
        List<Distribution> distribution=Arrays.stream(TaskStatus.values()).map(status -> new Distribution(status,items.stream().filter(t->t.status()==status).count())).toList();
        Map<String,long[]> months=new TreeMap<>();
        for(Item t:items) {
            if(t.createdAt()!=null) months.computeIfAbsent(YearMonth.from(t.createdAt().atZoneSameInstant(ZoneId.of(zone))).toString(), k->new long[2])[0]++;
            if(t.status()==TaskStatus.CLOSED && t.closureHours()!=null) months.computeIfAbsent(YearMonth.from(t.closedAt().atZoneSameInstant(ZoneId.of(zone))).toString(), k->new long[2])[1]++;
        }
        List<ExecutiveReport.Month> cohorts=months.entrySet().stream().map(e->new ExecutiveReport.Month(e.getKey(),e.getValue()[0],e.getValue()[1])).toList();
        return new ExecutiveReport(UUID.randomUUID(),"1.0",now,zone,admin ? "ORGANIZATION" : "COORDINATED_TEAMS",
                (Objects.toString(actor.firstName(),"")+" "+Objects.toString(actor.lastName(),"")).trim(),filter,summary,quality,
                groups(items,true,now),groups(items,false,now),distribution,cohorts,List.copyOf(findings),List.copyOf(recommendations),items);
    }
    private Item item(ReportTask row, OffsetDateTime now) {
        Task t=row.task(); TaskStatus status=t.isOverdue(now) ? TaskStatus.DELAYED : t.status();
        Double closure=TaskReportingPolicy.hasValidClosure(t) ? TaskReportingPolicy.closureHours(t) : null;
        return new Item(t.id(),t.title(),t.teamId(),t.teamName(),t.responsibleUserId(),row.responsibleName(),row.responsibleEmail(),
                status,t.createdAt(),t.dueDate(),t.closedAt(),row.lastActivityAt(),row.observations(),
                t.isOverdue(now) ? hours(t.dueDate(),now) : null,
                status.isActive() && t.createdAt()!=null && !t.createdAt().isAfter(now) ? hours(t.createdAt(),now) : null,
                closure, TaskReportingPolicy.isEvaluable(t) ? TaskReportingPolicy.isOnTime(t) : null);
    }
    private List<Group> groups(List<Item> items, boolean team, OffsetDateTime now) {
        Map<String,List<Item>> grouped=items.stream().collect(Collectors.groupingBy(t->Objects.toString(team?t.teamId():t.responsibleId(),"UNASSIGNED")));
        return grouped.values().stream().map(list -> {
            Item t=list.get(0); UUID id=team?t.teamId():t.responsibleId();
            String name=id==null ? (team?"Sin equipo":"Sin responsable") : team?t.teamName():t.responsibleName();
            return new Group(id,name==null ? "Nombre no disponible" : name,team?null:t.responsibleEmail(),metrics(list,now));
        }).sorted(Comparator.comparingLong((Group g)->g.metrics().overdue()).reversed()
                .thenComparing(g->g.name(),String.CASE_INSENSITIVE_ORDER).thenComparing(g->Objects.toString(g.id(),""))).toList();
    }
    private Metrics metrics(List<Item> items, OffsetDateTime now) {
        Function<TaskStatus,Long> count=status->items.stream().filter(t->t.status()==status).count();
        long total=items.size(),closed=count.apply(TaskStatus.CLOSED),cancelled=count.apply(TaskStatus.CANCELLED);
        long evaluable=items.stream().filter(t->t.closedOnTime()!=null).count(),onTime=items.stream().filter(t->Boolean.TRUE.equals(t.closedOnTime())).count();
        List<Double> closure=items.stream().map(Item::closureHours).filter(Objects::nonNull).sorted().toList();
        List<Double> ages=items.stream().map(Item::ageHours).filter(Objects::nonNull).toList();
        List<Double> delays=items.stream().map(Item::overdueHours).filter(Objects::nonNull).toList();
        long dueSoon=items.stream().filter(t->t.status().isActive() && t.dueDate()!=null && t.dueDate().isAfter(now) && !t.dueDate().isAfter(now.plusHours(24))).count();
        return new Metrics(total,items.stream().filter(t->t.status().isActive()).count(),count.apply(TaskStatus.PENDING),
            count.apply(TaskStatus.IN_PROGRESS),count.apply(TaskStatus.COMPLETED),closed,cancelled,count.apply(TaskStatus.DELAYED),dueSoon,
            items.stream().filter(t->t.responsibleId()==null).count(),evaluable,onTime,
            percentage(count.apply(TaskStatus.COMPLETED)+closed,total-cancelled),percentage(onTime,evaluable),average(closure),median(closure),average(ages),maximum(ages),maximum(delays));
    }
    private static Integer percentage(long numerator,long denominator) { return denominator==0?null:(int)Math.round(100.0*numerator/denominator); }
    private static Double average(List<Double> values) { return values.isEmpty()?null:round(values.stream().mapToDouble(Double::doubleValue).average().orElseThrow()); }
    private static Double maximum(List<Double> values) { return values.isEmpty()?null:round(values.stream().mapToDouble(Double::doubleValue).max().orElseThrow()); }
    private static Double median(List<Double> values) { if(values.isEmpty())return null;int m=values.size()/2;return round(values.size()%2==0?(values.get(m-1)+values.get(m))/2:values.get(m)); }
    private static double hours(OffsetDateTime start,OffsetDateTime end) { return Duration.between(start,end).toMillis()/3600000.0; }
    private static double round(double value) { return Math.round(value*10.0)/10.0; }
}

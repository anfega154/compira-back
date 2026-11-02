package co.com.compira.usecase.executivereport;

import co.com.compira.model.report.*;
import co.com.compira.model.report.ExecutiveReport.*;
import co.com.compira.model.report.gateways.ExecutiveReportGateway;
import co.com.compira.model.organization.*;
import co.com.compira.model.organization.gateways.*;
import co.com.compira.model.task.*;
import co.com.compira.model.task.gateways.*;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.*;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GetExecutiveReportUseCaseTest {
    final ExecutiveReportGateway gateway=mock(ExecutiveReportGateway.class);
    final TaskUserDirectoryGateway users=mock(TaskUserDirectoryGateway.class);
    final OrganizationSettingsGateway settings=mock(OrganizationSettingsGateway.class);
    final OffsetDateTime now=OffsetDateTime.parse("2026-11-01T20:00:00Z");
    final GetExecutiveReportUseCase usecase=new GetExecutiveReportUseCase(gateway,users,settings,()->now);
    final Filter all=new Filter(null,null,null,null,null,false,null);
    final UUID team=UUID.fromString("00000000-0000-0000-0000-000000000001");
    @BeforeEach void setup() {
        when(users.findByEmail(anyString())).thenReturn(Mono.just(TaskTestData.administrator()));
        when(settings.get()).thenReturn(Mono.just(new OrganizationSettings("America/Bogota",true)));
        when(gateway.find(any(),any(),anyString(),any(),anyInt())).thenReturn(Flux.empty());
    }
    ReportTask task(TaskStatus status,OffsetDateTime due,OffsetDateTime closed,UUID responsible) {
        return new ReportTask(new Task(UUID.randomUUID(),"Revisión gerencial ñá",null,due,status,responsible,
            TaskTestData.COORDINATOR_ID,now.minusHours(48),now.plusHours(5),team,"Operaciones",closed),"Ana García","ana@example.test",now,2);
    }
    void rows(ReportTask... tasks) { when(gateway.find(any(),any(),anyString(),any(),anyInt())).thenReturn(Flux.fromArray(tasks)); }
    ExecutiveReport generate() { return usecase.execute("admin@example.test",all).block(); }
    @Test void buildsReconciledSummaryIncludingUnassignedAndRealClosures() {
        rows(task(TaskStatus.CLOSED,now,now.minusHours(4),TaskTestData.COLLABORATOR_ID),
             task(TaskStatus.CLOSED,now.minusHours(10),now.minusHours(2),TaskTestData.COLLABORATOR_ID),
             task(TaskStatus.CLOSED,now,null,null),task(TaskStatus.PENDING,now.plusHours(24),null,null),
             task(TaskStatus.IN_PROGRESS,now,null,TaskTestData.COLLABORATOR_ID),
             task(TaskStatus.COMPLETED,now.minusDays(1),null,TaskTestData.COLLABORATOR_ID),
             task(TaskStatus.CANCELLED,now.minusDays(1),null,null));
        var r=generate(); var s=r.summary();
        assertEquals(7,s.total());assertEquals(2,s.active());assertEquals(3,s.closed());assertEquals(2,s.evaluableClosed());
        assertEquals(1,s.closedOnTime());assertEquals(50,s.compliancePercentage());assertEquals(67,s.progressPercentage());
        assertEquals(1,s.overdue());assertEquals(1,s.dueSoon());assertEquals(3,s.unassigned());
        assertEquals(45.0,s.averageClosureHours());assertEquals(45.0,s.medianClosureHours());
        assertEquals(1,r.quality().closedWithoutHistory());assertEquals(7,r.assignees().stream().mapToLong(g->g.metrics().total()).sum());
        assertEquals(7,r.teams().stream().mapToLong(g->g.metrics().total()).sum());
        assertTrue(r.findings().size()>=4);assertTrue(r.recommendations().size()>=4);
        assertEquals("ORGANIZATION",r.scope());assertNotNull(r.reportId());
        verify(gateway).find(isNull(),eq(all),eq("America/Bogota"),eq(now),eq(5001));
    }
    @Test void coordinatorUsesCurrentTeamScopeNotCreator() {
        when(users.findByEmail(anyString())).thenReturn(Mono.just(TaskTestData.coordinator()));
        assertEquals("COORDINATED_TEAMS",generate().scope());
        verify(gateway).find(eq(TaskTestData.COORDINATOR_ID),eq(all),anyString(),eq(now),eq(5001));
    }
    @Test void collaboratorCannotGenerate() {
        when(users.findByEmail(anyString())).thenReturn(Mono.just(TaskTestData.collaborator()));
        StepVerifier.create(usecase.execute("member",all)).expectErrorMatches(e->e instanceof CompiraException c&&c.getCode().equals("REPORT_FORBIDDEN")).verify();
        verifyNoInteractions(gateway,settings);
    }
    @Test void noDataDoesNotMeanZeroPercentOrZeroDuration() {
        var r=generate();assertEquals(0,r.summary().total());assertNull(r.summary().progressPercentage());
        assertNull(r.summary().compliancePercentage());assertNull(r.summary().averageClosureHours());assertNull(r.summary().medianClosureHours());
        assertNull(r.summary().maximumOverdueHours());assertTrue(r.teams().isEmpty());assertTrue(r.tasks().isEmpty());
        assertEquals(1,r.recommendations().size());
    }
    @Test void allCancelledHasNoProgressDenominator() {
        rows(task(TaskStatus.CANCELLED,null,null,null));var r=generate();assertNull(r.summary().progressPercentage());
        assertEquals(1,r.quality().missingDueDate());assertNull(r.tasks().get(0).ageHours());
    }
    @Test void validatesFiltersWithoutInventingDates() {
        assertThrows(IllegalArgumentException.class,()->new Filter(LocalDate.of(2026,2,2),LocalDate.of(2026,2,1),null,null,null,false,null));
        assertThrows(IllegalArgumentException.class,()->new Filter(null,null,null,null,UUID.randomUUID(),true,null));
        assertThrows(IllegalArgumentException.class,()->new Filter(LocalDate.of(1800,1,1),null,null,null,null,false,null));
        assertEquals(DateBasis.CREATED,all.dateBasis());
    }
    @Test void refusesOversizedReportsWithoutPartialData() {
        var item=task(TaskStatus.PENDING,null,null,null);
        when(gateway.find(any(),any(),anyString(),any(),anyInt())).thenReturn(Flux.range(0,5001).map(i->item));
        StepVerifier.create(usecase.execute("admin",all)).expectErrorMatches(e->e instanceof CompiraException c && c.getCode().equals("REPORT_LIMIT_EXCEEDED")).verify();
    }
    @Test void doesNotUseUpdatedAtWhenHistoryMissingOrInvalid() {
        rows(task(TaskStatus.CLOSED,now,now.minusDays(10),TaskTestData.COLLABORATOR_ID),task(TaskStatus.CLOSED,now,null,null));
        var r=generate();assertEquals(1,r.quality().invalidClosureDates());assertEquals(1,r.quality().closedWithoutHistory());
        assertEquals(0,r.summary().evaluableClosed());assertNull(r.summary().compliancePercentage());assertNull(r.summary().averageClosureHours());
    }
    @Test void closureWithoutDeadlineHasDurationButNotTimeliness() {
        rows(task(TaskStatus.CLOSED,null,now.minusHours(2),TaskTestData.COLLABORATOR_ID));var r=generate();
        assertEquals(46.0,r.summary().averageClosureHours());assertNull(r.summary().compliancePercentage());assertNull(r.tasks().get(0).closedOnTime());
    }
    @Test void dueSoonIncludes24HoursButExcludesLaterAndCompleted() {
        rows(task(TaskStatus.PENDING,now.plusHours(24),null,null),task(TaskStatus.PENDING,now.plusHours(24).plusSeconds(1),null,null),
             task(TaskStatus.COMPLETED,now.plusHours(1),null,null));assertEquals(1,generate().summary().dueSoon());
    }
    @Test void groupsAndMonthsAreDeterministicAndUseOrganizationZone() {
        var rtask=task(TaskStatus.CLOSED,now,now.minusHours(19),null); // 01:00UTC = octubre31 Bogotá
        rows(rtask);var r=generate();assertEquals("2026-10",r.monthlyCohorts().get(0).month());
        assertEquals("Sin responsable",r.assignees().get(0).name());assertEquals(1,r.monthlyCohorts().get(0).closed());
    }
    @Test void nullZoneFallsBackToUtc() {
        when(settings.get()).thenReturn(Mono.just(new OrganizationSettings(null,false)));assertEquals("UTC",generate().timeZone());
    }
    @Test void noTeamAndProfileHaveExplicitLabelsAndMedianIsExact() {
        var source=task(TaskStatus.CLOSED,now,now.minusHours(1),null).task();
        var noTeam=new Task(source.id(),source.title(),null,source.dueDate(),source.status(),source.responsibleUserId(),source.createdByUserId(),source.createdAt(),source.updatedAt(),null,null,source.closedAt());
        rows(new ReportTask(noTeam,null,null,null,0));var r=generate();assertEquals("Sin equipo",r.teams().get(0).name());
        assertEquals(47.0,r.summary().medianClosureHours());assertEquals(1,r.quality().missingTeam());
    }
    @Test void absentActorIsRejectedBeforeRepository() {
        when(users.findByEmail(anyString())).thenReturn(Mono.empty());StepVerifier.create(usecase.execute("absent",all)).expectError().verify();verifyNoInteractions(gateway);
    }
}

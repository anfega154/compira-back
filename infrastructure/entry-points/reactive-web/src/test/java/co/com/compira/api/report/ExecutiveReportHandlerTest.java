package co.com.compira.api.report;

import co.com.compira.api.router.ExecutiveReportRouter;
import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.model.report.ExecutiveReport;
import co.com.compira.model.common.error.*;
import co.com.compira.usecase.executivereport.GetExecutiveReportUseCase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExecutiveReportHandlerTest {
 final GetExecutiveReportUseCase reports=mock(GetExecutiveReportUseCase.class);
 WebTestClient client;
 @BeforeEach void setup() {
  client=WebTestClient.bindToRouterFunction(new ExecutiveReportRouter().executiveReportRoutes(new ExecutiveReportHandler(reports,new TaskErrorHandler())))
    .webFilter((exchange,chain)->chain.filter(exchange.mutate().principal(Mono.just((java.security.Principal)()->"local-actor@example.test")).build())).build();
  when(reports.execute(anyString(),any())).thenAnswer(call->Mono.just(new ExecutiveReport(UUID.fromString("50000000-0000-0000-0000-000000000001"),"1.0",OffsetDateTime.parse("2026-11-01T12:00:00Z"),"America/Bogota","COORDINATED_TEAMS","Ana García",call.getArgument(1),null,null,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of())));
 }
 @Test void acceptsDefaultsAndUsesPrincipalInsteadOfSpoofedIdentity() {
  client.get().uri("/api/v1/tasks/reports/executive").header("X-Actor-Email","fake-admin@example.test").exchange().expectStatus().isOk()
    .expectHeader().valueEquals("Cache-Control","no-store").expectBody().jsonPath("$.filters.dateBasis").isEqualTo("CREATED").jsonPath("$.scope").isEqualTo("COORDINATED_TEAMS");
  verify(reports).execute(eq("local-actor@example.test"),any());
 }
 @Test void acceptsFullFiltersWithoutInterpretingForeignIdentifiersAsAuthorization() {
  client.get().uri("/api/v1/tasks/reports/executive?from=2026-10-01&to=2026-10-31&dateBasis=CLOSED&teamId=10000000-0000-0000-0000-000000000001&responsibleId=10000000-0000-0000-0000-000000000002&status=CLOSED")
   .exchange().expectStatus().isOk().expectBody().jsonPath("$.filters.status").isEqualTo("CLOSED").jsonPath("$.filters.to").isEqualTo("2026-10-31");
 }
 @Test void acceptsExplicitUnassigned() {
  client.get().uri("/api/v1/tasks/reports/executive?unassigned=true&dateBasis=DUE").exchange().expectStatus().isOk().expectBody().jsonPath("$.filters.unassigned").isEqualTo(true);
 }
 @ParameterizedTest @ValueSource(strings={"from=invalid","to=2026-02-30","dateBasis=INVALID","teamId=not-a-uuid","responsibleId=no","status=unknown","unassigned=yes","unknown=1","from=2026-10-01&from=2026-10-02","from=2026-11-02&to=2026-11-01","unassigned=true&responsibleId=10000000-0000-0000-0000-000000000001"})
 void rejectsInvalidRepeatedOrUnknownParameters(String query) {
  client.get().uri("/api/v1/tasks/reports/executive?"+query).exchange().expectStatus().isBadRequest();verifyNoInteractions(reports);
 }
 @Test void handlesLimitWithoutReturningPartialData() {
  when(reports.execute(anyString(),any())).thenReturn(Mono.error(new CompiraException("REPORT_LIMIT_EXCEEDED","Reduzca el periodo",ErrorCategory.BAD_REQUEST)));
  client.get().uri("/api/v1/tasks/reports/executive").exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("REPORT_LIMIT_EXCEEDED");
 }
 @Test void handlesTimeoutAsRetryableServiceError() {
  when(reports.execute(anyString(),any())).thenReturn(Mono.error(new java.util.concurrent.TimeoutException()));
  client.get().uri("/api/v1/tasks/reports/executive").exchange().expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("REPORT_TIMEOUT");
 }
}

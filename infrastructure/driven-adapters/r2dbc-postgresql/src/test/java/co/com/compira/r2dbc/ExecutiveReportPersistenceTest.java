package co.com.compira.r2dbc;

import co.com.compira.model.report.ExecutiveReport.*;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.r2dbc.mapper.TaskDataMapper;
import io.r2dbc.spi.ConnectionFactories;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ExecutiveReportPersistenceTest {
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17-alpine").withEnv("POSTGRES_HOST_AUTH_METHOD","trust");
 static DatabaseClient db;
 static final UUID C1=UUID.fromString("10000000-0000-0000-0000-000000000001"), C2=UUID.fromString("10000000-0000-0000-0000-000000000002");
 static final UUID T1=UUID.fromString("20000000-0000-0000-0000-000000000001"), T2=UUID.fromString("20000000-0000-0000-0000-000000000002");
 static final OffsetDateTime NOW=OffsetDateTime.parse("2026-11-02T01:00:00Z");
 ExecutiveReportAdapter adapter;
 @BeforeAll static void setup() throws Exception {
  var ds=new PGSimpleDataSource();ds.setURL(PG.getJdbcUrl());ds.setUser(PG.getUsername());ds.setPassword(PG.getPassword());
  var lb=new SpringLiquibase();lb.setDataSource(ds);lb.setChangeLog("classpath:db/db.changelog-master.yaml");lb.afterPropertiesSet();
  db=DatabaseClient.create(ConnectionFactories.get("r2dbc:postgresql://"+PG.getUsername()+":"+PG.getPassword()+"@"+PG.getHost()+":"+PG.getFirstMappedPort()+"/"+PG.getDatabaseName()));
 }
 @BeforeEach void seed() {
  adapter=new ExecutiveReportAdapter(db,new TaskDataMapper());
  db.sql("TRUNCATE task_notifications,task_history,task_observations,task_teams,team_members,teams,tasks,user_roles,users CASCADE").then().block();
  db.sql("""
   INSERT INTO users(id,cognito_sub,email,first_name,last_name,phone_number,status,preferred_mfa_channel) VALUES
   ('10000000-0000-0000-0000-000000000001','c1','c1@example.test','Ana','García','+573001112233','ACTIVE','EMAIL'),
   ('10000000-0000-0000-0000-000000000002','c2','c2@example.test','Luis','Pérez','+573001112234','ACTIVE','EMAIL');
   INSERT INTO teams(id,name,coordinator_user_id) VALUES
   ('20000000-0000-0000-0000-000000000001','Equipo A','10000000-0000-0000-0000-000000000001'),
   ('20000000-0000-0000-0000-000000000002','Equipo B','10000000-0000-0000-0000-000000000002');
   INSERT INTO tasks(id,title,description,status,created_by_user_id,responsible_user_id,created_at,updated_at,due_date) VALUES
   ('30000000-0000-0000-0000-000000000001','Cierre con observación posterior','Detalle','CLOSED','10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001','2026-11-01 12:00Z','2026-11-01 19:00Z','2026-11-01 15:00Z'),
   ('30000000-0000-0000-0000-000000000002','Sin responsable','Detalle','PENDING','10000000-0000-0000-0000-000000000001',NULL,'2026-11-02 00:30Z','2026-11-02 00:30Z','2026-11-02 01:00Z'),
   ('30000000-0000-0000-0000-000000000003','Equipo ajeno','Detalle','PENDING','10000000-0000-0000-0000-000000000001',NULL,'2026-10-01 12:00Z','2026-10-01 12:00Z','2026-12-01 12:00Z');
   INSERT INTO task_teams(task_id,team_id) VALUES
   ('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001'),
   ('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000001'),
   ('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000002');
   INSERT INTO task_history(task_id,event,created_at) VALUES
   ('30000000-0000-0000-0000-000000000001','CLOSED','2026-11-01 14:00Z'),
   ('30000000-0000-0000-0000-000000000001','OBSERVATION_ADDED','2026-11-01 20:00Z');
   INSERT INTO task_observations(task_id,author_user_id,content) VALUES
   ('30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001','No incluir el contenido privado en el reporte');
   """).then().block();
 }
 Filter filter(LocalDate from,LocalDate to,DateBasis basis,UUID team,UUID responsible,boolean unassigned,TaskStatus status) { return new Filter(from,to,basis,team,responsible,unassigned,status); }
 @Test void scopeUsesCurrentCoordinatorInsteadOfCreatorAndDoesNotDuplicate() {
  var rows=adapter.find(C1,filter(null,null,null,null,null,false,null),"America/Bogota",NOW,5001).collectList().block();
  assertEquals(2,rows.size());assertTrue(rows.stream().allMatch(r->r.task().teamId().equals(T1)));
  assertEquals(3,adapter.find(null,filter(null,null,null,null,null,false,null),"UTC",NOW,5001).count().block());
 }
 @Test void closureAndActivityAreDifferentAndLegacyTaskReadsAlsoUseHistory() {
  var row=adapter.find(C1,filter(null,null,null,null,C1,false,null),"UTC",NOW,5001).blockFirst();
  assertEquals(OffsetDateTime.parse("2026-11-01T14:00Z").toInstant(),row.task().closedAt().toInstant());
  assertEquals(OffsetDateTime.parse("2026-11-01T20:00Z").toInstant(),row.lastActivityAt().toInstant());assertEquals(1,row.observations());assertEquals("Ana García",row.responsibleName());
  assertEquals(row.task().closedAt(),new TaskRepositoryAdapter(db,new TaskDataMapper()).findById(row.task().id()).block().closedAt());
 }
 @Test void localDateRangeIsInclusiveAndUsesZoneNotUtcDay() {
  var f=filter(LocalDate.of(2026,11,1),LocalDate.of(2026,11,1),DateBasis.CREATED,null,null,false,null);
  assertEquals(2,adapter.find(C1,f,"America/Bogota",NOW,5001).count().block());
  assertEquals(1,adapter.find(C1,f,"UTC",NOW,5001).count().block());
 }
 @Test void filterAppliesBeforeLimitAndForeignTeamCannotWidenScope() {
  assertEquals(0,adapter.find(C1,filter(null,null,null,T2,null,false,null),"UTC",NOW,5001).count().block());
  var row=adapter.find(null,filter(null,null,null,T2,null,false,null),"UTC",NOW,1).blockFirst();assertEquals(T2,row.task().teamId());
 }
 @Test void dueAndClosureBasesAndDerivedStatusAreFilteredInSql() {
  assertEquals(1,adapter.find(C1,filter(null,null,null,null,null,true,TaskStatus.DELAYED),"UTC",NOW,5001).count().block());
  assertEquals(0,adapter.find(C1,filter(null,null,null,null,null,true,TaskStatus.PENDING),"UTC",NOW,5001).count().block());
  assertEquals(1,adapter.find(null,filter(LocalDate.of(2026,11,1),LocalDate.of(2026,11,1),DateBasis.CLOSED,null,null,false,null),"UTC",NOW,5001).count().block());
  assertEquals(1,adapter.find(null,filter(LocalDate.of(2026,12,1),null,DateBasis.DUE,null,null,false,null),"UTC",NOW,5001).count().block());
 }
}

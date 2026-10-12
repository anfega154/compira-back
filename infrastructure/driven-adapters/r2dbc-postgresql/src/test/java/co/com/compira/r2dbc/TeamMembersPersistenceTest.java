package co.com.compira.r2dbc;

import co.com.compira.model.team.TeamMember;
import co.com.compira.r2dbc.mapper.TeamDataMapper;
import io.r2dbc.spi.ConnectionFactories;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Spec HU-12 Parte A (T2): consulta de colaboradores activos de un equipo contra PostgreSQL real.
 */
@Testcontainers
class TeamMembersPersistenceTest {
    private static final UUID TEAM_A = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID TEAM_B = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID EMPTY_TEAM = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID COORDINATOR = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ANA_ZETA = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID ANA_ALFA = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID NO_NAME = UUID.fromString("10000000-0000-4000-8000-000000000004");
    private static final UUID DISABLED = UUID.fromString("10000000-0000-4000-8000-000000000005");
    private static final UUID NOT_COLLABORATOR = UUID.fromString("10000000-0000-4000-8000-000000000006");
    private static final UUID OTHER_TEAM = UUID.fromString("10000000-0000-4000-8000-000000000007");
    private static final String FIXTURES = """
            INSERT INTO users (id, cognito_sub, email, first_name, last_name, phone_number, preferred_mfa_channel, status) VALUES
              ('10000000-0000-4000-8000-000000000001', 'coord', 'coord@compira.co', 'Coord', 'Uno', '+573001110001', 'EMAIL', 'ACTIVE'),
              ('10000000-0000-4000-8000-000000000002', 'ana-zeta', 'ana.zeta@compira.co', 'Ana', 'Zeta', '+573001110002', 'EMAIL', 'ACTIVE'),
              ('10000000-0000-4000-8000-000000000003', 'ana-alfa', 'ana.alfa@compira.co', 'Ana', 'Alfa', '+573001110003', 'EMAIL', 'ACTIVE'),
              ('10000000-0000-4000-8000-000000000004', 'no-name', 'sin.nombre@compira.co', NULL, NULL, '+573001110004', 'EMAIL', 'ACTIVE'),
              ('10000000-0000-4000-8000-000000000005', 'disabled', 'inactivo@compira.co', 'Beto', 'Inactivo', '+573001110005', 'EMAIL', 'DISABLED'),
              ('10000000-0000-4000-8000-000000000006', 'not-collab', 'no.colab@compira.co', 'Carla', 'Coordinadora', '+573001110006', 'EMAIL', 'ACTIVE'),
              ('10000000-0000-4000-8000-000000000007', 'other-team', 'otro.equipo@compira.co', 'Dario', 'Otro', '+573001110007', 'EMAIL', 'ACTIVE');
            INSERT INTO user_roles (user_id, role_id)
              SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'COORDINATOR' WHERE u.id IN
                ('10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000006');
            INSERT INTO user_roles (user_id, role_id)
              SELECT u.id, r.id FROM users u JOIN roles r ON r.code = 'COLLABORATOR' WHERE u.id IN
                ('10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000003',
                 '10000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000005',
                 '10000000-0000-4000-8000-000000000007');
            INSERT INTO teams (id, name, coordinator_user_id) VALUES
              ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Equipo A', '10000000-0000-4000-8000-000000000001'),
              ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Equipo B', '10000000-0000-4000-8000-000000000001'),
              ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'Equipo vacio', '10000000-0000-4000-8000-000000000001');
            INSERT INTO team_members (user_id, team_id) VALUES
              ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
              ('10000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
              ('10000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
              ('10000000-0000-4000-8000-000000000005', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
              ('10000000-0000-4000-8000-000000000006', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
              ('10000000-0000-4000-8000-000000000007', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
            """;

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");
    private static DatabaseClient database;
    private TeamRepositoryAdapter teams;

    @BeforeAll
    static void migrate() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        var connectionFactory = ConnectionFactories.get("r2dbc:postgresql://" + POSTGRES.getUsername() + ":" + POSTGRES.getPassword()
                + "@" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + POSTGRES.getDatabaseName());
        database = DatabaseClient.create(connectionFactory);
    }

    @BeforeEach
    void seed() {
        teams = new TeamRepositoryAdapter(database, new TeamDataMapper());
        StepVerifier.create(database.sql("TRUNCATE task_notifications, task_history, task_observations, task_teams, team_members, teams, tasks, user_roles, users CASCADE")
                .then().then(database.sql(FIXTURES).then())).verifyComplete();
    }

    /**
     * Caso: equipo con colaboradores activos, un colaborador inactivo, un usuario sin rol de colaborador,
     * un colaborador de otro equipo y un colaborador sin nombre.
     * Esperado: solo se devuelven los colaboradores activos del equipo consultado, ordenados por nombre,
     * apellido y correo (los nombres nulos al final).
     * Resultado: Ana Alfa, Ana Zeta y el colaborador sin nombre, en ese orden.
     */
    @Test
    @DisplayName("Devuelve solo colaboradores activos del equipo, ordenados por nombre, apellido y correo")
    void returnsOnlyActiveCollaboratorsOfTheTeamInOrder() {
        StepVerifier.create(teams.findActiveCollaborators(TEAM_A).map(TeamMember::id).collectList())
                .assertNext(ids -> assertEquals(java.util.List.of(ANA_ALFA, ANA_ZETA, NO_NAME), ids))
                .verifyComplete();
    }

    /**
     * Caso: se consulta un equipo cuyos miembros son distintos a los de otro equipo.
     * Esperado: no se mezclan miembros de equipos distintos.
     * Resultado: el equipo B devuelve únicamente a su colaborador.
     */
    @Test
    @DisplayName("No mezcla miembros de otros equipos")
    void doesNotMixMembersOfOtherTeams() {
        StepVerifier.create(teams.findActiveCollaborators(TEAM_B).map(TeamMember::id))
                .expectNext(OTHER_TEAM)
                .verifyComplete();
    }

    /**
     * Caso: equipo sin miembros.
     * Esperado: flujo vacío.
     * Resultado: sin elementos.
     */
    @Test
    @DisplayName("Equipo sin miembros devuelve vacío")
    void returnsNothingForTeamWithoutMembers() {
        StepVerifier.create(teams.findActiveCollaborators(EMPTY_TEAM)).verifyComplete();
    }

    /**
     * Caso: equipo inexistente.
     * Esperado: flujo vacío (la validación de existencia es del caso de uso).
     * Resultado: sin elementos.
     */
    @Test
    @DisplayName("Equipo inexistente devuelve vacío")
    void returnsNothingForUnknownTeam() {
        StepVerifier.create(teams.findActiveCollaborators(UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd")))
                .verifyComplete();
    }

    /**
     * Caso: se mapea una fila con los cuatro campos del contrato.
     * Esperado: el mapeo conserva `id`, `email`, `firstName` y `lastName`, incluidos los nulos.
     * Resultado: el primer elemento trae los datos de Ana Alfa y el último conserva nombres nulos.
     */
    @Test
    @DisplayName("Mapea id, email, firstName y lastName (incluye nulos)")
    void mapsRowFieldsIncludingNulls() {
        StepVerifier.create(teams.findActiveCollaborators(TEAM_A).collectList())
                .assertNext(members -> {
                    assertEquals(new TeamMember(ANA_ALFA, "ana.alfa@compira.co", "Ana", "Alfa"), members.get(0));
                    assertEquals(new TeamMember(NO_NAME, "sin.nombre@compira.co", null, null), members.get(2));
                })
                .verifyComplete();
    }
}

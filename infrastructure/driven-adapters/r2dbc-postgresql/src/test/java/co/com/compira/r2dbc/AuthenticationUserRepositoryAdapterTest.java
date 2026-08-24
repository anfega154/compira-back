package co.com.compira.r2dbc;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.auth.UserStatus;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.r2dbc.mapper.ApplicationUserDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationUserRepositoryAdapterTest {
    private final DatabaseClient databaseClient = mock(DatabaseClient.class);
    private final TransactionalOperator transactionalOperator = mock(TransactionalOperator.class);
    private final ApplicationUserDataMapper mapper = new ApplicationUserDataMapper();
    private AuthenticationUserRepositoryAdapter adapter;

    private static final UUID USER_ID = UUID.fromString("fbb1401d-95a6-4f73-b5b8-a6d1d6f3c812");
    private static final String EMAIL = "john.doe@compira.co";
    private static final String COGNITO_SUB = "cognito-sub-123";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        adapter = new AuthenticationUserRepositoryAdapter(databaseClient, transactionalOperator, mapper);
        lenient().when(transactionalOperator.transactional(ArgumentMatchers.<Mono<ApplicationUser>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCreatePendingUser() {
        Map<String, Object> userRow = buildUserRow(UserStatus.PENDING_CONFIRMATION);
        GenericExecuteSpec upsertSpec = mockExecuteSpec(userRow);
        GenericExecuteSpec roleSpec = mockExecuteSpecRowsUpdated(1L);
        GenericExecuteSpec rolesQuerySpec = mockExecuteSpecFlux(List.of("COLLABORATOR"));

        when(databaseClient.sql(anyString()))
                .thenReturn(upsertSpec)
                .thenReturn(roleSpec)
                .thenReturn(rolesQuerySpec);

        RegisterUserCommand command = new RegisterUserCommand(
                EMAIL, "TempPass123!", "John", "Doe", "+573001112233", MfaChannel.EMAIL, RoleCode.COLLABORATOR);

        StepVerifier.create(adapter.createPendingUser(command, COGNITO_SUB))
                .assertNext(user -> {
                    assertNotNull(user);
                    assertEquals(EMAIL, user.user().email());
                    assertEquals(COGNITO_SUB, user.cognitoSub());
                    assertEquals(UserStatus.PENDING_CONFIRMATION, user.status());
                    assertEquals(List.of("COLLABORATOR"), user.roles());
                })
                .verifyComplete();
    }

    @Test
    void shouldFailCreatePendingUserWhenUpsertReturnsEmpty() {
        GenericExecuteSpec emptySpec = mockExecuteSpecEmpty();

        when(databaseClient.sql(anyString())).thenReturn(emptySpec);

        RegisterUserCommand command = new RegisterUserCommand(
                EMAIL, "TempPass123!", "John", "Doe", "+573001112233", MfaChannel.EMAIL, RoleCode.COLLABORATOR);

        StepVerifier.create(adapter.createPendingUser(command, COGNITO_SUB))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_011".equals(((CompiraException) error).getCode()))
                .verify();
    }

    @Test
    void shouldFindByEmail() {
        Map<String, Object> userRow = buildUserRow(UserStatus.ACTIVE);
        GenericExecuteSpec selectSpec = mockExecuteSpec(userRow);
        GenericExecuteSpec rolesQuerySpec = mockExecuteSpecFlux(List.of("COLLABORATOR"));

        when(databaseClient.sql(anyString()))
                .thenReturn(selectSpec)
                .thenReturn(rolesQuerySpec);

        StepVerifier.create(adapter.findByEmail(EMAIL))
                .assertNext(user -> {
                    assertEquals(EMAIL, user.user().email());
                    assertEquals(UserStatus.ACTIVE, user.status());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenUserNotFoundByEmail() {
        GenericExecuteSpec emptySpec = mockExecuteSpecEmpty();

        when(databaseClient.sql(anyString())).thenReturn(emptySpec);

        StepVerifier.create(adapter.findByEmail("unknown@compira.co"))
                .verifyComplete();
    }

    @Test
    void shouldActivateUser() {
        Map<String, Object> userRow = buildUserRow(UserStatus.ACTIVE);
        GenericExecuteSpec activateSpec = mockExecuteSpec(userRow);
        GenericExecuteSpec rolesQuerySpec = mockExecuteSpecFlux(List.of("COLLABORATOR"));

        when(databaseClient.sql(anyString()))
                .thenReturn(activateSpec)
                .thenReturn(rolesQuerySpec);

        StepVerifier.create(adapter.activateUser(EMAIL))
                .assertNext(user -> {
                    assertEquals(UserStatus.ACTIVE, user.status());
                    assertEquals(EMAIL, user.user().email());
                })
                .verifyComplete();
    }

    @Test
    void shouldFailActivateUserWhenNotFound() {
        GenericExecuteSpec emptySpec = mockExecuteSpecEmpty();

        when(databaseClient.sql(anyString())).thenReturn(emptySpec);

        StepVerifier.create(adapter.activateUser("unknown@compira.co"))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_010".equals(((CompiraException) error).getCode()))
                .verify();
    }

    @Test
    void shouldUpdateLastLogin() {
        Map<String, Object> userRow = buildUserRow(UserStatus.ACTIVE);
        userRow.put("last_login_at", NOW);
        GenericExecuteSpec updateSpec = mockExecuteSpec(userRow);
        GenericExecuteSpec rolesQuerySpec = mockExecuteSpecFlux(List.of("COORDINATOR"));

        when(databaseClient.sql(anyString()))
                .thenReturn(updateSpec)
                .thenReturn(rolesQuerySpec);

        StepVerifier.create(adapter.updateLastLogin(EMAIL))
                .assertNext(user -> {
                    assertEquals(EMAIL, user.user().email());
                    assertEquals(NOW, user.lastLoginAt());
                    assertEquals(List.of("COORDINATOR"), user.roles());
                })
                .verifyComplete();
    }

    @Test
    void shouldFailUpdateLastLoginWhenNotFound() {
        GenericExecuteSpec emptySpec = mockExecuteSpecEmpty();

        when(databaseClient.sql(anyString())).thenReturn(emptySpec);

        StepVerifier.create(adapter.updateLastLogin("unknown@compira.co"))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_010".equals(((CompiraException) error).getCode()))
                .verify();
    }

    @Test
    void shouldDeleteByEmail() {
        GenericExecuteSpec deleteSpec = mockExecuteSpecRowsUpdated(1L);

        when(databaseClient.sql(anyString())).thenReturn(deleteSpec);

        StepVerifier.create(adapter.deleteByEmail(EMAIL))
                .verifyComplete();
    }

    @Test
    void shouldFailDeleteByEmailWhenNotFound() {
        GenericExecuteSpec deleteSpec = mockExecuteSpecRowsUpdated(0L);

        when(databaseClient.sql(anyString())).thenReturn(deleteSpec);

        StepVerifier.create(adapter.deleteByEmail("unknown@compira.co"))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && "AUTH_010".equals(((CompiraException) error).getCode()))
                .verify();
    }

    @Test
    void shouldCreatePendingUserWithDefaultRoleWhenNull() {
        Map<String, Object> userRow = buildUserRow(UserStatus.PENDING_CONFIRMATION);
        GenericExecuteSpec upsertSpec = mockExecuteSpec(userRow);
        GenericExecuteSpec roleSpec = mockExecuteSpecRowsUpdated(1L);
        GenericExecuteSpec rolesQuerySpec = mockExecuteSpecFlux(List.of("COLLABORATOR"));

        when(databaseClient.sql(anyString()))
                .thenReturn(upsertSpec)
                .thenReturn(roleSpec)
                .thenReturn(rolesQuerySpec);

        RegisterUserCommand command = new RegisterUserCommand(
                EMAIL, "TempPass123!", "John", "Doe", "+573001112233", MfaChannel.EMAIL, null);

        StepVerifier.create(adapter.createPendingUser(command, COGNITO_SUB))
                .assertNext(user -> assertNotNull(user))
                .verifyComplete();
    }

    private Map<String, Object> buildUserRow(UserStatus status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", USER_ID);
        row.put("cognito_sub", COGNITO_SUB);
        row.put("email", EMAIL);
        row.put("first_name", "John");
        row.put("last_name", "Doe");
        row.put("phone_number", "+573001112233");
        row.put("preferred_mfa_channel", "EMAIL");
        row.put("status", status.name());
        row.put("created_at", NOW);
        row.put("updated_at", NOW);
        row.put("last_login_at", null);
        return row;
    }

    @SuppressWarnings("unchecked")
    private GenericExecuteSpec mockExecuteSpec(Map<String, Object> row) {
        GenericExecuteSpec spec = mock(GenericExecuteSpec.class);
        FetchSpec<Map<String, Object>> fetchSpec = mock(FetchSpec.class);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.one()).thenReturn(Mono.just(row));
        return spec;
    }

    @SuppressWarnings("unchecked")
    private GenericExecuteSpec mockExecuteSpecEmpty() {
        GenericExecuteSpec spec = mock(GenericExecuteSpec.class);
        FetchSpec<Map<String, Object>> fetchSpec = mock(FetchSpec.class);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.one()).thenReturn(Mono.empty());
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(0L));
        return spec;
    }

    @SuppressWarnings("unchecked")
    private GenericExecuteSpec mockExecuteSpecRowsUpdated(long count) {
        GenericExecuteSpec spec = mock(GenericExecuteSpec.class);
        FetchSpec<Map<String, Object>> fetchSpec = mock(FetchSpec.class);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(count));
        return spec;
    }

    @SuppressWarnings("unchecked")
    private GenericExecuteSpec mockExecuteSpecFlux(List<String> roles) {
        GenericExecuteSpec spec = mock(GenericExecuteSpec.class);
        org.springframework.r2dbc.core.RowsFetchSpec<String> rowsFetchSpec = mock(org.springframework.r2dbc.core.RowsFetchSpec.class);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(any(java.util.function.BiFunction.class))).thenReturn(rowsFetchSpec);
        when(rowsFetchSpec.all()).thenReturn(Flux.fromIterable(roles));
        return spec;
    }
}

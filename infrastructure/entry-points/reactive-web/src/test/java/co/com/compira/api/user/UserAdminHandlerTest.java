package co.com.compira.api.user;

import co.com.compira.api.config.SecurityTestData;
import co.com.compira.api.router.UserRouterRest;
import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.api.task.TaskRequestValidator;
import co.com.compira.model.user.OrganizationUser;
import co.com.compira.usecase.listusers.ListUsersUseCase;
import co.com.compira.usecase.updateuser.UpdateUserUseCase;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdminHandlerTest {
    private final ListUsersUseCase listUsers = mock(ListUsersUseCase.class);
    private final UpdateUserUseCase updateUser = mock(UpdateUserUseCase.class);
    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private WebTestClient client;

    private static OrganizationUser user(List<String> roles) {
        return new OrganizationUser(UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "collaborator@compira.co", "Colab", "Uno", "+573001112233", "ACTIVE", roles,
                UUID.fromString("55555555-5555-5555-5555-555555555555"), "Operaciones",
                OffsetDateTime.parse("2026-09-20T10:00:00Z"), OffsetDateTime.parse("2026-09-24T08:00:00Z"));
    }

    @BeforeEach
    void configure() {
        var errors = new TaskErrorHandler();
        var validator = new TaskRequestValidator(factory.getValidator());
        var transactions = mock(TransactionalOperator.class);
        when(transactions.transactional(org.mockito.ArgumentMatchers.<Mono<Object>>any())).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = new UserAdminHandler(listUsers, updateUser, new UserResponseMapper(), validator, errors, transactions);
        var principal = new JwtAuthenticationToken(SecurityTestData.token(), List.of(), SecurityTestData.EMAIL);
        client = WebTestClient.bindToRouterFunction(new UserRouterRest().userRouterFunction(handler))
                .webFilter((exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(principal)).build())).build();
    }

    @AfterEach
    void close() {
        factory.close();
    }

    @Test
    void listsOrganizationUsers() {
        when(listUsers.execute(SecurityTestData.EMAIL)).thenReturn(Flux.just(user(List.of("COLLABORATOR"))));
        client.get().uri(UserAdminHandler.BASE).exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].email").isEqualTo("collaborator@compira.co")
                .jsonPath("$[0].teamName").isEqualTo("Operaciones")
                .jsonPath("$[0].roles[0]").isEqualTo("COLLABORATOR");
    }

    @Test
    void updatesUserRoles() {
        when(updateUser.updateRoles(eq(SecurityTestData.EMAIL), eq("collaborator@compira.co"), any()))
                .thenReturn(Mono.just(user(List.of("COORDINATOR", "COLLABORATOR"))));
        client.put().uri(UserAdminHandler.ROLES).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"collaborator@compira.co\",\"roles\":[\"COORDINATOR\",\"COLLABORATOR\"]}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.roles[0]").isEqualTo("COORDINATOR");
    }

    @Test
    void rejectsUpdateWithoutRoles() {
        client.put().uri(UserAdminHandler.ROLES).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"collaborator@compira.co\",\"roles\":[]}")
                .exchange().expectStatus().isBadRequest();
        verify(updateUser, never()).updateRoles(anyString(), anyString(), any());
    }

    @Test
    void resetsPassword() {
        when(updateUser.resetPassword(eq(SecurityTestData.EMAIL), eq("collaborator@compira.co"), anyString()))
                .thenReturn(Mono.empty());
        client.post().uri(UserAdminHandler.PASSWORD_RESET).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"collaborator@compira.co\",\"temporaryPassword\":\"TempPass123*\"}")
                .exchange().expectStatus().isNoContent();
        verify(updateUser).resetPassword(SecurityTestData.EMAIL, "collaborator@compira.co", "TempPass123*");
    }

    @Test
    void rejectsResetWithShortPassword() {
        client.post().uri(UserAdminHandler.PASSWORD_RESET).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"collaborator@compira.co\",\"temporaryPassword\":\"short\"}")
                .exchange().expectStatus().isBadRequest();
        verify(updateUser, never()).resetPassword(anyString(), anyString(), anyString());
    }
}

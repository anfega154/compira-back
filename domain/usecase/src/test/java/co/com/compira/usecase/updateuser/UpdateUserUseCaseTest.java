package co.com.compira.usecase.updateuser;

import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.model.user.gateways.UserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import co.com.compira.usecase.user.UserAdminTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateUserUseCaseTest {
    private final UserDirectoryGateway userDirectoryGateway = mock(UserDirectoryGateway.class);
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final TaskUserDirectoryGateway userDirectory = mock(TaskUserDirectoryGateway.class);
    private final UpdateUserUseCase useCase = new UpdateUserUseCase(userDirectoryGateway, authenticationGateway, userDirectory);

    @Test
    void updatesRoleSetWhenActorIsAdministrator() {
        when(userDirectory.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.administrator()));
        when(userDirectoryGateway.findByEmail(UserAdminTestData.TARGET_EMAIL))
                .thenReturn(Mono.just(UserAdminTestData.organizationUser(List.of("COLLABORATOR"))));
        when(userDirectoryGateway.replaceRoles(eq(UserAdminTestData.TARGET_EMAIL), any()))
                .thenReturn(Mono.just(UserAdminTestData.organizationUser(List.of("COORDINATOR", "COLLABORATOR"))));

        StepVerifier.create(useCase.updateRoles(TaskTestData.COORDINATOR_EMAIL, UserAdminTestData.TARGET_EMAIL,
                        List.of("COORDINATOR", "COLLABORATOR")))
                .assertNext(user -> {
                    if (!user.roles().contains("COORDINATOR") || !user.roles().contains("COLLABORATOR")) {
                        throw new AssertionError("Roles not updated");
                    }
                })
                .verifyComplete();
        verify(userDirectoryGateway).replaceRoles(eq(UserAdminTestData.TARGET_EMAIL), eq(List.of("COORDINATOR", "COLLABORATOR")));
    }

    @Test
    void rejectsEmptyRoleSet() {
        when(userDirectory.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.administrator()));

        StepVerifier.create(useCase.updateRoles(TaskTestData.COORDINATOR_EMAIL, UserAdminTestData.TARGET_EMAIL, List.of()))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.BAD_REQUEST)
                .verify();
        verify(userDirectoryGateway, never()).replaceRoles(any(), any());
    }

    @Test
    void rejectsInvalidRoleCode() {
        when(userDirectory.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.administrator()));

        StepVerifier.create(useCase.updateRoles(TaskTestData.COORDINATOR_EMAIL, UserAdminTestData.TARGET_EMAIL, List.of("WIZARD")))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.BAD_REQUEST)
                .verify();
        verify(userDirectoryGateway, never()).replaceRoles(any(), any());
    }

    @Test
    void forbidsNonAdministratorFromUpdatingRoles() {
        when(userDirectory.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));

        StepVerifier.create(useCase.updateRoles(TaskTestData.COORDINATOR_EMAIL, UserAdminTestData.TARGET_EMAIL, List.of("COLLABORATOR")))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();
        verify(userDirectoryGateway, never()).replaceRoles(any(), any());
    }

    @Test
    void resetsPasswordWhenActorIsAdministrator() {
        when(userDirectory.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.administrator()));
        when(userDirectoryGateway.findByEmail(UserAdminTestData.TARGET_EMAIL))
                .thenReturn(Mono.just(UserAdminTestData.organizationUser(List.of("COLLABORATOR"))));
        when(authenticationGateway.resetUserPassword(eq(UserAdminTestData.TARGET_EMAIL), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.resetPassword(TaskTestData.COORDINATOR_EMAIL, UserAdminTestData.TARGET_EMAIL, "TempPass123*"))
                .verifyComplete();
        verify(authenticationGateway).resetUserPassword(eq(UserAdminTestData.TARGET_EMAIL), eq("TempPass123*"));
    }

    @Test
    void rejectsResetWhenUserDoesNotExist() {
        when(userDirectory.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.administrator()));
        when(userDirectoryGateway.findByEmail(UserAdminTestData.TARGET_EMAIL)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.resetPassword(TaskTestData.COORDINATOR_EMAIL, UserAdminTestData.TARGET_EMAIL, "TempPass123*"))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.NOT_FOUND)
                .verify();
        verify(authenticationGateway, never()).resetUserPassword(any(), any());
    }
}

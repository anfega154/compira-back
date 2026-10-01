package co.com.compira.usecase.listusers;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.model.user.gateways.UserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import co.com.compira.usecase.user.UserAdminTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListUsersUseCaseTest {
    private final UserDirectoryGateway userDirectoryGateway = mock(UserDirectoryGateway.class);
    private final TaskUserDirectoryGateway userDirectory = mock(TaskUserDirectoryGateway.class);
    private final ListUsersUseCase useCase = new ListUsersUseCase(userDirectoryGateway, userDirectory);

    @Test
    void returnsOrganizationUsersWhenActorIsAdministrator() {
        when(userDirectory.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.administrator()));
        when(userDirectoryGateway.findAll()).thenReturn(Flux.just(UserAdminTestData.organizationUser(List.of("COLLABORATOR"))));

        StepVerifier.create(useCase.execute(TaskTestData.COORDINATOR_EMAIL))
                .assertNext(user -> {
                    if (!user.email().equals(UserAdminTestData.TARGET_EMAIL)) {
                        throw new AssertionError("Unexpected user");
                    }
                })
                .verifyComplete();
    }

    @Test
    void forbidsNonAdministratorActors() {
        when(userDirectory.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));

        StepVerifier.create(useCase.execute(TaskTestData.COORDINATOR_EMAIL))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();
        verifyNoInteractions(userDirectoryGateway);
    }
}

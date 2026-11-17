package co.com.compira.model.task.gateways;

import co.com.compira.model.task.TaskUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface TaskUserDirectoryGateway {
    Mono<TaskUser> findByEmail(String email);

    Mono<TaskUser> findById(UUID id);

    Flux<TaskUser> findByIds(Collection<UUID> ids);
}

package co.com.compira.model.team.gateways;

import co.com.compira.model.team.Team;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface TeamRepositoryGateway {
    Flux<Team> findAll();
    Flux<Team> findByCoordinator(UUID coordinatorId);
    Mono<Team> findById(UUID teamId);
    Mono<Team> findByTaskId(UUID taskId);
    Mono<Team> findByMemberId(UUID memberId);
    Mono<Team> create(String name, UUID coordinatorId);
    Mono<Team> changeCoordinator(UUID teamId, UUID coordinatorId);
    Mono<Void> addMember(UUID teamId, UUID memberId);
    Mono<Void> linkTask(UUID taskId, UUID teamId);
}

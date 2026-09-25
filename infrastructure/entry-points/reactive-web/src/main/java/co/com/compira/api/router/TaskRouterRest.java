package co.com.compira.api.router;

import co.com.compira.api.task.TaskHandler;
import co.com.compira.api.task.TaskRoute;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class TaskRouterRest {
    @Bean
    public RouterFunction<ServerResponse> taskRouterFunction(TaskHandler taskHandler) {
        return RouterFunctions.route()
                .path(TaskRoute.API_V1 + TaskRoute.TASKS_BASE, builder -> builder
                        .GET(TaskRoute.ASSIGNED, taskHandler::listAssignedTasks)
                        .GET(TaskRoute.HISTORY, taskHandler::listHistory)
                        .GET(TaskRoute.OBSERVATIONS, taskHandler::listObservations)
                        .POST(TaskRoute.ASSIGN, taskHandler::assignTask)
                        .POST(TaskRoute.REASSIGN, taskHandler::reassignTask)
                        .POST(TaskRoute.STATUS, taskHandler::updateTaskStatus)
                        .POST(TaskRoute.CANCEL, taskHandler::cancelTask)
                        .POST(TaskRoute.APPROVE, taskHandler::approveTask)
                        .POST(TaskRoute.OBSERVATIONS, taskHandler::addObservation)
                        .GET(TaskRoute.BY_ID, taskHandler::getTask)
                        .GET("", taskHandler::listManagedTasks)
                        .POST("", taskHandler::createTask))
                .build();
    }
}

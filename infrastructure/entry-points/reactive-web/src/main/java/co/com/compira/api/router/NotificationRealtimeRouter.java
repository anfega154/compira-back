package co.com.compira.api.router;
import co.com.compira.api.notification.NotificationRealtimeHandler;
import co.com.compira.model.notification.RealtimeTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.*;
@Configuration
public class NotificationRealtimeRouter {
    @Bean
    @RouterOperation(path = NotificationRealtimeHandler.PATH, method = RequestMethod.POST, beanClass = NotificationRealtimeHandler.class, beanMethod = "ticket",
        operation = @Operation(operationId = "notificationRealtimeTicket", summary = "Emitir enlace WebSocket con ticket de un solo uso (60 segundos); nunca enviar JWT por URL", security = @SecurityRequirement(name = "bearerAuth"),
            responses = {@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = RealtimeTicket.class))), @ApiResponse(responseCode = "401", description = "Sesión inválida"), @ApiResponse(responseCode = "403", description = "Rol no autorizado"), @ApiResponse(responseCode = "503", description = "Canal deshabilitado o no disponible; usar consulta de respaldo")}))
    public RouterFunction<ServerResponse> notificationRealtimeRouter(NotificationRealtimeHandler handler) {
        return RouterFunctions.route().POST(NotificationRealtimeHandler.PATH, handler::ticket).build();
    }
}

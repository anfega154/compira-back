package co.com.compira.api.router;

import co.com.compira.api.report.ExecutiveReportHandler;
import co.com.compira.model.report.ExecutiveReport;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.*;

@Configuration
public class ExecutiveReportRouter {
    @Bean
    @RouterOperation(path="/api/v1/tasks/reports/executive",method=RequestMethod.GET,beanClass=ExecutiveReportHandler.class,beanMethod="generate",
        operation=@Operation(operationId="generateExecutiveTaskReport",summary="Generar corte gerencial de tareas del alcance autorizado",
            description="Estado actual, no reconstrucción histórica. Máximo5000 tareas filtradas, sin truncar. Fechas inclusivas en zona organizacional. Sin responsable incluido. Cierre desde historial. Respuesta no-store.",
            security=@SecurityRequirement(name="bearerAuth"),
            parameters={@Parameter(name="from",in=ParameterIn.QUERY,description="Fecha inicial inclusiva YYYY-MM-DD"),
                @Parameter(name="to",in=ParameterIn.QUERY,description="Fecha final inclusiva YYYY-MM-DD"),
                @Parameter(name="dateBasis",in=ParameterIn.QUERY,schema=@Schema(allowableValues={"CREATED","DUE","CLOSED"})),
                @Parameter(name="teamId",in=ParameterIn.QUERY),@Parameter(name="responsibleId",in=ParameterIn.QUERY),
                @Parameter(name="unassigned",in=ParameterIn.QUERY,schema=@Schema(type="boolean")),
                @Parameter(name="status",in=ParameterIn.QUERY,schema=@Schema(allowableValues={"PENDING","IN_PROGRESS","DELAYED","COMPLETED","CLOSED","CANCELLED"}))},
            responses={@ApiResponse(responseCode="200",content=@Content(schema=@Schema(implementation=ExecutiveReport.class))),
                @ApiResponse(responseCode="400",description="Filtros inválidos o más5000 tareas"),@ApiResponse(responseCode="401"),@ApiResponse(responseCode="403"),@ApiResponse(responseCode="503",description="Tiempo de espera agotado; reducir periodo y reintentar")}))
    public RouterFunction<ServerResponse> executiveReportRoutes(ExecutiveReportHandler handler) {
        return RouterFunctions.route().GET("/api/v1/tasks/reports/executive",handler::generate).build();
    }
}

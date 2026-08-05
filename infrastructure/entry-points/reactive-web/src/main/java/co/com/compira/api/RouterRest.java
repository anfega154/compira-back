package co.com.compira.api;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;


@Configuration
public class RouterRest {
    @RouterOperations({
            // V1 Endpoints
            @RouterOperation(
                    path = "/api/v1/companies",
                    beanClass = HandlerV1.class,
                    beanMethod = "listCompanies",
                    method = RequestMethod.GET
            ),
            @RouterOperation(
                    path = "/api/v1/companies",
                    beanClass = HandlerV1.class,
                    beanMethod = "createCompany",
                    method = RequestMethod.POST
            ),
            @RouterOperation(
                    path = "/api/v1/companies/{id}",
                    beanClass = HandlerV1.class,
                    beanMethod = "getCompanyById",
                    method = RequestMethod.GET
            ),
            // V2 Endpoints
            @RouterOperation(
                    path = "/api/v2/companies",
                    beanClass = HandlerV2.class,
                    beanMethod = "listCompanies",
                    method = RequestMethod.GET
            ),
            @RouterOperation(
                    path = "/api/v2/companies",
                    beanClass = HandlerV2.class,
                    beanMethod = "createCompany",
                    method = RequestMethod.POST
            ),
            @RouterOperation(
                    path = "/api/v2/companies/{id}",
                    beanClass = HandlerV2.class,
                    beanMethod = "getCompanyById",
                    method = RequestMethod.GET
            )
    })
    @Bean
    public RouterFunction<ServerResponse> routerFunction(HandlerV1 handlerV1, HandlerV2 handlerV2) {
        return RouterFunctions
                .route()
                .path("/api/v1", builder -> builder
                        .GET("/companies", handlerV1::listCompanies)
                        .POST("/companies", handlerV1::createCompany)
                        .GET("/companies/{id}", handlerV1::getCompanyById)
                )
                .path("/api/v2", builder -> builder
                        .GET("/companies", handlerV2::listCompanies)
                        .POST("/companies", handlerV2::createCompany)
                        .GET("/companies/{id}", handlerV2::getCompanyById)
                )
                .build();
    }
}

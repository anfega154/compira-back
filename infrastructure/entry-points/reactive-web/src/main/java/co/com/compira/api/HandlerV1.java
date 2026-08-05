package co.com.compira.api;

import co.com.compira.api.dto.ApiErrorResponse;
import co.com.compira.api.dto.CompanyResponse;
import co.com.compira.api.dto.CreateCompanyRequest;
import co.com.compira.usecase.createcompany.CreateCompanyUseCase;
import co.com.compira.usecase.getcompanybyid.GetCompanyByIdUseCase;
import co.com.compira.usecase.listcompanies.ListCompaniesUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class HandlerV1 {
    private final CreateCompanyUseCase createCompanyUseCase;
    private final GetCompanyByIdUseCase getCompanyByIdUseCase;
    private final ListCompaniesUseCase listCompaniesUseCase;

    public HandlerV1(CreateCompanyUseCase createCompanyUseCase,
                     GetCompanyByIdUseCase getCompanyByIdUseCase,
                     ListCompaniesUseCase listCompaniesUseCase) {
        this.createCompanyUseCase = createCompanyUseCase;
        this.getCompanyByIdUseCase = getCompanyByIdUseCase;
        this.listCompaniesUseCase = listCompaniesUseCase;
    }

    public Mono<ServerResponse> listCompanies(ServerRequest serverRequest) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(listCompaniesUseCase.execute().map(CompanyResponse::fromDomain), CompanyResponse.class);
    }

    public Mono<ServerResponse> getCompanyById(ServerRequest serverRequest) {
        return getCompanyByIdUseCase.execute(serverRequest.pathVariable("id"))
                .map(CompanyResponse::fromDomain)
                .flatMap(company ->
                        ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(company))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> createCompany(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(CreateCompanyRequest.class)
                .map(CreateCompanyRequest::toDomain)
                .flatMap(createCompanyUseCase::execute)
                .map(CompanyResponse::fromDomain)
                .flatMap(company ->
                        ServerResponse.status(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(company))
                .onErrorResume(IllegalArgumentException.class, error ->
                        ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new ApiErrorResponse(error.getMessage())));
    }
}

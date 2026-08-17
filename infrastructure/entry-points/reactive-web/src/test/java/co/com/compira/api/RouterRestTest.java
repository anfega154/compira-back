package co.com.compira.api;

import co.com.compira.api.auth.AuthenticationHandler;
import co.com.compira.model.company.Company;
import co.com.compira.usecase.createcompany.CreateCompanyUseCase;
import co.com.compira.usecase.getcompanybyid.GetCompanyByIdUseCase;
import co.com.compira.usecase.listcompanies.ListCompaniesUseCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouterRestTest {
    private final CreateCompanyUseCase createCompanyUseCase = mock(CreateCompanyUseCase.class);
    private final GetCompanyByIdUseCase getCompanyByIdUseCase = mock(GetCompanyByIdUseCase.class);
    private final ListCompaniesUseCase listCompaniesUseCase = mock(ListCompaniesUseCase.class);
    private final AuthenticationHandler authenticationHandler = mock(AuthenticationHandler.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        HandlerV1 handlerV1 = new HandlerV1(createCompanyUseCase, getCompanyByIdUseCase, listCompaniesUseCase);
        HandlerV2 handlerV2 = new HandlerV2(createCompanyUseCase, getCompanyByIdUseCase, listCompaniesUseCase);
        webTestClient = WebTestClient.bindToRouterFunction(new RouterRest().routerFunction(handlerV1, handlerV2, authenticationHandler)).build();
    }

    @Test
    void testListCompaniesV1() {
        Company company = new Company("company-1", "Compira", "hello@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));
        when(listCompaniesUseCase.execute()).thenReturn(Flux.just(company));

        webTestClient.get()
                .uri("/api/v1/companies")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("company-1")
                .jsonPath("$[0].name").isEqualTo("Compira")
                .jsonPath("$[0].email").isEqualTo("hello@compira.co");
    }

    @Test
    void testListCompaniesV2() {
        Company company = new Company("company-2", "Compira V2", "v2@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));
        when(listCompaniesUseCase.execute()).thenReturn(Flux.just(company));

        webTestClient.get()
                .uri("/api/v2/companies")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("company-2")
                .jsonPath("$[0].name").isEqualTo("Compira V2")
                .jsonPath("$[0].email").isEqualTo("v2@compira.co");
    }

    @Test
    void testGetCompanyByIdV1() {
        Company company = new Company("company-1", "Compira", "hello@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));
        when(getCompanyByIdUseCase.execute("company-1")).thenReturn(Mono.just(company));

        webTestClient.get()
                .uri("/api/v1/companies/company-1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("company-1")
                .jsonPath("$.name").isEqualTo("Compira");
    }

    @Test
    void testGetCompanyByIdNotFound() {
        when(getCompanyByIdUseCase.execute("missing")).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/v1/companies/missing")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testCreateCompanyV1() {
        Company company = new Company("company-3", "Compira Create", "create@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));
        when(createCompanyUseCase.execute(any(Company.class))).thenReturn(Mono.just(company));

        webTestClient.post()
                .uri("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "Compira Create",
                          "email": "create@compira.co"
                        }
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("company-3")
                .jsonPath("$.email").isEqualTo("create@compira.co");
    }

    @Test
    void testCreateCompanyBadRequest() {
        when(createCompanyUseCase.execute(any(Company.class))).thenThrow(new IllegalArgumentException("Company email is required"));

        webTestClient.post()
                .uri("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name": "Compira Create",
                          "email": ""
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(String.class)
                .value(response -> {
                            Assertions.assertThat(response).contains("Company email is required");
                        }
                );
    }
}

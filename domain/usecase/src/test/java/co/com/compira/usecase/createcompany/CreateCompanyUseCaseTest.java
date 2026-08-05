package co.com.compira.usecase.createcompany;

import co.com.compira.model.company.Company;
import co.com.compira.model.company.gateways.CompanyRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateCompanyUseCaseTest {
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final CreateCompanyUseCase useCase = new CreateCompanyUseCase(companyRepository);

    @Test
    void shouldCreateCompany() {
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.execute(new Company(null, "Compira", "hello@compira.co", null)))
                .assertNext(company -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(company.getId());
                    org.junit.jupiter.api.Assertions.assertEquals("Compira", company.getName());
                    org.junit.jupiter.api.Assertions.assertEquals("hello@compira.co", company.getEmail());
                    org.junit.jupiter.api.Assertions.assertNotNull(company.getCreatedAt());
                })
                .verifyComplete();
    }

    @Test
    void shouldFailWhenEmailIsMissing() {
        StepVerifier.create(Mono.defer(() -> useCase.execute(new Company(null, "Compira", "", null))))
                .expectErrorMessage("Company email is required")
                .verify();
    }
}

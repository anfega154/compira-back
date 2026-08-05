package co.com.compira.r2dbc;

import co.com.compira.model.company.Company;
import co.com.compira.r2dbc.data.CompanyData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivecommons.utils.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyReactiveRepositoryAdapterTest {

    @InjectMocks
    private CompanyReactiveRepositoryAdapter repositoryAdapter;

    @Mock
    private CompanyReactiveRepository repository;

    @Mock
    private ObjectMapper mapper;

    @Test
    void mustFindValueById() {
        CompanyData data = new CompanyData("1", "Compira", "hello@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));
        Company company = new Company("1", "Compira", "hello@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));

        when(repository.findById("1")).thenReturn(Mono.just(data));
        when(mapper.map(data, Company.class)).thenReturn(company);

        StepVerifier.create(repositoryAdapter.findById("1"))
                .expectNext(company)
                .verifyComplete();
    }

    @Test
    void mustFindAllValues() {
        CompanyData data = new CompanyData("1", "Compira", "hello@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));
        Company company = new Company("1", "Compira", "hello@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));

        when(repository.findAll()).thenReturn(Flux.just(data));
        when(mapper.map(data, Company.class)).thenReturn(company);

        StepVerifier.create(repositoryAdapter.findAll())
                .expectNext(company)
                .verifyComplete();
    }

    @Test
    void mustSaveValue() {
        Company company = new Company("1", "Compira", "hello@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));
        CompanyData data = new CompanyData("1", "Compira", "hello@compira.co", LocalDateTime.of(2026, 8, 5, 18, 0));

        when(mapper.map(company, CompanyData.class)).thenReturn(data);
        when(repository.save(data)).thenReturn(Mono.just(data));
        when(mapper.map(data, Company.class)).thenReturn(company);

        StepVerifier.create(repositoryAdapter.save(company))
                .expectNext(company)
                .verifyComplete();
    }
}

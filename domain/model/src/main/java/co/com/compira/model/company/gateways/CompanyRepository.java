package co.com.compira.model.company.gateways;

import co.com.compira.model.company.Company;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CompanyRepository {
    Mono<Company> save(Company company);

    Mono<Company> findById(String id);

    Flux<Company> findAll();
}

package co.com.compira.usecase.listcompanies;

import co.com.compira.model.company.Company;
import co.com.compira.model.company.gateways.CompanyRepository;
import reactor.core.publisher.Flux;

public class ListCompaniesUseCase {
    private final CompanyRepository companyRepository;

    public ListCompaniesUseCase(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public Flux<Company> execute() {
        return companyRepository.findAll();
    }
}

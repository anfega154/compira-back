package co.com.compira.usecase.getcompanybyid;

import co.com.compira.model.company.Company;
import co.com.compira.model.company.gateways.CompanyRepository;
import reactor.core.publisher.Mono;

public class GetCompanyByIdUseCase {
    private final CompanyRepository companyRepository;

    public GetCompanyByIdUseCase(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public Mono<Company> execute(String companyId) {
        return companyRepository.findById(companyId);
    }
}

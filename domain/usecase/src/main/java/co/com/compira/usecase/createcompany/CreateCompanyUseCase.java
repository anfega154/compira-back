package co.com.compira.usecase.createcompany;

import co.com.compira.model.company.Company;
import co.com.compira.model.company.gateways.CompanyRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public class CreateCompanyUseCase {
    private final CompanyRepository companyRepository;

    public CreateCompanyUseCase(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public Mono<Company> execute(Company company) {
        validate(company);

        Company companyToSave = new Company(
                company.getId() == null || company.getId().isBlank() ? UUID.randomUUID().toString() : company.getId(),
                company.getName().trim(),
                company.getEmail().trim().toLowerCase(),
                company.getCreatedAt() == null ? LocalDateTime.now() : company.getCreatedAt()
        );

        return companyRepository.save(companyToSave);
    }

    private void validate(Company company) {
        if (company == null) {
            throw new IllegalArgumentException("Company payload is required");
        }
        if (company.getName() == null || company.getName().isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }
        if (company.getEmail() == null || company.getEmail().isBlank()) {
            throw new IllegalArgumentException("Company email is required");
        }
    }
}

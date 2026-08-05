package co.com.compira.api.dto;

import co.com.compira.model.company.Company;

public record CreateCompanyRequest(String name, String email) {
    public Company toDomain() {
        return new Company(null, name, email, null);
    }
}

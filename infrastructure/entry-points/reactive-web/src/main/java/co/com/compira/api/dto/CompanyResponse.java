package co.com.compira.api.dto;

import co.com.compira.model.company.Company;

import java.time.LocalDateTime;

public record CompanyResponse(String id, String name, String email, LocalDateTime createdAt) {
    public static CompanyResponse fromDomain(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getEmail(),
                company.getCreatedAt()
        );
    }
}

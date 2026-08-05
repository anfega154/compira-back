package co.com.compira.api;

import co.com.compira.usecase.createcompany.CreateCompanyUseCase;
import co.com.compira.usecase.getcompanybyid.GetCompanyByIdUseCase;
import co.com.compira.usecase.listcompanies.ListCompaniesUseCase;
import org.springframework.stereotype.Component;

@Component
public class HandlerV2 extends HandlerV1 {
    public HandlerV2(CreateCompanyUseCase createCompanyUseCase,
                     GetCompanyByIdUseCase getCompanyByIdUseCase,
                     ListCompaniesUseCase listCompaniesUseCase) {
        super(createCompanyUseCase, getCompanyByIdUseCase, listCompaniesUseCase);
    }
}

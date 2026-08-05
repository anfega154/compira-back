package co.com.compira.r2dbc;

import co.com.compira.model.company.Company;
import co.com.compira.model.company.gateways.CompanyRepository;
import co.com.compira.r2dbc.data.CompanyData;
import co.com.compira.r2dbc.helper.ReactiveAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CompanyReactiveRepositoryAdapter extends ReactiveAdapterOperations<
        Company,
        CompanyData,
        String,
        CompanyReactiveRepository
        > implements CompanyRepository {
    public CompanyReactiveRepositoryAdapter(CompanyReactiveRepository repository, ObjectMapper mapper) {
        super(repository, mapper, data -> mapper.map(data, Company.class));
    }
}

package co.com.compira.r2dbc;

import co.com.compira.r2dbc.data.CompanyData;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface CompanyReactiveRepository
        extends ReactiveCrudRepository<CompanyData, String>, ReactiveQueryByExampleExecutor<CompanyData> {
}

package co.com.compira.r2dbc.config.liquibase;

import org.springframework.boot.liquibase.autoconfigure.LiquibaseDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class LiquibaseConfig {
    private static final String JDBC_URL_TEMPLATE = "jdbc:postgresql://%s:%d/%s?currentSchema=%s&sslmode=%s";

    @Bean
    @LiquibaseDataSource
    public DataSource liquibaseDataSource(JdbcConnectionProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.driverClassName());
        dataSource.setUrl(buildJdbcUrl(properties));
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        return dataSource;
    }

    private String buildJdbcUrl(JdbcConnectionProperties properties) {
        return JDBC_URL_TEMPLATE.formatted(
                properties.host(),
                properties.port(),
                properties.database(),
                properties.schema(),
                properties.sslMode());
    }
}

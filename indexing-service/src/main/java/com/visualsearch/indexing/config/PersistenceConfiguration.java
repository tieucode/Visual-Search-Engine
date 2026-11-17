package com.visualsearch.indexing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;

import javax.sql.DataSource;

@Configuration
public class PersistenceConfiguration {
    @Bean
    JdbcTransactionManager indexingTransactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}

package com.gt.ssrs.conf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Configuration
public class BeanConfig {

    @Bean
    public DataSource getDataSource(@Value("${ssrs.datasource.postgres.url}") String url,
                                    @Value("${ssrs.datasource.postgres.username}") String username,
                                    @Value("${ssrs.datasource.postgres.password}") String password) {
        return new DriverManagerDataSource(url, username, password);
    }

    @Bean
    public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(DataSource dataSource) {

        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public Connection getBlobDatabaseConnection(@Value("${ssrs.datasource.postgres.url}") String url,
                                                @Value("${ssrs.datasource.postgres.username}") String username,
                                                @Value("${ssrs.datasource.postgres.password}") String password) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);

        Connection conn = DriverManager.getConnection(url, props);
        conn.setAutoCommit(false);
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

        return conn;
    }
}
package com.gt.ssrs.conf;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.blob.impl.BlobDaoPG;
import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.reviewSession.ReviewEventDao;
import com.gt.ssrs.reviewSession.ScheduledReviewDao;
import com.gt.ssrs.reviewSession.impl.ReviewEventDaoPG;
import com.gt.ssrs.reviewSession.impl.ScheduledReviewDaoPG;
import com.gt.ssrs.word.WordDao;
import com.gt.ssrs.lexicon.impl.LexiconDaoPG;
import com.gt.ssrs.word.impl.WordDaoPG;
import com.gt.ssrs.reviewHistory.WordReviewHistoryDao;
import com.gt.ssrs.reviewHistory.impl.WordReviewHistoryDaoPG;
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
public class PGBeanConfig {

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

    @Bean
    public BlobDao getBlobDao(Connection blobDatabaseConnection) {
        return new BlobDaoPG(blobDatabaseConnection);
    }

    @Bean
    public LexiconDao getLexiconDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new LexiconDaoPG(namedParameterJdbcTemplate);
    }

    @Bean
    public WordDao getWordDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new WordDaoPG(namedParameterJdbcTemplate);
    }

    @Bean
    public WordReviewHistoryDao getWordReviewHistoryDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new WordReviewHistoryDaoPG(namedParameterJdbcTemplate);
    }

    @Bean
    public ReviewEventDao getReviewEventDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new ReviewEventDaoPG(namedParameterJdbcTemplate);
    }

    @Bean
    public ScheduledReviewDao getScheduledReviewDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new ScheduledReviewDaoPG(namedParameterJdbcTemplate);
    }
}
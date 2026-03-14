package com.gt.ssrs.conf.pg;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.blob.pg.BlobDaoPG;
import com.gt.ssrs.filter.pg.JwtAuthFilter;
import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.notepad.UserNotepadDao;
import com.gt.ssrs.notepad.pg.UserNotepadDaoPG;
import com.gt.ssrs.reviewSession.ReviewEventDao;
import com.gt.ssrs.reviewSession.ScheduledReviewDao;
import com.gt.ssrs.reviewSession.pg.ReviewEventDaoPG;
import com.gt.ssrs.reviewSession.pg.ScheduledReviewDaoPG;
import com.gt.ssrs.security.pg.JwtService;
import com.gt.ssrs.userconfig.UserConfigDao;
import com.gt.ssrs.userconfig.pg.UserConfigDaoPG;
import com.gt.ssrs.word.WordDao;
import com.gt.ssrs.lexicon.pg.LexiconDaoPG;
import com.gt.ssrs.word.pg.WordDaoPG;
import com.gt.ssrs.reviewHistory.WordReviewHistoryDao;
import com.gt.ssrs.reviewHistory.pg.WordReviewHistoryDaoPG;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.UserDetailsService;

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
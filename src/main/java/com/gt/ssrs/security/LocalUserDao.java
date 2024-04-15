package com.gt.ssrs.security;

import com.gt.ssrs.exception.DaoException;
import com.gt.ssrs.model.LocalUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LocalUserDao {

    private static final Logger log = LoggerFactory.getLogger(LocalUserDao.class);

    private static final String GET_USER_SQL = "SELECT id, username, password, account_expiration, locked, credential_expiration, enabled " +
            "FROM users " +
            "WHERE username = :username";
    private static final String INSERT_USER_SQL = "INSERT INTO users (username, password, account_expiration, credential_expiration, locked, enabled) " +
            "VALUES (:username, :password, :accountExpiration, :credentialExpiration, :locked, :enabled)";

    private NamedParameterJdbcTemplate template;

    @Autowired
    public LocalUserDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {

        this.template = namedParameterJdbcTemplate;
    }

    public void createLocalUser(LocalUser localUser) {
        Map<String, Object> localUserParameters = new HashMap<>();
        localUserParameters.put("username", localUser.getUsername());
        localUserParameters.put("password", localUser.getPassword());
        localUserParameters.put("accountExpiration", localUser.getAccountExpiration());
        localUserParameters.put("credentialExpiration", localUser.getCredentialExpiration());
        localUserParameters.put("locked", localUser.getLocked());
        localUserParameters.put("enabled", localUser.getEnabled());

        template.update(INSERT_USER_SQL, localUserParameters);
    }

    public LocalUser getLocalUser(String username) {
        List<LocalUser> queriedUsers = template.query(GET_USER_SQL, Map.of("username", username), (rs, rowNum) ->
            new LocalUser(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password"),
                    toInstant(rs.getTimestamp("account_expiration")),
                    toInstant(rs.getTimestamp("credential_expiration")),
                    rs.getBoolean("locked"),
                    rs.getBoolean("enabled"))
        );

        if (queriedUsers == null || queriedUsers.size() == 0) {
            return null;
        }
        if (queriedUsers.size() > 1) {
            throw new DaoException("Expected 1 user, but found 2 or more.");
        }
        return queriedUsers.get(0);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

package com.gt.ssrs.reviewSession.impl;

import com.gt.ssrs.model.ReviewType;
import com.gt.ssrs.model.ScheduledReview;
import com.gt.ssrs.reviewSession.ScheduledReviewDao;
import org.postgresql.util.PGInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ScheduledReviewDaoPG implements ScheduledReviewDao {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReviewDaoPG.class);

    private static final String CREATE_SCHEDULED_REVIEW_SQL =
            "INSERT INTO scheduled_review " +
                    "(id, owner, lexicon_id, word_id, review_type, test_relationship_id, scheduled_test_time, test_delay_ms, completed) " +
                    "VALUES (:id, :owner, :lexiconId, :wordId, :reviewType, :testRelationshipId, :scheduledTestTime, :testDelayMs, :completed) " +
            "ON CONFLICT (id) DO UPDATE " +
                    "SET owner = :owner, lexicon_id = :lexiconId, word_id = :wordId, review_type = :reviewType, test_relationship_id = :testRelationshipId, " +
                    "scheduled_test_time = :scheduledTestTime, test_delay_ms = :testDelayMs, completed = :completed";

    private static final String MARK_SCHEDULED_REVIEW_COMPLETE_SQL =
            "UPDATE scheduled_review " +
            "SET completed = true " +
            "WHERE id = :scheduledReviewId";

    private static final String LOAD_SCHEDULED_REVIEWS_SQL =
            "SELECT id, owner, lexicon_id, word_id, review_type, test_relationship_id, scheduled_test_time, test_delay_ms, completed " +
            "FROM scheduled_review " +
            "WHERE lexicon_id = :lexiconId AND owner = :owner AND scheduled_test_time < :cutoffInstant AND completed IS NOT TRUE AND (:testRelationshipId = '' OR test_relationship_id = :testRelationshipId)";

    private static final String LOAD_SCHEDULED_REVIEWS_FOR_WORDS_SQL =
            "SELECT id, owner, lexicon_id, word_id, review_type, test_relationship_id, scheduled_test_time, test_delay_ms, completed " +
            "FROM scheduled_review " +
            "WHERE lexicon_id = :lexiconId AND owner = :owner AND word_id IN (:wordIds) AND completed IS NOT TRUE";

    private static final String DELETE_USER_SCHEDULED_REVIEW_FOR_WORDS =
            "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId AND owner = :username AND word_id IN (:wordIds); ";

    private static final String DELETE_SCHEDULED_REVIEWS_FOR_WORDS =
            "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); ";

    private static final String DELETE_ALL_SCHEDULED_REVIEWS_FOR_USER =
            "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId AND owner = :username; ";

    private static final String DELETE_ALL_SCHEDULED_REVIEWS =
            "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId; ";

    private static final String ADJUST_NEXT_REVIEW_TIME_SQL =
            "UPDATE scheduled_review " +
            "SET scheduled_test_time = scheduled_test_time + :adjustmentHours " +
            "WHERE lexicon_id = :lexiconId AND owner = :username AND completed IS NOT TRUE";

    private static final String PURGE_OLD_SCHEDULED_REVIEWS_SQL =
            "DELETE FROM scheduled_review WHERE completed IS TRUE AND (update_instant < :cutoff OR update_instant IS NULL);";

    private final NamedParameterJdbcTemplate template;

    public ScheduledReviewDaoPG(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    @Override
    public void createScheduledReviewsBatch(List<ScheduledReview> scheduledReviews) {
        SqlParameterSource paramsArray[] = new SqlParameterSource[scheduledReviews.size()];

        for (int index = 0; index < scheduledReviews.size(); index++) {
            paramsArray[index] = new MapSqlParameterSource(Map.of(
                    "id", scheduledReviews.get(index).id(),
                    "owner", scheduledReviews.get(index).username(),
                    "lexiconId", scheduledReviews.get(index).lexiconId(),
                    "wordId", scheduledReviews.get(index).wordId(),
                    "reviewType", scheduledReviews.get(index).reviewType().toString(),
                    "testRelationshipId", scheduledReviews.get(index).testRelationshipId(),
                    "scheduledTestTime", Timestamp.from(scheduledReviews.get(index).scheduledTestTime()),
                    "testDelayMs", scheduledReviews.get(index).testDelay().toMillis(),
                    "completed", scheduledReviews.get(index).completed()));
        }

        template.batchUpdate(CREATE_SCHEDULED_REVIEW_SQL, paramsArray);
    }

    @Override
    public int markScheduledReviewComplete(String scheduledReviewId) {
        return template.update(MARK_SCHEDULED_REVIEW_COMPLETE_SQL, Map.of("scheduledReviewId", scheduledReviewId));
    }

    @Override
    public List<ScheduledReview> loadScheduledReviews(String username, String lexiconId, String testRelationshipId, Optional<Instant> cutoffInstant) {

        return template.query(LOAD_SCHEDULED_REVIEWS_SQL, Map.of("owner", username,
                        "lexiconId", lexiconId,
                        "testRelationshipId", testRelationshipId == null ? "" : testRelationshipId,   // needs to be blank if not being used as a filter
                        "cutoffInstant", Timestamp.from(cutoffInstant.orElse(Instant.now()))),
                ScheduledReviewDaoPG::getDBScheduledReviewFromResultSet);
    }

    @Override
    public List<ScheduledReview> loadScheduledReviewsForWords(String username, String lexiconId, Collection<String> wordIds) {
        return template.query(LOAD_SCHEDULED_REVIEWS_FOR_WORDS_SQL, Map.of("owner", username,
                        "lexiconId", lexiconId,
                        "wordIds", wordIds),
                ScheduledReviewDaoPG::getDBScheduledReviewFromResultSet);
    }

    public int adjustNextReviewTimes(String lexiconId, String username, Duration adjustment) {
        return template.update(ADJUST_NEXT_REVIEW_TIME_SQL, Map.of(
                "lexiconId", lexiconId,
                "username", username,
                "adjustmentHours", new PGInterval(0, 0, 0, (int)adjustment.toHours(), 0,0)));
    }

    public int purgeOldScheduledReviews(Instant cutoff) {
        return template.update(PURGE_OLD_SCHEDULED_REVIEWS_SQL, Map.of("cutoff", Timestamp.from(cutoff)));
    }

    @Override
    public void deleteUserScheduledReviewForWords(String lexiconId, Collection<String> wordIds, String username) {
        template.update(DELETE_USER_SCHEDULED_REVIEW_FOR_WORDS, Map.of(
                "lexiconId", lexiconId,
                "wordIds", wordIds,
                "username", username));
    }

    @Override
    public void deleteScheduledReviewsForWords(String lexiconId, Collection<String> wordIds) {
        template.update(DELETE_SCHEDULED_REVIEWS_FOR_WORDS, Map.of(
                "lexiconId", lexiconId,
                "wordIds", wordIds));
    }

    @Override
    public void deleteAllLexiconReviewEventsForUser(String lexiconId, String username) {
        template.update(DELETE_ALL_SCHEDULED_REVIEWS_FOR_USER, Map.of(
                "lexiconId", lexiconId,
                "username", username));
    }

    @Override
    public void deleteAllLexiconReviewEvents(String lexiconId) {
        template.update(DELETE_ALL_SCHEDULED_REVIEWS, Map.of(
                "lexiconId", lexiconId));
    }

    private static ScheduledReview getDBScheduledReviewFromResultSet(ResultSet rs, int rowNum) throws SQLException {
        return new ScheduledReview(
                rs.getString("id"),
                rs.getString("owner"),
                rs.getString("lexicon_id"),
                rs.getString("word_id"),
                ReviewType.valueOf(rs.getString("review_type")),
                rs.getString("test_relationship_id"),
                toInstant(rs.getTimestamp("scheduled_test_time")),
                Duration.ofMillis(rs.getLong("test_delay_ms")),
                rs.getBoolean("completed"));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

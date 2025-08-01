package com.gt.ssrs.review;

import com.gt.ssrs.review.model.DBLexiconReviewHistory;
import com.gt.ssrs.review.model.DBReviewEvent;
import com.gt.ssrs.review.model.DBScheduledReview;
import com.gt.ssrs.model.ReviewMode;
import com.gt.ssrs.model.ReviewType;
import com.gt.ssrs.model.TestHistory;
import org.postgresql.util.PGInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class ReviewSessionDao {

    private static final Logger log = LoggerFactory.getLogger(ReviewSessionDao.class);

    private static final String INSERT_REVIEW_EVENT_SQL =
            "INSERT INTO review_events " +
            "(lexicon_id, word_id, review_type, review_mode, test_on, prompt_with, correct, near_miss, elapsed_time_ms, username, event_instant, override, processed, scheduled_review_id) " +
            "VALUES " +
            "(:lexiconId, :wordId, :reviewType, :reviewMode, :testOn, :promptWith, :isCorrect, :isNearMiss, :elapsedTimeMs, :username, :eventInstant, :override, false, :scheduledReviewId);" +
            "UPDATE scheduled_review " +
            "SET completed = true " +
            "WHERE id = :scheduledReviewId";

    private static final String LOAD_UNPROCESSED_EVENTS_FOR_USER_BATCH =
            "SELECT event_id, lexicon_id, word_id, username, event_instant, review_type, review_mode, test_on, prompt_with, correct, near_miss, elapsed_time_ms, override " +
            "FROM review_events " +
            "WHERE username = :username AND lexicon_id = :lexiconId AND processed IS NOT TRUE AND event_id > :lastId " +
            "ORDER BY event_id ASC LIMIT :batchSize";

    private static final String MARK_EVENTS_AS_PROCESSED =
            "UPDATE review_events " +
            "SET processed = true " +
            "WHERE event_id in (:eventIds)";

    private static final String CREATE_SCHEDULED_REVIEW_SQL =
            "INSERT INTO scheduled_review " +
                "(id, owner, lexicon_id, word_id, review_type, test_relationship_id, scheduled_test_time, test_delay_ms, completed) " +
                "VALUES (:id, :owner, :lexiconId, :wordId, :reviewType, :testRelationshipId, :scheduledTestTime, :testDelayMs, :completed) " +
            "ON CONFLICT (id) DO UPDATE " +
                "SET owner = :owner, lexicon_id = :lexiconId, word_id = :wordId, review_type = :reviewType, test_relationship_id = :testRelationshipId, " +
                    "scheduled_test_time = :scheduledTestTime, test_delay_ms = :testDelayMs, completed = :completed";

    private static final String LOAD_SCHEDULED_REVIEWS_SQL =
            "SELECT id, owner, lexicon_id, word_id, review_type, test_relationship_id, scheduled_test_time, test_delay_ms, completed " +
            "FROM scheduled_review " +
            "WHERE lexicon_id = :lexiconId AND owner = :owner AND scheduled_test_time < :cutoffInstant AND completed IS NOT TRUE AND (:testRelationshipId = '' OR test_relationship_id = :testRelationshipId)";

    private static final String LOAD_SCHEDULED_REVIEWS_FOR_WORDS_SQL =
            "SELECT id, owner, lexicon_id, word_id, review_type, test_relationship_id, scheduled_test_time, test_delay_ms, completed " +
            "FROM scheduled_review " +
            "WHERE lexicon_id = :lexiconId AND owner = :owner AND word_id IN (:wordIds) AND completed IS NOT TRUE";

    private static final String GET_LEXICON_REVIEW_HISTORY_BATCH_SQL =
            "SELECT l.word_id, l.learned, l.most_recent_test_time, l.current_test_delay_sec, l.current_boost, l.current_boost_expiration_delay_sec, " +
                    "h.relationship_id, h.total_tests, h.correct_tests, h.correct_streak " +
                    "FROM lexicon_review_history l LEFT JOIN lexicon_word_test_history h ON l.lexicon_id = h.lexicon_id AND l.word_id = h.word_id AND l.username = h.username " +
                    "WHERE l.lexicon_id = :lexiconId AND l.username = :username AND l.word_id IN (:wordIds) " +
                    "ORDER BY l.word_id";

    private static final String INSERT_LEXICON_REVIEW_HISTORY_SQL =
            "INSERT INTO lexicon_review_history " +
                    "(lexicon_id, word_id, username, learned, most_recent_test_time, current_test_delay_sec, " +
                    " current_boost, current_boost_expiration_delay_sec) " +
                "VALUES (:lexiconId, :wordId, :username, :learned, :mostRecentTestTime, :currentTestDelaySec, " +
                    "    :currentBoost, :currentBoostExpirationDelaySec) " +
            "ON CONFLICT (lexicon_id, word_id, username) " +
                "DO UPDATE SET " +
                    "learned = :learned, most_recent_test_time = :mostRecentTestTime, current_test_delay_sec = :currentTestDelaySec, " +
                    "current_boost = :currentBoost, current_boost_expiration_delay_sec = :currentBoostExpirationDelaySec ";

    private static final String INSERT_LEXICON_WORD_TEST_HISTORY_SQL =
            "INSERT INTO lexicon_word_test_history " +
                "(lexicon_id, word_id, relationship_id, username, total_tests, correct_tests, correct_streak) " +
                "VALUES (:lexiconId, :wordId, :relationshipId, :username, :totalTests, :correctTests, :correctStreak) " +
            "ON CONFLICT (lexicon_id, word_id, relationship_id, username) " +
                "DO UPDATE " +
                "SET total_tests = :totalTests, correct_tests = :correctTests, correct_streak = :correctStreak ";

    private static final String DELETE_LEXICON_REVIEW_HISTORY_SQL =
            "DELETE FROM review_events WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); " +
            "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); " +
            "DELETE FROM lexicon_review_history WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); " +
            "DELETE FROM lexicon_word_test_history WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); ";

    private static final String ADJUST_NEXT_REVIEW_TIME_SQL =
            "UPDATE scheduled_review " +
            "SET scheduled_test_time = scheduled_test_time + :adjustmentHours " +
            "WHERE lexicon_id = :lexiconId AND completed IS NOT TRUE";

    private static final String GET_TOTAL_LEARNED_WORDS_SQL =
            "SELECT COUNT(*) " +
            "FROM lexicon_review_history " +
            "WHERE lexicon_id = :lexiconId AND username = :username AND learned IS TRUE";

    private static final String PURGE_OLD_SCHEDULED_REVIEWS_SQL =
            "DELETE FROM scheduled_review WHERE completed IS TRUE AND (update_instant < :cutoff OR update_instant IS NULL);";

    private static final String PURGE_OLD_REVIEW_EVENTS_SQL =
            "DELETE FROM review_events WHERE processed IS TRUE AND (update_instant < :cutoff OR update_instant IS NULL);";

    private NamedParameterJdbcTemplate template;

    @Autowired
    public ReviewSessionDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    public boolean saveReviewEvent(DBReviewEvent event, String scheduledReviewId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("lexiconId", event.lexiconId());
        params.addValue("wordId", event.wordId());
        params.addValue("reviewType", event.reviewType().toString());
        params.addValue("reviewMode", event.reviewMode().toString());
        params.addValue("testOn", event.testOn());
        params.addValue("promptWith", event.promptWith());
        params.addValue("isCorrect", event.isCorrect());
        params.addValue("isNearMiss", event.isNearMiss());
        params.addValue("elapsedTimeMs", event.elapsedTimeMs());
        params.addValue("username", event.username());
        params.addValue("eventInstant", Timestamp.from(event.eventInstant()));
        params.addValue("override", event.override());
        params.addValue("scheduledReviewId", scheduledReviewId);

        return template.update(INSERT_REVIEW_EVENT_SQL, params) > 0;
    }

    public List<DBReviewEvent> loadUnprocessedReviewEventsForUserBatch(String username, String lexiconId, int lastId, int batchSize) {
        return template.query(LOAD_UNPROCESSED_EVENTS_FOR_USER_BATCH,
                Map.of("username", username,
                       "lexiconId", lexiconId,
                       "lastId", lastId,
                       "batchSize", batchSize),
                (rs, rowNum) -> {
            return new DBReviewEvent(
                    rs.getInt("event_id"),
                    rs.getString("lexicon_id"),
                    rs.getString("word_id"),
                    rs.getString("username"),
                    toInstant(rs.getTimestamp("event_instant")),
                    ReviewType.valueOf(rs.getString("review_type")),
                    ReviewMode.valueOf(rs.getString("review_mode")),
                    rs.getString("test_on"),
                    rs.getString("prompt_with"),
                    rs.getBoolean("correct"),
                    rs.getBoolean("near_miss"),
                    rs.getLong("elapsed_time_ms"),
                    rs.getBoolean("override"));
        });
    }

    public void markEventsAsProcessed(List<DBReviewEvent> events) {
        List<Integer> eventIds = events.stream().map(event -> event.eventId()).toList();

        template.update(MARK_EVENTS_AS_PROCESSED, Map.of("eventIds", eventIds));
    }

    public void createScheduledReviewsBatch(List<DBScheduledReview> scheduledReviews, String owner) {
        SqlParameterSource paramsArray[] = new SqlParameterSource[scheduledReviews.size()];

        for (int index = 0; index < scheduledReviews.size(); index++) {
            paramsArray[index] = new MapSqlParameterSource(Map.of(
                    "id", scheduledReviews.get(index).id(),
                    "owner", owner,
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

    public List<DBScheduledReview> loadScheduledReviews(String owner, String lexiconId, String testRelationshipId, Optional<Instant> cutoffInstant) {

        return template.query(LOAD_SCHEDULED_REVIEWS_SQL, Map.of("owner", owner,
                                                                 "lexiconId", lexiconId,
                                                                 "testRelationshipId", testRelationshipId == null ? "" : testRelationshipId,   // needs to be blank if not being used as a filter
                                                                 "cutoffInstant", Timestamp.from(cutoffInstant.orElse(Instant.now()))),
                ReviewSessionDao::getDBScheduledReviewFromResultSet);
    }

    public List<DBScheduledReview> loadScheduledReviewsForWords(String owner, String lexiconId, Collection<String> wordIds) {
        return template.query(LOAD_SCHEDULED_REVIEWS_FOR_WORDS_SQL, Map.of("owner", owner,
                                                                           "lexiconId", lexiconId,
                                                                           "wordIds", wordIds),
                ReviewSessionDao::getDBScheduledReviewFromResultSet);
    }

    public List<DBLexiconReviewHistory> getLexiconReviewHistoryBatch(String lexiconId, String username, Collection<String> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return List.of();
        }

        return template.query(GET_LEXICON_REVIEW_HISTORY_BATCH_SQL, Map.of("lexiconId", lexiconId, "wordIds", wordIds, "username", username), (rs) -> {
            List<DBLexiconReviewHistory> lexiconWordHistories = new ArrayList<>();

            String curWordId = null;
            boolean learned = false;
            Instant mostRecentTestTime = null;
            Duration currentTestDelay = null;
            double currentBoost = 0;
            Duration currentBoostExpirationDelay = null;
            Map<String, TestHistory> testHistory = Map.of();

            while (rs.next()) {
                String wordId = rs.getString("word_id");
                if (wordId != null) {
                    if (!wordId.equals(curWordId)) {
                        if (curWordId != null) {
                            lexiconWordHistories.add(new DBLexiconReviewHistory(lexiconId, curWordId,
                                    learned,
                                    mostRecentTestTime,
                                    currentTestDelay,
                                    currentBoost,
                                    currentBoostExpirationDelay,
                                    testHistory));
                        }

                        curWordId = wordId;
                        mostRecentTestTime = toInstant(rs.getTimestamp("most_recent_test_time"));
                        learned = rs.getBoolean("learned");
                        currentTestDelay = Duration.ofSeconds(rs.getLong("current_test_delay_sec"));
                        currentBoost = rs.getDouble("current_boost");
                        currentBoostExpirationDelay = Duration.ofSeconds(rs.getLong("current_boost_expiration_delay_sec"));
                        testHistory = new HashMap<>();
                    }
                }

                String relationshipId = rs.getString("relationship_id");
                if (relationshipId != null && !relationshipId.isBlank()) {
                    testHistory.put(relationshipId, new TestHistory(
                            rs.getInt("total_tests"),
                            rs.getInt("correct_tests"),
                            rs.getInt("correct_streak")));
                }
            }

            if (curWordId != null) {
                lexiconWordHistories.add(new DBLexiconReviewHistory(lexiconId, curWordId,
                        learned,
                        mostRecentTestTime,
                        currentTestDelay,
                        currentBoost,
                        currentBoostExpirationDelay,
                        testHistory));
            }

            return lexiconWordHistories;
        });
    }

    public int updateLexiconReviewHistoryBatch(String username, List<DBLexiconReviewHistory> wordHistories) {
        List<SqlParameterSource> wordHistoryParamList = new ArrayList<>();
        List<SqlParameterSource> testHistoryParamList = new ArrayList<>();

        for(DBLexiconReviewHistory wordHistory : wordHistories) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("lexiconId", wordHistory.lexiconId());
            params.addValue("wordId", wordHistory.wordId());
            params.addValue("username", username);
            params.addValue("learned", wordHistory.learned());
            params.addValue("mostRecentTestTime", Timestamp.from(wordHistory.mostRecentTestTime()));
            params.addValue("currentTestDelaySec", wordHistory.currentTestDelay().getSeconds());
            params.addValue("currentBoost", wordHistory.currentBoost());
            params.addValue("currentBoostExpirationDelaySec", wordHistory.currentBoostExpirationDelay() == null ? null : wordHistory.currentBoostExpirationDelay().getSeconds());
            wordHistoryParamList.add(params);

            for (Map.Entry<String, TestHistory> testHistory : wordHistory.testHistory().entrySet()) {
                testHistoryParamList.add(new MapSqlParameterSource(Map.of(
                        "lexiconId", wordHistory.lexiconId(),
                        "wordId", wordHistory.wordId(),
                        "relationshipId", testHistory.getKey(),
                        "username", username,
                        "totalTests", testHistory.getValue().totalTests(),
                        "correctTests", testHistory.getValue().correct(),
                        "correctStreak", testHistory.getValue().correctStreak())));
            }
        }

        int[] rowCnts = template.batchUpdate(INSERT_LEXICON_REVIEW_HISTORY_SQL, wordHistoryParamList.toArray(new SqlParameterSource[0]));
        template.batchUpdate(INSERT_LEXICON_WORD_TEST_HISTORY_SQL, testHistoryParamList.toArray(new SqlParameterSource[0]));

        return Arrays.stream(rowCnts).sum();
    }

    public int deleteLexiconHistoryBatch(String lexiconId, Collection<String> wordIds) {
        return template.update(DELETE_LEXICON_REVIEW_HISTORY_SQL, Map.of(
                "lexiconId", lexiconId,
                "wordIds", wordIds));
    }

    public int adjustNextReviewTimes(String lexiconId, Duration adjustment) {
        return template.update(ADJUST_NEXT_REVIEW_TIME_SQL, Map.of(
                "lexiconId", lexiconId,
                "adjustmentHours", new PGInterval(0, 0, 0, (int)adjustment.toHours(), 0,0)));
    }

    public int getTotalLearnedWordCount(String lexiconId, String username) {
        return template.queryForObject(GET_TOTAL_LEARNED_WORDS_SQL, Map.of("lexiconId", lexiconId, "username", username), Integer.class);
    }

    public int purgeOldScheduledReviews(Instant cutoff) {
        return template.update(PURGE_OLD_SCHEDULED_REVIEWS_SQL, Map.of("cutoff", Timestamp.from(cutoff)));
    }

    public int purgeOldReviewEvents(Instant cutoff) {
        return template.update(PURGE_OLD_REVIEW_EVENTS_SQL, Map.of("cutoff", Timestamp.from(cutoff)));
    }

    private static DBScheduledReview getDBScheduledReviewFromResultSet(ResultSet rs, int rowNum) throws SQLException {
        return new DBScheduledReview(
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

package com.gt.ssrs.reviewSession.impl;

import com.gt.ssrs.model.ReviewMode;
import com.gt.ssrs.model.ReviewType;
import com.gt.ssrs.reviewSession.ReviewEventDao;
import com.gt.ssrs.reviewSession.model.DBReviewEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ReviewEventDaoPG implements ReviewEventDao {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventDaoPG.class);

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

    private static final String DELETE_WORD_REVIEW_EVENTS_SQL =
            "DELETE FROM review_events WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); " +
                    "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); ";

    private static final String DELETE_ALL_LEXICON_REVIEW_EVENTS_SQL =
            "DELETE FROM review_events WHERE lexicon_id = :lexiconId; " +
                    "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId; ";

    private static final String PURGE_OLD_REVIEW_EVENTS_SQL =
            "DELETE FROM review_events WHERE processed IS TRUE AND (update_instant < :cutoff OR update_instant IS NULL);";

    private final NamedParameterJdbcTemplate template;

    public ReviewEventDaoPG(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    @Override
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

    @Override
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

    @Override
    public void markEventsAsProcessed(List<DBReviewEvent> events) {
        List<Integer> eventIds = events.stream().map(event -> event.eventId()).toList();

        template.update(MARK_EVENTS_AS_PROCESSED, Map.of("eventIds", eventIds));
    }

    @Override
    public void deleteWordReviewEvents(String lexiconId, Collection<String> wordIds) {
        template.update(DELETE_WORD_REVIEW_EVENTS_SQL, Map.of(
                "lexiconId", lexiconId,
                "wordIds", wordIds));
    }

    @Override
    public void deleteAllLexiconReviewEvents(String lexiconId) {
        template.update(DELETE_ALL_LEXICON_REVIEW_EVENTS_SQL, Map.of("lexiconId", lexiconId));
    }

    @Override
    public int purgeOldReviewEvents(Instant cutoff) {
        return template.update(PURGE_OLD_REVIEW_EVENTS_SQL, Map.of("cutoff", Timestamp.from(cutoff)));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

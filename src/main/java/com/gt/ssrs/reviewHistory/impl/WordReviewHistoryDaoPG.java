package com.gt.ssrs.reviewHistory.impl;

import com.gt.ssrs.model.TestHistory;
import com.gt.ssrs.model.WordReviewHistory;
import com.gt.ssrs.reviewHistory.WordReviewHistoryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class WordReviewHistoryDaoPG implements WordReviewHistoryDao {

    private static final Logger log = LoggerFactory.getLogger(WordReviewHistoryDaoPG.class);

    private final NamedParameterJdbcTemplate template;

    private static final String GET_LEXICON_REVIEW_HISTORY_BATCH_SQL =
            "SELECT l.word_id, l.learned, l.most_recent_test_time, l.most_recent_test_relationship_id, l.current_test_delay_sec, l.current_boost, l.current_boost_expiration_delay_sec, " +
                    "h.relationship_id, h.total_tests, h.correct_tests, h.correct_streak " +
                    "FROM lexicon_review_history l LEFT JOIN lexicon_word_test_history h ON l.lexicon_id = h.lexicon_id AND l.word_id = h.word_id AND l.username = h.username " +
                    "WHERE l.lexicon_id = :lexiconId AND l.username = :username AND l.word_id IN (:wordIds) " +
                    "ORDER BY l.word_id";

    private static final String INSERT_LEXICON_REVIEW_HISTORY_SQL =
            "INSERT INTO lexicon_review_history " +
                    "(lexicon_id, word_id, username, learned, most_recent_test_time, most_recent_test_relationship_id, current_test_delay_sec, " +
                    " current_boost, current_boost_expiration_delay_sec) " +
                    "VALUES (:lexiconId, :wordId, :username, :learned, :mostRecentTestTime, :mostRecentTestRelationshipId, :currentTestDelaySec, " +
                    "        :currentBoost, :currentBoostExpirationDelaySec) " +
                    "ON CONFLICT (lexicon_id, word_id, username) " +
                    "DO UPDATE SET " +
                    "   learned = :learned, most_recent_test_time = :mostRecentTestTime, most_recent_test_relationship_id = :mostRecentTestRelationshipId, " +
                    "   current_test_delay_sec = :currentTestDelaySec, current_boost = :currentBoost, current_boost_expiration_delay_sec = :currentBoostExpirationDelaySec ";

    private static final String INSERT_LEXICON_WORD_TEST_HISTORY_SQL =
            "INSERT INTO lexicon_word_test_history " +
                    "(lexicon_id, word_id, relationship_id, username, total_tests, correct_tests, correct_streak) " +
                    "VALUES (:lexiconId, :wordId, :relationshipId, :username, :totalTests, :correctTests, :correctStreak) " +
                    "ON CONFLICT (lexicon_id, word_id, relationship_id, username) " +
                    "DO UPDATE " +
                    "SET total_tests = :totalTests, correct_tests = :correctTests, correct_streak = :correctStreak ";

    private static final String GET_WORD_IDS_TO_LEARN_SQL =
            "SELECT word_id " +
            "FROM lexicon_review_history " +
            "WHERE lexicon_id = :lexiconId AND username = :username AND learned IS FALSE " +
            "ORDER BY create_seq_num asc " +
            "LIMIT :wordCnt";

    private static final String DELETE_USER_WORD_REVIEW_HISTORY_SQL =
            "DELETE FROM lexicon_review_history WHERE lexicon_id = :lexiconId AND username = :username AND word_id IN (:wordIds); " +
            "DELETE FROM lexicon_word_test_history WHERE lexicon_id = :lexiconId AND username = :username AND word_id IN (:wordIds); ";

    private static final String DELETE_WORD_REVIEW_HISTORY_SQL =
            "DELETE FROM lexicon_review_history WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); " +
            "DELETE FROM lexicon_word_test_history WHERE lexicon_id = :lexiconId AND word_id IN (:wordIds); ";

    private static final String DELETE_LEXICON_WORD_REVIEW_HISTORY_FOR_USER_SQL =
            "DELETE FROM lexicon_review_history WHERE lexicon_id = :lexiconId AND username = :username; " +
            "DELETE FROM lexicon_word_test_history WHERE lexicon_id = :lexiconId AND username = :username; ";

    private static final String DELETE_LEXICON_WORD_REVIEW_HISTORY_SQL =
            "DELETE FROM lexicon_review_history WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM lexicon_word_test_history WHERE lexicon_id = :lexiconId; ";

    public WordReviewHistoryDaoPG(NamedParameterJdbcTemplate template) {
        this.template = template;
    }

    @Override
    public List<WordReviewHistory> createWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistories) {
        return createOrUpdateWordReviewHistory(username, wordReviewHistories);
    }

    @Override
    public List<WordReviewHistory> getWordReviewHistoryBatch(String lexiconId, String username, Collection<String> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return List.of();
        }

        return template.query(GET_LEXICON_REVIEW_HISTORY_BATCH_SQL, Map.of("lexiconId", lexiconId, "wordIds", wordIds, "username", username), (rs) -> {
            List<WordReviewHistory> lexiconWordHistories = new ArrayList<>();

            String curWordId = null;
            boolean learned = false;
            Instant mostRecentTestTime = null;
            String mostRecentTestRelationshipId = null;
            Duration currentTestDelay = null;
            double currentBoost = 0;
            Duration currentBoostExpirationDelay = null;
            Map<String, TestHistory> testHistory = Map.of();

            while (rs.next()) {
                String wordId = rs.getString("word_id");
                if (wordId != null) {
                    if (!wordId.equals(curWordId)) {
                        if (curWordId != null) {
                            lexiconWordHistories.add(new WordReviewHistory(
                                    lexiconId,
                                    username,
                                    curWordId,
                                    learned,
                                    mostRecentTestTime,
                                    mostRecentTestRelationshipId,
                                    currentTestDelay,
                                    currentBoost,
                                    currentBoostExpirationDelay,
                                    testHistory));
                        }

                        curWordId = wordId;
                        mostRecentTestTime = toInstant(rs.getTimestamp("most_recent_test_time"));
                        mostRecentTestRelationshipId = rs.getString("most_recent_test_relationship_id");
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
                lexiconWordHistories.add(new WordReviewHistory(
                        lexiconId,
                        username,
                        curWordId,
                        learned,
                        mostRecentTestTime,
                        mostRecentTestRelationshipId,
                        currentTestDelay,
                        currentBoost,
                        currentBoostExpirationDelay,
                        testHistory));
            }

            return lexiconWordHistories;
        });
    }

    @Override
    public List<WordReviewHistory> updateWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistories) {
        return createOrUpdateWordReviewHistory(username, wordReviewHistories);
    }

    @Override
    public List<String> getIdsForWordsToLearn(String lexiconId, String username, int wordCnt) {
        return template.query(
                GET_WORD_IDS_TO_LEARN_SQL,
                Map.of("lexiconId", lexiconId,
                       "username", username,
                       "wordCnt", wordCnt),
                (rs, rowNum) -> rs.getString("word_id"));
    }

    @Override
    public void deleteUserWordReviewHistories(String lexiconId, String username, Collection<String> wordIds) {
        template.update(DELETE_USER_WORD_REVIEW_HISTORY_SQL,
                Map.of("lexiconId", lexiconId,
                        "username", username,
                        "wordIds", wordIds));
    }

    @Override
    public void deleteWordReviewHistories(String lexiconId, Collection<String> wordIds) {
        template.update(DELETE_WORD_REVIEW_HISTORY_SQL,
                        Map.of("lexiconId", lexiconId,
                               "wordIds", wordIds));
    }

    @Override
    public void deleteLexiconWordReviewHistoryForUser(String lexiconId, String username) {
        template.update(DELETE_LEXICON_WORD_REVIEW_HISTORY_FOR_USER_SQL,
                        Map.of("lexiconId", lexiconId,
                               "username", username));
    }

    @Override
    public void deleteLexiconWordReviewHistory(String lexiconId) {
        template.update(DELETE_LEXICON_WORD_REVIEW_HISTORY_SQL,
                Map.of("lexiconId", lexiconId));
    }

    private List<WordReviewHistory> createOrUpdateWordReviewHistory(String username, List<WordReviewHistory> wordReviewHistories) {
        List<SqlParameterSource> wordHistoryParamList = new ArrayList<>();
        List<SqlParameterSource> testHistoryParamList = new ArrayList<>();

        for(WordReviewHistory wordHistory : wordReviewHistories) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("lexiconId", wordHistory.lexiconId());
            params.addValue("wordId", wordHistory.wordId());
            params.addValue("username", username);
            params.addValue("learned", wordHistory.learned());
            params.addValue("mostRecentTestTime", wordHistory.mostRecentTestTime() == null ? null : Timestamp.from(wordHistory.mostRecentTestTime()));
            params.addValue("mostRecentTestRelationshipId", wordHistory.mostRecentTestRelationshipId());
            params.addValue("currentTestDelaySec", wordHistory.currentTestDelay() == null ? null : wordHistory.currentTestDelay().getSeconds());
            params.addValue("currentBoost", wordHistory.currentBoost());
            params.addValue("currentBoostExpirationDelaySec", wordHistory.currentBoostExpirationDelay() == null ? null : wordHistory.currentBoostExpirationDelay().getSeconds());
            wordHistoryParamList.add(params);

            if (wordHistory.testHistory() != null) {
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
        }

        int[] rowCnts = template.batchUpdate(INSERT_LEXICON_REVIEW_HISTORY_SQL, wordHistoryParamList.toArray(new SqlParameterSource[0]));
        template.batchUpdate(INSERT_LEXICON_WORD_TEST_HISTORY_SQL, testHistoryParamList.toArray(new SqlParameterSource[0]));

        return getSavedReviewHistory(wordReviewHistories, rowCnts);
    }

    private List<WordReviewHistory> getSavedReviewHistory(List<WordReviewHistory> wordReviewHistories, int[] savedRowCnts) {
        List<WordReviewHistory> savedReviewHistory = new ArrayList<>();

        for (int i = 0; i < wordReviewHistories.size(); i++) {
            if (savedRowCnts.length > i && savedRowCnts[i] > 0) {
                savedReviewHistory.add(wordReviewHistories.get(i));
            }
        }

        return savedReviewHistory;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

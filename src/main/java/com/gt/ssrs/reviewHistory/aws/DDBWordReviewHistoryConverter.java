package com.gt.ssrs.reviewHistory.aws;

import com.gt.ssrs.model.TestHistory;
import com.gt.ssrs.model.WordReviewHistory;
import com.gt.ssrs.reviewHistory.model.LearnedStatus;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class DDBWordReviewHistoryConverter {

    private static final String ID_DELIMITER = "#";

    public static DDBWordReviewHistory convertWordReviewHistory(WordReviewHistory wordReviewHistory) {
        return convertWordReviewHistory(wordReviewHistory, Instant.now());
    }

    public static DDBWordReviewHistory convertWordReviewHistory(WordReviewHistory wordReviewHistory, Instant createInstant) {
        return DDBWordReviewHistory.builder()
                .id(buildId(wordReviewHistory))
                .lexiconId(wordReviewHistory.lexiconId())
                .username(wordReviewHistory.username())
                .wordId(wordReviewHistory.wordId())
                .learned(wordReviewHistory.learned() ? LearnedStatus.Learned : LearnedStatus.ReadyToLearn)
                .mostRecentTestTime(wordReviewHistory.mostRecentTestTime())
                .mostRecentTestRelationshipId(wordReviewHistory.mostRecentTestRelationshipId())
                .currentTestDelay(wordReviewHistory.currentTestDelay())
                .currentBoost(wordReviewHistory.currentBoost())
                .currentBoostExpiration(wordReviewHistory.currentBoostExpirationDelay())
                .wordTestHistory(wordReviewHistory.testHistory() == null ? null :wordReviewHistory.testHistory()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(entry -> entry.getKey(), entry -> convertTestHistory(entry.getValue()))))
                .createInstant(createInstant)
                .updateInstant(Instant.now())
                .build();
    }

    public static List<WordReviewHistory> convertDDBWordReviewHistoryBatch(List<DDBWordReviewHistory> ddbWordReviewHistories) {
        return ddbWordReviewHistories.stream()
                .map(ddbWordReviewHistory -> new WordReviewHistory(
                        ddbWordReviewHistory.lexiconId(),
                        ddbWordReviewHistory.username(),
                        ddbWordReviewHistory.wordId(),
                        ddbWordReviewHistory.learned() == LearnedStatus.ReadyToLearn ? false : true,
                        ddbWordReviewHistory.mostRecentTestTime(),
                        ddbWordReviewHistory.mostRecentTestRelationshipId(),
                        ddbWordReviewHistory.currentTestDelay(),
                        ddbWordReviewHistory.currentBoost(),
                        ddbWordReviewHistory.currentBoostExpiration(),
                        ddbWordReviewHistory.wordTestHistory() == null ? null : ddbWordReviewHistory.wordTestHistory()
                                .entrySet()
                                .stream()
                                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> convertDDBTestHistory(entry.getValue())))))
                .collect(Collectors.toUnmodifiableList());
    }

    public static DDBTestHistory convertTestHistory(TestHistory testHistory) {
        return DDBTestHistory.builder()
                .totalTests(testHistory.totalTests())
                .correctTests(testHistory.correct())
                .correctStreak(testHistory.correctStreak())
                .build();
    }

    public static TestHistory convertDDBTestHistory(DDBTestHistory ddbTestHistory) {
        return new TestHistory(ddbTestHistory.totalTests(), ddbTestHistory.correctTests(), ddbTestHistory.correctStreak());
    }

    public static String buildId(WordReviewHistory wordReviewHistory) {
        return buildId(wordReviewHistory.lexiconId(), wordReviewHistory.username());
    }

    public static String buildId(String lexiconId, String username) {
        return lexiconId + ID_DELIMITER + username;
    }
}
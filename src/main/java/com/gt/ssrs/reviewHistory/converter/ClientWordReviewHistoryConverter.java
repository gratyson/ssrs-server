package com.gt.ssrs.reviewHistory.converter;

import com.gt.ssrs.model.WordReviewHistory;
import com.gt.ssrs.reviewHistory.model.ClientWordReviewHistory;

import java.util.Map;

public class ClientWordReviewHistoryConverter {

    public static WordReviewHistory convertClientWordReviewHistory(String username, ClientWordReviewHistory clientWordReviewHistory) {
        return new WordReviewHistory(
                clientWordReviewHistory.lexiconId(),
                username,
                clientWordReviewHistory.wordId(),
                clientWordReviewHistory.learned(),
                clientWordReviewHistory.mostRecentTestTime(),
                clientWordReviewHistory.mostRecentTestRelationshipId(),
                clientWordReviewHistory.currentTestDelay(),
                clientWordReviewHistory.currentBoost(),
                clientWordReviewHistory.currentBoostExpirationDelay(),
                clientWordReviewHistory.testHistory() == null ? null : Map.copyOf(clientWordReviewHistory.testHistory()));
    }

    public static ClientWordReviewHistory convertWordReviewHistory(WordReviewHistory wordReviewHistory) {
        return new ClientWordReviewHistory(
                wordReviewHistory.lexiconId(),
                wordReviewHistory.wordId(),
                wordReviewHistory.learned(),
                wordReviewHistory.mostRecentTestTime(),
                wordReviewHistory.mostRecentTestRelationshipId(),
                wordReviewHistory.currentTestDelay(),
                wordReviewHistory.currentBoost(),
                wordReviewHistory.currentBoostExpirationDelay(),
                wordReviewHistory.testHistory() == null ? null : Map.copyOf(wordReviewHistory.testHistory()));
    }
}

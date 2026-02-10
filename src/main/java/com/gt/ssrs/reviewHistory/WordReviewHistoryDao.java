package com.gt.ssrs.reviewHistory;

import com.gt.ssrs.model.WordReviewHistory;

import java.util.Collection;
import java.util.List;

public interface WordReviewHistoryDao {

    List<WordReviewHistory> createWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistories);

    List<WordReviewHistory> getWordReviewHistoryBatch(String lexiconId, String username, Collection<String> wordIds);

    List<WordReviewHistory> updateWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistories);

    List<String> getIdsForWordsToLearn(String lexiconId, String username, int wordCnt);

    void deleteUserWordReviewHistories(String lexiconId, String username, Collection<String> wordIds);

    void deleteWordReviewHistories(String lexiconId, Collection<String> wordIds);

    void deleteLexiconWordReviewHistoryForUser(String lexiconId, String username);

    void deleteLexiconWordReviewHistory(String lexiconId);
}

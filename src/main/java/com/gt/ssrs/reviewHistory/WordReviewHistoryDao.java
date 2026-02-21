package com.gt.ssrs.reviewHistory;

import com.gt.ssrs.model.WordReviewHistory;
import com.gt.ssrs.reviewHistory.model.LearnedStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface WordReviewHistoryDao {

    List<WordReviewHistory> createWordReviewHistory(String username, List<WordReviewHistory> wordReviewHistories);

    List<WordReviewHistory> getWordReviewHistory(String lexiconId, String username, Collection<String> wordIds);

    List<WordReviewHistory> updateWordReviewHistory(String username, List<WordReviewHistory> wordReviewHistories);

    List<WordReviewHistory> getAllWordReviewHistory(String lexiconId, String username);

    List<String> getIdsForWordsToLearn(String lexiconId, String username, int wordCnt);

    void deleteUserWordReviewHistories(String lexiconId, String username, Collection<String> wordIds);

    void deleteWordReviewHistories(String lexiconId, Collection<String> wordIds);

    void deleteLexiconWordReviewHistoryForUser(String lexiconId, String username);

    void deleteLexiconWordReviewHistory(String lexiconId);

    int getTotalLearnedWordCount(String lexiconId, String username);

    Map<LearnedStatus, List<String>> getWordIdsForUserByLearned(String lexiconId, String username);
}

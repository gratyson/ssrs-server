package com.gt.ssrs.reviewHistory;

import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordReviewHistory;
import com.gt.ssrs.reviewSession.ScheduledReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class WordReviewHistoryService {

    private static final Logger log = LoggerFactory.getLogger(WordReviewHistoryService.class);

    private final WordReviewHistoryDao wordReviewHistoryDao;

    @Autowired
    public WordReviewHistoryService(WordReviewHistoryDao wordReviewHistoryDao) {
        this.wordReviewHistoryDao = wordReviewHistoryDao;
    }

    public void createEmptyWordReviewHistoryForWords(String username, List<Word> words) {
        List<WordReviewHistory> wordReviewHistoryToSave = words.stream()
                .map(word -> createEmptyWordReviewHistory(word.lexiconId(), username, word.id()))
                .collect(Collectors.toUnmodifiableList());

        wordReviewHistoryDao.createWordReviewHistoryBatch(username, wordReviewHistoryToSave);
    }

    public void resetLearningHistory(String lexiconId, String username, Collection<String> wordIds) {
        List<WordReviewHistory> wordReviewHistoryToSave = wordReviewHistoryDao.getWordReviewHistoryBatch(lexiconId, username, wordIds)
                .stream()
                .map(wordReviewHistory -> createEmptyWordReviewHistory(lexiconId, username, wordReviewHistory.wordId()))
                .collect(Collectors.toUnmodifiableList());

        wordReviewHistoryDao.updateWordReviewHistoryBatch(username, wordReviewHistoryToSave);
    }


    public List<WordReviewHistory> getWordReviewHistory(String lexiconId, String username, Collection<String> wordIds) {
        return wordReviewHistoryDao.getWordReviewHistoryBatch(lexiconId, username, wordIds);
    }

    public List<WordReviewHistory> updateWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistories) {
        return wordReviewHistoryDao.updateWordReviewHistoryBatch(username, wordReviewHistories);
    }

    public List<String> getIdsForWordsToLearn(String lexiconId, String username, int wordCnt) {
        return wordReviewHistoryDao.getIdsForWordsToLearn(lexiconId, username, wordCnt);
    }

    public void deleteUserWordReviewHistories(String lexiconId, String username, Collection<String> wordIds) {
        wordReviewHistoryDao.deleteUserWordReviewHistories(lexiconId, username, wordIds);
    }

    public void deleteWordReviewHistories(String lexiconId, Collection<String> wordIds) {
        wordReviewHistoryDao.deleteWordReviewHistories(lexiconId, wordIds);
    }

    public void deleteLexiconWordReviewHistoryForUser(String lexiconId, String username) {
        wordReviewHistoryDao.deleteLexiconWordReviewHistoryForUser(lexiconId, username);
    }

    public void deleteLexiconWordReviewHistory(String lexiconId) {
        wordReviewHistoryDao.deleteLexiconWordReviewHistory(lexiconId);
    }

    private WordReviewHistory createEmptyWordReviewHistory(String lexiconId, String username, String wordId) {
        return new WordReviewHistory(
                lexiconId,
                username,
                wordId,
                false,
                null,
                null,
                null,
                0,
                null,
                null);
    }
}

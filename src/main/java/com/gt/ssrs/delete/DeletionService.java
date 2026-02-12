package com.gt.ssrs.delete;

import com.gt.ssrs.exception.UserAccessException;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.reviewHistory.WordReviewHistoryService;
import com.gt.ssrs.reviewSession.ReviewSessionService;
import com.gt.ssrs.reviewSession.ScheduledReviewService;
import com.gt.ssrs.word.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

// Service to manage handling deletions of items and their dependencies. This exists in part to avoid
// circular dependencies in data services, but also to centralize the logic of deleting dependant data
@Component
public class DeletionService {

    private static final Logger log = LoggerFactory.getLogger(DeletionService.class);

    private final LexiconService lexiconService;
    private final WordService wordService;
    private final WordReviewHistoryService wordReviewHistoryService;
    private final ScheduledReviewService scheduledReviewService;
    private final ReviewSessionService reviewSessionService;

    @Autowired
    public DeletionService(LexiconService lexiconService, WordService wordService, WordReviewHistoryService wordReviewHistoryService, ScheduledReviewService scheduledReviewService, ReviewSessionService reviewSessionService) {
        this.lexiconService = lexiconService;
        this.wordService = wordService;
        this.wordReviewHistoryService = wordReviewHistoryService;
        this.scheduledReviewService = scheduledReviewService;
        this.reviewSessionService = reviewSessionService;
    }

    public void deleteLexicon(String username, String lexiconId) {
        verifyCanEditLexicon(username, lexiconId);

        reviewSessionService.deleteAllLexiconReviewEvents(lexiconId, username);
        wordReviewHistoryService.deleteLexiconWordReviewHistory(lexiconId);
        wordService.deleteLexiconWords(lexiconId, username);
        lexiconService.deleteLexiconMetadata(lexiconId, username);
    }

    public void deleteWords(String username, String lexiconId, Collection<String> wordIds) {
        verifyCanEditLexicon(username, lexiconId);

        reviewSessionService.deleteScheduledReviewsForWords(lexiconId, wordIds, username);
        wordReviewHistoryService.deleteWordReviewHistories(lexiconId, wordIds);
        wordService.deleteWords(lexiconId, wordIds, username);
    }

    public void deleteUserWordTestHistory(String username, String lexiconId, Collection<String> wordIds) {
        scheduledReviewService.deleteUserScheduledReviewForWords(lexiconId, wordIds, username);
        wordReviewHistoryService.deleteUserWordReviewHistories(lexiconId, username, wordIds);
    }

    public void deleteUserScheduledReviews(String username, String lexiconId, Collection<String> wordIds) {
        wordReviewHistoryService.deleteUserWordReviewHistories(lexiconId, username, wordIds);
    }

    private void verifyCanEditLexicon(String username, String lexiconId) {
        LexiconMetadata lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);

        if (lexiconMetadata == null || !lexiconMetadata.owner().equals(username)) {
            String errMsg = "User does not have permission to edit lexicon";

            log.error(errMsg);
            throw new UserAccessException(errMsg);
        }
    }
}

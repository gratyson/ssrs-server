package com.gt.ssrs.reviewSession;

import com.gt.ssrs.exception.UserAccessException;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ScheduledReviewService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReviewService.class);

    private final LexiconService lexiconService;
    private final ScheduledReviewDao scheduledReviewDao;
    private final double futureEventAllowedRatio;

    @Autowired
    public ScheduledReviewService(LexiconService lexiconService,
                                  ScheduledReviewDao scheduledReviewDao,
                                  @Value("${ssrs.review.futureEventAllowedRatio}") double futureEventAllowedRatio) {
        this.lexiconService = lexiconService;
        this.scheduledReviewDao = scheduledReviewDao;

        this.futureEventAllowedRatio = futureEventAllowedRatio;
    }

    public int scheduleReviewsForHistory(String username, String lexiconId, List<WordReviewHistory> wordReviewHistories, List<ScheduledReview> scheduledReviews) {
        if (wordReviewHistories.size() == 0) {
            return 0;
        }

        log.info("Saving new review history for {} words", wordReviewHistories.size());

        List<WordReviewHistory> wordReviewHistoriesToSave = wordReviewHistories
                .stream()
                .filter(wordReviewHistory -> wordReviewHistory.learned())   // don't create histories if not yet learned
                .toList();

        Map<String, ScheduledReview> scheduledReviewsByWordId = scheduledReviews.stream()
                .filter(scheduledReview -> !scheduledReview.completed() && scheduledReview.lexiconId().equals(lexiconId))
                .map(scheduledReview -> withOwner(username, scheduledReview))
                .collect(Collectors.toMap(scheduledReview -> scheduledReview.wordId(), scheduledReview -> scheduledReview));
        
        return createOrUpdateScheduledReviewBatch(lexiconId, username, wordReviewHistoriesToSave, scheduledReviewsByWordId);
    }

    private ScheduledReview withOwner(String username, ScheduledReview scheduledReview) {
        return new ScheduledReview(
                scheduledReview.id() == null || scheduledReview.id().isBlank() ? UUID.randomUUID().toString() : scheduledReview.id(),
                username,
                scheduledReview.lexiconId(),
                scheduledReview.wordId(),
                scheduledReview.reviewType(),
                scheduledReview.testRelationshipId(),
                scheduledReview.scheduledTestTime(),
                scheduledReview.testDelay(),
                scheduledReview.completed());
    }

    private int createOrUpdateScheduledReviewBatch(String lexiconId, String username, List<WordReviewHistory> wordReviewHistories, Map<String, ScheduledReview> scheduledReviewsByWordId) {
        Language language = Language.getLanguageById(lexiconService.getLexiconLanguageId(lexiconId));

        List<String> wordHistoryWordIds = wordReviewHistories.stream().map(WordReviewHistory::wordId).toList();
        Map<String, List<ScheduledReview>> existingScheduledReviews =
                scheduledReviewDao.loadScheduledReviewsForWords(lexiconId, username, wordHistoryWordIds)
                        .stream()
                        .collect(Collectors.groupingBy(ScheduledReview::wordId));

        List<ScheduledReview> reviewsToSave = new ArrayList<>();
        for(WordReviewHistory wordReviewHistory : wordReviewHistories) {
            ScheduledReview reviewToSave = scheduledReviewsByWordId.get(wordReviewHistory.wordId());
            String idToUse = getIdToUse(reviewToSave, existingScheduledReviews.computeIfAbsent(wordReviewHistory.wordId(), (wordId) -> List.of()));

            if (reviewToSave != null) {
                reviewToSave = withId(reviewToSave, idToUse);
            } else {
                log.warn("Building ScheduledReview from history");
                reviewToSave = buildScheduledReviewFromHistory(idToUse, wordReviewHistory, language);
            }

            reviewsToSave.add(reviewToSave);
        }

        scheduledReviewDao.createScheduledReviewsBatch(reviewsToSave);

        return reviewsToSave.size();
    }

    public Optional<ScheduledReview> loadEarliestScheduledReview(String lexiconId, String username, String wordId) {
        Optional<ScheduledReview> earliestScheduledReview = scheduledReviewDao.loadScheduledReviewsForWords(username, lexiconId, List.of(wordId))
                .stream()
                .filter(scheduledReview -> scheduledReview.reviewType().equals(ReviewType.Review))
                .min(Comparator.comparing(ScheduledReview::scheduledTestTime));

        if (earliestScheduledReview.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(earliestScheduledReview.get());
    }

    public Map<String, Integer> getScheduledReviewCounts(String username, String lexiconId, Optional<Instant> cutoffInstant) {
        Map<String, Integer> scheduledReviewCounts = new HashMap<>();

        for (ScheduledReview scheduledReview : getCurrentScheduledReviewForLexicon(lexiconId, username,Optional.empty(), cutoffInstant)) {
            scheduledReviewCounts.put(scheduledReview.testRelationshipId(), scheduledReviewCounts.getOrDefault(scheduledReview.testRelationshipId(), 0) + 1);
        }

        return scheduledReviewCounts;
    }

    public List<ScheduledReview> getScheduledReviewForWords(String username, String lexiconId, List<String> wordIds) {
        return scheduledReviewDao.loadScheduledReviewsForWords(username, lexiconId, wordIds);
    }

    public void adjustNextReviewTimes(String lexiconId, Duration adjustment, String username) {
        verifyUserAccessAllowed(lexiconId, username);

        scheduledReviewDao.adjustNextReviewTimes(lexiconId, username, adjustment);
    }

    public void deleteUserScheduledReviewForWords(String lexiconId, Collection<String> wordIds, String username) {
        scheduledReviewDao.deleteUserScheduledReviewForWords(lexiconId, wordIds, username);
    }

    public void deleteScheduledReviewsForWords(String lexiconId, Collection<String> wordIds) {
        scheduledReviewDao.deleteScheduledReviewsForWords(lexiconId, wordIds);
    }

    public void deleteAllLexiconReviewEventsForUser(String lexiconId, String username) {
        scheduledReviewDao.deleteAllLexiconReviewEventsForUser(lexiconId, username);
    }

    public void deleteAllLexiconReviewEvents(String lexiconId) {
        scheduledReviewDao.deleteAllLexiconReviewEvents(lexiconId);
    }

    private String getIdToUse(ScheduledReview newScheduledReview, List<ScheduledReview> existingScheduledReviews) {
        for (ScheduledReview scheduledReview : existingScheduledReviews) {
            if (!scheduledReview.completed() && scheduledReview.reviewType() == ReviewType.Review) {
                return scheduledReview.id();
            }
        }

        if (newScheduledReview != null && newScheduledReview.id() != null && !newScheduledReview.id().isBlank()) {
            return newScheduledReview.id();
        }

        return UUID.randomUUID().toString();
    }

    private ScheduledReview buildScheduledReviewFromHistory(String id, WordReviewHistory wordReviewHistory, Language language) {
        String idToUse = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;

        Instant nextTestTime = wordReviewHistory.mostRecentTestTime().plus(wordReviewHistory.currentTestDelay());
        String nextTestRelationshipId = WordReviewHelper.getNextTestRelationship(language, language.getReviewTestRelationships(), wordReviewHistory);

        return new ScheduledReview(idToUse, wordReviewHistory.username(), wordReviewHistory.lexiconId(), wordReviewHistory.wordId(), ReviewType.Review, nextTestRelationshipId,
                nextTestTime, Duration.between(wordReviewHistory.mostRecentTestTime(), nextTestTime), false);
    }

    public List<ScheduledReview> getCurrentScheduledReviewForLexicon(String lexiconId, String username, Optional<String> reviewRelationship, Optional<Instant> cutoffInstant) {
        Instant now = Instant.now();

        List<ScheduledReview> scheduledReviews = scheduledReviewDao.loadScheduledReviews(username, lexiconId, reviewRelationship.orElse(""), cutoffInstant);


        if (cutoffInstant.isPresent() && cutoffInstant.get().isAfter(now)) {
            scheduledReviews = scheduledReviews.stream().filter(scheduledReview -> isFutureEventAllowed(scheduledReview, now)).toList();
        }

        return scheduledReviews;
    }

    private boolean isFutureEventAllowed(ScheduledReview dbScheduledReview, Instant now) {
        return (dbScheduledReview.scheduledTestTime().toEpochMilli() - now.toEpochMilli()) < (dbScheduledReview.testDelay().toMillis() * (1 - futureEventAllowedRatio));
    }

    private void verifyUserAccessAllowed(String lexiconId, String username) {
        LexiconMetadata lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);

        if (lexiconMetadata == null) {
            throw new IllegalArgumentException("Lexicon " + lexiconId + " does not exist");
        }

        verifyUserAccessAllowed(lexiconMetadata, username);
    }

    private void verifyUserAccessAllowed(LexiconMetadata lexiconMetadata, String username) {
        if (!lexiconMetadata.owner().equals(username)) {
            String errMsg = "User " + username + " does not have access to review lexicon " + lexiconMetadata.id();

            log.error(errMsg);
            throw new UserAccessException(errMsg);
        }
    }

    private ScheduledReview withId(ScheduledReview scheduledReview, String id) {
        String idToUse;
        if (id != null && !id.isBlank()) {
            idToUse = id;
        } else if (scheduledReview.id() != null && !scheduledReview.id().isBlank()) {
            idToUse = scheduledReview.id();
        } else {
            idToUse = UUID.randomUUID().toString();
        }

        return new ScheduledReview(
                idToUse,
                scheduledReview.username(),
                scheduledReview.lexiconId(),
                scheduledReview.wordId(),
                scheduledReview.reviewType(),
                scheduledReview.testRelationshipId(),
                scheduledReview.scheduledTestTime(),
                scheduledReview.testDelay(),
                scheduledReview.completed());
    }
}
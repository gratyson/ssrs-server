package com.gt.ssrs.reviewSession;

import com.gt.ssrs.model.ScheduledReview;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduledReviewDao {

    void createScheduledReviewsBatch(List<ScheduledReview> scheduledReviews);

    int markScheduledReviewComplete(String scheduledReviewId);

    List<ScheduledReview> loadScheduledReviews(String username, String lexiconId, String testRelationshipId, Optional<Instant> cutoffInstant);

    List<ScheduledReview> loadScheduledReviewsForWords(String username, String lexiconId, Collection<String> wordIds);

    void deleteUserScheduledReviewForWords(String lexiconId, Collection<String> wordIds, String username);

    void deleteScheduledReviewsForWords(String lexiconId, Collection<String> wordIds);

    void deleteAllLexiconReviewEventsForUser(String lexiconId, String username);

    void deleteAllLexiconReviewEvents(String lexiconId);

    int adjustNextReviewTimes(String lexiconId, String username, Duration adjustment);

    int purgeOldScheduledReviews(Instant cutoff);
}

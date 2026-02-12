package com.gt.ssrs.reviewSession;

import com.gt.ssrs.reviewSession.model.DBScheduledReview;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduledReviewDao {

    void createScheduledReviewsBatch(List<DBScheduledReview> scheduledReviews, String owner);

    public List<DBScheduledReview> loadScheduledReviews(String owner, String lexiconId, String testRelationshipId, Optional<Instant> cutoffInstant);

    public List<DBScheduledReview> loadScheduledReviewsForWords(String owner, String lexiconId, Collection<String> wordIds);

    public void deleteUserScheduledReviewForWords(String lexiconId, Collection<String> wordIds, String username);

    public int adjustNextReviewTimes(String lexiconId, Duration adjustment);

    int purgeOldScheduledReviews(Instant cutoff);
}

package com.gt.ssrs.reviewSession;

import com.gt.ssrs.model.ReviewEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReviewEventDao {

    boolean saveReviewEvent(ReviewEvent event);

    List<ReviewEvent> loadUnprocessedReviewEventsForUser(String username, String lexiconId);

    List<String> markEventsAsProcessed(List<ReviewEvent> events);

    void deleteWordReviewEvents(String lexiconId, Collection<String> wordIds);

    void deleteWordReviewEventsForUser(String lexiconId, String username, Collection<String> wordIds);

    void deleteAllLexiconReviewEvents(String lexiconId);

    void deleteAllLexiconReviewEventsForUser(String lexiconId, String username);

    int purgeOldReviewEvents(Instant cutoff);
}

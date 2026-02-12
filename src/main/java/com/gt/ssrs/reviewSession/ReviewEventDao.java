package com.gt.ssrs.reviewSession;

import com.gt.ssrs.reviewSession.model.DBReviewEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReviewEventDao {

    boolean saveReviewEvent(DBReviewEvent event, String scheduledReviewId);

    List<DBReviewEvent> loadUnprocessedReviewEventsForUserBatch(String username, String lexiconId, int lastId, int batchSize);

    public void markEventsAsProcessed(List<DBReviewEvent> events);

    public void deleteWordReviewEvents(String lexiconId, Collection<String> wordIds);

    public void deleteAllLexiconReviewEvents(String lexiconId);

    int purgeOldReviewEvents(Instant cutoff);
}

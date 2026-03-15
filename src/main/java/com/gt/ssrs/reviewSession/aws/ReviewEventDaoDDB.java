package com.gt.ssrs.reviewSession.aws;

import com.gt.ssrs.reviewSession.ReviewEventDao;
import com.gt.ssrs.model.ReviewEvent;
import com.gt.ssrs.reviewSession.model.ReviewEventStatus;
import com.gt.ssrs.util.ListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ReviewEventDaoDDB implements ReviewEventDao {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventDaoDDB.class);

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final int deleteAfterDays;
    private final int maxWriteBatchSize;

    private final DynamoDbTable<DDBReviewEvent> reviewEventsTable;

    @Autowired
    public ReviewEventDaoDDB(DynamoDbClient dynamoDbClient,
                             DynamoDbEnhancedClient dynamoDbEnhancedClient,
                             @Value("${aws.dynamodb.reviews.deleteAfterDays}") int deleteAfterDays,
                             @Value("${aws.dynamodb.maxWriteBatchSize}") int maxWriteBatchSize) {
        this.dynamoDbClient = dynamoDbClient;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.deleteAfterDays = deleteAfterDays;
        this.maxWriteBatchSize = maxWriteBatchSize;

        reviewEventsTable = dynamoDbEnhancedClient.table(DDBReviewEvent.TABLE_NAME, TableSchema.fromImmutableClass(DDBReviewEvent.class));
    }

    @Override
    public boolean saveReviewEvent(ReviewEvent event) {
        try {
            reviewEventsTable.putItem(DDBReviewEventConverter.convertReviewEvent(event));
        } catch (DynamoDbException ex) {
            log.error("Failed to save review event", ex);
            return false;
        }

        return true;
    }

    @Override
    public List<ReviewEvent> loadUnprocessedReviewEventsForUser(String username, String lexiconId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(b -> b
                        .partitionValue(lexiconId)
                        .addSortValue(username)
                        .addSortValue(ReviewEventStatus.Unprocessed.name())))
                .scanIndexForward(true)
                .build();

        SdkIterable<Page<DDBReviewEvent>> results = reviewEventsTable.index(DDBReviewEvent.REVIEW_EVENT_BY_LEXICON_INDEX_NAME).query(request);

        return results.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbReviewEvent -> DDBReviewEventConverter.convertDDBReviewEvent(ddbReviewEvent))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> markEventsAsProcessed(List<ReviewEvent> events) {
        List<String> processEvents = new ArrayList<>();

        for (List<ReviewEvent> subList : ListUtil.partitionList(events, maxWriteBatchSize)) {
            processEvents.addAll(markEventsAsProcessedBatch(subList));
        }

        return processEvents;
    }

    private List<String> markEventsAsProcessedBatch(List<ReviewEvent> events) {
        Instant deleteAfterInstant = Instant.now().plus(Duration.ofDays(deleteAfterDays));
        WriteBatch.Builder<DDBReviewEvent> batchBuilder = WriteBatch.builder(DDBReviewEvent.class).mappedTableResource(reviewEventsTable);
        events.forEach(reviewEvent -> batchBuilder.addPutItem(DDBReviewEventConverter.convertReviewEvent(reviewEvent, true, deleteAfterInstant)));

        BatchWriteResult batchWriteResult = dynamoDbEnhancedClient.batchWriteItem(b -> b.writeBatches(batchBuilder.build()));

        Set<String> skippedIds = batchWriteResult.unprocessedPutItemsForTable(reviewEventsTable).stream()
                .map(ddbReviewEvent -> ddbReviewEvent.id())
                .collect(Collectors.toSet());

       return events.stream()
                .map(reviewEvent -> reviewEvent.eventId())
                .filter(eventId -> !skippedIds.contains(eventId))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteWordReviewEvents(String lexiconId, Collection<String> wordIds) {
        for (String eventId : getEventIdsForWords(lexiconId, Optional.empty(), Optional.of(wordIds))) {
            reviewEventsTable.deleteItem(Key.builder().partitionValue(eventId).build());
        }
    }

    @Override
    public void deleteWordReviewEventsForUser(String lexiconId, String username, Collection<String> wordIds) {
        for (String eventId : getEventIdsForWords(lexiconId, Optional.of(username), Optional.of(wordIds))) {
            reviewEventsTable.deleteItem(Key.builder().partitionValue(eventId).build());
        }
    }

    @Override
    public void deleteAllLexiconReviewEvents(String lexiconId) {
        for (String eventId : getEventIdsForWords(lexiconId, Optional.empty(),Optional.empty())) {
            reviewEventsTable.deleteItem(Key.builder().partitionValue(eventId).build());
        }
    }

    @Override
    public void deleteAllLexiconReviewEventsForUser(String lexiconId, String username) {
        for (String entryId : getEventIdsForWords(lexiconId, Optional.of(username), Optional.empty())) {
            reviewEventsTable.deleteItem(Key.builder().partitionValue(entryId).build());
        }
    }

    private List<String> getEventIdsForWords(String lexiconId, Optional<String> username, Optional<Collection<String>> wordIds) {
        Key.Builder keyBuilder = Key.builder().partitionValue(lexiconId);
        if (username.isPresent()) {
            keyBuilder.addSortValue(username.get());
        }

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(keyBuilder.build()))
                .attributesToProject(
                        DDBReviewEvent.ID_ATTRIBUTE_NAME,
                        DDBReviewEvent.WORD_ID_ATTRIBUTE_NAME)
                .scanIndexForward(true)
                .build();

        SdkIterable<Page<DDBReviewEvent>> results = reviewEventsTable.index(DDBReviewEvent.REVIEW_EVENT_BY_LEXICON_INDEX_NAME).query(request);

        Set<String> wordIdSet = Set.copyOf(wordIds.orElseGet(() -> List.of()));
        return results.stream()
                .flatMap(page -> page.items().stream())
                .filter(ddbReviewEvent -> wordIds.isEmpty() || wordIdSet.contains(ddbReviewEvent.wordId()))
                .map(ddbReviewEvent -> ddbReviewEvent.id())
                .collect(Collectors.toList());
    }

    @Override
    public int purgeOldReviewEvents(Instant cutoff) {
        return 0;
    }
}

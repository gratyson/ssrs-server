package com.gt.ssrs.reviewSession.aws;

import com.gt.ssrs.model.ScheduledReview;
import com.gt.ssrs.reviewSession.ScheduledReviewDao;
import com.gt.ssrs.reviewSession.model.ScheduledReviewStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ScheduledReviewDaoDDB implements ScheduledReviewDao {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReviewDaoDDB.class);

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final int deleteAfterDays;

    private final DynamoDbTable<DDBScheduledReview> scheduledReviewTable;

    @Autowired
    public ScheduledReviewDaoDDB(DynamoDbClient dynamoDbClient,
                                 DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                 @Value("${aws.dynamodb.reviews.deleteAfterDays}") int deleteAfterDays) {
        this.dynamoDbClient = dynamoDbClient;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.deleteAfterDays = deleteAfterDays;

        scheduledReviewTable = dynamoDbEnhancedClient.table(DDBScheduledReview.TABLE_NAME, TableSchema.fromImmutableClass(DDBScheduledReview.class));
    }

    @Override
    public void createScheduledReviewsBatch(List<ScheduledReview> scheduledReviews) {
        WriteBatch.Builder<DDBScheduledReview> batchBuilder = WriteBatch.builder(DDBScheduledReview.class).mappedTableResource(scheduledReviewTable);
        scheduledReviews.forEach(scheduledReview -> batchBuilder.addPutItem(DDBScheduledReviewConverter.convertScheduledReview(scheduledReview)));

        dynamoDbEnhancedClient.batchWriteItem(b -> b.writeBatches(batchBuilder.build()));

        // TODO: Handle unprocessed items?
    }

    @Override
    public int markScheduledReviewComplete(String scheduledReviewId) {
        DDBScheduledReview completedReview = DDBScheduledReview.builder()
                .id(scheduledReviewId)
                .status(ScheduledReviewStatus.COMPLETED)
                .deleteAfterInstant(Instant.now().plus(Duration.ofDays(deleteAfterDays)))
                .build();

        UpdateItemEnhancedRequest<DDBScheduledReview> updateRequest = UpdateItemEnhancedRequest.builder(DDBScheduledReview.class)
                .item(completedReview)
                .ignoreNullsMode(IgnoreNullsMode.SCALAR_ONLY)
                .build();

        DDBScheduledReview updatedReview = scheduledReviewTable.updateItem(updateRequest);

        if (updatedReview == null) {
            return 0;
        }

        return 1;
    }

    @Override
    public List<ScheduledReview> loadScheduledReviews(String username, String lexiconId, String testRelationshipId, Optional<Instant> cutoffInstant) {
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.sortLessThanOrEqualTo(Key.builder()
                                .partitionValue(lexiconId)
                                .addSortValue(username)
                                .addSortValue(ScheduledReviewStatus.SCHEDULED.name())
                                .addSortValue(cutoffInstant.orElse(Instant.now()).toString())
                                .build()))
                .scanIndexForward(true);

        if (testRelationshipId != null && !testRelationshipId.isBlank()) {
            requestBuilder.filterExpression(Expression.builder()
                    .expression("#testRelationshipId = :testRelationshipId")
                    .expressionNames(Map.of("#testRelationshipId", DDBScheduledReview.TEST_RELATIONSHIP_ID_ATTRIBUTE_NAME))
                    .expressionValues(Map.of(":testRelationshipId", AttributeValue.builder().s(testRelationshipId).build()))
                    .build());
        }

        SdkIterable<Page<DDBScheduledReview>> result = scheduledReviewTable.index(DDBScheduledReview.SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME).query(requestBuilder.build());

        return result.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbScheduledReview -> DDBScheduledReviewConverter.convertDDBScheduledReview(ddbScheduledReview))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<ScheduledReview> loadScheduledReviewsForWords(String username, String lexiconId, Collection<String> wordIds) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortGreaterThan(Key.builder()
                        .partitionValue(lexiconId)
                        .sortValue(username)
                        .sortValue(ScheduledReviewStatus.SCHEDULED.name())
                        .build()))
                .filterExpression(buildWordIdFilterExpression(wordIds))
                .scanIndexForward(true)
                .build();

        SdkIterable<Page<DDBScheduledReview>> result = scheduledReviewTable.index(DDBScheduledReview.SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME).query(request);

        return result.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbScheduledReview -> DDBScheduledReviewConverter.convertDDBScheduledReview(ddbScheduledReview))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void deleteUserScheduledReviewForWords(String lexiconId, Collection<String> wordIds, String username) {
        List<String> idsToDelete = getScheduledReviewIdsFor(lexiconId, username, wordIds);

        batchDelete(idsToDelete);
    }

    @Override
    public void deleteScheduledReviewsForWords(String lexiconId, Collection<String> wordIds) {
        List<String> idsToDelete = getScheduledReviewIdsFor(lexiconId, "", wordIds);

        batchDelete(idsToDelete);
    }

    @Override
    public void deleteAllLexiconReviewEventsForUser(String lexiconId, String username) {
        List<String> idsToDelete = getScheduledReviewIdsFor(lexiconId, username, null);

        batchDelete(idsToDelete);
    }

    @Override
    public void deleteAllLexiconReviewEvents(String lexiconId) {
        List<String> idsToDelete = getScheduledReviewIdsFor(lexiconId, "", null);

        batchDelete(idsToDelete);
    }

    @Override
    public int adjustNextReviewTimes(String lexiconId, String username, Duration adjustment) {
        SdkIterable<Page<DDBScheduledReview>> results = scheduledReviewTable.index(DDBScheduledReview.SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME)
                .query(QueryConditional.sortGreaterThan(
                        Key.builder()
                                .partitionValue(lexiconId)
                                .sortValue(username)
                                .sortValue(ScheduledReviewStatus.SCHEDULED.name())
                                .build()));

        List<DDBScheduledReview> reviewsToSave = results.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbScheduledReview -> DDBScheduledReview
                        .builder(ddbScheduledReview)
                        .scheduledTestTime(ddbScheduledReview.scheduledTestTime().plus(adjustment))
                        .build())
                .collect(Collectors.toUnmodifiableList());

        WriteBatch.Builder<DDBScheduledReview> batchBuilder = WriteBatch.builder(DDBScheduledReview.class).mappedTableResource(scheduledReviewTable);
        reviewsToSave.forEach(ddbScheduledReview -> batchBuilder.addPutItem(ddbScheduledReview));

        BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(b -> b.writeBatches(batchBuilder.build()));
        return reviewsToSave.size() - result.unprocessedPutItemsForTable(scheduledReviewTable).size();
    }

    @Override
    public int purgeOldScheduledReviews(Instant cutoff) {
        // Purging handled by TTL when using Dynamo
        return 0;
    }

    private List<String> getScheduledReviewIdsFor(String lexiconId, String username, Collection<String> wordIds) {
        Key key;
        if (username == null || username.isEmpty()) {
            key = Key.builder().partitionValue(lexiconId).build();
        } else {
            key = Key.builder().partitionValue(lexiconId).sortValue(username).build();
        }

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(key))
                .attributesToProject(DDBScheduledReview.ID_ATTRIBUTE_NAME)
                .scanIndexForward(true);

        if (wordIds != null && wordIds.size() > 0) {
            requestBuilder.filterExpression(buildWordIdFilterExpression(wordIds));
        }

        SdkIterable<Page<DDBScheduledReview>> results = scheduledReviewTable.index(DDBScheduledReview.SCHEDULED_REVIEW_BY_LEXICON_INDEX_NAME).query(requestBuilder.build());

        return results.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbScheduledReview -> ddbScheduledReview.id())
                .collect(Collectors.toUnmodifiableList());
    }

    private Expression buildWordIdFilterExpression(Collection<String> wordIds) {
        StringBuilder sb = new StringBuilder("#wordId IN (");
        Map<String, AttributeValue> attributeValueMap = new HashMap<>();

        int pos = 0;
        for (String wordId : wordIds) {
            if (pos > 0) {
                sb.append(", ");
            }
            sb.append(":wordId").append(pos);

            attributeValueMap.put(":wordId" + pos, AttributeValue.builder().s(wordId).build());

            pos++;
        }
        sb.append(")");

        return Expression.builder()
                .expression(sb.toString())
                .expressionNames(Map.of("#wordId", DDBScheduledReview.WORD_ID_ATTRIBUTE_NAME))
                .expressionValues(attributeValueMap)
                .build();
    }

    private void batchDelete(List<String> ids) {
        for (String id : ids) {
            scheduledReviewTable.deleteItem(Key.builder().partitionValue(id).build());
        }
    }
}

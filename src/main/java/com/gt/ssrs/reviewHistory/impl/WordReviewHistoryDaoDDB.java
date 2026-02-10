package com.gt.ssrs.reviewHistory.impl;

import com.gt.ssrs.model.WordReviewHistory;
import com.gt.ssrs.reviewHistory.WordReviewHistoryDao;
import com.gt.ssrs.reviewHistory.converter.DDBWordReviewHistoryConverter;
import com.gt.ssrs.reviewHistory.model.DDBWordReviewHistory;
import com.gt.ssrs.reviewHistory.model.LearnedStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class WordReviewHistoryDaoDDB implements WordReviewHistoryDao {

    private static final Logger log = LoggerFactory.getLogger(WordReviewHistoryDaoDDB.class);

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

    private final DynamoDbTable<DDBWordReviewHistory> wordReviewHistoryTable;

    public WordReviewHistoryDaoDDB(DynamoDbClient dynamoDbClient, DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;

        wordReviewHistoryTable = dynamoDbEnhancedClient.table(DDBWordReviewHistory.TABLE_NAME, TableSchema.fromImmutableClass(DDBWordReviewHistory.class));
    }

    @Override
    public List<WordReviewHistory> createWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistories) {
        WriteBatch.Builder<DDBWordReviewHistory> batchBuilder = WriteBatch.builder(DDBWordReviewHistory.class).mappedTableResource(wordReviewHistoryTable);
        Instant createInstant = Instant.now();

        for (WordReviewHistory wordReviewHistory : wordReviewHistories) {
            if (wordReviewHistory.username().equals(username)) {
                batchBuilder.addPutItem(DDBWordReviewHistoryConverter.convertWordReviewHistory(wordReviewHistory, createInstant, createInstant));
                createInstant = createInstant.plusMillis(1);
            }
        }

        BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(b -> b.addWriteBatch(batchBuilder.build()));

        return filterNotSaved(wordReviewHistories, result.unprocessedPutItemsForTable(wordReviewHistoryTable));
    }

    @Override
    public List<WordReviewHistory> getWordReviewHistoryBatch(String lexiconId, String username, Collection<String> wordIds) {
        return DDBWordReviewHistoryConverter.convertDDBWordReviewHistoryBatch(getDDBWordReviewHistoryBatch(lexiconId, username, wordIds));
    }

    @Override
    public List<WordReviewHistory> updateWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistoriesToUpdate) {
        List<WordReviewHistory> savedWordReviewHistories = new ArrayList<>();

        Map<String, List<String>> wordIdsByLexiconId = wordReviewHistoriesToUpdate
                .stream()
                .filter(wordReviewHistory -> wordReviewHistory.username().equals(username))
                .collect(Collectors.groupingBy(WordReviewHistory::lexiconId, Collectors.collectingAndThen(Collectors.toList(), wordReviewHistory -> wordReviewHistory
                        .stream()
                        .map(w -> w.wordId())
                        .collect(Collectors.toUnmodifiableList()))));

        for (String lexiconId : wordIdsByLexiconId.keySet()) {
            Map<Integer, DDBWordReviewHistory> existingDDBWordReviewHistoryByIdHash = getDDBWordReviewHistoryBatch(lexiconId, username, wordIdsByLexiconId.get(lexiconId))
                    .stream()
                    .filter(ddbWordReviewHistory -> ddbWordReviewHistory.username().equals(username))
                    .collect(Collectors.toMap(ddbWordReviewHistory -> idHash(ddbWordReviewHistory),
                                              ddbWordReviewHistory -> ddbWordReviewHistory));


            WriteBatch.Builder<DDBWordReviewHistory> batchBuilder = WriteBatch.builder(DDBWordReviewHistory.class).mappedTableResource(wordReviewHistoryTable);
            for (WordReviewHistory wordReviewHistory : wordReviewHistoriesToUpdate) {
                DDBWordReviewHistory existingDDBWordReviewHistory = existingDDBWordReviewHistoryByIdHash.get(idHash(wordReviewHistory));
                if (existingDDBWordReviewHistory != null) {
                    batchBuilder.addPutItem(DDBWordReviewHistoryConverter.convertWordReviewHistory(wordReviewHistory, existingDDBWordReviewHistory.createInstant(), Instant.now()));
                }
            }

            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(b -> b.addWriteBatch(batchBuilder.build()));
            savedWordReviewHistories.addAll(filterNotSaved(wordReviewHistoriesToUpdate, result.unprocessedPutItemsForTable(wordReviewHistoryTable)));
        }

        return savedWordReviewHistories;
    }

    @Override
    public List<String> getIdsForWordsToLearn(String lexiconId, String username, int wordCnt) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(DDBWordReviewHistory.TABLE_NAME)
                .indexName(DDBWordReviewHistory.BY_LEARNED_INDEX_NAME)
                .keyConditionExpression("#lexiconId = :lexiconId AND #username = :username AND #learned = :learned")
                .expressionAttributeNames(Map.of("#lexiconId", "lexiconId",
                                                 "#username", "username",
                                                 "#learned", "learned"))
                .expressionAttributeValues(Map.of(":lexiconId", AttributeValue.builder().s(lexiconId).build(),
                                                  ":username", AttributeValue.builder().s(username).build(),
                                                  ":learned", AttributeValue.builder().s(LearnedStatus.ReadyToLearn.name()).build()))
                .attributesToGet("wordId")
                .scanIndexForward(true)
                .build();

        QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
        return queryResponse.items().stream()
                .map(map -> map.get("wordId"))
                .filter(Objects::nonNull)
                .map(val -> val.s())
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public void deleteUserWordReviewHistories(String lexiconId, String username, Collection<String> wordIds) {
        for(String wordId : wordIds) {
            wordReviewHistoryTable.deleteItem(Key.builder()
                    .partitionValue(lexiconId)
                    .partitionValue(username)
                    .partitionValue(wordId)
                    .build());
        }
    }

    @Override
    public void deleteWordReviewHistories(String lexiconId, Collection<String> wordIds) {
        // TODO
    }

    @Override
    public void deleteLexiconWordReviewHistoryForUser(String lexiconId, String username) {
        List<String> wordIdsToDelete = getWordIdsForLexicon(lexiconId, username);

        for (String wordId : wordIdsToDelete) {
            wordReviewHistoryTable.deleteItem(Key.builder().partitionValue(lexiconId).partitionValue(username).partitionValue(wordId).build());
        }
    }

    @Override
    public void deleteLexiconWordReviewHistory(String lexiconId) {
        // TODO
    }

    public List<DDBWordReviewHistory> getDDBWordReviewHistoryBatch(String lexiconId, String username, Collection<String> wordIds) {
        ReadBatch.Builder<DDBWordReviewHistory> batchBuilder = ReadBatch.builder(DDBWordReviewHistory.class).mappedTableResource(wordReviewHistoryTable);
        wordIds.forEach(wordId -> batchBuilder.addGetItem(Key.builder().partitionValue(lexiconId).partitionValue(username).partitionValue(wordId).build()));

        BatchGetResultPageIterable resultPages = dynamoDbEnhancedClient.batchGetItem(b -> b.addReadBatch(batchBuilder.build()));

        return resultPages.resultsForTable(wordReviewHistoryTable).stream().toList();
    }

    private static List<WordReviewHistory> filterNotSaved(List<WordReviewHistory> original, List<DDBWordReviewHistory> notSaved) {
        Set<Integer> unprocessedWordReviewHistoryIdHashes = notSaved.stream()
                .map(ddbWordReviewHistory -> idHash(ddbWordReviewHistory))
                .collect(Collectors.toUnmodifiableSet());

        return original.stream()
                .filter(wordReviewHistory -> !unprocessedWordReviewHistoryIdHashes.contains(idHash(wordReviewHistory)))
                .collect(Collectors.toUnmodifiableList());
    }

    private List<String> getWordIdsForLexicon(String lexiconId, String username) {
        ScanRequest scanRequest = ScanRequest.builder()
                .filterExpression("#lexiconId = :lexiconId AND #username = :username")
                .expressionAttributeNames(Map.of("#lexiconId", "lexiconId",
                                                 "#username", "username"))
                .expressionAttributeValues(Map.of(":lexiconId", AttributeValue.builder().s(lexiconId).build(),
                                                  ":username", AttributeValue.builder().s(username).build()))
                .attributesToGet("wordId")
                .build();

        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

        return scanResponse.items().stream()
                .map(map -> map.get("wordId"))
                .filter(Objects::nonNull)
                .map(val -> val.s())
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    private static int idHash(WordReviewHistory wordReviewHistory) {
        return idHash(wordReviewHistory.lexiconId(), wordReviewHistory.username(), wordReviewHistory.wordId());
    }

    private static int idHash(DDBWordReviewHistory ddbWordReviewHistory) {
        return idHash(ddbWordReviewHistory.lexiconId(), ddbWordReviewHistory.username(), ddbWordReviewHistory.wordId());
    }

    private static int idHash(String lexiconId, String username, String wordId) {
        return Objects.hash(lexiconId, username, wordId);
    }
}

package com.gt.ssrs.reviewHistory.aws;

import com.gt.ssrs.model.WordReviewHistory;
import com.gt.ssrs.reviewHistory.WordReviewHistoryDao;
import com.gt.ssrs.reviewHistory.model.LearnedStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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

@Component
public class WordReviewHistoryDaoDDB implements WordReviewHistoryDao {

    private static final Logger log = LoggerFactory.getLogger(WordReviewHistoryDaoDDB.class);

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final int maxWriteBatchSize;

    private final DynamoDbTable<DDBWordReviewHistory> wordReviewHistoryTable;

    @Autowired
    public WordReviewHistoryDaoDDB(DynamoDbClient dynamoDbClient,
                                   DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                   @Value("${aws.dynamodb.maxWriteBatchSize}") int maxWriteBatchSize) {
        this.dynamoDbClient = dynamoDbClient;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.maxWriteBatchSize = maxWriteBatchSize;

        wordReviewHistoryTable = dynamoDbEnhancedClient.table(DDBWordReviewHistory.TABLE_NAME, TableSchema.fromImmutableClass(DDBWordReviewHistory.class));
    }

    @Override
    public List<WordReviewHistory> createWordReviewHistory(String username, List<WordReviewHistory> wordReviewHistories) {
        List<WordReviewHistory> updatedWordReviewHistory = new ArrayList<>();

        for(int offset = 0; offset < wordReviewHistories.size(); offset += maxWriteBatchSize) {
            updatedWordReviewHistory.addAll(createWordReviewHistoryBatch(username, wordReviewHistories.subList(offset, Math.min(wordReviewHistories.size(), offset + maxWriteBatchSize))));
        }

        return updatedWordReviewHistory;
    }

    public List<WordReviewHistory> createWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistories) {
        WriteBatch.Builder<DDBWordReviewHistory> batchBuilder = WriteBatch.builder(DDBWordReviewHistory.class).mappedTableResource(wordReviewHistoryTable);
        Instant createInstant = Instant.now();

        for (WordReviewHistory wordReviewHistory : wordReviewHistories) {
            if (wordReviewHistory.username().equals(username)) {
                batchBuilder.addPutItem(DDBWordReviewHistoryConverter.convertWordReviewHistory(wordReviewHistory, createInstant));
                createInstant = createInstant.plusMillis(1);
            }
        }

        BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(b -> b.addWriteBatch(batchBuilder.build()));

        return filterNotSaved(wordReviewHistories, result.unprocessedPutItemsForTable(wordReviewHistoryTable));
    }

    @Override
    public List<WordReviewHistory> getWordReviewHistory(String lexiconId, String username, Collection<String> wordIds) {
        return DDBWordReviewHistoryConverter.convertDDBWordReviewHistoryBatch(getDDBWordReviewHistoryBatch(lexiconId, username, wordIds));
    }

    @Override
    public List<WordReviewHistory> updateWordReviewHistory(String username, List<WordReviewHistory> wordReviewHistoriesToUpdate) {
        List<WordReviewHistory> updatedWordReviewHistory = new ArrayList<>();

        for(int offset = 0; offset < wordReviewHistoriesToUpdate.size(); offset += maxWriteBatchSize) {
            updatedWordReviewHistory.addAll(updateWordReviewHistoryBatch(username, wordReviewHistoriesToUpdate.subList(offset, Math.min(wordReviewHistoriesToUpdate.size(), offset + maxWriteBatchSize))));
        }

        return updatedWordReviewHistory;
    }

    public List<WordReviewHistory> updateWordReviewHistoryBatch(String username, List<WordReviewHistory> wordReviewHistoriesToUpdate) {
        List<WordReviewHistory> savedWordReviewHistories = new ArrayList<>();

        Map<String, List<String>> wordIdsByLexiconId = wordReviewHistoriesToUpdate
                .stream()
                .filter(wordReviewHistory -> wordReviewHistory.username().equals(username))
                .collect(Collectors.groupingBy(WordReviewHistory::lexiconId, Collectors.collectingAndThen(Collectors.toList(), wordReviewHistory -> wordReviewHistory
                        .stream()
                        .map(w -> w.wordId())
                        .distinct()
                        .toList())));

        for (String lexiconId : wordIdsByLexiconId.keySet()) {
            Map<String, DDBWordReviewHistory> existingDDBWordReviewHistoryByWordId = getDDBWordReviewHistoryBatch(lexiconId, username, wordIdsByLexiconId.get(lexiconId))
                    .stream()
                    .filter(ddbWordReviewHistory -> ddbWordReviewHistory.username().equals(username))
                    .collect(Collectors.toMap(ddbWordReviewHistory -> ddbWordReviewHistory.wordId(),
                                              ddbWordReviewHistory -> ddbWordReviewHistory));


            WriteBatch.Builder<DDBWordReviewHistory> batchBuilder = WriteBatch.builder(DDBWordReviewHistory.class).mappedTableResource(wordReviewHistoryTable);
            for (WordReviewHistory wordReviewHistory : wordReviewHistoriesToUpdate) {
                DDBWordReviewHistory existingDDBWordReviewHistory = existingDDBWordReviewHistoryByWordId.get(wordReviewHistory.wordId());
                Instant createInstant = existingDDBWordReviewHistory == null || existingDDBWordReviewHistory.createInstant() == null ? Instant.now() : existingDDBWordReviewHistory.createInstant();

                batchBuilder.addPutItem(DDBWordReviewHistoryConverter.convertWordReviewHistory(wordReviewHistory, createInstant));
            }

            BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(b -> b.addWriteBatch(batchBuilder.build()));
            savedWordReviewHistories.addAll(filterNotSaved(wordReviewHistoriesToUpdate, result.unprocessedPutItemsForTable(wordReviewHistoryTable)));
        }

        return savedWordReviewHistories;
    }

    @Override
    public List<WordReviewHistory> getAllWordReviewHistory(String lexiconId, String username) {
        return List.of();  // TODO: Implement, obviously
    }

    @Override
    public List<String> getIdsForWordsToLearn(String lexiconId, String username, int wordCnt) {
        return getWordIdsByLearned(lexiconId, username, LearnedStatus.ReadyToLearn, wordCnt);
    }

    @Override
    public void deleteUserWordReviewHistories(String lexiconId, String username, Collection<String> wordIds) {
        for(String wordId : wordIds) {
            wordReviewHistoryTable.deleteItem(Key.builder()
                    .partitionValue(DDBWordReviewHistoryConverter.buildId(lexiconId, username))
                    .sortValue(wordId)
                    .build());
        }
    }

    @Override
    public void deleteWordReviewHistories(String lexiconId, Collection<String> wordIds) {
        Set<String> wordIdsSet = wordIds.stream().collect(Collectors.toUnmodifiableSet());

        for(UserWordIdTuple userWordIdTuple : getUserAndWordIdsForLexicon(lexiconId)) {
            if (wordIdsSet.contains(userWordIdTuple.wordId)) {
                wordReviewHistoryTable.deleteItem(Key.builder().partitionValue(DDBWordReviewHistoryConverter.buildId(lexiconId, userWordIdTuple.username)).sortValue(userWordIdTuple.wordId).build());
            }
        }
    }

    @Override
    public void deleteLexiconWordReviewHistoryForUser(String lexiconId, String username) {
        Map<LearnedStatus, List<String>> wordIdsToDeleteByLearned = getWordIdsForUserByLearned(lexiconId, username);

        for (LearnedStatus learnedStatus : wordIdsToDeleteByLearned.keySet()) {
            for (String wordId : wordIdsToDeleteByLearned.get(learnedStatus)) {
                wordReviewHistoryTable.deleteItem(Key.builder().partitionValue(DDBWordReviewHistoryConverter.buildId(lexiconId, username)).sortValue(wordId).build());
            }
        }
    }

    @Override
    public void deleteLexiconWordReviewHistory(String lexiconId) {
        for(UserWordIdTuple userWordIdTuple : getUserAndWordIdsForLexicon(lexiconId)) {
            wordReviewHistoryTable.deleteItem(Key.builder().partitionValue(DDBWordReviewHistoryConverter.buildId(lexiconId, userWordIdTuple.username)).sortValue(userWordIdTuple.wordId).build());
        }
    }

    @Override
    public int getTotalLearnedWordCount(String lexiconId, String username) {
        return getWordIdsByLearned(lexiconId, username, LearnedStatus.Learned, 0).size();
    }

    @Override
    public Map<LearnedStatus, List<String>> getWordIdsForUserByLearned(String lexiconId, String username) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(DDBWordReviewHistory.TABLE_NAME)
                .indexName(DDBWordReviewHistory.BY_LEARNED_INDEX_NAME)
                .keyConditionExpression("#lexiconId = :lexiconId AND #username = :username")
                .expressionAttributeNames(Map.of(
                        "#lexiconId", DDBWordReviewHistory.LEXICON_ID_ATTRIBUTE_NAME,
                        "#username", DDBWordReviewHistory.USERNAME_ATTRIBUTE_NAME))
                .expressionAttributeValues(Map.of(
                        ":lexiconId", AttributeValue.builder().s(lexiconId).build(),
                        ":username", AttributeValue.builder().s(username).build()))
                .projectionExpression(String.join(", ", List.of(
                        DDBWordReviewHistory.LEARNED_ATTRIBUTE_NAME,
                        DDBWordReviewHistory.WORD_ID_ATTRIBUTE_NAME)))
                .build();

        return getWordIdsByLearnedQueryResponse(dynamoDbClient.query(queryRequest));
    }

    private List<String> getWordIdsByLearned(String lexiconId, String username, LearnedStatus learnedStatus, int limit) {
        QueryRequest.Builder queryRequestBuilder = QueryRequest.builder()
                .tableName(DDBWordReviewHistory.TABLE_NAME)
                .indexName(DDBWordReviewHistory.BY_LEARNED_INDEX_NAME)
                .keyConditionExpression("#lexiconId = :lexiconId AND #username = :username AND #learned = :learned")
                .expressionAttributeNames(Map.of(
                        "#lexiconId", DDBWordReviewHistory.LEXICON_ID_ATTRIBUTE_NAME,
                        "#username", DDBWordReviewHistory.USERNAME_ATTRIBUTE_NAME,
                        "#learned", DDBWordReviewHistory.LEARNED_ATTRIBUTE_NAME))
                .expressionAttributeValues(Map.of(":lexiconId", AttributeValue.builder().s(lexiconId).build(),
                        ":username", AttributeValue.builder().s(username).build(),
                        ":learned", AttributeValue.builder().s(learnedStatus.name()).build()))
                .projectionExpression(DDBWordReviewHistory.WORD_ID_ATTRIBUTE_NAME)
                .scanIndexForward(true);

        if (limit > 0) {
            queryRequestBuilder.limit(limit);
        }

        return getWordIdsFromQueryResponse(dynamoDbClient.query(queryRequestBuilder.build()));
    }

    private List<String> getWordIdsFromQueryResponse(QueryResponse queryResponse) {
        return queryResponse.items().stream()
                .map(map -> map.get(DDBWordReviewHistory.WORD_ID_ATTRIBUTE_NAME))
                .filter(Objects::nonNull)
                .map(val -> val.s())
                .distinct()
                .toList();
    }

    private Map<LearnedStatus, List<String>> getWordIdsByLearnedQueryResponse(QueryResponse queryResponse) {
        Map<LearnedStatus, List<String>> wordIdsByLearned = new HashMap<>();

        for (Map<String, AttributeValue> items : queryResponse.items()) {
            AttributeValue learnedStatusAttribute = items.get(DDBWordReviewHistory.LEARNED_ATTRIBUTE_NAME);
            AttributeValue wordIdAttribute = items.get(DDBWordReviewHistory.WORD_ID_ATTRIBUTE_NAME);

            if (wordIdAttribute != null) {
                LearnedStatus learnedStatus = learnedStatusAttribute == null ? LearnedStatus.ReadyToLearn : LearnedStatus.valueOf(learnedStatusAttribute.s());
                String wordId = wordIdAttribute.s();

                wordIdsByLearned.computeIfAbsent(learnedStatus, ls -> new ArrayList<>()).add(wordId);
            }
        }

        return wordIdsByLearned;
    }

    private List<UserWordIdTuple> getUserAndWordIdsForLexicon(String lexiconId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(DDBWordReviewHistory.TABLE_NAME)
                .indexName(DDBWordReviewHistory.BY_LEARNED_INDEX_NAME)
                .keyConditionExpression("#lexiconId = :lexiconId")
                .expressionAttributeNames(Map.of("#lexiconId", "lexiconId"))
                .expressionAttributeValues(Map.of(":lexiconId", AttributeValue.builder().s(lexiconId).build()))
                .projectionExpression("username, wordId")
                .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        return response.items().stream()
                .map(resp -> new UserWordIdTuple(resp.get("username").s(), resp.get("wordId").s()))
                .distinct()
                .toList();
    }

    private List<DDBWordReviewHistory> getDDBWordReviewHistoryBatch(String lexiconId, String username, Collection<String> wordIds) {
        ReadBatch.Builder<DDBWordReviewHistory> batchBuilder = ReadBatch.builder(DDBWordReviewHistory.class).mappedTableResource(wordReviewHistoryTable);
        wordIds.forEach(wordId -> batchBuilder.addGetItem(Key.builder().partitionValue(DDBWordReviewHistoryConverter.buildId(lexiconId, username)).sortValue(wordId).build()));

        BatchGetResultPageIterable resultPages = dynamoDbEnhancedClient.batchGetItem(b -> b.addReadBatch(batchBuilder.build()));

        return resultPages.resultsForTable(wordReviewHistoryTable).stream().toList();
    }

    private static List<WordReviewHistory> filterNotSaved(List<WordReviewHistory> original, List<DDBWordReviewHistory> notSaved) {
        Set<Integer> unprocessedWordReviewHistoryIdHashes = notSaved.stream()
                .map(ddbWordReviewHistory -> idHash(ddbWordReviewHistory))
                .collect(Collectors.toUnmodifiableSet());

        return original.stream()
                .filter(wordReviewHistory -> !unprocessedWordReviewHistoryIdHashes.contains(idHash(wordReviewHistory)))
                .toList();
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

    private record UserWordIdTuple(String username, String wordId) { }
}
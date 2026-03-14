package com.gt.ssrs.word.aws;

import com.gt.ssrs.exception.DaoException;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.word.WordDao;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class WordDaoDDB implements WordDao {

    private static final Logger log = LoggerFactory.getLogger(WordDaoDDB.class);

    private static final String MAX_ISO_DATE_STR = "9999-12-31T23:59:59.999999999Z";

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

    private final DynamoDbTable<DDBWord> wordTable;

    @Autowired
    public WordDaoDDB(DynamoDbClient dynamoDbClient, DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;

        this.wordTable = dynamoDbEnhancedClient.table(DDBWord.TABLE_NAME, TableSchema.fromImmutableClass(DDBWord.class));
    }

    @Override
    public Word loadWord(String wordId) {
        DDBWord ddbWord = wordTable.getItem(Key.builder().partitionValue(wordId).build());

        return DDBWordConverter.convertDDBWord(ddbWord);
    }

    @Override
    public List<Word> loadWords(Collection<String> wordIds) {
        ReadBatch.Builder<DDBWord> batchBuilder = ReadBatch.builder(DDBWord.class).mappedTableResource(wordTable);
        wordIds.stream()
                .distinct()
                .forEach(wordId -> batchBuilder.addGetItem(Key.builder().partitionValue(wordId).build()));

        BatchGetResultPageIterable resultPages = dynamoDbEnhancedClient.batchGetItem(b -> b.addReadBatch(batchBuilder.build()));

        return resultPages.resultsForTable(wordTable).stream()
                .map(ddbWord -> DDBWordConverter.convertDDBWord(ddbWord))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public int createWord(Language language, String lexiconId, Word word) {
        wordTable.putItem(DDBWordConverter.convertWord(word));

        return 1;
    }

    @Override
    public List<Word> createWords(Language language, String lexiconId, List<Word> words) {
        WriteBatch.Builder<DDBWord> batchBuilder = WriteBatch.builder(DDBWord.class).mappedTableResource(wordTable);
        DDBWordConverter.convertWordBatch(words).forEach(wordDDB -> batchBuilder.addPutItem(wordDDB));

        BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(b -> b.writeBatches(batchBuilder.build()));

        List<DDBWord> unprocessedWords = result.unprocessedPutItemsForTable(wordTable);
        if (unprocessedWords.size() > 0) {
            Set<String> unprocessedWordIds = unprocessedWords.stream()
                    .map(wordDDB -> wordDDB.id())
                    .collect(Collectors.toUnmodifiableSet());

            return words.stream()
                    .filter(word -> !unprocessedWordIds.contains(word.id()))
                    .collect(Collectors.toUnmodifiableList());
        }

        return words;
    }

    @Override
    public Word findDuplicateWordInOtherLexicons(Language language, String lexiconId, String owner, Word word) {
        // TODO: Skipping for now
        return null;
    }

    @Override
    public int updateWord(Word word) {
        DDBWord updateWord = DDBWordConverter.convertWord(word);  // learned is irrelevant here as it gets overwritten based on WordReviewHistory
        DDBWord existingWord = wordTable.getItem(Key.builder().partitionValue(word.id()).build());

        if (existingWord == null) {
            String errMsg = "Attempt to update word " + word.id() + " failed because word does not existing in data store";

            log.error(errMsg);
            throw new DaoException(errMsg);
        }

        // Some values need to maintained (ownership, review history) and some need to either be
        // updated or deleted. This can't be done strictly with ignore nulls setting, so read the
        // existing and update/maintain attributes as needed
        DDBWord wordToSave = DDBWord.builder()
                .id(existingWord.id())
                .lexiconId(existingWord.lexiconId())
                .owner(existingWord.owner())
                .elements(updateWord.elements())
                .attributes(updateWord.attributes())
                .audioFiles(updateWord.audioFiles())
                .createInstant(existingWord.createInstant())
                .updateInstant(Instant.now())
                .build();

        wordTable.putItem(wordToSave);

        return 1;
    }

    @Override
    public void deleteWords(Collection<String> wordIds) {
        for (String wordId : wordIds) {
            DDBWord deletedWord = wordTable.deleteItem(Key.builder().partitionValue(wordId).build());
        }
    }

    @Override
    public void deleteAllLexiconWords(String lexiconId) {
        // TODO
    }

    @Override
    public List<Word> getLexiconWordsBatch(String lexiconId, String username, int count, int offset, Word lastWord) {
        return getLexiconWordsBatchWithFilter(lexiconId, username, count, offset, lastWord, null);
    }

    @Override
    public List<Word> getLexiconWordsBatchWithFilter(String lexiconId, String username, int count, int offset, Word lastWord, WordFilterOptions wordFilterOptions) {
        String sortStartDate = lastWord == null ? MAX_ISO_DATE_STR : lastWord.createInstant().toString();

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .scanIndexForward(false);

        if (lastWord == null || lastWord.createInstant() == null) {
            requestBuilder.queryConditional(QueryConditional
                    .keyEqualTo(b -> b.partitionValue(lexiconId)));
        } else {
            requestBuilder.queryConditional(QueryConditional
                    .sortLessThan(b -> b
                            .partitionValue(lexiconId)
                            .sortValue(sortStartDate)));
        }

        if (wordFilterOptions != null) {
            Expression filterExpression = buildFilterFromOptions(wordFilterOptions);
            if (filterExpression != null) {
                requestBuilder.filterExpression(filterExpression);
            }
        }

        SdkIterable<Page<DDBWord>> results = wordTable.index(DDBWord.CREATE_INSTANT_INDEX_NAME).query(requestBuilder.build());

        List<Word> wordsToReturn = new ArrayList<>();
        for (Page<DDBWord> page : results) {
            wordsToReturn.addAll(page.items()
                    .stream()
                    .map(ddbWord -> DDBWordConverter.convertDDBWord(ddbWord))
                    .collect(Collectors.toUnmodifiableList()));

            if (wordsToReturn.size() > count) {
                break;
            }
        }

        return wordsToReturn;
    }

    @Override
    public List<String> getAudioFileNamesForWord(String wordId) {
        Word word = loadWord(wordId);

        if (word == null) {
            return null;
        }

        return word.audioFiles();
    }

    @Override
    public Map<String, List<String>> getAudioFileNamesForWordBatch(List<String> wordIds) {
        List<Word> words = loadWords(wordIds);

        return words.stream().collect(Collectors.toMap(word -> word.id(), word -> word.audioFiles()));
    }

    @Override
    public int setAudioFileNameForWord(String wordId, String audioFileName) {
        DDBWord existingWord = wordTable.getItem(Key.builder().partitionValue(wordId).build());

        // TODO: check ownership

        List<String> audioFilesToSave = new ArrayList<>();
        if (existingWord.audioFiles() != null) {
            audioFilesToSave.addAll(existingWord.audioFiles());
        }
        audioFilesToSave.add(audioFileName);

        DDBWord wordToSave = DDBWord.builder(existingWord)
                .audioFiles(audioFilesToSave.isEmpty() ? null : audioFilesToSave)
                .build();

        DDBWord savedWord = wordTable.updateItem(wordToSave);

        if (savedWord == null) {
            return 0;
        }

        return 1;
    }

    @Override
    public int deleteAudioFileName(String wordId, String audioFileName) {
        DDBWord existingWord = wordTable.getItem(Key.builder().partitionValue(wordId).build());

        if (existingWord == null) {
            return 0;
        }

        // TODO: Check username

        if (existingWord.audioFiles() == null) {
            return 0;  // Nothing to delete
        }
        List<String> audioFilesToSave = existingWord.audioFiles()
                .stream()
                .filter(name -> !name.equals(audioFileName))
                .collect(Collectors.toUnmodifiableList());

        if (audioFilesToSave.size() == existingWord.audioFiles().size()) {
            return 0;  // Nothing to delete
        }

        DDBWord wordSaved = wordTable.updateItem(b -> b
                .item(DDBWord.builder()
                    .id(wordId)
                    .audioFiles(audioFilesToSave)
                    .build())
                .ignoreNullsMode(IgnoreNullsMode.SCALAR_ONLY));

        if (wordSaved == null) {
            return 0;
        }

        return 1;
    }

    @Override
    public List<String> getWordsUniqueToLexicon(String lexiconId) {
        // TODO: This is only used for deleting audio files which is unneeded in DDB. Audio file delete should probably
        // be refactored
        return List.of();
    }

    @Override
    public int getTotalLexiconWordCount(String lexiconId) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(DDBWord.TABLE_NAME)
                .indexName(DDBWord.CREATE_INSTANT_INDEX_NAME)
                .keyConditionExpression("#lexiconId = :lexiconId")
                .expressionAttributeNames(Map.of("#lexiconId", "lexiconId"))
                .expressionAttributeValues(Map.of(":lexiconId", AttributeValue.builder().s(lexiconId).build()))
                .projectionExpression(DDBWord.ID_ATTRIBUTE_NAME)
                .build();

        QueryResponse queryResponse = dynamoDbClient.query(queryRequest);

        return queryResponse.items().size();
    }

    @Override
    public List<String> getUniqueElementValues(String lexiconId, WordElement wordElement, int limit) {
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(DDBWord.TABLE_NAME)
                .indexName(DDBWord.CREATE_INSTANT_INDEX_NAME)
                .keyConditionExpression("#lexiconId = :lexiconId")
                .expressionAttributeNames(Map.of("#lexiconId", "lexiconId"))
                .expressionAttributeValues(Map.of(":lexiconId", AttributeValue.builder().s(lexiconId).build()))
                .projectionExpression("elements." + wordElement.getId())
                .limit(limit)
                .build();

        QueryResponse queryResponse = dynamoDbClient.query(queryRequest);

        return queryResponse.items().stream()
                .map(map -> map.get("elements"))
                .filter(Objects::nonNull)
                .map(attributeValue -> attributeValue.m().get(wordElement.getId()))
                .filter(Objects::nonNull)
                .map(attributeValue -> attributeValue.s())
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    private Expression buildOwnedByUserFilterExpression(String username) {
        return Expression.builder()
                .expression("#owner = :username")
                .expressionNames(Map.of("#owner", "owner"))
                .expressionValues(Map.of(":username", AttributeValue.builder().s(username).build()))
                .build();
    }

    private Expression buildFilterFromOptions(WordFilterOptions options) {
        String expression = "";
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();

        if (options.attributes() != null && !options.attributes().isBlank()) {
            expression += "contains(#attributes, :attributes)";
            expressionNames.put("#attributes", DDBWord.ATTRIBUTES_ATTRIBUTE_NAME);
            expressionValues.put(":attributes", AttributeValue.builder().s(options.attributes()).build());
        }

        if (options.elements() != null && !options.elements().isEmpty()) {
            for (Map.Entry<String, String> elementEntry : options.elements().entrySet()) {
                String elementName = "element." + elementEntry.getKey();
                expression += (!expression.isEmpty() ? " AND " : "") + "contains(#" + elementName + ", :" + elementName + ")";
                expressionNames.put("#" + elementName, DDBWord.ELEMENTS_ATTRIBUTE_NAME + "." + elementEntry.getKey());
                expressionValues.put(":" + elementName, AttributeValue.builder().s(elementEntry.getValue()).build());
            }
        }

        if (options.hasAudio() != null) {
            if (!expression.isEmpty()) {
                expression += " AND ";
            }

            expression += "(attribute_not_exists(#audioFiles) OR size(#audioFiles) = :zero)";
            expressionNames.put("#audioFiles", DDBWord.AUDIO_FILES_ATTRIBUTE_NAME);
            expressionValues.put(":zero", AttributeValue.builder().n("0").build());
        }

        if (expression.isEmpty()) {
            return null;
        }

        return Expression.builder()
                .expression(expression)
                .expressionNames(expressionNames)
                .expressionValues(expressionValues)
                .build();
    }
}
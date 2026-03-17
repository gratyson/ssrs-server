package com.gt.ssrs.word.aws;

import com.gt.ssrs.exception.DaoException;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.util.ListUtil;
import com.gt.ssrs.word.WordDao;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class WordDaoDDB implements WordDao {

    private static final Logger log = LoggerFactory.getLogger(WordDaoDDB.class);

    private static final String MAX_ISO_DATE_STR = "9999-12-31T23:59:59.999999999Z";

    private static final int ELEMENT_FILTER_LIMIT_MULTIPLER = 12;
    private static final int ATTRIBUTE_FILTER_LIMIT_MULTIPLER = 4;
    private static final int AUDIO_FILTER_LIMIT_MULTIPLER = 2;

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private final int maxWriteBatchSize;

    private final DynamoDbTable<DDBWord> wordTable;

    @Autowired
    public WordDaoDDB(DynamoDbClient dynamoDbClient,
                      DynamoDbEnhancedClient dynamoDbEnhancedClient,
                      @Value("${aws.dynamodb.maxWriteBatchSize}") int maxWriteBatchSize) {
        this.dynamoDbClient = dynamoDbClient;
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
        this.maxWriteBatchSize = maxWriteBatchSize;

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
                .toList();
    }

    @Override
    public int createWord(Language language, String lexiconId, Word word) {
        wordTable.putItem(DDBWordConverter.convertWord(language, word));

        return 1;
    }

    @Override
    public List<Word> createWords(Language language, String lexiconId, List<Word> words) {
        List<Word> createdWords = new ArrayList<>();

        for (List<Word> subList : ListUtil.partitionList(words, maxWriteBatchSize)) {
            createdWords.addAll(createWordsBatch(language, lexiconId, subList));
        }

        return createdWords;
    }

    private List<Word> createWordsBatch(Language language, String lexiconId, List<Word> words) {
        WriteBatch.Builder<DDBWord> batchBuilder = WriteBatch.builder(DDBWord.class).mappedTableResource(wordTable);
        DDBWordConverter.convertWordBatch(language, words).forEach(wordDDB -> batchBuilder.addPutItem(wordDDB));

        BatchWriteResult result = dynamoDbEnhancedClient.batchWriteItem(b -> b.writeBatches(batchBuilder.build()));

        List<DDBWord> unprocessedWords = result.unprocessedPutItemsForTable(wordTable);
        if (!unprocessedWords.isEmpty()) {
            Set<String> unprocessedWordIds = unprocessedWords.stream()
                    .map(wordDDB -> wordDDB.id())
                    .collect(Collectors.toUnmodifiableSet());

            return words.stream()
                    .filter(word -> !unprocessedWordIds.contains(word.id()))
                    .toList();
        }

        return words;
    }

    @Override
    public Word findDuplicateWords(Language language, List<String> lexiconIdsCheck, String owner, Word word) {
        List<String> duplicateWordIds = findDuplicateWordsIds(language, word, lexiconIdsCheck);

        if (duplicateWordIds != null && !duplicateWordIds.isEmpty()) {
            List<Word> duplicateWords = loadWords(duplicateWordIds);
            List<Word> duplicateOwnedWords = duplicateWords.stream()
                    .filter(dupWord -> owner.equals(dupWord.owner()))
                    .toList();

            if (!duplicateOwnedWords.isEmpty()) {
                return duplicateOwnedWords.getFirst();
            }
        }

        return null;
    }

    private List<String> findDuplicateWordsIds(Language language, Word word, List<String> lexiconIdsCheck) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(DDBWordConverter.computeDepupeHash(language, word)).build()))
                .filterExpression(buildLexiconIdInExpression(lexiconIdsCheck))
                .attributesToProject(DDBWord.ID_ATTRIBUTE_NAME)
                .build();

        SdkIterable<Page<DDBWord>> response = wordTable.index(DDBWord.DEDUPE_INDEX_NAME).query(request);

        return response.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbWord -> ddbWord.id()).toList();
    }

    private Expression buildLexiconIdInExpression(List<String> lexiconIdsCheck) {
        Map<String, String> expressionAttributeNames = new HashMap<>();
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();

        StringBuilder filterExpression = new StringBuilder("#lexiconId IN (");
        expressionAttributeNames.put("#lexiconId", DDBWord.LEXICON_ID_ATTRIBUTE_NAME);
        for (int i = 0; i < lexiconIdsCheck.size(); i++) {
            if (i > 0) {
                filterExpression.append(", ");
            }
            filterExpression.append(":lexiconId").append(i);
            expressionAttributeValues.put(":lexiconId" + i, AttributeValue.builder().s(lexiconIdsCheck.get(i)).build());
        }
        filterExpression.append(")");

        return Expression.builder()
                .expression(filterExpression.toString())
                .expressionNames(expressionAttributeNames)
                .expressionValues(expressionAttributeValues)
                .build();

    }

    @Override
    public int updateWord(Language language, Word word) {
        DDBWord updateWord = DDBWordConverter.convertWord(language, word);
        DDBWord ddbExistingWord = wordTable.getItem(Key.builder().partitionValue(word.id()).build());

        if (ddbExistingWord == null) {
            String errMsg = "Attempt to update word " + word.id() + " failed because word does not existing in data store";

            log.error(errMsg);
            throw new DaoException(errMsg);
        }

        Word existingWord = DDBWordConverter.convertDDBWord(ddbExistingWord);

        // Copy over only the values that should be updated. Convert back to Word again
        // so any processing done by the converter gets run on the updated word
        Word newWordToSave = new Word(
                existingWord.id(),
                existingWord.lexiconId(),
                existingWord.owner(),
                updateWord.elements(),
                updateWord.attributes(),
                updateWord.audioFiles(),
                existingWord.createInstant(),
                Instant.now());

        wordTable.putItem(DDBWordConverter.convertWord(language, newWordToSave));

        return 1;
    }

    @Override
    public void deleteWords(Collection<String> wordIds) {
        for (String wordId : wordIds) {
            wordTable.deleteItem(Key.builder().partitionValue(wordId).build());
        }
    }

    @Override
    public void deleteAllLexiconWords(String lexiconId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional
                        .keyEqualTo(b -> b.partitionValue(lexiconId)))
                .attributesToProject(DDBWord.ID_ATTRIBUTE_NAME)
                .build();

        SdkIterable<Page<DDBWord>> responseIterable = wordTable.index(DDBWord.CREATE_INSTANT_INDEX_NAME).query(request);

        for (Page<DDBWord> ddbWordPage : responseIterable) {
            for (DDBWord ddbWord : ddbWordPage.items()) {
                wordTable.deleteItem(Key.builder().partitionValue(ddbWord.id()).build());
            }
        }
    }

    @Override
    public List<Word> getLexiconWordsBatch(String lexiconId, String username, int count, int offset, Word lastWord) {
        return getLexiconWordsBatchWithFilter(lexiconId, username, count, offset, lastWord, null);
    }

    @Override
    public List<Word> getLexiconWordsBatchWithFilter(String lexiconId, String username, int count, int offset, Word lastWord, WordFilterOptions wordFilterOptions) {
        String sortStartDate = lastWord == null ? MAX_ISO_DATE_STR : lastWord.createInstant().toString();

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .limit(count * getLimitMultipler(wordFilterOptions))
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
                    .toList());

            if (wordsToReturn.size() >= count) {
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
                .toList();

        if (audioFilesToSave.size() == existingWord.audioFiles().size()) {
            return 0;  // Nothing to delete
        }

        DDBWord wordSaved = wordTable.updateItem(b -> b
                .item(DDBWord.builder(existingWord)
                    .audioFiles(audioFilesToSave)
                    .build()));

        if (wordSaved == null) {
            return 0;
        }

        return 1;
    }

    @Override
    public List<String> getWordsUniqueToLexicon(String lexiconId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(lexiconId).build()))
                .attributesToProject(DDBWord.ID_ATTRIBUTE_NAME)
                .build();

        SdkIterable<Page<DDBWord>> responseIterable = wordTable.index(DDBWord.CREATE_INSTANT_INDEX_NAME).query(request);

        return responseIterable.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbWord -> ddbWord.id())
                .toList();
    }

    @Override
    public int getTotalLexiconWordCount(String lexiconId) {
        return getWordsUniqueToLexicon(lexiconId).size();
    }

    @Override
    public List<String> getUniqueElementValues(String lexiconId, WordElement wordElement, int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(lexiconId).build()))
                .attributesToProject(DDBWord.ELEMENTS_ATTRIBUTE_NAME)
                .limit(limit)
                .build();

        SdkIterable<Page<DDBWord>> responseIterable = wordTable.index(DDBWord.CREATE_INSTANT_INDEX_NAME).query(request);

        return responseIterable.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbWord -> ddbWord == null ? null : ddbWord.elements() == null ? null : ddbWord.elements().get(wordElement.getId()))
                .filter(elementValue -> elementValue != null && !elementValue.isBlank())
                .distinct()
                .toList();
    }

    private Expression buildFilterFromOptions(WordFilterOptions options) {
        StringBuilder expression = new StringBuilder();
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();

        if (options.attributes() != null && !options.attributes().isBlank()) {
            expression.append("contains(#attributes, :attributes)");
            expressionNames.put("#attributes", DDBWord.ATTRIBUTES_ATTRIBUTE_NAME);
            expressionValues.put(":attributes", AttributeValue.builder().s(options.attributes()).build());
        }

        if (options.elements() != null && !options.elements().isEmpty()) {
            for (Map.Entry<String, String> elementEntry : options.elements().entrySet()) {
                if (elementEntry.getValue() != null && !elementEntry.getValue().isBlank()) {
                    String elementName = "element_" + elementEntry.getKey();
                    expression.append((!expression.isEmpty()) ? " AND " : "").append("begins_with (#elements.").append(elementEntry.getKey()).append(", :").append(elementName).append(")");
                    expressionNames.put("#elements", DDBWord.ELEMENTS_ATTRIBUTE_NAME);
                    expressionValues.put(":" + elementName, AttributeValue.builder().s(elementEntry.getValue()).build());
                }
            }
        }

        if (options.hasAudio() != null) {
            if (!expression.isEmpty()) {
                expression.append(" AND ");
            }

            if (options.hasAudio()) {
                expression.append("size(#audioFiles) > :zero");
            } else {
                expression.append("(attribute_not_exists(#audioFiles) OR size(#audioFiles) = :zero)");
            }
            expressionNames.put("#audioFiles", DDBWord.AUDIO_FILES_ATTRIBUTE_NAME);
            expressionValues.put(":zero", AttributeValue.builder().n("0").build());
        }

        if (expression.isEmpty()) {
            return null;
        }

        return Expression.builder()
                .expression(expression.toString())
                .expressionNames(expressionNames)
                .expressionValues(expressionValues)
                .build();
    }

    // For non-filtered searches, the limit is set to the exact number that needs to be returned. For filtered searches,
    // using a limit set to the desired results will drastically slow down the search as the limit is applied before
    // and filters, so it's likely that a large number of calls to Dynamo simply return an empty set. This function
    // returns a heuristic value for how that limit should be modified based on which filters are used.
    private int getLimitMultipler(WordFilterOptions options) {
        if (options != null) {
            if (options.elements() != null && !options.elements().isEmpty()) {
                for (String elementValue : options.elements().values()) {
                    if (elementValue != null && !elementValue.isBlank()) {
                        return ELEMENT_FILTER_LIMIT_MULTIPLER;
                    }
                }
            }

            if (options.attributes() != null && !options.attributes().isBlank()) {
                return ATTRIBUTE_FILTER_LIMIT_MULTIPLER;
            }

            if (options.hasAudio() != null) {
                return AUDIO_FILTER_LIMIT_MULTIPLER;
            }
        }

        return 1;
    }
}
package com.gt.ssrs.lexicon.aws;

import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.model.LexiconMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class LexiconDaoDDB implements LexiconDao {

    private static final Logger log = LoggerFactory.getLogger(LexiconDaoDDB.class);

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;

    private final DynamoDbTable<DDBLexiconMetadata> lexiconTable;

    @Autowired
    public LexiconDaoDDB(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;

        this.lexiconTable = dynamoDbEnhancedClient.table(DDBLexiconMetadata.TABLE_NAME, TableSchema.fromImmutableClass(DDBLexiconMetadata.class));
    }

    @Override
    public List<LexiconMetadata> getAllLexiconMetadata(String username) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(username).build()))
                .scanIndexForward(true)
                .build();

        SdkIterable<Page<DDBLexiconMetadata>> results = lexiconTable.index(DDBLexiconMetadata.OWNER_INDEX_NAME).query(request);

        return results.stream()
                .flatMap(page -> page.items().stream())
                .map(ddbLexiconMetadata -> DDBLexiconConverter.convertDDBLexiconMetadata(ddbLexiconMetadata))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public LexiconMetadata getLexiconMetadata(String id) {
        DDBLexiconMetadata ddbLexiconMetadata = lexiconTable.getItem(Key.builder().partitionValue(id).build());

        if (ddbLexiconMetadata == null) {
            return null;
        }

        return DDBLexiconConverter.convertDDBLexiconMetadata(ddbLexiconMetadata);
    }

    @Override
    public List<LexiconMetadata> getLexiconMetadatas(Collection<String> ids) {
        ReadBatch.Builder<DDBLexiconMetadata> batchBuilder = ReadBatch.builder(DDBLexiconMetadata.class).mappedTableResource(lexiconTable);
        ids.forEach(id -> batchBuilder.addGetItem(Key.builder().partitionValue(id).build()));

        BatchGetResultPageIterable resultPages = dynamoDbEnhancedClient.batchGetItem(b -> b.addReadBatch(batchBuilder.build()));

        return resultPages.resultsForTable(lexiconTable).stream()
                .map(ddbLexiconMetadata -> DDBLexiconConverter.convertDDBLexiconMetadata(ddbLexiconMetadata))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public int updateLexiconMetadata(LexiconMetadata lexicon) {
        DDBLexiconMetadata existingLexiconMetadata = lexiconTable.getItem(Key.builder().partitionValue(lexicon.id()).build());

        if (existingLexiconMetadata == null) {
            return 0;
        }

        // TODO: Verify owner?

        lexiconTable.updateItem(DDBLexiconConverter.convertLexiconMetadata(lexicon));

        return 1;
    }

    @Override
    public int createLexiconMetadata(LexiconMetadata lexicon) {
        lexiconTable.putItem(DDBLexiconConverter.convertLexiconMetadata(lexicon));

        return 1;
    }

    @Override
    public void deleteLexiconMetadata(String lexiconId) {
        lexiconTable.deleteItem(Key.builder().partitionValue(lexiconId).build());
    }

}

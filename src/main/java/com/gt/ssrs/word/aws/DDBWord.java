package com.gt.ssrs.word.aws;

import software.amazon.awssdk.enhanced.dynamodb.mapper.Order;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@DynamoDbImmutable(builder = DDBWord.Builder.class)
public class DDBWord {

    public static final String TABLE_NAME = "Words";

    public static final String CREATE_INSTANT_INDEX_NAME = TABLE_NAME + "-by-create-instant";

    public static final String ELEMENT_INDEX_NAME_PREFIX = TABLE_NAME + "-by-";
    public static final String KANA_INDEX_NAME = ELEMENT_INDEX_NAME_PREFIX + "kana";
    public static final String KANJI_INDEX_NAME = ELEMENT_INDEX_NAME_PREFIX + "kanji";
    public static final String MEANING_INDEX_NAME = ELEMENT_INDEX_NAME_PREFIX + "meaning";

    public static final String ID_ATTRIBUTE_NAME = "id";
    public static final String LEXICON_ID_ATTRIBUTE_NAME = "lexiconId";
    public static final String OWNER_ATTRIBUTE_NAME = "owner";
    public static final String ELEMENTS_ATTRIBUTE_NAME = "elements";
    public static final String ATTRIBUTES_ATTRIBUTE_NAME = "attributes";
    public static final String AUDIO_FILES_ATTRIBUTE_NAME = "audioFiles";
    public static final String CREATE_INSTANT_ATTRIBUTE_NAME = "createInstant";
    public static final String UPDATE_INSTANT_ATTRIBUTE_NAME = "updateInstant";

    private final String id;
    private final String lexiconId;
    private final String owner;
    private final Map<String, String> elements;
    private final String attributes;
    private final List<String> audioFiles;
    private final Instant createInstant;
    private final Instant updateInstant;

    private DDBWord(Builder b) {
        this.id = b.id;
        this.lexiconId = b.lexiconId;
        this.owner = b.owner;
        this.elements = b.elements == null ? null : Map.copyOf(b.elements);
        this.attributes = b.attributes;
        this.audioFiles = b.audioFiles == null ? null : List.copyOf(b.audioFiles);
        this.createInstant = b.createInstant;
        this.updateInstant = b.updateInstant;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(DDBWord ddbWord) {
        return new Builder(ddbWord);
    }

    @DynamoDbPartitionKey()
    public String id() {
        return id;
    }

    @DynamoDbAttribute(LEXICON_ID_ATTRIBUTE_NAME)
    @DynamoDbSecondaryPartitionKey(indexNames = { KANA_INDEX_NAME, KANJI_INDEX_NAME, MEANING_INDEX_NAME, CREATE_INSTANT_INDEX_NAME })
    public String lexiconId() {
        return lexiconId;
    }

    @DynamoDbAttribute(OWNER_ATTRIBUTE_NAME)
    public String owner() {
        return owner;
    }

    @DynamoDbAttribute(ELEMENTS_ATTRIBUTE_NAME)
    public Map<String, String> elements() {
        return elements;
    }

    @DynamoDbAttribute(ATTRIBUTES_ATTRIBUTE_NAME)
    public String attributes() {
        return attributes;
    }

    @DynamoDbAttribute(AUDIO_FILES_ATTRIBUTE_NAME)
    public List<String> audioFiles() {
        return audioFiles;
    }

    @DynamoDbAttribute(CREATE_INSTANT_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { DDBWord.CREATE_INSTANT_INDEX_NAME }, order = Order.FIRST)
    public Instant createInstant() {
        return createInstant;
    }

    @DynamoDbAttribute(UPDATE_INSTANT_ATTRIBUTE_NAME    )
    public Instant updateInstant() {
        return updateInstant;
    }

    @Override
    public String toString() {
        return "DDBWord{" +
                "id='" + id + '\'' +
                ", lexiconId='" + lexiconId + '\'' +
                ", owner='" + owner + '\'' +
                ", elements=" + elements +
                ", attributes='" + attributes + '\'' +
                ", audioFiles=" + audioFiles +
                ", createInstant=" + createInstant +
                ", updateInstant=" + updateInstant +
                '}';
    }

    public static class Builder {
        private String id;
        private String lexiconId;
        private String owner;
        private Map<String, String> elements;
        private String attributes;
        private List<String> audioFiles;
        private Instant createInstant;
        private Instant updateInstant;

        private Builder() { }

        private Builder(DDBWord ddbWord) {
            this.id = ddbWord.id;
            this.lexiconId = ddbWord.lexiconId;
            this.owner = ddbWord.owner;
            this.elements = ddbWord.elements;
            this.attributes = ddbWord.attributes;
            this.audioFiles = ddbWord.audioFiles;
            this.createInstant = ddbWord.createInstant;
            this.updateInstant = ddbWord.updateInstant;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder lexiconId(String lexiconId) {
            this.lexiconId = lexiconId;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder elements(Map<String, String> elements) {
            this.elements = elements;
            return this;
        }

        public Builder attributes(String attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder audioFiles(List<String> audioFiles) {
            this.audioFiles = audioFiles;
            return this;
        }

        public Builder createInstant(Instant createInstant) {
            this.createInstant = createInstant;
            return this;
        }

        public Builder updateInstant(Instant updateInstant) {
            this.updateInstant = updateInstant;
            return this;
        }

        public DDBWord build() {
            return new DDBWord(this);
        }
    }
}
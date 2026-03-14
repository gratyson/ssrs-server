package com.gt.ssrs.lexicon.aws;

import com.gt.ssrs.word.aws.DDBWord;
import software.amazon.awssdk.enhanced.dynamodb.mapper.Order;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;

@DynamoDbImmutable(builder = DDBLexiconMetadata.Builder.class)
public class DDBLexiconMetadata {

    public static final String TABLE_NAME = "LexiconMetadata";
    public static final String OWNER_INDEX_NAME = "LexiconMetadata-by-owner";

    public static final String ID_ATTRIBUTE_NAME = "id";
    public static final String OWNER_ATTRIBUTE_NAME = "owner";
    public static final String TITLE_ATTRIBUTE_NAME = "title";
    public static final String DESCRIPTION_ATTRIBUTE_NAME = "description";
    public static final String LANGUAGE_ID_ATTRIBUTE_NAME = "languageId";
    public static final String IMAGE_FILE_NAME_ATTRIBUTE_NAME = "imageFileName";
    public static final String CREATE_INSTANT_ATTRIBUTE_NAME = "createInstant";
    public static final String UPDATE_INSTANT_ATTRIBUTE_NAME = "updateInstant";

    private final String id;
    private final String owner;
    private final String title;
    private final String description;
    private final long languageId;
    private final String imageFileName;
    private final Instant createInstant;
    private final Instant updateInstant;

    private DDBLexiconMetadata(Builder b) {
        this.id = b.id;
        this.owner = b.owner;
        this.title = b.title;
        this.description = b.description;
        this.languageId = b.languageId;
        this.imageFileName = b.imageFileName;
        this.createInstant = b.createInstant;
        this.updateInstant = b.updateInstant;
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbAttribute(ID_ATTRIBUTE_NAME)
    @DynamoDbPartitionKey
    public String id() {
        return id;
    }

    @DynamoDbAttribute(OWNER_ATTRIBUTE_NAME)
    @DynamoDbSecondaryPartitionKey(indexNames = { OWNER_INDEX_NAME })
    public String owner() {
        return owner;
    }

    @DynamoDbAttribute(TITLE_ATTRIBUTE_NAME)
    @DynamoDbSecondarySortKey(indexNames = { OWNER_INDEX_NAME })
    public String title() {
        return title;
    }

    @DynamoDbAttribute(DESCRIPTION_ATTRIBUTE_NAME)
    public String description() {
        return description;
    }

    @DynamoDbAttribute(LANGUAGE_ID_ATTRIBUTE_NAME)
    public long languageId() {
        return languageId;
    }

    @DynamoDbAttribute(IMAGE_FILE_NAME_ATTRIBUTE_NAME)
    public String imageFileName() {
        return imageFileName;
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
        return "DDBLexiconMetadata{" +
                "id='" + id + '\'' +
                ", owner='" + owner + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", languageId=" + languageId +
                ", imageFileName='" + imageFileName + '\'' +
                ", createInstant=" + createInstant +
                ", updateInstant=" + updateInstant +
                '}';
    }

    public static class Builder {
        private String id;
        private String owner;
        private String title;
        private String description;
        private long languageId;
        private String imageFileName;
        private Instant createInstant;
        private Instant updateInstant;

        private Builder() { }

        public DDBLexiconMetadata build() {
            return new DDBLexiconMetadata(this);
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder languageId(long languageId) {
            this.languageId = languageId;
            return this;
        }

        public Builder imageFileName(String imageFileName) {
            this.imageFileName = imageFileName;
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
    }
}

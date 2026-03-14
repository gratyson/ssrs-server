package com.gt.ssrs.word.aws;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@DynamoDbImmutable(builder = DDBWordElements.Builder.class)
public class DDBWordElements {

    // Explicitly defining the word elements so secondary indexes can be defined
    // on the appropriate properties.
    //
    // If adding a new element type in the WordElement, then the corresponding property
    // needs to be here and the conversion function in DDBWordConverter needs to be updated.
    // Unit tests are set up that should fail is an WordElement value is added without the
    // appropriate updates, though it will not verify indexes are created as expected

    private final String kana;
    private final String kanji;
    private final String alternateKanji;
    private final String meaning;
    private final String accent;

    private DDBWordElements(Builder b) {
        this.kana = b.kana;
        this.kanji = b.kanji;
        this.alternateKanji = b.alternateKanji;
        this.meaning = b.meaning;
        this.accent = b.accent;
    }

    public static Builder builder() {
        return new Builder();
    }

    @DynamoDbAttribute("kana")
    @DynamoDbSecondarySortKey(indexNames = { DDBWord.KANA_INDEX_NAME })
    public String kana() {
        return kana;
    }

    @DynamoDbAttribute("kanji")
    @DynamoDbSecondarySortKey(indexNames = { DDBWord.KANJI_INDEX_NAME })
    public String kanji() {
        return kanji;
    }

    @DynamoDbAttribute("alternateKanji")
    public String alternateKanji() {
        return alternateKanji;
    }

    @DynamoDbAttribute("meaning")
    @DynamoDbSecondarySortKey(indexNames = { DDBWord.MEANING_INDEX_NAME })
    public String meaning() {
        return meaning;
    }

    @DynamoDbAttribute("accent")
    public String accent() {
        return accent;
    }

    public static class Builder {
        private String kana;
        private String kanji;
        private String alternateKanji;
        private String meaning;
        private String accent;

        private Builder() { }

        public DDBWordElements build() {
            return new DDBWordElements(this);
        }

        public Builder kana(String kana) {
            this.kana = kana;
            return this;
        }

        public Builder kanji(String kanji) {
            this.kanji = kanji;
            return this;
        }

        public Builder alternateKanji(String alternateKanji) {
            this.alternateKanji = alternateKanji;
            return this;
        }

        public Builder meaning(String meaning) {
            this.meaning = meaning;
            return this;
        }

        public Builder accent(String accent) {
            this.accent = accent;
            return this;
        }
    }
}

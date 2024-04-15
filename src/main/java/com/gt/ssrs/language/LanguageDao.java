package com.gt.ssrs.language;

import com.gt.ssrs.language.model.DBLanguage;
import com.gt.ssrs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LanguageDao {

    private static final Logger log = LoggerFactory.getLogger(LanguageDao.class);

    private NamedParameterJdbcTemplate template;

    @Autowired
    public LanguageDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    private static final String GET_ALL_LANGUAGE_ELEMENTS_SQL =
            "SELECT id, name, abbreviation, weight, apply_language_font, test_time_multiplier " +
            "FROM word_elements";

    private static final String GET_ALL_LANGUAGES_SQL =
            "SELECT l.id, l.display_name, l.font, l.audio_file_regex, l.tests_to_double, e.word_element_id, e.required, e.core, e.dedupe, e.ordinal " +
            "FROM language l LEFT JOIN language_elements e on l.id = e.language_id " +
            "ORDER BY l.id, e.ordinal";

    private static final String GET_ALL_LANGUAGE_REVIEW_RELATIONSHIPS_SQL =
            "SELECT id, display_name, language_id, test_on, prompt_with, show_after_test, fallback_id, is_review_relationship " +
            "FROM language_test_relationships " +
            "ORDER BY language_id, ordinal";

    private static final String GET_LANGUAGE_SEQUENCE_SQL =
            "SELECT review_mode, option_count, record_event, relationship_id " +
            "FROM language_sequence " +
            "WHERE language_id = :languageId AND review_type = :reviewType " +
            "ORDER BY ordinal";

    public List<WordElement> getAllLanguageElements() {
        return template.query(GET_ALL_LANGUAGE_ELEMENTS_SQL,
                (rs, rowNum) -> new WordElement(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("abbreviation"),
                        rs.getInt("weight"),
                        rs.getBoolean("apply_language_font"),
                        rs.getDouble("test_time_multiplier")));
    }

    public List<DBLanguage> getAllLanguages() {
        return template.query(GET_ALL_LANGUAGES_SQL, rs -> {
            List<DBLanguage> languages = new ArrayList<>();

            int curId = -1;
            String displayName = "";
            String fontName = "";
            String audioFileRegex = "";
            double testsToDouble = 1;
            List<String> validElements = new ArrayList<>();
            List<String> requiredElements = new ArrayList<>();
            List<String> coreElements = new ArrayList<>();
            List<String> dedupeElements = new ArrayList<>();

            while (rs.next()) {
                int id = rs.getInt("id");
                if (curId != id) {
                    if (curId > 0) {
                        languages.add(new DBLanguage(curId, displayName, fontName, audioFileRegex, testsToDouble, validElements, requiredElements, coreElements, dedupeElements));
                    }

                    curId = id;
                    displayName = rs.getString("display_name");
                    fontName = rs.getString("font");
                    audioFileRegex = rs.getString("audio_file_regex");
                    testsToDouble = rs.getDouble("tests_to_double");

                    validElements.clear();
                    requiredElements.clear();
                    coreElements.clear();
                }

                String wordElementId = rs.getString("word_element_id");
                validElements.add(wordElementId);
                if (rs.getBoolean("required")) {
                    requiredElements.add(wordElementId);
                }
                if (rs.getBoolean("core")) {
                    coreElements.add(wordElementId);
                }
                if (rs.getBoolean("dedupe")) {
                    dedupeElements.add(wordElementId);
                }
            }
            if (curId > 0) {
                languages.add(new DBLanguage(curId, displayName, fontName, audioFileRegex, testsToDouble, validElements,
                        requiredElements, coreElements, dedupeElements));
            }

            return languages;
        });
    }

    public Map<Long, List<TestRelationship>> getAllReviewRelationships() {
        return template.query(GET_ALL_LANGUAGE_REVIEW_RELATIONSHIPS_SQL, rs -> {
            Map<Long, List<TestRelationship>> allLanguageReviewRelationships = new HashMap<>();

            long curLanguageId = -1;
            List<TestRelationship> reviewRelationships = null;

            while(rs.next()) {
                long languageId = rs.getLong("language_id");
                if (curLanguageId != languageId) {
                    if (reviewRelationships != null) {
                        allLanguageReviewRelationships.put(curLanguageId, reviewRelationships);
                    }
                    curLanguageId = languageId;
                    reviewRelationships = new ArrayList<>();
                }

                reviewRelationships.add(new TestRelationship(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("test_on"),
                        rs.getString("prompt_with"),
                        rs.getString("show_after_test"),
                        rs.getString("fallback_id"),
                        rs.getBoolean("is_review_relationship")));
            }

            if (reviewRelationships != null) {
                allLanguageReviewRelationships.put(curLanguageId, reviewRelationships);
            }

            return allLanguageReviewRelationships;
        });
    }

    public List<LanguageSequenceValue> getLanguageSequence(long languageId, ReviewType reviewType) {
        return template.query(GET_LANGUAGE_SEQUENCE_SQL, Map.of("languageId", languageId, "reviewType", reviewType.name()), (rs, rowNum) -> {
           return new LanguageSequenceValue(ReviewMode.valueOf(
                   rs.getString("review_mode")),
                   rs.getInt("option_count"),
                   rs.getBoolean("record_event"),
                   rs.getString("relationship_id"));
        });
    }
}

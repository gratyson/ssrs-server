package com.gt.ssrs.word.impl;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.word.WordDao;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WordDaoPG implements WordDao {

    private static final Logger log = LoggerFactory.getLogger(WordDaoPG.class);

    private final NamedParameterJdbcTemplate template;

    private static final List<String> AVAILABLE_ELEMENTS = List.of(
            "kana",
            "meaning",
            "kanji",
            "alt_kanji",
            "accent");

    private static final String DUMMY_ID = "_dummy_id";

    private static final String AUDIO_JOIN_PREFIX = "WITH words AS (";
    private static final String AUDIO_JOIN_SUFFIX = ") SELECT * FROM words w LEFT JOIN word_audio a ON w.id = a.word_id ORDER BY w.create_seq_num DESC";
    private static final String WORDS_QUERY_SQL =
            "SELECT id, lexicon_id, owner, attributes, " + String.join(", ", AVAILABLE_ELEMENTS) + ", create_seq_num, create_instant, update_instant " +
                    "FROM words " +
                    "WHERE id IN (:wordIds)";
    private static final String CREATE_WORD_SQL_PREFIX =
            "INSERT INTO words (id, lexicon_id, owner, attributes, " + String.join(", ", AVAILABLE_ELEMENTS) + ") " +
                    "SELECT :wordId as id, :lexiconId as lexicon_id, :owner as owner, :attributes as attributes, " + String.join(", ", AVAILABLE_ELEMENTS.stream().map(element -> ":" + element + " as " + element).toList()) + " " +
                    "WHERE NOT EXISTS (SELECT 1 FROM words WHERE lexicon_id = :lexiconId AND owner = :owner";
    private static final String CREATE_WORD_ELEMENTS_EXISTS_SQL =
            " AND %s = :%s";
    private static final String CREATE_WORD_CLOSING_SQL =
            ") LIMIT 1 " +
                    "ON CONFLICT DO NOTHING";

    private static final String FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_PREFIX =
            "WITH words AS " +
                    "(SELECT id, lexicon_id, owner, attributes, create_instant, update_instant, " + String.join(", ", AVAILABLE_ELEMENTS) + " " +
                    "FROM words " +
                    "WHERE lexicon_id != :lexiconId AND owner = :owner ";
    private static final String FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_DEDUPE_CONDITION =
            " AND %s = :%s";
    private static final String FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_SUFFIX =
            ") SELECT w.id, w.lexicon_id, w.owner, w.attributes, w." + String.join(", w.", AVAILABLE_ELEMENTS) + ", a.audio_file_name, w.create_instant, w.update_instant FROM words w LEFT JOIN word_audio a ON w.id = a.word_id";

    private static final String UPDATE_WORD_SQL = "UPDATE words " +
            "SET lexicon_id = :lexiconId, owner = :owner, attributes = :attributes, " + String.join(", ", AVAILABLE_ELEMENTS.stream().map(element -> element + " = :" + element).toList()) + " " +
            "WHERE id = :wordId";

    private static final String WORDS_BATCH_QUERY_SQL = "SELECT id, lexicon_id, owner, attributes, " + AVAILABLE_ELEMENTS.stream().collect(Collectors.joining(", ")) + ", create_seq_num " +
            "FROM words " +
            "WHERE lexicon_id = :lexiconId ORDER BY create_seq_num DESC OFFSET :offset LIMIT :count ";

    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_SELECT =
            "SELECT word.id, word.lexicon_id, word.owner, word.attributes, " + AVAILABLE_ELEMENTS.stream().map(e -> "word." + e).collect(Collectors.joining(", ")) + ", word.create_seq_num " +
                    "FROM words word " +
                    "LEFT JOIN lexicon_review_history history ON word.lexicon_id = history.lexicon_id AND word.id = history.word_id and history.username = :username ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_AUDIO_JOIN =
            "LEFT JOIN LATERAL (SELECT audio_file_name FROM word_audio a WHERE a.word_id = word.id LIMIT 1) audio ON TRUE ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_WHERE =
            "WHERE word.lexicon_id = :lexiconId ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_ATTRIBUTE_FILTER =
            "AND word.%s LIKE '%%' || :%s || '%%' ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_WITH_AUDIO =
            "AND audio.audio_file_name IS NOT NULL ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_NO_AUDIO =
            "AND audio.audio_file_name IS NULL ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_LEARNED =
            "AND history.learned IS TRUE ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_NOT_LEARNED =
            "AND history.learned IS NOT TRUE ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_ORDER_AND_LIMIT =
            "ORDER BY word.create_seq_num DESC OFFSET :offset LIMIT :count";

    private static final String GET_WORDS_UNIQUE_TO_LEXICON_SQL =
            "SELECT id " +
                    "FROM words " +
                    "WHERE lexicon_id = :lexiconId";

    private static final String GET_TOTAL_LEXICON_WORDS_SQL =
            "SELECT COUNT(*) FROM words WHERE lexicon_id = :lexiconId";

    public WordDaoPG(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    private static final String GET_AUDIO_FILES_NAMES_FOR_WORD_SQL = "SELECT audio_file_name FROM word_audio WHERE word_id = :wordId";
    private static final String GET_AUDIO_FILES_NAMES_FOR_WORD_BATCH_SQL = "SELECT word_id, audio_file_name FROM word_audio WHERE word_id in (:wordIds)";
    private static final String SET_AUDIO_FILE_FOR_WORD_SQL = "INSERT INTO word_audio (word_id, audio_file_name) VALUES (:wordId, :audioFileName)";
    private static final String DELETE_AUDIO_FILE_SQL = "DELETE FROM word_audio WHERE word_id = :wordId AND audio_file_name = :audioFileName";

    private static final String WORD_ELEMENT_TOKEN = "$wordElement$";
    private static final String GET_UNIQUE_VALUES_OF_ELEMENT_IN_LEXICON =
            "SELECT distinct(" + WORD_ELEMENT_TOKEN + ") " +
                    "FROM words " +
                    "WHERE lexicon_id = :lexiconId AND " + WORD_ELEMENT_TOKEN + " IS NOT NULL AND " + WORD_ELEMENT_TOKEN + " != '' " +
                    "LIMIT :limit";

    private static final String DELETE_WORDS_SQL =
            "DELETE FROM word_audio WHERE word_id IN (:wordIds); " +
            "DELETE FROM words WHERE id IN (:wordIds); ";


    private static final String DELETE_LEXICON_WORDS_SQL =
            "DELETE FROM word_audio WHERE word_id IN (SELECT id FROM words WHERE lexicon_id = :lexiconId); " +
            "DELETE FROM words WHERE lexicon_id = :lexiconId; ";

    @Override
    public Word loadWord(String wordId) {
        List<Word> loadedWords = loadWords(List.of(wordId));

        if (loadedWords.size() > 0) {
            return loadedWords.get(0);
        }

        return Word.EMPTY_WORD;
    }

    @Override
    public List<Word> loadWords(Collection<String> wordIds) {
        return template.query(AUDIO_JOIN_PREFIX + WORDS_QUERY_SQL + AUDIO_JOIN_SUFFIX,
                Map.of("wordIds", wordIds),
                rs -> { return processWordWithAudioResultSet(rs); });
    }

    @Override
    public int createWord(Language language, String lexiconId, Word word) {
        return template.update(getCreateWordSql(language), new MapSqlParameterSource(getWordParamMap(word)));
    }

    @Override
    public List<Word> createWords(Language language, String lexiconId, List<Word> words) {
        List<MapSqlParameterSource> paramsList = new ArrayList<>();

        for(Word word : words) {
            paramsList.add(new MapSqlParameterSource(getWordParamMap(word)));
        }

        int[] updateCnts = template.batchUpdate(getCreateWordSql(language), paramsList.toArray(new MapSqlParameterSource[0]));
        return IntStream.range(0, updateCnts.length).mapToObj(i -> updateCnts[i] > 0 ? words.get(i) : null).filter(word -> word != null).toList();

    }

    private String getCreateWordSql(Language language) {
        StringBuilder sb = new StringBuilder();
        sb.append(CREATE_WORD_SQL_PREFIX);
        for(WordElement languageElement : language.getDedupeElements()) {
            sb.append(String.format(CREATE_WORD_ELEMENTS_EXISTS_SQL, languageElement.getId(), languageElement.getId()));
        }
        sb.append(CREATE_WORD_CLOSING_SQL);
        return sb.toString();
    }

    @Override
    public Word findDuplicateWordInOtherLexicons(Language language, String lexiconId, String owner, Word word) {
        String sql = createFindDuplicateWordsInOtherLexiconsSql(language);

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("lexiconId", lexiconId);
        params.addValue("owner", owner);
        for (WordElement dedupeElement : language.getDedupeElements()) {
            params.addValue(dedupeElement.getId(), word.elements().getOrDefault(dedupeElement.getId(), null));
        }

        List<Word> duplicateWords = template.query(sql, params, (rs) -> {
            return processWordWithAudioResultSet(rs);
        });

        if (duplicateWords == null || duplicateWords.size() == 0) {
            return null;
        }

        Word duplicateWord = duplicateWords.get(0);

        if (duplicateWords.size() > 1) {
            log.warn("Multiple duplicate words found match word {}", duplicateWord.id());
        }

        return duplicateWord;
    }

    private String createFindDuplicateWordsInOtherLexiconsSql(Language language) {
        StringBuilder sb = new StringBuilder();
        sb.append(FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_PREFIX);
        for(WordElement dedupeElement : language.getDedupeElements()) {
            sb.append(String.format(FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_DEDUPE_CONDITION, dedupeElement.getId(), dedupeElement.getId()));
        }
        sb.append(FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_SUFFIX);

        return sb.toString();
    }

    @Override
    public int updateWord(Word word) {
        return template.update(UPDATE_WORD_SQL, getWordParamMap(word));
    }

    private Map<String, Object> getWordParamMap(Word word) {
        Map<String, Object> paramMap = new HashMap<>();

        paramMap.put("wordId", word.id());
        paramMap.put("lexiconId", word.lexiconId());
        paramMap.put("owner", word.owner());
        paramMap.put("attributes", word.attributes() != null ? word.attributes() : "");
        for(String element : AVAILABLE_ELEMENTS) {
            paramMap.put(element, word.elements().get(element));
        }

        return paramMap;
    }

    @Override
    public List<Word> getLexiconWordsBatch(String lexiconId, String username, int count, int offset, Word lastWord) {
        List<Word> words = template.query(AUDIO_JOIN_PREFIX + WORDS_BATCH_QUERY_SQL + AUDIO_JOIN_SUFFIX,
                Map.of("lexiconId", lexiconId, "count", count, "offset", offset),
                rs -> { return processWordWithAudioResultSet(rs); });

        return words;
    }

    @Override
    public List<Word> getLexiconWordsBatchWithFilter(String lexiconId, String username, int count, int offset, Word lastWord, WordFilterOptions wordFilterOptions) {
        return template.query(
                getLexiconWordsBatchWithFilterSql(lexiconId, count, offset, wordFilterOptions),
                getLexiconWordsBatchWithFilterParams(lexiconId, username, count, offset, wordFilterOptions),
                rs -> { return processWordWithAudioResultSet(rs); });
    }

    private String getLexiconWordsBatchWithFilterSql(String lexiconId, int count, int offset, WordFilterOptions wordFilterOptions) {
        StringBuilder sql = new StringBuilder();

        sql.append(AUDIO_JOIN_PREFIX);

        sql.append(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_SELECT);
        if (wordFilterOptions.hasAudio() != null) {
            sql.append(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_AUDIO_JOIN);
        }
        sql.append(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_WHERE);
        for(String elementId : wordFilterOptions.elements().keySet()) {
            String elementValue = wordFilterOptions.elements().get(elementId);
            if (!elementValue.isBlank()) {
                sql.append(String.format(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_ATTRIBUTE_FILTER, elementId, elementId));
            }
        }
        if (wordFilterOptions.attributes() != null && !wordFilterOptions.attributes().isBlank()) {
            sql.append(String.format(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_ATTRIBUTE_FILTER, "attributes", "attributes"));
        }
        if (wordFilterOptions.hasAudio() != null) {
            if (wordFilterOptions.hasAudio()) {
                sql.append(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_WITH_AUDIO);
            } else {
                sql.append(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_NO_AUDIO);
            }
        }
        if (wordFilterOptions.learned() != null)  {
            if (wordFilterOptions.learned()) {
                sql.append(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_LEARNED);
            } else {
                sql.append(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_NOT_LEARNED);
            }
        }
        sql.append(WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_ORDER_AND_LIMIT);

        sql.append(AUDIO_JOIN_SUFFIX);

        return sql.toString();
    }

    private Map<String, Object> getLexiconWordsBatchWithFilterParams(String lexiconId, String username, int count, int offset, WordFilterOptions wordFilterOptions) {
        Map<String, Object> paramMap = new HashMap<>();

        paramMap.put("lexiconId", lexiconId);
        paramMap.put("count", count);
        paramMap.put("offset", offset);
        paramMap.put("username", username);
        for(String elementId : wordFilterOptions.elements().keySet()) {
            String elementValue = wordFilterOptions.elements().get(elementId);
            if (!elementValue.isBlank()) {
                paramMap.put(elementId, elementValue);
            }
        }
        if (wordFilterOptions.attributes() != null && !wordFilterOptions.attributes().isBlank()) {
            paramMap.put("attributes", wordFilterOptions.attributes());
        }

        return paramMap;
    }

    private List<Word> processWordWithAudioResultSet(ResultSet rs) throws SQLException {
        if (rs == null || !rs.next()) {
            return List.of();
        }

        String lastId = "";
        List<Word> words = new ArrayList<>();
        Word wordWithoutAudio = null;
        List<String> audioFileNames = new ArrayList<>();

        do {
            String id = rs.getString("id");
            if (id != null && !id.equals(lastId)) {
                if (wordWithoutAudio != null) {
                    words.add(new Word(wordWithoutAudio.id(), wordWithoutAudio.lexiconId(), wordWithoutAudio.owner(), wordWithoutAudio.elements(), wordWithoutAudio.attributes(), audioFileNames, wordWithoutAudio.createInstant(), wordWithoutAudio.updateInstant()));
                }

                Map<String, String> wordElements = new HashMap<>();
                for (String element : AVAILABLE_ELEMENTS) {
                    String elementValue = rs.getObject(element, String.class);

                    if (elementValue != null && !elementValue.isBlank()) {
                        wordElements.put(element, elementValue);
                    }
                }
                wordWithoutAudio = new Word(id, rs.getString("lexicon_id"), rs.getString("owner"), wordElements, rs.getString("attributes"), List.of(), toInstant(rs.getTimestamp("create_instant")), toInstant(rs.getTimestamp("update_instant")));
                audioFileNames = new ArrayList<>();
                lastId = id;
            }
            String audioFileName = rs.getString("audio_file_name");
            if (audioFileName != null && !audioFileName.isBlank()) {
                audioFileNames.add(audioFileName);
            }
        } while (rs.next());

        if (wordWithoutAudio != null) {
            words.add(new Word(wordWithoutAudio.id(), wordWithoutAudio.lexiconId(), wordWithoutAudio.owner(), wordWithoutAudio.elements(), wordWithoutAudio.attributes(), audioFileNames, wordWithoutAudio.createInstant(), wordWithoutAudio.updateInstant()));
        }

        return words;
    }


    @Override
    public List<String> getAudioFileNamesForWord(String wordId) {
        return template.query(GET_AUDIO_FILES_NAMES_FOR_WORD_SQL, Map.of("wordId", wordId),
                (rs, rowNum) -> rs.getString("audio_file_name"));
    }

    @Override
    public Map<String,List<String>> getAudioFileNamesForWordBatch(List<String> wordIds) {
        return template.query(GET_AUDIO_FILES_NAMES_FOR_WORD_BATCH_SQL, Map.of("wordIds", nonEmptyIdList(wordIds)), (rs) -> {
            Map<String,List<String>> wordIdToAudioFilesMap = new HashMap<>();

            if(rs != null) {
                while (rs.next()) {
                    String wordId = rs.getString("word_id");
                    String audioFileName = rs.getString("audio_file_name");

                    wordIdToAudioFilesMap.computeIfAbsent(wordId, k -> new ArrayList<>()).add(audioFileName);
                }
            }

            return wordIdToAudioFilesMap;
        });
    }

    @Override
    public int setAudioFileNameForWord(String wordId, String audioFileName) {
        return template.update(SET_AUDIO_FILE_FOR_WORD_SQL, Map.of("wordId", wordId, "audioFileName", audioFileName));
    }

    @Override
    public int deleteAudioFileName(String wordId, String audioFileName) {
        return template.update(DELETE_AUDIO_FILE_SQL, Map.of("wordId", wordId, "audioFileName", audioFileName));
    }


    @Override
    public List<String> getWordsUniqueToLexicon(String lexiconId) {
        return template.query(GET_WORDS_UNIQUE_TO_LEXICON_SQL, Map.of("lexiconId", lexiconId), (rs, rowNum) -> rs.getString("id"));
    }

    @Override
    public int getTotalLexiconWordCount(String lexiconId) {
        return template.queryForObject(GET_TOTAL_LEXICON_WORDS_SQL, Map.of("lexiconId", lexiconId), Integer.class);
    }

    @Override
    public List<String> getUniqueElementValues(String lexiconId, WordElement wordElement, int limit) {
        String sql = GET_UNIQUE_VALUES_OF_ELEMENT_IN_LEXICON.replace(WORD_ELEMENT_TOKEN, wordElement.getId());

        return template.query(
                sql,
                Map.of("lexiconId", lexiconId,
                        "limit", limit),
                (rs, rowNum) -> rs.getString(wordElement.getId()));
    }

    @Override
    public void deleteWords(Collection<String> wordIds) {
        template.update(
                DELETE_WORDS_SQL,
                Map.of("wordIds", wordIds));
    }

    @Override
    public void deleteAllLexiconWords(String lexiconId) {
        template.update(
                DELETE_LEXICON_WORDS_SQL,
                Map.of("lexiconId", lexiconId));
    }

    private List<String> nonEmptyIdList(List<String> idList) {
        if (idList == null || idList.size() == 0) {
            return List.of(DUMMY_ID);
        }
        return idList;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

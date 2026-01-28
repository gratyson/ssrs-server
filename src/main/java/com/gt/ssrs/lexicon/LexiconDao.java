package com.gt.ssrs.lexicon;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.lexicon.model.TestOnWordPair;
import com.gt.ssrs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Component
public class LexiconDao {

    private static final Logger log = LoggerFactory.getLogger(LexiconDao.class);

    private static final String DUMMY_ID = "_dummy_id";

    private static final List<String> AVAILABLE_ELEMENTS = List.of(
            "kana",
            "meaning",
            "kanji",
            "alt_kanji",
            "accent");

    private static final String AUDIO_JOIN_PREFIX = "WITH words AS (";
    private static final String AUDIO_JOIN_SUFFIX = ") SELECT * FROM words w LEFT JOIN word_audio a ON w.id = a.word_id ORDER BY w.create_seq_num DESC";
    private static final String WORDS_QUERY_SQL =
            "SELECT id, owner, attributes, " + String.join(", ", AVAILABLE_ELEMENTS) + ", create_seq_num " +
            "FROM words " +
            "WHERE id IN (:wordIds)";
    private static final String CREATE_WORD_SQL_PREFIX =
            "INSERT INTO words (id, owner, attributes, " + String.join(", ", AVAILABLE_ELEMENTS) + ") " +
            "SELECT :wordId as id, :owner as owner, :attributes as attributes, " + String.join(", ", AVAILABLE_ELEMENTS.stream().map(element -> ":" + element + " as " + element).toList()) + " " +
            "WHERE NOT EXISTS (SELECT 1 FROM lexicon_words lexicon_words LEFT JOIN words words ON lexicon_words.word_id = words.id WHERE words.owner = :owner";
    private static final String CREATE_WORD_ELEMENTS_EXISTS_SQL =
            " AND %s = :%s";
    private static final String CREATE_WORD_CLOSING_SQL =
            ") LIMIT 1 " +
            "ON CONFLICT DO NOTHING";

    private static final String FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_PREFIX =
            "WITH words AS " +
                    "(SELECT w.id, w.owner, w.attributes, w." + String.join(", w.", AVAILABLE_ELEMENTS) + " " +
                    "FROM words w RIGHT JOIN lexicon_words l ON w.id = l.word_id " +
                    "WHERE l.lexicon_id != :lexiconId AND w.owner = :owner ";
    private static final String FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_DEDUPE_CONDITION =
            " AND %s = :%s";
    private static final String FIND_DUPLICATE_WORD_IN_OTHER_LEXICONS_SUFFIX =
            ") SELECT w.id, w.owner, w.attributes, w." + String.join(", w.", AVAILABLE_ELEMENTS) + ", a.audio_file_name FROM words w LEFT JOIN word_audio a ON w.id = a.word_id";

    private static final String CREATE_LEXICON_WORD_ENTRY_IF_WORD_EXISTS_SQL =
            "INSERT INTO lexicon_words (lexicon_id, word_id) " +
            "SELECT :lexiconId as lexicon_id, :wordId as word_id " +
            "FROM words " +
            "WHERE EXISTS (SELECT 1 FROM words WHERE owner = :owner and id = :wordId) " +
            "LIMIT 1 " +
            "ON CONFLICT DO NOTHING";

    private static final String UPDATE_WORD_SQL = "UPDATE words " +
            "SET owner = :owner, attributes = :attributes, " + String.join(", ", AVAILABLE_ELEMENTS.stream().map(element -> element + " = :" + element).toList()) + " " +
            "WHERE id = :wordId";

    private static final String REMOVE_WORD_FROM_LEXICON_SQL =
            "DELETE FROM review_events WHERE lexicon_id = :lexiconId AND word_id = :wordId; " +
            "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId AND word_id = :wordId; " +
            "DELETE FROM lexicon_review_history WHERE lexicon_id = :lexiconId AND word_id = :wordId; " +
            "DELETE FROM lexicon_word_test_history WHERE lexicon_id = :lexiconId AND word_id = :wordId; " +
            "DELETE FROM lexicon_words WHERE lexicon_id = :lexiconId AND word_id = :wordId; ";
    private static final String DELETE_WORD_SQL =
            "DELETE FROM word_audio WHERE word_id = :wordId; " +
            "DELETE FROM words WHERE id = :wordId; ";

    private static final String ATTACHED_LEXICON_COUNT_SQL =
            "SELECT COUNT(*) FROM lexicon_words WHERE word_id = :wordId";

    private static final String WORDS_BATCH_QUERY_SQL = "SELECT w.id, w.owner, w.attributes, " + AVAILABLE_ELEMENTS.stream().map(e -> "w." + e).collect(Collectors.joining(", ")) + ", l.create_seq_num " +
            "FROM lexicon_words l LEFT JOIN words w on l.word_id = w.id " +
            "WHERE l.lexicon_id = :lexiconId ORDER BY l.create_seq_num DESC OFFSET :offset LIMIT :count ";

    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_SELECT =
            "SELECT word.id, word.owner, word.attributes, " + AVAILABLE_ELEMENTS.stream().map(e -> "word." + e).collect(Collectors.joining(", ")) + ", lexicon_words.create_seq_num " +
            "FROM lexicon_words lexicon_words " +
                    "LEFT JOIN words word ON lexicon_words.word_id = word.id " +
                    "LEFT JOIN lexicon_review_history history ON lexicon_words.lexicon_id = history.lexicon_id AND lexicon_words.word_id = history.word_id and history.username = :username ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_AUDIO_JOIN =
            "LEFT JOIN LATERAL (SELECT audio_file_name FROM word_audio a WHERE a.word_id = word.id LIMIT 1) audio ON TRUE ";
    private static final String WORDS_BATCH_QUERY_WITH_FILTER_SELECT_SQL_WHERE =
            "WHERE lexicon_words.lexicon_id = :lexiconId ";
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
            "ORDER BY lexicon_words.create_seq_num DESC OFFSET :offset LIMIT :count";
    private static final String ADD_WORD_TO_LEXICON_SQL = "INSERT INTO lexicon_words (lexicon_id, word_id) VALUES (:lexiconId, :wordId)";

    private static final String GET_WORDS_TO_LEARN_SQL =
            "WITH words AS " +
                    "(SELECT w.id, w.owner, w.attributes, w." + String.join(", w.", AVAILABLE_ELEMENTS) + ", l.create_seq_num " +
                    "FROM words w " +
                    "RIGHT JOIN lexicon_words l ON w.id = l.word_id " +
                    "LEFT JOIN lexicon_review_history h on l.word_id = h.word_id AND h.username = :username " +
                    "WHERE l.lexicon_id = :lexiconId AND h.learned IS NOT TRUE " +
                    "ORDER BY l.create_seq_num ASC " +
                    "LIMIT :wordCnt) " +
            "SELECT w.id, w.owner, w.attributes, w." + String.join(", w.", AVAILABLE_ELEMENTS) + ", a.audio_file_name FROM words w LEFT JOIN word_audio a ON w.id = a.word_id ORDER BY w.create_seq_num ASC";

    private static final String GET_WORDS_UNIQUE_TO_LEXICON_SQL =
            "SELECT word_id " +
            "FROM lexicon_words " +
            "WHERE lexicon_id = :lexiconId AND word_id NOT IN (" +
                    "SELECT word_id " +
                    "FROM lexicon_words " +
                    "WHERE lexicon_id != :lexiconId)";

    private static final String GET_TOTAL_LEXICON_WORDS_SQL =
            "SELECT COUNT(*) FROM lexicon_words WHERE lexicon_id = :lexiconId";

    private static final String LEXICON_QUERY_SQL = "SELECT h.id, h.owner, h.title, h.lang, h.description, h.image_file_name, w.word_id " +
            "FROM lexicon_header h LEFT JOIN lexicon_words w ON h.id = w.lexicon_id " +
            "WHERE h.id = :id";

    private static final String LEXICON_METADATA_QUERY_PREFIX = "SELECT id, owner, title, lang, description, image_file_name " +
            "FROM lexicon_header";
    private static final String ALL_LEXICON_METADATA_QUERY_SQL = LEXICON_METADATA_QUERY_PREFIX + " WHERE owner = :owner ORDER BY title ASC";
    private static final String LEXICON_METADATAS_QUERY_SQL = LEXICON_METADATA_QUERY_PREFIX + " WHERE id in (:ids)";

    private static final String CREATE_LEXICON_METADATA_SQL = "INSERT INTO lexicon_header (id, owner, title, lang, description, image_file_name)" +
            "VALUES (:id, :owner, :title, :lang, :description, :imageFileName)";
    private static final String UPDATE_LEXICON_METADATA_SQL = "UPDATE lexicon_header " +
            "SET owner = :owner, title = :title, lang = :lang, description = :description, image_file_name = :imageFileName " +
            "WHERE id = :id";
    private static final String UPDATE_LEXICON_METADATA_NO_IMAGE_UPDATE_SQL = "UPDATE lexicon_header " +
            "SET title = :title, lang = :lang, description = :description " +
            "WHERE id = :id AND owner = :owner";

    private static final String GET_AUDIO_FILES_NAMES_FOR_WORD_SQL = "SELECT audio_file_name FROM word_audio WHERE word_id = :wordId";
    private static final String GET_AUDIO_FILES_NAMES_FOR_WORD_BATCH_SQL = "SELECT word_id, audio_file_name FROM word_audio WHERE word_id in (:wordIds)";
    private static final String SET_AUDIO_FILE_FOR_WORD_SQL = "INSERT INTO word_audio (word_id, audio_file_name) VALUES (:wordId, :audioFileName)";
    private static final String DELETE_AUDIO_FILE_SQL = "DELETE FROM word_audio WHERE word_id = :wordId AND audio_file_name = :audioFileName";

    private static final String DELETE_LEXICON_SQL =
            "DELETE FROM review_events WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM lexicon_review_history WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM lexicon_word_test_history WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM lexicon_words WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM lexicon_header WHERE id = :lexiconId; " +
            "DELETE FROM word_audio WHERE word_id NOT IN (SELECT word_id FROM lexicon_words); " +
            "DELETE FROM words WHERE id NOT IN (SELECT word_id FROM lexicon_words); ";

    private static final String WORD_ELEMENT_TOKEN = "$wordElement$";
    private static final String GET_UNIQUE_VALUES_OF_ELEMENT_IN_LEXICON =
            "SELECT distinct(w." + WORD_ELEMENT_TOKEN + ") " +
            "FROM lexicon_words l LEFT JOIN words w ON l.word_id = w.id " +
            "WHERE l.lexicon_id = :lexiconId AND w." + WORD_ELEMENT_TOKEN + " IS NOT NULL AND w." + WORD_ELEMENT_TOKEN + " != '' " +
            "LIMIT :limit";


    private final NamedParameterJdbcTemplate template;

    @Autowired
    public LexiconDao(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    public Word loadWord(String wordId) {
        List<Word> loadedWords = loadWords(List.of(wordId));

        if (loadedWords.size() > 0) {
            return loadedWords.get(0);
        }

        return Word.EMPTY_WORD;
    }

    public List<Word> loadWords(Collection<String> wordIds) {
        return template.query(AUDIO_JOIN_PREFIX + WORDS_QUERY_SQL + AUDIO_JOIN_SUFFIX,
                Map.of("wordIds", wordIds),
                rs -> { return processWordWithAudioResultSet(rs); });
    }

    public int createWord(Language language, String lexiconId, Word word) {
        return template.update(getCreateWordSql(language), getCreateWordParamMap(lexiconId, word));
    }

    public List<Word> createWords(Language language, String lexiconId, List<Word> words) {
        List<MapSqlParameterSource> paramsList = new ArrayList<>();

        for(Word word : words) {
            paramsList.add(getCreateWordParamMap(lexiconId, word));
        }

        int[] updateCnts = template.batchUpdate(getCreateWordSql(language), paramsList.toArray(new MapSqlParameterSource[0]));
        return IntStream.range(0, updateCnts.length).mapToObj(i -> updateCnts[i] > 0 ? words.get(i) : null).filter(word -> word != null).toList();

    }

    public int attachWordToLexicon(String lexiconId, String wordId, String owner) {
        return template.update(CREATE_LEXICON_WORD_ENTRY_IF_WORD_EXISTS_SQL, Map.of(
                "lexiconId", lexiconId,
                "wordId", wordId,
                "owner", owner));
    }


    public List<Word> attachWordsToLexicon(String lexiconId, List<Word> words, String owner) {
        List<MapSqlParameterSource> paramsList = new ArrayList<>();

        for (Word word : words) {
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("lexiconId", lexiconId);
            params.addValue("wordId", word.id());
            params.addValue("owner", owner);

            paramsList.add(params);
        }

        int[] updateCnts = template.batchUpdate(CREATE_LEXICON_WORD_ENTRY_IF_WORD_EXISTS_SQL, paramsList.toArray(new MapSqlParameterSource[0]));
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

    private MapSqlParameterSource getCreateWordParamMap(String lexiconId, Word word) {
        MapSqlParameterSource paramMap = new MapSqlParameterSource(getWordParamMap(word));

        paramMap.addValue("lexiconId", lexiconId);

        return paramMap;
    }

    public int updateWord(Word word) {
        return template.update(UPDATE_WORD_SQL, getWordParamMap(word));
    }

    public int removeWordFromLexicon(String lexiconId, String wordId) {
        return template.update(REMOVE_WORD_FROM_LEXICON_SQL, Map.of("lexiconId", lexiconId, "wordId", wordId));
    }

    public int deleteWord(String wordId) {
        return template.update(DELETE_WORD_SQL, Map.of("wordId", wordId));
    }

    public int attachedLexiconCount(String wordId) {
        return template.queryForObject(ATTACHED_LEXICON_COUNT_SQL, Map.of("wordId", wordId), Integer.class);
    }

    public List<Word> getWordsToLearn(String lexiconId, String username, int wordCnt) {
        return template.query(GET_WORDS_TO_LEARN_SQL,
                              Map.of("lexiconId", lexiconId, "wordCnt", wordCnt, "username", username),
                              rs -> { return processWordWithAudioResultSet(rs); });
    }

    public int addWordToLexicon(String lexiconId, String wordId) {
        return template.update(ADD_WORD_TO_LEXICON_SQL, Map.of("lexiconId", lexiconId, "wordId", wordId));
    }

    private Map<String, Object> getWordParamMap(Word word) {
        Map<String, Object> paramMap = new HashMap<>();

        paramMap.put("wordId", word.id());
        paramMap.put("owner", word.owner());
        paramMap.put("attributes", word.attributes() != null ? word.attributes() : "");
        for(String element : AVAILABLE_ELEMENTS) {
            paramMap.put(element, word.elements().get(element));
        }

        return paramMap;
    }

    public List<Word> getLexiconWordsBatch(String lexiconId, int count, int offset) {
        List<Word> words = template.query(AUDIO_JOIN_PREFIX + WORDS_BATCH_QUERY_SQL + AUDIO_JOIN_SUFFIX,
                                          Map.of("lexiconId", lexiconId, "count", count, "offset", offset),
                                          rs -> { return processWordWithAudioResultSet(rs); });

        return words;
    }

    public List<Word> getLexiconWordsBatchWithFilter(String lexiconId, String username, int count, int offset, WordFilterOptions wordFilterOptions) {
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
                    words.add(new Word(wordWithoutAudio.id(), wordWithoutAudio.owner(), wordWithoutAudio.elements(), wordWithoutAudio.attributes(), audioFileNames));
                }

                Map<String, String> wordElements = new HashMap<>();
                for (String element : AVAILABLE_ELEMENTS) {
                    String elementValue = rs.getObject(element, String.class);

                    if (elementValue != null && !elementValue.isBlank()) {
                        wordElements.put(element, elementValue);
                    }
                }
                wordWithoutAudio = new Word(id, rs.getString("owner"), wordElements, rs.getString("attributes"), List.of());
                audioFileNames = new ArrayList<>();
                lastId = id;
            }
            String audioFileName = rs.getString("audio_file_name");
            if (audioFileName != null && !audioFileName.isBlank()) {
                audioFileNames.add(audioFileName);
            }
        } while (rs.next());

        if (wordWithoutAudio != null) {
            words.add(new Word(wordWithoutAudio.id(), wordWithoutAudio.owner(), wordWithoutAudio.elements(), wordWithoutAudio.attributes(), audioFileNames));
        }

        return words;
    }

    public Lexicon getLexicon(String id) {
        return template.query(LEXICON_QUERY_SQL, Map.of("id", id), rs -> {
            if (rs == null || !rs.next()) {
                log.warn("No lexicon data found for lexicon " + id);
                return Lexicon.EMPTY_LEXICON;
            }

            String id1 = rs.getString("id");
            String owner = rs.getString("owner");
            String title = rs.getString("title");
            String desc = rs.getString("description");
            long languageId = rs.getLong("lang");
            String imageFileName = rs.getString("image_file_name");
            List<String> wordIds = new ArrayList<>();

            do {
                String wordId = rs.getString("word_id");
                if (wordId != null) {
                    wordIds.add(wordId);
                }
            } while(rs.next());

            return new Lexicon(id1, owner, title, desc, languageId, imageFileName, wordIds);
        });
    }

    public List<Lexicon> getAllLexiconMetadata(String username) {
        return template.query(ALL_LEXICON_METADATA_QUERY_SQL, Map.of("owner", username), (rs, rowNum) -> mapLexiconRow(rs, rowNum));
    }

    public Lexicon getLexiconMetadata(String id) {
        List<Lexicon> lexiconMetadata = getLexiconMetadatas(List.of(id));
        return lexiconMetadata.size() > 0 ? lexiconMetadata.get(0) : null;
    }

    public List<Lexicon> getLexiconMetadatas(Collection<String> ids) {
        return template.query(LEXICON_METADATAS_QUERY_SQL, Map.of("ids", ids), (rs, rowNum) -> mapLexiconRow(rs, rowNum));
    }

    private Lexicon mapLexiconRow(ResultSet rs, int rowNum) throws SQLException {
        return new Lexicon(
                rs.getString("id"),
                rs.getString("owner"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getLong("lang"),
                rs.getString("image_file_name"),
                new ArrayList<>());
    }

    public int updateLexiconMetadata(Lexicon lexicon) {
        return template.update(UPDATE_LEXICON_METADATA_SQL, Map.of(
                "id", lexicon.id(),
                "owner", lexicon.owner(),
                "title", lexicon.title(),
                "description", lexicon.description(),
                "lang", lexicon.languageId(),
                "imageFileName", lexicon.imageFileName()));
    }

    public int updateLexiconMetadataNoImageUpdate(Lexicon lexicon) {
        return template.update(UPDATE_LEXICON_METADATA_NO_IMAGE_UPDATE_SQL, Map.of(
                "id", lexicon.id(),
                "owner", lexicon.owner(),
                "title", lexicon.title(),
                "description", lexicon.description(),
                "lang", lexicon.languageId()));
    }

    public int createLexiconMetadata(String newId, Lexicon lexicon) {
        return template.update(CREATE_LEXICON_METADATA_SQL, Map.of(
                "id", newId,
                "owner", lexicon.owner(),
                "title", lexicon.title(),
                "description", lexicon.description(),
                "lang", lexicon.languageId(),
                "imageFileName", lexicon.imageFileName()));
    }

    public List<String> getAudioFileNamesForWord(String wordId) {
        return template.query(GET_AUDIO_FILES_NAMES_FOR_WORD_SQL, Map.of("wordId", wordId),
                (rs, rowNum) -> rs.getString("audio_file_name"));
    }

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

    public int setAudioFileNameForWord(String wordId, String audioFileName) {
        return template.update(SET_AUDIO_FILE_FOR_WORD_SQL, Map.of("wordId", wordId, "audioFileName", audioFileName));
    }

    public int deleteAudioFileName(String wordId, String audioFileName) {
        return template.update(DELETE_AUDIO_FILE_SQL, Map.of("wordId", wordId, "audioFileName", audioFileName));
    }

    public int deleteLexicon(String lexiconId) {
        return template.update(DELETE_LEXICON_SQL, Map.of("lexiconId", lexiconId));
    }

    public List<String> getWordsUniqueToLexicon(String lexiconId) {
        return template.query(GET_WORDS_UNIQUE_TO_LEXICON_SQL, Map.of("lexiconId", lexiconId), (rs, rowNum) -> rs.getString("word_id"));
    }

    public int getTotalLexiconWordCount(String lexiconId) {
        return template.queryForObject(GET_TOTAL_LEXICON_WORDS_SQL, Map.of("lexiconId", lexiconId), Integer.class);
    }

    public List<String> getUniqueElementValues(String lexiconId, WordElement wordElement, int limit) {
        String sql = GET_UNIQUE_VALUES_OF_ELEMENT_IN_LEXICON.replace(WORD_ELEMENT_TOKEN, wordElement.getId());

        return template.query(
                sql,
                Map.of("lexiconId", lexiconId,
                       "limit", limit),
                (rs, rowNum) -> rs.getString(wordElement.getId()));
    }

    private List<String> nonEmptyIdList(List<String> idList) {
        if (idList == null || idList.size() == 0) {
            return List.of(DUMMY_ID);
        }
        return idList;
    }
}

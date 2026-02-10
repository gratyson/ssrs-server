package com.gt.ssrs.lexicon.impl;

import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class LexiconDaoPG implements LexiconDao {

    private static final Logger log = LoggerFactory.getLogger(LexiconDaoPG.class);

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

    private static final String DELETE_LEXICON_SQL =
            "DELETE FROM review_events WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM scheduled_review WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM lexicon_review_history WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM lexicon_word_test_history WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM word_audio WHERE word_id IN (SELECT id FROM words where lexicon_id = :lexiconId); " +
            "DELETE FROM words WHERE lexicon_id = :lexiconId; " +
            "DELETE FROM lexicon_header WHERE id = :lexiconId; ";

    private static final String DELETE_LEXICON_METADATA_SQL =
            "DELETE FROM lexicon_header WHERE id = :lexiconId; ";




    private final NamedParameterJdbcTemplate template;

    public LexiconDaoPG(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.template = namedParameterJdbcTemplate;
    }

    @Override
    public List<LexiconMetadata> getAllLexiconMetadata(String username) {
        return template.query(ALL_LEXICON_METADATA_QUERY_SQL, Map.of("owner", username), (rs, rowNum) -> mapLexiconRow(rs, rowNum));
    }

    @Override
    public LexiconMetadata getLexiconMetadata(String id) {
        List<LexiconMetadata> lexiconMetadata = getLexiconMetadatas(List.of(id));
        return lexiconMetadata.size() > 0 ? lexiconMetadata.get(0) : null;
    }

    @Override
    public List<LexiconMetadata> getLexiconMetadatas(Collection<String> ids) {
        return template.query(LEXICON_METADATAS_QUERY_SQL, Map.of("ids", ids), (rs, rowNum) -> mapLexiconRow(rs, rowNum));
    }

    private LexiconMetadata mapLexiconRow(ResultSet rs, int rowNum) throws SQLException {
        return new LexiconMetadata(
                rs.getString("id"),
                rs.getString("owner"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getLong("lang"),
                rs.getString("image_file_name"));
    }

    @Override
    public int updateLexiconMetadata(LexiconMetadata lexicon) {
        return template.update(UPDATE_LEXICON_METADATA_SQL, Map.of(
                "id", lexicon.id(),
                "owner", lexicon.owner(),
                "title", lexicon.title(),
                "description", lexicon.description(),
                "lang", lexicon.languageId(),
                "imageFileName", lexicon.imageFileName()));
    }

    @Override
    public int updateLexiconMetadataNoImageUpdate(LexiconMetadata lexicon) {
        return template.update(UPDATE_LEXICON_METADATA_NO_IMAGE_UPDATE_SQL, Map.of(
                "id", lexicon.id(),
                "owner", lexicon.owner(),
                "title", lexicon.title(),
                "description", lexicon.description(),
                "lang", lexicon.languageId()));
    }

    @Override
    public int createLexiconMetadata(String newId, LexiconMetadata lexicon) {
        return template.update(CREATE_LEXICON_METADATA_SQL, Map.of(
                "id", newId,
                "owner", lexicon.owner(),
                "title", lexicon.title(),
                "description", lexicon.description(),
                "lang", lexicon.languageId(),
                "imageFileName", lexicon.imageFileName()));
    }

    @Override
    public void deleteLexiconMetadata(String lexiconId) {
        template.update(DELETE_LEXICON_METADATA_SQL, Map.of("lexiconId", lexiconId));
    }
}

package com.gt.ssrs.lexicon;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface LexiconDao {

    List<LexiconMetadata> getAllLexiconMetadata(String username);

    LexiconMetadata getLexiconMetadata(String id);

    List<LexiconMetadata> getLexiconMetadatas(Collection<String> ids);

    int updateLexiconMetadata(LexiconMetadata lexicon);

    int updateLexiconMetadataNoImageUpdate(LexiconMetadata lexicon);

    int createLexiconMetadata(String newId, LexiconMetadata lexicon);

    void deleteLexiconMetadata(String lexiconId);
}

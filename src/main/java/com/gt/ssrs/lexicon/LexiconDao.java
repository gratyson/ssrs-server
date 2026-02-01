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

    Word loadWord(String wordId);

    List<Word> loadWords(Collection<String> wordIds);

    int createWord(Language language, String lexiconId, Word word);

    List<Word> createWords(Language language, String lexiconId, List<Word> words);

    Word findDuplicateWordInOtherLexicons(Language language, String lexiconId, String owner, Word word);

    int updateWord(Word word);

    int deleteWord(String wordId);

    List<Word> getWordsToLearn(String lexiconId, String username, int wordCnt);

    List<Word> getLexiconWordsBatch(String lexiconId, int count, int offset);

    List<Word> getLexiconWordsBatchWithFilter(String lexiconId, String username, int count, int offset, WordFilterOptions wordFilterOptions);

    List<LexiconMetadata> getAllLexiconMetadata(String username);

    LexiconMetadata getLexiconMetadata(String id);

    List<LexiconMetadata> getLexiconMetadatas(Collection<String> ids);

    int updateLexiconMetadata(LexiconMetadata lexicon);

    int updateLexiconMetadataNoImageUpdate(LexiconMetadata lexicon);

    int createLexiconMetadata(String newId, LexiconMetadata lexicon);

    List<String> getAudioFileNamesForWord(String wordId);

    Map<String,List<String>> getAudioFileNamesForWordBatch(List<String> wordIds);

    int setAudioFileNameForWord(String wordId, String audioFileName);

    int deleteAudioFileName(String wordId, String audioFileName);

    int deleteLexicon(String lexiconId);

    List<String> getWordsUniqueToLexicon(String lexiconId);

    int getTotalLexiconWordCount(String lexiconId);

    List<String> getUniqueElementValues(String lexiconId, WordElement wordElement, int limit);
}

package com.gt.ssrs.word;

import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface WordDao {

    Word loadWord(String wordId);

    List<Word> loadWords(Collection<String> wordIds);

    int createWord(Language language, String lexiconId, Word word);

    List<Word> createWords(Language language, String lexiconId, List<Word> words);

    Word findDuplicateWordInOtherLexicons(Language language, String lexiconId, String owner, Word word);

    int updateWord(Word word);

    List<Word> getLexiconWordsBatch(String lexiconId, String username, int count, int offset, Word lastWord);

    List<Word> getLexiconWordsBatchWithFilter(String lexiconId, String username, int count, int offset, Word lastWord, WordFilterOptions wordFilterOptions);

    List<String> getAudioFileNamesForWord(String wordId);

    Map<String,List<String>> getAudioFileNamesForWordBatch(List<String> wordIds);

    int setAudioFileNameForWord(String wordId, String audioFileName);

    int deleteAudioFileName(String wordId, String audioFileName);

    List<String> getWordsUniqueToLexicon(String lexiconId);

    int getTotalLexiconWordCount(String lexiconId);

    List<String> getUniqueElementValues(String lexiconId, WordElement wordElement, int limit);

    void deleteWords(Collection<String> wordId);

    void deleteAllLexiconWords(String lexiconId);
}

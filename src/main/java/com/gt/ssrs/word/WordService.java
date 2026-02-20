package com.gt.ssrs.word;

import com.gt.ssrs.audio.AudioService;
import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.exception.UserAccessException;
import com.gt.ssrs.language.Language;
import com.gt.ssrs.language.WordElement;
import com.gt.ssrs.lexicon.LexiconService;
import com.gt.ssrs.model.LexiconMetadata;
import com.gt.ssrs.model.Word;
import com.gt.ssrs.model.WordFilterOptions;
import com.gt.ssrs.reviewHistory.WordReviewHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class WordService {

    private static final Logger log = LoggerFactory.getLogger(WordService.class);

    private static final int DELETE_BATCH_SIZE = 100;

    private final LexiconService lexiconService;
    private final WordReviewHistoryService wordReviewHistoryService;
    private final AudioService audioService;
    private final WordDao wordDao;
    private final BlobDao blobDao;

    @Autowired
    public WordService(LexiconService lexiconService,
                       WordReviewHistoryService wordReviewHistoryService,
                       AudioService audioService,
                       WordDao wordDao,
                       BlobDao blobDao) {
        this.lexiconService = lexiconService;
        this.wordReviewHistoryService = wordReviewHistoryService;
        this.audioService = audioService;
        this.wordDao = wordDao;
        this.blobDao = blobDao;
    }

    public Word loadWord(String id) {
        return wordDao.loadWord(id);
    }

    public List<Word> loadWords(Collection<String> ids) {
        return wordDao.loadWords(ids);
    }

    public Word updateWord(Word word, String username) {
        Word oldWord = loadWord(word.id());
        if (oldWord != null && !oldWord.owner().equals(username)) {
            // TODO: Create new word if owned by another user
            return null;
        }

        Word wordToSave = withUsername(word, username);
        if (saveExistingWord(wordToSave)) {
            return wordToSave;
        }

        return null;
    }

    public List<Word> saveWords(List<Word> words, String lexiconId, String username) {
        LexiconMetadata lexiconMetadata = lexiconService.getLexiconMetadata(lexiconId);
        verifyCanEditLexicon(lexiconMetadata, username);
        Language language = Language.getLanguageById(lexiconMetadata.languageId());

        List<Word> wordsToSave = new ArrayList<>();
        Set<String> newWordIds = new HashSet<>();

        for(Word word : words) {
            // Logic for updating words:
            //  - If an ID is specified, overwrite existing word elements and attributes as long as the user owns the word
            //  - If the user already owns a word that is considered a duplicate, attach duplicate word to lexicon
            //  - Otherwise, assign a new ID and save the word
            // After saving, attach the new word to the lexicon as long as it does not duplicate another word in the lexicon

            Word wordToSave = null;

            if (word.id() != null && !word.id().isBlank()) {
                Word existingWord = wordDao.loadWord(word.id());
                // if a word already exists, only update if the user owns the word and the word is part of the specified lexicon
                if (existingWord == null || (existingWord.owner().equals(username) && existingWord.lexiconId().equals(lexiconId))) {
                    wordToSave = buildWordToSave(lexiconId, word, username, existingWord);
                    if (existingWord == null) {
                        newWordIds.add(wordToSave.id());
                    }
                }
            } else {
                Word duplicateWord = wordDao.findDuplicateWordInOtherLexicons(language, lexiconId, username, word);
                if (duplicateWord != null) {
                    // TODO: Return skipped duplicates with option to force?
                    //wordsToAttach.add(duplicateWord);
                } else {
                    wordToSave = buildWordToSave(lexiconId, word, username, null);
                    newWordIds.add(wordToSave.id());
                }
            }

            if (wordToSave != null && validateWord(language, wordToSave)) {
                wordsToSave.add(wordToSave);
            }
        }


        List<Word> savedWords = wordDao.createWords(language, lexiconId, wordsToSave);

        wordReviewHistoryService.createEmptyWordReviewHistoryForWords(username,
                savedWords.stream()
                        .filter(word -> newWordIds.contains(word.id()))
                        .collect(Collectors.toUnmodifiableList()));

        log.info("Saved {} new words in lexicon {}. {} duplicate words were skipped.", savedWords.size(), lexiconId, words.size() - savedWords.size());

        return savedWords;
    }

    private Word buildWordToSave(String lexiconId, Word word, String username, Word existingWord) {
        Map<String, String> processedElements =
                word.elements()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().strip()));

        List<String> audioFiles;
        Instant createInstant;
        String wordId = word.id() != null && !word.id().isBlank() ? word.id() : UUID.randomUUID().toString();
        if (existingWord != null) {
            audioFiles = word.audioFiles() == null || word.audioFiles().isEmpty() ? existingWord.audioFiles() : word.audioFiles();
            createInstant = existingWord.createInstant();
        } else {
            audioFiles = word.audioFiles();
            createInstant = Instant.now();
        }

        return new Word(wordId, lexiconId, username, processedElements, word.attributes().strip(), audioFiles, createInstant, Instant.now());
    }

    private boolean saveExistingWord(Word word) {
        return wordDao.updateWord(word) > 0;
    }

    private Language getLanguageForLexicon(String lexiconId) {
        long languageId = lexiconService.getLexiconLanguageId(lexiconId);
        return Language.getLanguageById(languageId);
    }

    private Word saveNewWord(Word word, Language language, String lexiconId) {
        if (validateWord(language, word) && wordDao.createWord(language, lexiconId, word) > 0) {
            log.info("Created word {}", word.id());
            return word;
        }
        return null;
    }

    public void deleteWords(String lexiconId, Collection<String> wordIds, String username) {
        List<Word> wordsToDelete = wordDao.loadWords(wordIds)
                .stream()
                .filter(word -> word.lexiconId().equals(lexiconId) && word.owner().equals(username))
                .collect(Collectors.toUnmodifiableList());

        for (Word word : wordsToDelete) {
            if (word.audioFiles() != null && !word.audioFiles().isEmpty()) {
                blobDao.deleteAudioFiles(word.audioFiles());
            }
        }

        wordDao.deleteWords(wordsToDelete.stream()
                .map(word -> word.id())
                .collect(Collectors.toUnmodifiableList()));
    }

    private boolean validateWord(Language language, Word word) {
        for(WordElement requiredElement : language.getRequiredElements()) {
            if (!word.elements().containsKey(requiredElement.getId()) || word.elements().get(requiredElement.getId()).isBlank()) {
                log.info("Word missing required element {}, skipping save.", requiredElement);
                return false;
            }
        }

        for(WordElement validElement : language.getValidElements()) {
            if (validElement.getValidationRegex() != null
                    && !validElement.getValidationRegex().isBlank()
                    && word.elements().containsKey(validElement.getId())
                    && !word.elements().get(validElement.getId()).isBlank()
                    && !Pattern.matches(validElement.getValidationRegex(), word.elements().get(validElement.getId()))) {
                log.info("Word element {} does contains an invalid value, skipping save.", validElement.name());
                return false;
            }
        }

        return true;
    }

    public List<Word> getLexiconWordsBatch(String lexiconId, String username, int count, int offset, Word lastWord, WordFilterOptions wordFilterOptions) {
        if (isWordFilterOptionsEmpty(wordFilterOptions)) {
            return wordDao.getLexiconWordsBatch(lexiconId, username, count, offset, lastWord);
        } else {
            return wordDao.getLexiconWordsBatchWithFilter(lexiconId, username, count, offset, lastWord, wordFilterOptions);
        }
    }

    public int getTotalLexiconWordCount(String lexiconId) {
        return wordDao.getTotalLexiconWordCount(lexiconId);
    }

    public List<String> getUniqueElementValues(String lexiconId, WordElement wordElement, int limit) {
        return wordDao.getUniqueElementValues(lexiconId, wordElement, limit);
    }

    private boolean isWordFilterOptionsEmpty(WordFilterOptions wordFilterOptions) {
        if (wordFilterOptions == null) {
            return true;
        }

        if ((wordFilterOptions.attributes() != null && !wordFilterOptions.attributes().isBlank())
                || (wordFilterOptions.learned() != null)
                || (wordFilterOptions.hasAudio() != null)) {
            return false;
        }

        if (wordFilterOptions.elements() != null) {
            for (String elementValue : wordFilterOptions.elements().values()) {
                if (elementValue != null && !elementValue.isBlank()) {
                    return false;
                }
            }
        }

        return true;
    }

    public void deleteLexiconWords(String lexiconId, String username) {
        deleteLexiconAudio(lexiconId);
        wordDao.deleteAllLexiconWords(lexiconId);
    }

    private void deleteLexiconAudio(String lexiconId) {
        List<String> audioFileNames = audioService.getAudioFilesForWordBatch(wordDao.getWordsUniqueToLexicon(lexiconId))
                .values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        for(int idx = 0; idx < audioFileNames.size(); idx += DELETE_BATCH_SIZE) {
            blobDao.deleteAudioFiles(audioFileNames.subList(idx, Math.min(idx + DELETE_BATCH_SIZE, audioFileNames.size())));
        }
    }


    private Word withUsername(Word word, String username) {
        return new Word(word.id(), word.lexiconId(), username, word.elements(), word.attributes(), word.audioFiles(), word.createInstant(), word.updateInstant());
    }

    private void verifyCanEditLexicon(String lexiconId, String username) {
        verifyCanEditLexicon(lexiconService.getLexiconMetadata(lexiconId), username);
    }

    private void verifyCanEditLexicon(LexiconMetadata lexiconMetadata, String username) {
        if (!lexiconMetadata.owner().equals(username)) {
            String errMsg = "User does not permission to edit lexicon " + lexiconMetadata.id();

            log.error(errMsg);
            throw new UserAccessException(errMsg);
        }
    }
}

package com.gt.ssrs.lexicon;

import com.gt.ssrs.audio.AudioService;
import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.language.LanguageService;
import com.gt.ssrs.exception.DaoException;
import com.gt.ssrs.exception.MappingException;
import com.gt.ssrs.exception.UserAccessException;
import com.gt.ssrs.model.*;
import com.gt.ssrs.util.FileNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class LexiconService {

    private static final Logger log = LoggerFactory.getLogger(LexiconService.class);

    private static final int DELETE_BATCH_SIZE = 100;

    private final LexiconDao lexiconDao;
    private final BlobDao blobDao;
    private final LanguageService languageService;
    private final AudioService audioService;

    @Autowired
    public LexiconService(LexiconDao lexiconDao, BlobDao blobDao, LanguageService languageService, AudioService audioService) {
        this.lexiconDao = lexiconDao;
        this.blobDao = blobDao;
        this.languageService = languageService;
        this.audioService = audioService;
    }

    public Word loadWord(String id) {
        return lexiconDao.loadWord(id);
    }

    public List<Word> loadWords(List<String> ids) {
        return lexiconDao.loadWords(ids);
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

        Language language = getLanguageForLexicon(lexiconId);

        List<Word> wordsToSave = new ArrayList<>();
        List<Word> wordsToAttach = new ArrayList<>();
        List<Word> savedWords = new ArrayList<>();

        for(Word word : words) {
            // Logic for updating words:
            //  - If an ID is specified, overwrite existing word elements and attributes as long as the user owns the word
            //  - If the user already owns a word that is considered a duplicate, attach duplicate word to lexicon
            //  - Otherwise, assign a new ID and save the word
            // After saving, attach the new word to the lexicon as long as it does not duplicate another word in the lexicon

            Word wordToSave = null;

            if (word.id() != null && !word.id().isBlank()) {
                Word existingWord = lexiconDao.loadWord(word.id());

                if (existingWord == null) {
                    wordToSave = buildWordToSave(word, username);
                } else if (existingWord.owner().equals(username)) {
                    wordToSave = buildWordToSave(word, username, existingWord.audioFiles());
                }
            } else {
                Word duplicateWord = lexiconDao.findDuplicateWordInOtherLexicons(language, lexiconId, username, word);
                if (duplicateWord != null) {
                    wordsToAttach.add(duplicateWord);
                } else {
                    wordToSave = buildWordToSave(word, username);
                }
            }

            if (wordToSave != null && validateWord(language, wordToSave)) {
                wordsToSave.add(wordToSave);
            }
        }

        wordsToAttach.addAll(lexiconDao.createWords(language, lexiconId, wordsToSave));
        savedWords.addAll(lexiconDao.attachWordsToLexicon(lexiconId, wordsToAttach, username));

        log.info("Saved {} new words in lexicon {}. {} duplicate words were skipped.", savedWords.size(), lexiconId, words.size() - savedWords.size());

        return savedWords;
    }

    private Word buildWordToSave(Word word, String username) {
        return buildWordToSave(word, username, word.audioFiles());
    }

    private Word buildWordToSave(Word word, String username, List<String> audioFiles) {
        Map<String, String> processedElements =
                word.elements()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().strip()));

        String wordId = word.id() != null && !word.id().isBlank() ? word.id() : UUID.randomUUID().toString();

        return new Word(wordId, username, processedElements, word.attributes().strip(), audioFiles);
    }

    private boolean saveExistingWord(Word word) {
        return lexiconDao.updateWord(word) > 0;
    }

    private Language getLanguageForLexicon(String lexiconId) {
        long languageId = getLexiconLanguageId(lexiconId);
        return languageService.GetLanguageById(languageId);
    }

    private Word saveNewWord(Word word, Language language, String lexiconId) {
        if (validateWord(language, word) && lexiconDao.createWord(language, lexiconId, word) > 0) {
            log.info("Created word {}", word.id());
            return word;
        }
        return null;
    }

    private Word attachWordToLexicon(String lexiconId, Word word, String username) {
        if (lexiconDao.attachWordToLexicon(lexiconId, word.id(), username) > 0) {
            return word;
        }

        return null;
    }

    public void deleteWords(String lexiconId, List<String> wordIds, String username) {
        verifyCanEditLexicon(lexiconId, username);

        List<Word> existingWords = lexiconDao.loadWords(wordIds);
        for(Word word : existingWords) {
            deleteWord(lexiconId, word);
        }
    }

    private void deleteWord(String lexiconId, Word word) {
        lexiconDao.removeWordFromLexicon(lexiconId, word.id());
        if (lexiconDao.attachedLexiconCount(word.id()) == 0) {
            log.info("Deleting word: {}", word.id());

            // Intentionally ignoring ownership -- always delete the word if it would
            // otherwise be left orphaned
            blobDao.deleteAudioFiles(audioService.GetAudioFilesForWord(word.id()));
            lexiconDao.deleteWord(word.id());
        } else {
            log.info("Removed word {} from lexicon {}. Not deleting word as it is attached to another lexicon", word.id(), lexiconId);
        }
    }

    private boolean validateWord(Language language, Word word) {
        for(WordElement requiredElement : language.requiredElements()) {
            if (!word.elements().containsKey(requiredElement.id()) || word.elements().get(requiredElement.id()).isBlank()) {
                log.info("Word missing required element {}, skipping save.", requiredElement);
                return false;
            }
        }

        for(WordElement validElement : language.validElements()) {
            if (validElement.validationRegex() != null
                    && !validElement.validationRegex().isBlank()
                    && word.elements().containsKey(validElement.id())
                    && !word.elements().get(validElement.id()).isBlank()
                    && !Pattern.matches(validElement.validationRegex(), word.elements().get(validElement.id()))) {
                log.info("Word element {} does contains an invalid value, skipping save.", validElement.name());
                return false;
            }
        }

        return true;
    }

    public List<Word> getLexiconWordsBatch(String lexiconId, String username, int count, int offset, WordFilterOptions wordFilterOptions) {
        if (isWordFilterOptionsEmpty(wordFilterOptions)) {
            return lexiconDao.getLexiconWordsBatch(lexiconId, count, offset);
        } else {
            return lexiconDao.getLexiconWordsBatchWithFilter(lexiconId, username, count, offset, wordFilterOptions);
        }
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

    public Lexicon getLexicon(String id) {
        return lexiconDao.getLexicon(id);
    }

    public long getLexiconLanguageId(String lexiconId) {
        Lexicon lexiconMetadata = lexiconDao.getLexiconMetadata(lexiconId);
        if (lexiconMetadata != null && lexiconMetadata.languageId() > 0) {
            return lexiconMetadata.languageId();
        }

        String errMsg = "No language id exists for lexicon " + lexiconId;
        log.error(errMsg);
        throw new MappingException(errMsg);
    }

    public List<Lexicon> loadAllLexiconMetadata(String username) {
        return lexiconDao.getAllLexiconMetadata(username);
    }

    public Lexicon getLexiconMetadata(String id) {
        return lexiconDao.getLexiconMetadata(id);
    }

    public List<Lexicon> getLexiconMetadatas(Collection<String> ids) {
        return lexiconDao.getLexiconMetadatas(ids);
    }

    // Saves the specified lexicon metadata and image. If the metadata does not specify id, creates
    // a new lexicon. Returns the saved metadata (including new id and image file) on success, null if not.
    public Lexicon saveLexiconMetadata(String username, Lexicon lexiconMetadata, MultipartFile newImageFile) {

        try {

            if (lexiconMetadata.id() == null || lexiconMetadata.id().isEmpty()) {
                String newId = UUID.randomUUID().toString();
                String imageFileName = updateImageBlobDataAsNeeded(newId, lexiconMetadata, newImageFile);
                Lexicon lexiconMetadataToSave = getLexiconMetadataToSave(newId, username, lexiconMetadata, imageFileName);

                if (lexiconDao.createLexiconMetadata(newId, lexiconMetadataToSave) == 0) {
                    log.warn("Unable to create row for new lexicon " + lexiconMetadata.title());
                    return null ;
                }

                return lexiconMetadataToSave;
            } else {
                Lexicon oldLexiconMetadata = lexiconDao.getLexiconMetadata(lexiconMetadata.id());
                verifyCanEditLexicon(oldLexiconMetadata, username);

                String imageFileName = updateImageBlobDataAsNeeded(lexiconMetadata.id(), lexiconMetadata, newImageFile);
                Lexicon lexiconMetadataToSave = getLexiconMetadataToSave(lexiconMetadata.id(), username, lexiconMetadata, imageFileName);
                int rowsUpdated = imageFileName.isBlank() ? lexiconDao.updateLexiconMetadataNoImageUpdate(lexiconMetadataToSave) : lexiconDao.updateLexiconMetadata(lexiconMetadataToSave);

                if (rowsUpdated == 0) {
                    log.warn("Unable to update lexicon metadata for lexicon " + lexiconMetadata.id());
                    return null;
                }
                return lexiconMetadataToSave;
            }
        } catch (IOException ex) {
            String errorMsg = "Error updating lexicon id = " + lexiconMetadata.id();

            log.error(errorMsg);
            throw new DaoException(errorMsg, ex);
        }


    }

    private static Lexicon getLexiconMetadataToSave(String newId, String username, Lexicon uploadedLexiconMetadata, String newImageFileName) {
        return new Lexicon(
                newId,
                username,
                uploadedLexiconMetadata.title(),
                uploadedLexiconMetadata.description(),
                uploadedLexiconMetadata.languageId(),
                !newImageFileName.isBlank() ? newImageFileName : uploadedLexiconMetadata.imageFileName(),
                new ArrayList<>());
    }

    private String updateImageBlobDataAsNeeded(String newId, Lexicon lexiconMetadata, MultipartFile newImageFile) throws IOException {
        if (newImageFile != null && newImageFile.getBytes().length > 0) {
            String newImageFileName = getImageFileName(newId, newImageFile.getOriginalFilename());
            ByteBuffer bytes = ByteBuffer.wrap(newImageFile.getBytes());

            if (lexiconMetadata.id() != null && !lexiconMetadata.id().isEmpty()) {
                Lexicon oldLexiconMetadata = getLexiconMetadata(lexiconMetadata.id());

                if (oldLexiconMetadata != null && oldLexiconMetadata.imageFileName() != null && !oldLexiconMetadata.imageFileName().isEmpty()) {
                    log.info("Deleting image file \"" + oldLexiconMetadata.imageFileName() + "\".");
                    blobDao.deleteImageFile(oldLexiconMetadata.imageFileName());
                }
            }

            blobDao.saveImageFile(newImageFileName, bytes);
            return newImageFileName;
        }
        return "";
    }

    private static String getImageFileName(String lexiconId, String uploadFileName) {
        return lexiconId + FileNameUtil.GetExtension(uploadFileName);
    }

    public boolean deleteEntireLexicon(String lexiconId, String username) {
        verifyCanEditLexicon(lexiconId, username);

        deleteLexiconAudio(lexiconId);
        deleteLexiconImage(lexiconId);
        deleteLexiconData(lexiconId);

        return true;
    }



    private void deleteLexiconData(String lexiconId) {
        lexiconDao.deleteLexicon(lexiconId);
    }

    private void deleteLexiconImage(String lexiconId) {
        Lexicon lexiconMetadata = getLexiconMetadata(lexiconId);
        String imageFileName = lexiconMetadata.imageFileName();
        if (imageFileName != null && !imageFileName.isBlank()) {
            blobDao.deleteImageFile(imageFileName);
        }
    }

    private void deleteLexiconAudio(String lexiconId) {
        List<String> audioFileNames = audioService.GetAudioFilesForWordBatch(lexiconDao.getWordsUniqueToLexicon(lexiconId))
                .values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        for(int idx = 0; idx < audioFileNames.size(); idx += DELETE_BATCH_SIZE) {
            blobDao.deleteAudioFiles(audioFileNames.subList(idx, Math.min(idx + DELETE_BATCH_SIZE, audioFileNames.size())));
        }
    }

    private Word withUsername(Word word, String username) {
        return new Word(word.id(), username, word.elements(), word.attributes(), word.audioFiles());
    }

    private void verifyCanEditLexicon(String lexiconId, String username) {
        verifyCanEditLexicon(getLexiconMetadata(lexiconId), username);
    }

    private void verifyCanEditLexicon(Lexicon lexiconMetadata, String username) {
        if (!lexiconMetadata.owner().equals(username)) {
            String errMsg = "User does not permission to edit lexicon " + lexiconMetadata.id();

            log.error(errMsg);
            throw new UserAccessException(errMsg);
        }
    }

    private void verifyCanEditWord(Word word, String username) {
        if (!word.owner().equals(username)) {
            String errMsg = "User does not permission to edit lexicon " + word.id();

            log.error(errMsg);
            throw new UserAccessException(errMsg);
        }
    }
}

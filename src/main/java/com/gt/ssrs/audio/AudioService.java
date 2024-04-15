package com.gt.ssrs.audio;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.lexicon.LexiconDao;
import com.gt.ssrs.exception.DaoException;
import com.gt.ssrs.util.FileNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    private final LexiconDao lexiconDao;
    private final BlobDao blobDao;

    @Autowired
    public AudioService(LexiconDao lexiconDao, BlobDao blobDao) {
        this.blobDao = blobDao;
        this.lexiconDao = lexiconDao;
    }

    // Word ID is unused for now. Included now to avoid a full stack update if needed later
    public ByteBuffer GetAudio(String wordId, String audioFileName) {
        return blobDao.loadAudioFile(audioFileName);
    }

    public List<String> GetAudioFilesForWord(String wordId) {
        return lexiconDao.getAudioFileNamesForWord(wordId);
    }

    public Map<String,List<String>> GetAudioFilesForWordBatch(List<String> wordIds) {
        if (wordIds != null && wordIds.size() > 0) {
            return lexiconDao.getAudioFileNamesForWordBatch(wordIds);
        }

        return new HashMap<>();
    }

    public Map<String, List<String>> SaveAudioMultiple(List<String> wordIds, List<MultipartFile> files) {
        Map<String, List<String>> savedFiles = new HashMap<>();

        for (int i =0; i < wordIds.size(); i++) {  // equal lengths already verified by controller
            String wordId = wordIds.get(i);
            String newFileName = SaveAudio(wordId, files.get(i));
            if (newFileName != null && !newFileName.isBlank()) {
                if (!savedFiles.containsKey(wordId)) {
                    savedFiles.put(wordId, new ArrayList<>(List.of(newFileName)));
                } else {
                    savedFiles.get(wordId).add(newFileName);
                }
            }
        }

        return savedFiles;
    }

    public String SaveAudio(String wordId, MultipartFile newAudioFile) {
        Set<String> currentFileNamesWithExtension = GetCurrentFileNamesWithoutExtension(wordId);
        String newFileName = GetNewFileName(wordId, newAudioFile.getOriginalFilename(), currentFileNamesWithExtension);

        try {
            log.info("Saving new audio file {} for word {}", newFileName, wordId);
            blobDao.saveAudioFile(newFileName, ByteBuffer.wrap(newAudioFile.getBytes()));
            if (lexiconDao.setAudioFileNameForWord(wordId, newFileName) > 0) {
                return newFileName;
            }

        } catch (Exception ex) {
            String errMsg = "Error saving audio file for word " + wordId;

            log.error(errMsg);
            throw new DaoException(errMsg, ex);
        }

        return "";
    }

    public boolean DeleteAudio(String wordId, String audioFileName) {
        try {
            blobDao.deleteAudioFile(audioFileName);
            if (lexiconDao.deleteAudioFileName(wordId, audioFileName) > 0) {
                return true;
            }
        } catch (Exception ex) {
            String errMsg = "Error saving audio file for word " + wordId;

            log.error(errMsg);
            throw new DaoException(errMsg, ex);
        }

        return false;
    }

    private static String GetNewFileName(String wordId, String existingFileName, Set<String> currentFileNamesWithExtension) {
        String prefix = wordId + "_";
        int offset = 1;
        while (currentFileNamesWithExtension.contains(prefix + offset)) {
            offset++;
        }
        return prefix + offset + FileNameUtil.GetExtension(existingFileName);
    }


    private Set<String> GetCurrentFileNamesWithoutExtension(String wordId) {
        return lexiconDao.getAudioFileNamesForWord(wordId)
                .stream()
                .map(fileName -> FileNameUtil.RemoveExtension(fileName))
                .collect(Collectors.toSet());
    }
}

package com.gt.ssrs.audio;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.blob.model.BlobPath;
import com.gt.ssrs.util.ShortUUIDUtil;
import com.gt.ssrs.word.WordDao;
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

    private final WordDao wordDao;
    private final BlobDao blobDao;

    @Autowired
    public AudioService(WordDao wordDao, BlobDao blobDao) {
        this.blobDao = blobDao;
        this.wordDao = wordDao;
    }

    public ByteBuffer getAudio(String audioFileName) {
        return blobDao.loadAudioFile(audioFileName);
    }

    public BlobPath getAudioPath(String audioFileName) {
        return blobDao.getAudioFilePath(audioFileName);
    }

    public List<String> getAudioFilesForWord(String wordId) {
        return wordDao.getAudioFileNamesForWord(wordId);
    }

    public Map<String,List<String>> getAudioFilesForWordBatch(List<String> wordIds) {
        if (wordIds != null && wordIds.size() > 0) {
            return wordDao.getAudioFileNamesForWordBatch(wordIds);
        }

        return new HashMap<>();

    }

    public Map<String, List<String>> saveAudioMultiple(List<String> wordIds, List<MultipartFile> files) {
        Map<String, List<String>> savedFiles = new HashMap<>();

        for (int i =0; i < wordIds.size(); i++) {  // equal lengths already verified by controller
            String wordId = wordIds.get(i);
            String newFileName = saveAudio(wordId, files.get(i));
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

    public String saveAudio(String wordId, MultipartFile newAudioFile) {
        Set<String> currentFileNamesWithoutExtension = getCurrentFileNamesWithoutExtension(wordId);
        String newFileName = getNewFileName(wordId, newAudioFile.getOriginalFilename(), currentFileNamesWithoutExtension);

        try {
            log.info("Saving new audio file {} for word {}", newFileName, wordId);
            if (blobDao.saveAudioFile(newFileName, ByteBuffer.wrap(newAudioFile.getBytes())) > 0) {
                if (wordDao.setAudioFileNameForWord(wordId, newFileName) > 0) {
                    return newFileName;
                } else {
                    blobDao.deleteAudioFile(newFileName);
                }
            }

        } catch (Exception ex) {
            String errMsg = "Error saving audio file for word " + wordId;

            log.error(errMsg);
            throw new DaoException(errMsg, ex);
        }

        return "";
    }

    public boolean deleteAudio(String wordId, String audioFileName) {
        try {
            blobDao.deleteAudioFile(audioFileName);
            if (wordDao.deleteAudioFileName(wordId, audioFileName) > 0) {
                return true;
            }
        } catch (Exception ex) {
            String errMsg = "Error saving audio file for word " + wordId;

            log.error(errMsg);
            throw new DaoException(errMsg, ex);
        }

        return false;
    }

    private static String getNewFileName(String wordId, String existingFileName, Set<String> currentFileNamesWithoutExtension) {
        String prefix = wordId + "_";
        String audioId = prefix + ShortUUIDUtil.newStubUUID();
        while (currentFileNamesWithoutExtension.contains(audioId)) {
            audioId = prefix + ShortUUIDUtil.newStubUUID();
        }
        return audioId + FileNameUtil.getExtension(existingFileName);
    }


    private Set<String> getCurrentFileNamesWithoutExtension(String wordId) {
        return wordDao.getAudioFileNamesForWord(wordId)
                .stream()
                .map(fileName -> FileNameUtil.removeExtension(fileName))
                .collect(Collectors.toSet());
    }
}

package com.gt.ssrs.lexicon;

import com.gt.ssrs.blob.BlobDao;
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

@Component
public class LexiconService {

    private static final Logger log = LoggerFactory.getLogger(LexiconService.class);

    private final LexiconDao lexiconDao;
    private final BlobDao blobDao;

    @Autowired
    public LexiconService(LexiconDao lexiconDao, BlobDao blobDao) {
        this.lexiconDao = lexiconDao;
        this.blobDao = blobDao;
    }

    public long getLexiconLanguageId(String lexiconId) {
        LexiconMetadata lexiconMetadata = lexiconDao.getLexiconMetadata(lexiconId);
        if (lexiconMetadata != null && lexiconMetadata.languageId() > 0) {
            return lexiconMetadata.languageId();
        }

        String errMsg = "No language id exists for lexicon " + lexiconId;
        log.error(errMsg);
        throw new MappingException(errMsg);
    }

    public List<LexiconMetadata> getAllLexiconMetadata(String username) {
        return lexiconDao.getAllLexiconMetadata(username);
    }

    public LexiconMetadata getLexiconMetadata(String id) {
        return lexiconDao.getLexiconMetadata(id);
    }

    // Saves the specified lexicon metadata and image. If the metadata does not specify id, creates
    // a new lexicon. Returns the saved metadata (including new id and image file) on success, null if not.
    public LexiconMetadata saveLexiconMetadata(String username, LexiconMetadata lexiconMetadata, MultipartFile newImageFile) {

        try {

            if (lexiconMetadata.id() == null || lexiconMetadata.id().isEmpty()) {
                String newId = UUID.randomUUID().toString();
                String imageFileName = updateImageBlobDataAsNeeded(newId, lexiconMetadata, newImageFile);
                LexiconMetadata lexiconMetadataToSave = getLexiconMetadataToSave(newId, username, lexiconMetadata, imageFileName);

                if (lexiconDao.createLexiconMetadata(lexiconMetadataToSave) == 0) {
                    log.warn("Unable to create row for new lexicon " + lexiconMetadata.title());
                    return null ;
                }

                return lexiconMetadataToSave;
            } else {
                LexiconMetadata oldLexiconMetadata = lexiconDao.getLexiconMetadata(lexiconMetadata.id());
                verifyCanEditLexicon(oldLexiconMetadata, username);

                String imageFileName = updateImageBlobDataAsNeeded(lexiconMetadata.id(), lexiconMetadata, newImageFile);
                LexiconMetadata lexiconMetadataToSave = getLexiconMetadataToSave(lexiconMetadata.id(), username, lexiconMetadata, imageFileName);
                int rowsUpdated = lexiconDao.updateLexiconMetadata(lexiconMetadataToSave);

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

    private static LexiconMetadata getLexiconMetadataToSave(String newId, String username, LexiconMetadata uploadedLexiconMetadata, String newImageFileName) {
        return new LexiconMetadata(
                newId,
                username,
                uploadedLexiconMetadata.title(),
                uploadedLexiconMetadata.description(),
                uploadedLexiconMetadata.languageId(),
                !newImageFileName.isBlank() ? newImageFileName : uploadedLexiconMetadata.imageFileName());
    }

    private String updateImageBlobDataAsNeeded(String newId, LexiconMetadata lexiconMetadata, MultipartFile newImageFile) throws IOException {
        if (newImageFile != null && newImageFile.getBytes().length > 0) {
            String newImageFileName = getImageFileName(newId, newImageFile.getOriginalFilename());
            ByteBuffer bytes = ByteBuffer.wrap(newImageFile.getBytes());

            if (lexiconMetadata.id() != null && !lexiconMetadata.id().isEmpty()) {
                LexiconMetadata oldLexiconMetadata = getLexiconMetadata(lexiconMetadata.id());

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


    public void deleteLexiconMetadata(String lexiconId, String username) {
        deleteLexiconImage(lexiconId);
        lexiconDao.deleteLexiconMetadata(lexiconId);
    }

    private void deleteLexiconImage(String lexiconId) {
        LexiconMetadata lexiconMetadata = getLexiconMetadata(lexiconId);
        String imageFileName = lexiconMetadata.imageFileName();
        if (imageFileName != null && !imageFileName.isBlank()) {
            blobDao.deleteImageFile(imageFileName);
        }
    }

    private void verifyCanEditLexicon(LexiconMetadata lexiconMetadata, String username) {
        if (!lexiconMetadata.owner().equals(username)) {
            String errMsg = "User does not permission to edit lexicon " + lexiconMetadata.id();

            log.error(errMsg);
            throw new UserAccessException(errMsg);
        }
    }
}

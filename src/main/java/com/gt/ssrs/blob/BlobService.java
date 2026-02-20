package com.gt.ssrs.blob;

import com.gt.ssrs.blob.model.BlobPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;

@Component
public class BlobService {
    private static final Logger log = LoggerFactory.getLogger(BlobService.class);

    private static final String DEFAULT_IMAGE_NAME = "DefaultImage.png";

    private final BlobDao blobDao;

    @Autowired
    public BlobService(BlobDao blobDao) {
        this.blobDao = blobDao;
    }

    public void saveImageFile(String name, ByteBuffer bytes) {
        blobDao.saveImageFile(name, bytes);
    }

    public ByteBuffer loadImageFileOrDefaultImage(String name) {
        return blobDao.loadImageFile(name);
    }

    public BlobPath getImageFilePath(String name) {
        return blobDao.getImageFilePath(name);
    }
}

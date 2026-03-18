package com.gt.ssrs.blob;

import com.gt.ssrs.blob.model.BlobPath;

import java.nio.ByteBuffer;
import java.util.List;

public interface BlobDao {

    void saveImageFile(String name, ByteBuffer bytes);

    ByteBuffer loadImageFile(String name);

    BlobPath getImageFilePath(String name);

    void deleteImageFile(String name);

    String saveAudioFile(String name, ByteBuffer bytes);

    List<String> saveAudioFiles(List<String> names, List<ByteBuffer> byteBuffers);

    ByteBuffer loadAudioFile(String name);

    BlobPath getAudioFilePath(String name);

    void deleteAudioFile(String name);

    void deleteAudioFiles(List<String> names);
}

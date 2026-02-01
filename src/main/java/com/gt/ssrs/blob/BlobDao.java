package com.gt.ssrs.blob;

import java.nio.ByteBuffer;
import java.util.List;

public interface BlobDao {

    public void saveImageFile(String name, ByteBuffer bytes);

    public ByteBuffer loadImageFile(String name);

    public void deleteImageFile(String name);


    public int saveAudioFile(String name, ByteBuffer bytes);

    public ByteBuffer loadAudioFile(String name);

    public void deleteAudioFile(String name);

    public void deleteAudioFiles(List<String> names);
}

package com.gt.ssrs.audio;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.exception.DaoException;
import com.gt.ssrs.lexicon.LexiconDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class AudioServiceTests {

    private AudioService audioService;

    @Mock private LexiconDao lexiconDao;
    @Mock private BlobDao blobDao;

    private static final String WORD_1_ID = "wordId1";
    private static final String WORD_2_ID = "wordId2";
    private static final String AUDIO_1_FILE_1 = "wordId1_1.mp3";
    private static final String AUDIO_1_FILE_2 = "wordId1_2.mp3";
    private static final String AUDIO_2_FILE_1 = "wordId2_1.mp3";

    private static final byte[] MOCK_FILE_BYTES_1 = "File bytes 1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MOCK_FILE_BYTES_2 = "File bytes 2".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MOCK_FILE_BYTES_3 = "File bytes 3".getBytes(StandardCharsets.UTF_8);

    private static final MultipartFile AUDIO_FILE_1 = mock(MultipartFile.class);
    private static final MultipartFile AUDIO_FILE_2 = mock(MultipartFile.class);
    private static final MultipartFile AUDIO_FILE_3 = mock(MultipartFile.class);

    @BeforeEach
    public void setup() throws IOException {
        when(blobDao.loadAudioFile(AUDIO_1_FILE_1)).thenReturn(ByteBuffer.wrap(MOCK_FILE_BYTES_1));
        when(lexiconDao.getAudioFileNamesForWord(WORD_1_ID)).thenReturn(List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2));
        when(lexiconDao.getAudioFileNamesForWord(WORD_2_ID)).thenReturn(List.of(AUDIO_2_FILE_1));
        when(lexiconDao.getAudioFileNamesForWordBatch(List.of(WORD_1_ID, WORD_2_ID))).thenReturn(Map.of(WORD_1_ID, List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2), AUDIO_2_FILE_1, List.of(AUDIO_2_FILE_1)));

        when(AUDIO_FILE_1.getBytes()).thenReturn(MOCK_FILE_BYTES_1);
        when(AUDIO_FILE_2.getBytes()).thenReturn(MOCK_FILE_BYTES_2);
        when(AUDIO_FILE_3.getBytes()).thenReturn(MOCK_FILE_BYTES_3);
        when(AUDIO_FILE_1.getOriginalFilename()).thenReturn("file1.mp3");
        when(AUDIO_FILE_2.getOriginalFilename()).thenReturn("file2.mp3");
        when(AUDIO_FILE_3.getOriginalFilename()).thenReturn("file3.mp3");

        audioService = new AudioService(lexiconDao, blobDao);
    }

    @Test
    public void testGetAudio() {
        assertEquals(ByteBuffer.wrap(MOCK_FILE_BYTES_1), audioService.GetAudio(WORD_1_ID, AUDIO_1_FILE_1));
    }

    @Test
    public void testGetAudioFilesForWord() {
        assertEquals(List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2), audioService.GetAudioFilesForWord(WORD_1_ID));
    }

    @Test
    public void testGetAudioFilesForWordBatch() {
        assertEquals(Map.of(WORD_1_ID, List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2), AUDIO_2_FILE_1, List.of(AUDIO_2_FILE_1)), audioService.GetAudioFilesForWordBatch(List.of(WORD_1_ID, WORD_2_ID)));
    }

    @Test
    public void testGetAudioFilesForWordBatch_NullList() {
        assertEquals(Map.of(), audioService.GetAudioFilesForWordBatch(null));
    }

    @Test
    public void testGetAudioFilesForWordBatch_EmptyList() {
        assertEquals(Map.of(), audioService.GetAudioFilesForWordBatch(List.of()));
    }

    @Test
    public void testSaveAudio() {
        String expectedFileName = WORD_1_ID + "_3.mp3";

        when(lexiconDao.setAudioFileNameForWord(WORD_1_ID, expectedFileName)).thenReturn(1);

        assertEquals(expectedFileName, audioService.SaveAudio(WORD_1_ID, AUDIO_FILE_1));

        verify(blobDao).saveAudioFile(expectedFileName, ByteBuffer.wrap(MOCK_FILE_BYTES_1));
    }

    @Test
    public void testSaveAudio_FailedSave() {
        String expectedFileName = WORD_1_ID + "_3.mp3";

        when(lexiconDao.setAudioFileNameForWord(WORD_1_ID, expectedFileName)).thenReturn(0);

        assertEquals("", audioService.SaveAudio(WORD_1_ID, AUDIO_FILE_1));

        verify(blobDao).saveAudioFile(expectedFileName, ByteBuffer.wrap(MOCK_FILE_BYTES_1));
    }

    @Test
    public void testSaveAudio_Exception() {
        when(blobDao.saveAudioFile(anyString(), any(ByteBuffer.class))).thenThrow(new DaoException("Test Exception"));

        try {
            audioService.SaveAudio(WORD_1_ID, AUDIO_FILE_1);
        } catch (DaoException ex) {
            return;
        }

        fail("Expected DaoException");
    }

    @Test
    public void testSaveAudioMultiple() throws IOException {
        when(lexiconDao.getAudioFileNamesForWord(WORD_1_ID)).thenReturn(List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2)).thenReturn(List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2, WORD_1_ID + "_3.mp3"));
        when(lexiconDao.getAudioFileNamesForWord(WORD_2_ID)).thenReturn(List.of(AUDIO_2_FILE_1)).thenReturn(List.of(AUDIO_2_FILE_1, WORD_2_ID + "_2.mp3"));

        byte[] failedSaveBytes = "failed save".getBytes(StandardCharsets.UTF_8);
        MultipartFile failedSave = mock(MultipartFile.class);
        when(failedSave.getBytes()).thenReturn(failedSaveBytes);
        when(failedSave.getOriginalFilename()).thenReturn("fail.mp3");

        when(lexiconDao.setAudioFileNameForWord(WORD_1_ID, WORD_1_ID + "_3.mp3")).thenReturn(1);
        when(lexiconDao.setAudioFileNameForWord(WORD_2_ID, WORD_2_ID + "_2.mp3")).thenReturn(1);
        when(lexiconDao.setAudioFileNameForWord(WORD_2_ID, WORD_2_ID + "_3.mp3")).thenReturn(1);

        assertEquals(
                Map.of(WORD_1_ID, List.of(WORD_1_ID + "_3.mp3"),
                       WORD_2_ID, List.of(WORD_2_ID + "_2.mp3", WORD_2_ID + "_3.mp3")),
                audioService.SaveAudioMultiple(List.of(WORD_1_ID, WORD_1_ID, WORD_2_ID, WORD_2_ID), List.of(AUDIO_FILE_1, failedSave, AUDIO_FILE_2, AUDIO_FILE_3)));
    }

    @Test
    public void testDeleteAudio() {
        when(lexiconDao.deleteAudioFileName(WORD_1_ID, AUDIO_1_FILE_1)).thenReturn(1);

        assertTrue(audioService.DeleteAudio(WORD_1_ID, AUDIO_1_FILE_1));

        verify(blobDao).deleteAudioFile(AUDIO_1_FILE_1);
    }

    @Test
    public void testDeleteAudio_FailedSave() {
        when(lexiconDao.deleteAudioFileName(WORD_1_ID, AUDIO_1_FILE_1)).thenReturn(0);

        assertFalse(audioService.DeleteAudio(WORD_1_ID, AUDIO_1_FILE_1));

        verify(blobDao).deleteAudioFile(AUDIO_1_FILE_1);
    }

    @Test
    public void testDeleteAudio_Exception() {
        doThrow(new DaoException("Test Exception")).when(blobDao).deleteAudioFile(AUDIO_1_FILE_1);

        try {
            audioService.DeleteAudio(WORD_1_ID, AUDIO_1_FILE_1);
        } catch (DaoException ex) {
            return;
        }

        fail("Expected DaoException");
    }
}

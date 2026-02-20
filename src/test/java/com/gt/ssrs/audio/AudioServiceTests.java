package com.gt.ssrs.audio;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.exception.DaoException;
import com.gt.ssrs.word.WordDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
public class AudioServiceTests {

    private static final String WORD_1_ID = "wordId1";
    private static final String WORD_2_ID = "wordId2";
    private static final String AUDIO_1_FILE_1 = "wordId1_1.mp3";
    private static final String AUDIO_1_FILE_2 = "wordId1_2.mp3";
    private static final String AUDIO_2_FILE_1 = "wordId2_1.mp3";

    private static final byte[] MOCK_FILE_BYTES_1 = "File bytes 1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MOCK_FILE_BYTES_2 = "File bytes 2".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MOCK_FILE_BYTES_3 = "File bytes 3".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MOCK_FILE_BYTES_4 = "File bytes 4".getBytes(StandardCharsets.UTF_8);

    private static final MultipartFile AUDIO_FILE_1 = mock(MultipartFile.class);
    private static final MultipartFile AUDIO_FILE_2 = mock(MultipartFile.class);
    private static final MultipartFile AUDIO_FILE_3 = mock(MultipartFile.class);
    private static final MultipartFile AUDIO_FILE_4 = mock(MultipartFile.class);

    private AudioService audioService;

    @Mock private WordDao wordDao;
    @Mock private BlobDao blobDao;

    private Map<String, List<String>> mockSavedAudioFiles = new HashMap<>();
    private List<String> failedSaveToWords = new ArrayList<>();

    @BeforeEach
    public void setup() throws IOException {
        when(blobDao.loadAudioFile(AUDIO_1_FILE_1)).thenReturn(ByteBuffer.wrap(MOCK_FILE_BYTES_1));
        when(blobDao.saveAudioFile(anyString(), any(ByteBuffer.class))).thenReturn(1);

        when(wordDao.getAudioFileNamesForWord(WORD_1_ID)).thenReturn(List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2));
        when(wordDao.getAudioFileNamesForWord(WORD_2_ID)).thenReturn(List.of(AUDIO_2_FILE_1));
        when(wordDao.getAudioFileNamesForWordBatch(List.of(WORD_1_ID, WORD_2_ID))).thenReturn(Map.of(WORD_1_ID, List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2), AUDIO_2_FILE_1, List.of(AUDIO_2_FILE_1)));

        when(AUDIO_FILE_1.getBytes()).thenReturn(MOCK_FILE_BYTES_1);
        when(AUDIO_FILE_2.getBytes()).thenReturn(MOCK_FILE_BYTES_2);
        when(AUDIO_FILE_3.getBytes()).thenReturn(MOCK_FILE_BYTES_3);
        when(AUDIO_FILE_4.getBytes()).thenReturn(MOCK_FILE_BYTES_4);
        when(AUDIO_FILE_1.getOriginalFilename()).thenReturn("file1.mp3");
        when(AUDIO_FILE_2.getOriginalFilename()).thenReturn("file2.mp3");
        when(AUDIO_FILE_3.getOriginalFilename()).thenReturn("file3.mp3");
        when(AUDIO_FILE_4.getOriginalFilename()).thenReturn("file4.mp3");

        mockSavedAudioFiles = new HashMap<>();
        failedSaveToWords = new ArrayList<>();

        when(wordDao.setAudioFileNameForWord(anyString(), anyString())).then(invocation -> {
            String wordId = invocation.getArgument(0);
            String audioFileName = invocation.getArgument(1);

            assertTrue(Set.of(WORD_1_ID, WORD_2_ID).contains(wordId));
            validateAudioFileName(wordId, audioFileName, "mp3");

            if (WORD_1_ID.equals(wordId) && mockSavedAudioFiles.get(WORD_1_ID) != null) {
                failedSaveToWords.add(audioFileName);
                return 0;
            }

            mockSavedAudioFiles.computeIfAbsent(wordId, unused -> new ArrayList<>()).add(audioFileName);
            return 1;
        });

        audioService = new AudioService(wordDao, blobDao);
    }

    @Test
    public void testGetAudio() {
        assertEquals(ByteBuffer.wrap(MOCK_FILE_BYTES_1), audioService.getAudio(AUDIO_1_FILE_1));
    }

    @Test
    public void testGetAudioFilesForWord() {
        assertEquals(List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2), audioService.getAudioFilesForWord(WORD_1_ID));
    }

    @Test
    public void testGetAudioFilesForWordBatch() {
        assertEquals(Map.of(WORD_1_ID, List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2), AUDIO_2_FILE_1, List.of(AUDIO_2_FILE_1)), audioService.getAudioFilesForWordBatch(List.of(WORD_1_ID, WORD_2_ID)));
    }

    @Test
    public void testGetAudioFilesForWordBatch_NullList() {
        assertEquals(Map.of(), audioService.getAudioFilesForWordBatch(null));
    }

    @Test
    public void testGetAudioFilesForWordBatch_EmptyList() {
        assertEquals(Map.of(), audioService.getAudioFilesForWordBatch(List.of()));
    }

    @Test
    public void testSaveAudio() {
        String savedFileName = audioService.saveAudio(WORD_1_ID, AUDIO_FILE_1);
        assertEquals(1, mockSavedAudioFiles.keySet().size());
        assertEquals(1, mockSavedAudioFiles.get(WORD_1_ID).size());
        assertEquals(mockSavedAudioFiles.get(WORD_1_ID).get(0), savedFileName);

        verify(blobDao).saveAudioFile(savedFileName, ByteBuffer.wrap(MOCK_FILE_BYTES_1));
        verifyNoMoreInteractions(blobDao);
    }

    @Test
    public void testSaveAudio_FailedSave() {
        when(wordDao.setAudioFileNameForWord(eq(WORD_1_ID), anyString())).thenReturn(0);

        assertEquals("", audioService.saveAudio(WORD_1_ID, AUDIO_FILE_1));

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
        verify(blobDao).saveAudioFile(stringCaptor.capture(), eq(ByteBuffer.wrap(MOCK_FILE_BYTES_1)));
        verify(blobDao).deleteAudioFile(stringCaptor.getValue());
        verifyNoMoreInteractions(blobDao);
    }

    @Test
    public void testSaveAudio_Exception() {
        when(blobDao.saveAudioFile(anyString(), any(ByteBuffer.class))).thenThrow(new DaoException("Test Exception"));

        try {
            audioService.saveAudio(WORD_1_ID, AUDIO_FILE_1);
        } catch (DaoException ex) {
            return;
        }

        fail("Expected DaoException");
    }

    @Test
    public void testSaveAudioMultiple() throws IOException {
        when(wordDao.getAudioFileNamesForWord(WORD_1_ID)).thenReturn(List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2)).thenReturn(List.of(AUDIO_1_FILE_1, AUDIO_1_FILE_2, WORD_1_ID + "_3.mp3"));
        when(wordDao.getAudioFileNamesForWord(WORD_2_ID)).thenReturn(List.of(AUDIO_2_FILE_1)).thenReturn(List.of(AUDIO_2_FILE_1, WORD_2_ID + "_2.mp3"));

        byte[] failedSaveBytes = "failed save".getBytes(StandardCharsets.UTF_8);
        MultipartFile failedSave = mock(MultipartFile.class);
        when(failedSave.getBytes()).thenReturn(failedSaveBytes);
        when(failedSave.getOriginalFilename()).thenReturn("fail.mp3");
        when(blobDao.saveAudioFile(anyString(), eq(ByteBuffer.wrap(failedSaveBytes)))).thenReturn(0);

        Map<String, List<String>> result = audioService.saveAudioMultiple(List.of(WORD_1_ID, WORD_1_ID, WORD_1_ID, WORD_2_ID, WORD_2_ID), List.of(AUDIO_FILE_1, failedSave, AUDIO_FILE_2, AUDIO_FILE_3, AUDIO_FILE_4));
        assertEquals(2, result.keySet().size());
        assertEquals(1, result.get(WORD_1_ID).size());
        assertEquals(2, result.get(WORD_2_ID).size());
        assertEquals(mockSavedAudioFiles, result);

        assertEquals(1, failedSaveToWords.size());
        verify(blobDao).deleteAudioFile(failedSaveToWords.get(0));

        verify(blobDao).saveAudioFile(result.get(WORD_1_ID).get(0), ByteBuffer.wrap(AUDIO_FILE_1.getBytes()));
        verify(blobDao).saveAudioFile(anyString(), eq(ByteBuffer.wrap(failedSaveBytes)));  // this would be the only time filename was used, so the actual value doesn't really matter
        verify(blobDao).saveAudioFile(failedSaveToWords.get(0), ByteBuffer.wrap(AUDIO_FILE_2.getBytes()));
        verify(blobDao).saveAudioFile(result.get(WORD_2_ID).get(0), ByteBuffer.wrap(AUDIO_FILE_3.getBytes()));
        verify(blobDao).saveAudioFile(result.get(WORD_2_ID).get(1), ByteBuffer.wrap(AUDIO_FILE_4.getBytes()));
        verifyNoMoreInteractions(blobDao);
    }

    @Test
    public void testDeleteAudio() {
        when(wordDao.deleteAudioFileName(WORD_1_ID, AUDIO_1_FILE_1)).thenReturn(1);

        assertTrue(audioService.deleteAudio(WORD_1_ID, AUDIO_1_FILE_1));

        verify(blobDao).deleteAudioFile(AUDIO_1_FILE_1);
    }

    @Test
    public void testDeleteAudio_FailedSave() {
        when(wordDao.deleteAudioFileName(WORD_1_ID, AUDIO_1_FILE_1)).thenReturn(0);

        assertFalse(audioService.deleteAudio(WORD_1_ID, AUDIO_1_FILE_1));

        verify(blobDao).deleteAudioFile(AUDIO_1_FILE_1);
    }

    @Test
    public void testDeleteAudio_Exception() {
        doThrow(new DaoException("Test Exception")).when(blobDao).deleteAudioFile(AUDIO_1_FILE_1);

        try {
            audioService.deleteAudio(WORD_1_ID, AUDIO_1_FILE_1);
        } catch (DaoException ex) {
            return;
        }

        fail("Expected DaoException");
    }

    private void validateAudioFileName(String wordId, String audioFileName, String extension) {
        // Expecting wordId_stubUUID.ext, where the stubUUID is an 8 character hex string
        assertTrue(audioFileName.startsWith(wordId + "_"));
        assertTrue(audioFileName.endsWith("." + extension));
        assertEquals(wordId.length() + extension.length() + 10, audioFileName.length());
    }
}


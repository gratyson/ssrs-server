package com.gt.ssrs.audio;

import com.gt.ssrs.blob.model.BlobPath;
import com.gt.ssrs.util.FileNameUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rest/audio")
public class AudioController {

    private static final int HTTP_STATUS_UNPROCESSABLE_CONTENT = 422;
    private static final int CACHE_DURATION_SECONDS = 365 * 24 * 60 * 60;

    private static final Logger log = LoggerFactory.getLogger(AudioController.class);

    private final AudioService audioService;

    @Autowired
    public AudioController(AudioService audioService) {
        this.audioService = audioService;
    }

    @GetMapping(value = "/audio")
    public ResponseEntity GetAudio(@RequestParam("audioFileName") String audioFileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "audio/" + FileNameUtil.getExtension(audioFileName).substring(1));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "public, max-age=" + CACHE_DURATION_SECONDS);

        return ResponseEntity
                .status(HttpStatus.OK)
                .headers(headers)
                .body(audioService.getAudio(audioFileName).array());
    }

    @GetMapping(value = "/audioPath")
    public BlobPath getAudioPath(@RequestParam("audioFileName") String audioFileName) {
        return audioService.getAudioPath(audioFileName);
    }

    @GetMapping(value = "/getAudioFilesForWord", produces = "application/json")
    public List<String> GetAudioFilesForWord(@RequestParam("wordId") String wordId) {
        return audioService.getAudioFilesForWord(wordId);
    }

    @PostMapping(value = "/getAudioFilesForWordBatch", consumes = "application/json", produces = "application/json")
    public Map<String,List<String>> GetAudioFilesForWordBatch(@RequestBody List<String> wordIds) {
        return audioService.getAudioFilesForWordBatch(wordIds);
    }


    @PutMapping(value = "/saveAudio", produces = "text/plain")
    public String SaveAudio(@RequestPart("wordId") String wordId,
                            @RequestPart(value = "file") MultipartFile file,
                            HttpServletResponse response) throws IOException {
        String newAudioFileName = audioService.saveAudio(wordId, file);
        if (newAudioFileName != null && !newAudioFileName.isBlank()) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return newAudioFileName;
        } else {
            response.setStatus(HTTP_STATUS_UNPROCESSABLE_CONTENT);
            return null;
        }
    }

    @PutMapping(value = "/saveAudioBatch", produces = "application/json")
    public Map<String, List<String>> SaveAudioMultiple(@RequestPart("wordIds") List<String> wordIds,
                                                       @RequestPart("files[]") MultipartFile[] files,
                                                       HttpServletResponse response) throws IOException {
        if(wordIds == null || files == null || wordIds.size() != files.length) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        Map<String, List<String>> savedAudio = audioService.saveAudioMultiple(wordIds, List.of(files));
        if (savedAudio != null && savedAudio.size() > 0) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
            return savedAudio;
        } else {
            response.setStatus(HTTP_STATUS_UNPROCESSABLE_CONTENT);
            return null;
        }
    }

    @PostMapping(value = "/deleteAudio", consumes = "application/json", produces = "application/json")
    public DeleteResponse DeleteAudio(@RequestBody DeleteRequest deleteRequest, HttpServletResponse response) {
        if (audioService.deleteAudio(deleteRequest.wordId, deleteRequest.audioFileName)) {
            response.setStatus(HttpServletResponse.SC_OK);
            return new DeleteResponse(deleteRequest.audioFileName);
        } else {
            response.setStatus(HTTP_STATUS_UNPROCESSABLE_CONTENT);
            return new DeleteResponse("");
        }
    }

    private record DeleteRequest(String wordId, String audioFileName) { }
    private record DeleteResponse(String fileDeleted) { }
}

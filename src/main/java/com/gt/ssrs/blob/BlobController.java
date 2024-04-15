package com.gt.ssrs.blob;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;

@RestController
@RequestMapping("/rest/blob")
public class BlobController {

    private static final Logger log = LoggerFactory.getLogger(BlobController.class);

    private final BlobService blobService;

    @Autowired
    public BlobController(BlobService blobService) {
        this.blobService = blobService;
    }

    @PostMapping("/saveImage")
    public void SaveImageFile(@RequestParam("fileName") String fileName, @RequestParam("file") MultipartFile file) throws IOException {
        blobService.SaveImageFile(fileName, ByteBuffer.wrap(file.getBytes()));
    }

    @GetMapping(value = "/image/{fileName}", produces = "image/*")
    public byte[] LoadImageFile(@PathVariable String fileName) {
        return blobService.LoadImageFileOrDefaultImage(fileName).array();
    }

    @GetMapping(value = "/audio/{fileName}", produces = "audio/mp3")
    public ResponseEntity LoadAudioFile(@PathVariable String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "audio/" + fileName.substring(fileName.lastIndexOf(".") + 1));

        return ResponseEntity
                .status(HttpStatus.OK)
                .headers(headers)
                .body(blobService.LoadAudioFile(fileName).array());
    }
}

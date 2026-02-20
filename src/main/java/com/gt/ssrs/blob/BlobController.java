package com.gt.ssrs.blob;

import com.gt.ssrs.blob.model.BlobPath;
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

    private static final int CACHE_DURATION_SECONDS = 365 * 24 * 60 * 60;

    private final BlobService blobService;

    @Autowired
    public BlobController(BlobService blobService) {
        this.blobService = blobService;
    }

    @PostMapping("/saveImage")
    public void saveImageFile(@RequestParam("fileName") String fileName, @RequestParam("file") MultipartFile file) throws IOException {
        blobService.saveImageFile(fileName, ByteBuffer.wrap(file.getBytes()));
    }

    @GetMapping(value = "/image/{fileName}", produces = "image/*")
    public ResponseEntity loadImageFile(@PathVariable String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "image/" + fileName.substring(fileName.lastIndexOf(".") + 1));
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "public, max-age=" + CACHE_DURATION_SECONDS);

        return ResponseEntity
                .status(HttpStatus.OK)
                .headers(headers)
                .body(blobService.loadImageFileOrDefaultImage(fileName).array());
    }

    @GetMapping(value = "/image/getPath/{fileName}")
    public BlobPath getImageFilePath(@PathVariable String fileName) {
        return blobService.getImageFilePath(fileName);
    }
}

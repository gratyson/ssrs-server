package com.gt.ssrs.blob.aws;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.blob.model.BlobPath;
import com.gt.ssrs.exception.DaoException;
import com.gt.ssrs.util.ListUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class BlobDaoS3 implements BlobDao {

    private static final Logger log = LoggerFactory.getLogger(BlobDaoS3.class);

    private static final String AUDIO_CONTENT_DISPOSITION_TYPE = "audio";
    private static final String IMAGE_CONTENT_DISPOSITION_TYPE = "image";

    private static final Duration SIGNATURE_DURATION = Duration.ofDays(7);
    private static final Duration EXTERNAL_DURATION_BUFFER = Duration.ofMinutes(1);

    private static final Duration ASYNC_PUT_OBJECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ASYNC_PUT_OBJECT_MIN_TIMEOUT = Duration.ofSeconds(1);

    private final S3Client s3Client;
    private final S3AsyncClient s3AsyncClient;
    private final S3Presigner s3Presigner;
    private final String imageBucketName;
    private final String audioBucketName;
    private final int maxConcurrentS3PutRequests;
    private final int deleteBatchSize;

    private final HttpRequest.Builder requestBuilder;
    private final HttpClient httpClient;

    @Autowired
    public BlobDaoS3(S3Client s3Client,
                     S3AsyncClient s3AsyncClient,
                     S3Presigner s3Presigner,
                     @Value("${aws.s3.imageBucketName}") String imageBucketName,
                     @Value("${aws.s3.audioBucketName}") String audioBucketName,
                     @Value("${aws.s3.maxConcurrentS3PutRequests:25}") int maxConcurrentS3PutRequests,
                     @Value("${aws.s3.deleteBatchSize:300}") int deleteBatchSize) {
        this.s3Client = s3Client;
        this.s3AsyncClient = s3AsyncClient;
        this.s3Presigner = s3Presigner;
        this.imageBucketName = imageBucketName;
        this.audioBucketName = audioBucketName;
        this.maxConcurrentS3PutRequests = maxConcurrentS3PutRequests;
        this.deleteBatchSize = deleteBatchSize;

        this.requestBuilder = HttpRequest.newBuilder();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void saveImageFile(String name, ByteBuffer bytes) {
        saveFile(imageBucketName, new BlobData(name, getImageContentDisposition(name), bytes));
    }

    @Override
    public ByteBuffer loadImageFile(String name) {
        return loadFile(imageBucketName, name);
    }

    @Override
    public BlobPath getImageFilePath(String fileName) {
        String url = getPresignedUrl(imageBucketName, fileName, SIGNATURE_DURATION);

        return new BlobPath(url, false, Instant.now().plus(SIGNATURE_DURATION.minus(EXTERNAL_DURATION_BUFFER)));
    }

    @Override
    public void deleteImageFile(String name) {
        deleteFiles(imageBucketName, List.of(name));
    }

    @Override
    public String saveAudioFile(String name, ByteBuffer bytes) {
        return saveFile(audioBucketName, new BlobData(name, getAudioContentDisposition(name), bytes));
    }

    @Override
    public List<String> saveAudioFiles(List<String> names, List<ByteBuffer> byteBuffers) {
        List<BlobData> blobDataToSave = new ArrayList<>();

        for (int idx = 0; idx < names.size(); idx++) {
            blobDataToSave.add(new BlobData(names.get(idx), getAudioContentDisposition(names.get(idx)), byteBuffers.get(idx)));
        }

        return saveFiles(audioBucketName, blobDataToSave);
    }

    @Override
    public ByteBuffer loadAudioFile(String name) {
        return loadFile(audioBucketName, name);
    }

    @Override
    public BlobPath getAudioFilePath(String name) {
        String url = getPresignedUrl(audioBucketName, name, SIGNATURE_DURATION);

        return new BlobPath(url, false, Instant.now().plus(SIGNATURE_DURATION.minus(EXTERNAL_DURATION_BUFFER)));
    }

    @Override
    public void deleteAudioFile(String name) {
        deleteFiles(audioBucketName, List.of(name));
    }

    @Override
    public void deleteAudioFiles(List<String> names) {
        deleteFiles(audioBucketName, names);
    }

    private String saveFile(String bucketName, BlobData blobData) {
        return processPutObjectResponse(bucketName, blobData.fileName, s3Client.putObject(toPutObjectRequest(bucketName, blobData), RequestBody.fromByteBuffer(blobData.bytes())));
    }

    private List<String> saveFiles(String bucketName, List<BlobData> blobDataToSave) {
        List<String> savedFileNames = new ArrayList<>();

        for (List<BlobData> blobDataBatch : ListUtil.partitionList(blobDataToSave, maxConcurrentS3PutRequests)) {
            // Ideally this could queue up new putObject requests as older requests complete, but needing to batch at all is expected to be an extremely rare edge case
            savedFileNames.addAll(saveFilesBatch(bucketName, blobDataBatch));
        }

        return savedFileNames;
    }

    private List<String> saveFilesBatch(String bucketName, List<BlobData> blobDataToSave) {
        Map<String, CompletableFuture<PutObjectResponse>> responseFutures = new HashMap<>();

        for (BlobData blobData : blobDataToSave) {
            responseFutures.put(blobData.fileName, s3AsyncClient.putObject(toPutObjectRequest(bucketName, blobData), AsyncRequestBody.fromByteBuffer(blobData.bytes())));
        }

        List<String> savedFileNames = new ArrayList<>();
        Instant timeoutInstant = Instant.now().plus(ASYNC_PUT_OBJECT_TIMEOUT);

        for(Map.Entry<String, CompletableFuture<PutObjectResponse>> responseFuture : responseFutures.entrySet()) {
            long timeoutMillis = Math.max(timeoutInstant.toEpochMilli() - Instant.now().toEpochMilli(), ASYNC_PUT_OBJECT_MIN_TIMEOUT.toMillis());
            try {
                try {
                    savedFileNames.add(processPutObjectResponse(bucketName, responseFuture.getKey(), responseFuture.getValue().get(timeoutMillis, TimeUnit.MILLISECONDS)));
                } catch (DaoException ex) {
                    // error already logged
                }
            } catch (Exception e) {
                log.error("Error awaiting for response to S3 PutObject", e);
            }
        }

        return savedFileNames;
    }

    private PutObjectRequest toPutObjectRequest(String bucketName, BlobData blobData) {
        return PutObjectRequest.builder()
                .bucket(bucketName)
                .key(blobData.fileName())
                .contentDisposition(blobData.contentDisposition())
                .cacheControl("public, max-age=" + SIGNATURE_DURATION.minus(EXTERNAL_DURATION_BUFFER).toSeconds())
                .build();
    }

    private String processPutObjectResponse(String bucketName, String fileName, PutObjectResponse response) {
        if (!response.sdkHttpResponse().isSuccessful()) {
            String errMsg = "Unable to save file to bucket " + bucketName
                    + ". Returned status code " + response.sdkHttpResponse().statusCode()
                    + " with message " + response.sdkHttpResponse().statusText().orElse("<n/a>");

            log.error(errMsg);
            throw new DaoException(errMsg);
        }

        return fileName;
    }

    private String getPresignedUrl(String bucketName, String fileName, Duration signatureDuration) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .getObjectRequest(request)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        return presignedRequest.url().toExternalForm();
    }

    private ByteBuffer loadFile(String bucketName, String fileName) {
        try {
            URI uri = URI.create(getPresignedUrl(bucketName, fileName, SIGNATURE_DURATION));

            HttpResponse<InputStream> response = httpClient.send(
                    requestBuilder.uri(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());

            return ByteBuffer.wrap(response.body().readAllBytes());
        } catch (Exception ex) {
            String errMsg = "Failed to load " + fileName + " from bucket " + bucketName;

            log.error(errMsg, ex);
            throw new DaoException(errMsg, ex);
        }
    }

    private void deleteFiles(String bucketName, List<String> fileNames) {
        for (List<String> fileNamesBatch : ListUtil.partitionList(fileNames, deleteBatchSize)) {
            deleteFilesBatch(bucketName, fileNamesBatch);
        }
    }

    private void deleteFilesBatch(String bucketName, List<String> fileNames) {
        Delete delete = Delete.builder()
                .objects(fileNames.stream().map(
                        fileName -> ObjectIdentifier.builder()
                                .key(fileName)
                                .build())
                        .toList())
                .build();

        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(bucketName)
                .delete(delete)
                .build();

        DeleteObjectsResponse response = s3Client.deleteObjects(request);

        if (!response.sdkHttpResponse().isSuccessful()) {
            String errMsg = "Unable to delete files " + fileNames
                    + " to bucket " + bucketName
                    + ". Returned status code " + response.sdkHttpResponse().statusCode()
                    + " with message " + response.sdkHttpResponse().statusText().orElse("<n/a>");

            log.error(errMsg);
            throw new DaoException(errMsg);
        }
    }

    private String getAudioContentDisposition(String name) {
        return getContentDisposition(AUDIO_CONTENT_DISPOSITION_TYPE, name);
    }

    private String getImageContentDisposition(String name) {
        return getContentDisposition(IMAGE_CONTENT_DISPOSITION_TYPE, name);
    }

    private String getContentDisposition(String type, String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
        return type + "/" + (StringUtils.hasText(extension) ? extension : "*");
    }

    private record BlobData(String fileName, String contentDisposition, ByteBuffer bytes) { }
}

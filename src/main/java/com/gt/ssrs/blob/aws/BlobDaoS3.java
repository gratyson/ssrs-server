package com.gt.ssrs.blob.aws;

import com.gt.ssrs.blob.BlobDao;
import com.gt.ssrs.blob.model.BlobPath;
import com.gt.ssrs.exception.DaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.sync.RequestBody;
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
import java.util.List;

@Component
public class BlobDaoS3 implements BlobDao {

    private static final Logger log = LoggerFactory.getLogger(BlobDaoS3.class);

    private static final Duration SIGNATURE_DURATION = Duration.ofDays(7);
    private static final Duration EXTERNAL_DURATION_BUFFER = Duration.ofMinutes(1);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String imageBucketName;
    private final String audioBucketName;

    private final HttpRequest.Builder requestBuilder;
    private final HttpClient httpClient;

    @Autowired
    public BlobDaoS3(S3Client s3Client,
                     S3Presigner s3Presigner,
                     @Value("${aws.s3.imageBucketName}") String imageBucketName,
                     @Value("${aws.s3.audioBucketName}") String audioBucketName) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.imageBucketName = imageBucketName;
        this.audioBucketName = audioBucketName;

        this.requestBuilder = HttpRequest.newBuilder();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void saveImageFile(String name, ByteBuffer bytes) {
        String extension = name.substring(name.lastIndexOf(".") + 1);
        String contentDisposition = "image/" + (StringUtils.hasText(extension) ? extension : "*");

        saveFile(imageBucketName, name, contentDisposition, bytes);
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
    public int saveAudioFile(String name, ByteBuffer bytes) {
        String extension = name.substring(name.lastIndexOf(".") + 1);
        String contentDisposition = "audio/" + (StringUtils.hasText(extension) ? extension : "*");

        saveFile(audioBucketName, name, contentDisposition, bytes);
        return 1;
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

    private void saveFile(String bucketName, String fileName, String contentDisposition, ByteBuffer bytes) {


        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentDisposition(contentDisposition)
                .cacheControl("public, max-age=" + SIGNATURE_DURATION.minus(EXTERNAL_DURATION_BUFFER).toSeconds())
                .build();

        PutObjectResponse response = s3Client.putObject(request, RequestBody.fromByteBuffer(bytes));

        if (!response.sdkHttpResponse().isSuccessful()) {
            String errMsg = "Unable to save file " + fileName
                    + " to bucket " + bucketName
                    + ". Returned status code " + response.sdkHttpResponse().statusCode()
                    + " with message " + response.sdkHttpResponse().statusText().orElse("<n/a>");

            log.error(errMsg);
            throw new DaoException(errMsg);
        }
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
}

package com.filemind.chunk.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filemind.chunk.model.ChunkStorageResult;
import com.filemind.chunk.model.StoredManifest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
@Primary
@ConditionalOnProperty(name = "aws.enabled", havingValue = "true")
public class AwsStorageProvider implements StorageProvider {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public AwsStorageProvider(S3Client s3Client, ObjectMapper objectMapper) {
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean chunkExists(String hash) {
        return hasObject(chunkKey(hash, false)) || hasObject(chunkKey(hash, true));
    }

    @Override
    public ChunkStorageResult storeChunk(String hash, byte[] bytes) {
        byte[] compressed = gzip(bytes);
        boolean useCompression = compressed.length < bytes.length;
        String key = chunkKey(hash, useCompression);
        byte[] payload = useCompression ? compressed : bytes;
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build(), RequestBody.fromBytes(payload));
        return new ChunkStorageResult(s3Uri(bucketName, key), payload.length);
    }

    @Override
    public ChunkStorageResult storeChunk(String hash, Path filePath) {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            return storeChunk(hash, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist chunk " + hash + " to S3", exception);
        }
    }

    @Override
    public byte[] readChunk(String storagePath) {
        try {
            S3ObjectLocation location = parseLocation(storagePath);
            byte[] bytes = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(location.bucketName())
                    .key(location.key())
                    .build(), ResponseTransformer.toBytes()).asByteArray();
            if (location.key().endsWith(".gz")) {
                return gunzip(bytes);
            }
            return bytes;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read stored chunk from S3: " + storagePath, exception);
        }
    }

    @Override
    public void deleteChunk(String storagePath) {
        S3ObjectLocation location = parseLocation(storagePath);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(location.bucketName())
                .key(location.key())
                .build());
    }

    @Override
    public void deleteChunkByHash(String hash) {
        deleteIfExists(chunkKey(hash, false));
        deleteIfExists(chunkKey(hash, true));
    }

    @Override
    public String writeManifest(String versionId, StoredManifest manifest) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(manifest);
            String key = manifestKey(versionId);
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build(), RequestBody.fromBytes(bytes));
            return s3Uri(bucketName, key);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write manifest " + versionId + " to S3", exception);
        }
    }

    @Override
    public StoredManifest readManifest(String manifestPath) {
        try {
            S3ObjectLocation location = parseLocation(manifestPath);
            byte[] bytes = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(location.bucketName())
                    .key(location.key())
                    .build(), ResponseTransformer.toBytes()).asByteArray();
            return objectMapper.readValue(bytes, StoredManifest.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read manifest from S3: " + manifestPath, exception);
        }
    }

    @Override
    public void deleteManifest(String manifestPath) {
        S3ObjectLocation location = parseLocation(manifestPath);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(location.bucketName())
                .key(location.key())
                .build());
    }

    private boolean hasObject(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new IllegalStateException("Unable to check object existence for key " + key, exception);
        }
    }

    private void deleteIfExists(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw new IllegalStateException("Unable to delete object " + key + " from S3", exception);
            }
        }
    }

    private String chunkKey(String hash, boolean compressed) {
        return "chunks/" + hash + (compressed ? ".gz" : ".bin");
    }

    private String manifestKey(String versionId) {
        return "manifests/" + versionId + ".json";
    }

    private String s3Uri(String bucket, String key) {
        return "s3://" + bucket + "/" + key;
    }

    private S3ObjectLocation parseLocation(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Storage path cannot be blank");
        }

        if (storagePath.startsWith("s3://")) {
            URI uri = URI.create(storagePath);
            String bucket = uri.getHost();
            String key = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
            return new S3ObjectLocation(bucket, key);
        }

        if (storagePath.startsWith("/") || storagePath.contains("\\")) {
            Path path = Path.of(storagePath);
            String fileName = path.getFileName() != null ? path.getFileName().toString() : storagePath;
            return new S3ObjectLocation(bucketName, "chunks/" + fileName);
        }

        String normalized = storagePath.startsWith("chunks/") || storagePath.startsWith("manifests/")
                ? storagePath
                : "chunks/" + storagePath;
        return new S3ObjectLocation(bucketName, normalized);
    }

    private byte[] gzip(byte[] bytes) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream)) {
            gzipStream.write(bytes);
            gzipStream.flush();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to gzip bytes before S3 upload", exception);
        }
    }

    private byte[] gunzip(byte[] bytes) {
        try (GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            gzipStream.transferTo(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to gunzip bytes from S3", exception);
        }
    }

    private record S3ObjectLocation(String bucketName, String key) {
    }
}

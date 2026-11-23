package ke.sauti.hms.capture;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import ke.sauti.hms.common.crypto.ColumnCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Audio pieces in S3-compatible storage, always encrypted with the app key
 * ({@link ColumnCipher}) before upload and decrypted after download, so the bucket only
 * ever holds unreadable bytes. Path-style addressing, which SeaweedFS and R2 both accept.
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class AudioStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AudioStore.class);
    private static final String CONTENT_TYPE = "application/octet-stream";

    private final S3Client s3;
    private final String bucket;
    private final ColumnCipher cipher;

    public AudioStore(StorageProperties properties, ColumnCipher cipher) {
        this.cipher = cipher;
        this.bucket = properties.bucket();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        if (properties.createBucket()) {
            ensureBucket();
        }
    }

    /** Encrypts and uploads; the key is the caller's (session and sequence based). */
    public void put(String key, byte[] audio) {
        byte[] sealed = cipher.encrypt(audio);
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(CONTENT_TYPE), RequestBody.fromBytes(sealed));
    }

    /** Downloads and decrypts; a tampered object fails the cipher's check. */
    public byte[] get(String key) {
        return cipher.decrypt(
                s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
    }

    /** Like {@link #get}, but empty when nothing is stored under the key. */
    public Optional<byte[]> find(String key) {
        try {
            return Optional.of(get(key));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    /** The stored bytes exactly as they sit in the bucket (encrypted), for checks. */
    byte[] getRaw(String key) {
        return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
    }

    /** Calls back with each page of keys under the prefix last changed before the given time. */
    public void forEachPageOlderThan(String prefix, Instant before, Consumer<List<String>> page) {
        s3.listObjectsV2Paginator(b -> b.bucket(bucket).prefix(prefix)).forEach(response -> {
            List<String> keys = response.contents().stream()
                    .filter(object -> object.lastModified().isBefore(before))
                    .map(S3Object::key)
                    .toList();
            if (!keys.isEmpty()) {
                page.accept(keys);
            }
        });
    }

    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    private void ensureBucket() {
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket(bucket));
            log.info("Created audio bucket {}", bucket);
        }
    }

    @Override
    public void close() {
        s3.close();
    }
}

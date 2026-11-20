package ke.sauti.hms.capture;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible storage for audio pieces: SeaweedFS in development, Cloudflare R2 in the
 * demo. Read from {@code STORAGE_*} environment variables outside development.
 *
 * @param endpoint the S3 endpoint URL
 * @param bucket the bucket holding encrypted pieces
 * @param accessKey access key id
 * @param secretKey secret access key
 * @param region the signing region (R2 accepts "auto")
 * @param createBucket create the bucket at startup if it is missing (development and tests only)
 */
@ConfigurationProperties(prefix = "sauti.storage")
public record StorageProperties(
        String endpoint, String bucket, String accessKey, String secretKey, String region, boolean createBucket) {}

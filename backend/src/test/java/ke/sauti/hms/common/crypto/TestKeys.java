package ke.sauti.hms.common.crypto;

/** Fixed, non-secret keys for unit tests (the same pair as application-test.yml). */
final class TestKeys {

    static final String ENCRYPTION = "2siE80h7Mi8O18091FZBA/I3Wm6lZHtxmUe1C0d5ssk="; // gitleaks:allow
    static final String HASH = "OsFzZqqzZek8HPtPeU4t8Civx+RwrrjO8AhYkPMVZJM="; // gitleaks:allow
    static final String OTHER = "2/cNRn5/KmEG1U+9IgMmz2IyJWQx3djKbAEUObgH7yI="; // gitleaks:allow

    private TestKeys() {}

    static CryptoKeys keys() {
        return CryptoKeys.from(new CryptoProperties(ENCRYPTION, HASH));
    }

    static CryptoKeys otherEncryptionKey() {
        return CryptoKeys.from(new CryptoProperties(OTHER, HASH));
    }

    static CryptoKeys otherHashKey() {
        return CryptoKeys.from(new CryptoProperties(ENCRYPTION, OTHER));
    }
}

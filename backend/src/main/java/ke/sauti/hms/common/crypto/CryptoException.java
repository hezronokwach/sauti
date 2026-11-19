package ke.sauti.hms.common.crypto;

/**
 * A stored value could not be decrypted: unknown key version, truncated bytes, a wrong
 * key or edited bytes. The message never contains any of the data.
 */
public class CryptoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}

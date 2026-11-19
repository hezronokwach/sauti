package ke.sauti.hms.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Encrypts a text attribute into a {@code bytea} column and back. Entity code reads and
 * writes plain strings; the database only ever holds {@link ColumnCipher} output.
 * <p>
 * Not applied automatically: each sensitive field opts in with
 * {@code @Convert(converter = EncryptedStringConverter.class)}. Hibernate obtains this
 * converter from Spring, which supplies the cipher.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<@Nullable String, byte @Nullable []> {

    private final ColumnCipher cipher;

    public EncryptedStringConverter(ColumnCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public byte @Nullable [] convertToDatabaseColumn(@Nullable String attribute) {
        return attribute == null ? null : cipher.encrypt(attribute.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public @Nullable String convertToEntityAttribute(byte @Nullable [] column) {
        return column == null ? null : new String(cipher.decrypt(column), StandardCharsets.UTF_8);
    }
}

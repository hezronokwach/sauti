package ke.sauti.hms.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EncryptedStringConverterTest {

    private final EncryptedStringConverter converter = new EncryptedStringConverter(new ColumnCipher(TestKeys.keys()));

    @Test
    void nullStaysNullBothWays() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void storesUnreadableBytesAndReadsBackTheText() {
        byte[] column = converter.convertToDatabaseColumn("JBSWY3DPEHPK3PXP");

        assertThat(column).isNotNull();
        assertThat(new String(column, StandardCharsets.ISO_8859_1)).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo("JBSWY3DPEHPK3PXP");
    }
}

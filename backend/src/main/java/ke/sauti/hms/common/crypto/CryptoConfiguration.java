package ke.sauti.hms.common.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Decodes the keys once at startup and shares one cipher and one hasher. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfiguration {

    @Bean
    CryptoKeys cryptoKeys(CryptoProperties properties) {
        return CryptoKeys.from(properties);
    }

    @Bean
    ColumnCipher columnCipher(CryptoKeys keys) {
        return new ColumnCipher(keys);
    }

    @Bean
    KeyedHasher keyedHasher(CryptoKeys keys) {
        return new KeyedHasher(keys);
    }
}

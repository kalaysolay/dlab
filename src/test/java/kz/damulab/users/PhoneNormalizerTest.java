package kz.damulab.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNormalizerTest {

    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    @Test
    void canonicalizesCommonKazakhstanFormats() {
        assertThat(normalizer.normalize("8 (701) 123-45-67")).isEqualTo("+77011234567");
        assertThat(normalizer.normalize("77011234567")).isEqualTo("+77011234567");
        assertThat(normalizer.normalize("7011234567")).isEqualTo("+77011234567");
    }

    @Test
    void preservesInternationalE164AndAllowsEmptyValue() {
        assertThat(normalizer.normalize("+14155552671")).isEqualTo("+14155552671");
        assertThat(normalizer.normalize("  ")).isNull();
    }

    @Test
    void rejectsAmbiguousOrMalformedValue() {
        assertThatThrownBy(() -> normalizer.normalize("12345"))
                .isInstanceOf(InvalidPhoneException.class);
    }
}

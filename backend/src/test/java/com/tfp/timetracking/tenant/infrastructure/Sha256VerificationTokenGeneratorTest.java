package com.tfp.timetracking.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tfp.timetracking.tenant.domain.VerificationToken;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T53-05: el token es aleatorio, apto para URL, y solo se almacena su hash. */
class Sha256VerificationTokenGeneratorTest {

    private final Sha256VerificationTokenGenerator generator = new Sha256VerificationTokenGenerator();

    @Test
    void generatesUrlSafeTokensWithEnoughEntropy() {
        VerificationToken token = generator.generate();

        // 32 bytes en Base64 sin padding = 43 caracteres.
        assertThat(token.value()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(token.hash()).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void neverRepeatsATokenAndTheHashDoesNotRevealIt() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            VerificationToken token = generator.generate();
            assertThat(values.add(token.value())).isTrue();
            assertThat(token.hash()).doesNotContain(token.value());
        }
    }

    @Test
    void hashIsDeterministicSoAReceivedTokenCanBeMatched() {
        VerificationToken token = generator.generate();

        assertThat(generator.hash(token.value())).isEqualTo(token.hash());
        assertThat(generator.hash(token.value() + "x")).isNotEqualTo(token.hash());
    }

    @Test
    void rejectsABlankToken() {
        assertThatIllegalArgumentException().isThrownBy(() -> generator.hash("  "));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.hash(null));
    }
}

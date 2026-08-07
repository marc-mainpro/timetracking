package com.tfp.timetracking.tenant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RF-REG-003: la IP se compara por huella, nunca se guarda en claro. */
class Sha256IpHasherTest {

    private final Sha256IpHasher hasher = new Sha256IpHasher("pepper-de-test");

    @Test
    void hashesTheSameIpToTheSameOpaqueValue() {
        assertThat(hasher.hash("203.0.113.10")).isEqualTo(hasher.hash(" 203.0.113.10 "));
        assertThat(hasher.hash("203.0.113.10")).hasSize(64).doesNotContain("203.0.113.10");
    }

    @Test
    void differentIpsProduceDifferentHashes() {
        assertThat(hasher.hash("203.0.113.10")).isNotEqualTo(hasher.hash("203.0.113.11"));
    }

    @Test
    void theSameIpUnderADifferentPepperIsNotComparable() {
        Sha256IpHasher other = new Sha256IpHasher("otro-pepper");

        assertThat(hasher.hash("203.0.113.10")).isNotEqualTo(other.hash("203.0.113.10"));
    }

    @Test
    void anUnknownIpHasNoHash() {
        assertThat(hasher.hash(null)).isNull();
        assertThat(hasher.hash("  ")).isNull();
    }

    @Test
    void anEmptyPepperFallsBackToARandomOneInsteadOfNone() {
        Sha256IpHasher unconfigured = new Sha256IpHasher("");
        Sha256IpHasher alsoUnconfigured = new Sha256IpHasher(null);

        assertThat(unconfigured.hash("203.0.113.10")).isNotNull();
        assertThat(unconfigured.hash("203.0.113.10")).isNotEqualTo(alsoUnconfigured.hash("203.0.113.10"));
    }
}

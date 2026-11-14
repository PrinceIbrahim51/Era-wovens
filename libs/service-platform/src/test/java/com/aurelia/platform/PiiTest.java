package com.aurelia.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelia.platform.security.GuestSession;
import com.aurelia.platform.web.Pii;
import org.junit.jupiter.api.Test;

class PiiTest {
    @Test
    void masksEmailAndPhone() {
        assertThat(Pii.maskEmail("priya@example.com")).isEqualTo("p***@example.com");
        assertThat(Pii.maskEmail(null)).isEqualTo("***");
        assertThat(Pii.maskPhone("9876543210")).isEqualTo("******10");
    }

    @Test
    void guestSessionMustBeSha256Hex() {
        assertThat(GuestSession.isValid("a".repeat(64))).isTrue();
        assertThat(GuestSession.isValid("A".repeat(64))).isFalse();
        assertThat(GuestSession.isValid("abc")).isFalse();
        assertThat(GuestSession.isValid(null)).isFalse();
    }
}

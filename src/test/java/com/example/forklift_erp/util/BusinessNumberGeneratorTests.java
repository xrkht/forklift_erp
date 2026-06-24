package com.example.forklift_erp.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessNumberGeneratorTests {

    @Test
    void nextBuildsUppercasePrefixTimestampAndSuffix() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-22T15:08:09.123Z"), ZoneId.of("Asia/Shanghai"));

        String number = BusinessNumberGenerator.next("po", 6, fixedClock);

        assertThat(number).startsWith("PO-20260622230809123-");
        assertThat(number).matches("PO-20260622230809123-[0-9A-F]{6}");
    }

    @Test
    void nextRejectsInvalidSuffixLength() {
        assertThatThrownBy(() -> BusinessNumberGenerator.next("PO", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Business number suffix length must be between 1 and 32");
    }
}

package org.microsoft.qintelipass.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerificationCodeGeneratorTests {
    private final VerificationCodeGenerator generator = new VerificationCodeGenerator();

    @Test
    void generatesSixDigitNumericCodesWithSecureRandom() {
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < 50; index++) {
            String code = generator.numericCode(6);
            assertThat(code).matches("\\d{6}");
            codes.add(code);
        }

        assertThat(codes).hasSizeGreaterThan(1);
    }

    @Test
    void rejectsUnsupportedLengths() {
        assertThrows(IllegalArgumentException.class, () -> generator.numericCode(0));
        assertThrows(IllegalArgumentException.class, () -> generator.numericCode(10));
    }
}

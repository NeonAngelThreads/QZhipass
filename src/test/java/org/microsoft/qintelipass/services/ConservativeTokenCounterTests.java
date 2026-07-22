package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConservativeTokenCounterTests {
    private final ConservativeTokenCounter counter = new ConservativeTokenCounter();

    @Test
    void countsChineseAndLatinWithoutUsingCharacterCountAsTokenCount() {
        assertThat(counter.count("这是中文测试")).isEqualTo(6);
        assertThat(counter.count("abcdefgh")).isEqualTo(2);
    }

    @Test
    void truncatesOnUnicodeCodePointBoundary() {
        String truncated = counter.truncate("你好🙂世界", 3);
        assertThat(truncated).isEqualTo("你好");
    }

    @Test
    void reservesExtraTokensForEmojiAndUncommonUnicode() {
        assertThat(counter.count("🙂")).isEqualTo(2);
        assertThat(counter.count("é")).isEqualTo(2);
    }
}

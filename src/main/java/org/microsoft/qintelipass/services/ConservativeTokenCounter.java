package org.microsoft.qintelipass.services;

import org.springframework.stereotype.Component;

@Component
public class ConservativeTokenCounter implements TokenCounter {
    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        int latinRun = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isLatinWordCharacter(codePoint)) {
                latinRun++;
                continue;
            }
            if (latinRun > 0) {
                tokens += (latinRun + 3) / 4;
                latinRun = 0;
            }
            if (!Character.isWhitespace(codePoint)) {
                tokens += tokenCost(codePoint);
            }
        }
        if (latinRun > 0) {
            tokens += (latinRun + 3) / 4;
        }
        return tokens;
    }

    @Override
    public String truncate(String text, int maxTokens) {
        if (text == null || maxTokens <= 0) {
            return "";
        }
        if (count(text) <= maxTokens) {
            return text;
        }
        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (count(text.substring(0, end)) <= maxTokens) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, text.offsetByCodePoints(0, low));
    }

    private boolean isLatinWordCharacter(int codePoint) {
        return codePoint < 128 && (Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == '-');
    }

    private int tokenCost(int codePoint) {
        if (codePoint < 128 || isCommonCjk(codePoint)) {
            return 1;
        }
        // Emoji, supplementary CJK and uncommon Unicode frequently split into multiple model tokens.
        return 2;
    }

    private boolean isCommonCjk(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF);
    }
}

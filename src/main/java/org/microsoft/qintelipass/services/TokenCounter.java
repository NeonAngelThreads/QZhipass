package org.microsoft.qintelipass.services;

public interface TokenCounter {
    int count(String text);

    String truncate(String text, int maxTokens);
}

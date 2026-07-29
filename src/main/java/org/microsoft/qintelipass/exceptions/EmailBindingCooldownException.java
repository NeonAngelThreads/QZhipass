package org.microsoft.qintelipass.exceptions;

import org.microsoft.qintelipass.services.EmailBindingMessages;
import org.springframework.http.HttpStatus;

public class EmailBindingCooldownException extends ApiException {
    private final long cooldownSeconds;

    public EmailBindingCooldownException(long cooldownSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, EmailBindingMessages.CODE_RATE_LIMITED);
        this.cooldownSeconds = cooldownSeconds;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }
}

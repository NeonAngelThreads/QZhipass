package org.microsoft.qintelipass.exceptions;

import org.microsoft.qintelipass.services.EmailBindingMessages;
import org.springframework.http.HttpStatus;

public class EmailBindingCacheException extends ApiException {
    public EmailBindingCacheException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, EmailBindingMessages.CACHE_UNAVAILABLE);
        initCause(cause);
    }
}
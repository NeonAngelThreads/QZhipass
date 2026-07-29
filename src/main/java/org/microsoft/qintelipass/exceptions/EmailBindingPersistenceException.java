package org.microsoft.qintelipass.exceptions;

import org.microsoft.qintelipass.services.EmailBindingMessages;
import org.springframework.http.HttpStatus;

public class EmailBindingPersistenceException extends ApiException {
    public EmailBindingPersistenceException(Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, EmailBindingMessages.PERSISTENCE_FAILED);
        initCause(cause);
    }
}

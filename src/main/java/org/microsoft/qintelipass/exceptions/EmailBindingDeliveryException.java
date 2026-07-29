package org.microsoft.qintelipass.exceptions;

import org.microsoft.qintelipass.services.EmailBindingMessages;
import org.springframework.http.HttpStatus;

public class EmailBindingDeliveryException extends ApiException {
    public EmailBindingDeliveryException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, EmailBindingMessages.CODE_DELIVERY_FAILED);
    }

    public EmailBindingDeliveryException(Throwable cause) {
        this();
        initCause(cause);
    }
}

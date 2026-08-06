package org.microsoft.qintelipass.exceptions;

import org.springframework.http.HttpStatus;

public class AiProviderException extends ApiException {
    public AiProviderException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}

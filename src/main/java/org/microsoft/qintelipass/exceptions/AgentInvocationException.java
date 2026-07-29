package org.microsoft.qintelipass.exceptions;

import org.springframework.http.HttpStatus;

public class AgentInvocationException extends ApiException {
    public AgentInvocationException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}

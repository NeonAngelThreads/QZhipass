package org.microsoft.qintelipass.dtos.response;

public record EmailBindingVerifyResponse(
        boolean bound,
        String email
) {
}
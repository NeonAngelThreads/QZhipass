package org.microsoft.qintelipass.response;

public record EmailBindingVerifyResponse(
        boolean bound,
        String email
) {
}

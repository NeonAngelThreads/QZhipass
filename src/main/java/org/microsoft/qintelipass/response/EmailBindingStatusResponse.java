package org.microsoft.qintelipass.response;

public record EmailBindingStatusResponse(
        boolean bound,
        String email,
        long cooldownSeconds
) {
}

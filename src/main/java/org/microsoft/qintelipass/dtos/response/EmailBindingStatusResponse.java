package org.microsoft.qintelipass.dtos.response;

public record EmailBindingStatusResponse(
        boolean bound,
        String email,
        long cooldownSeconds
) {
}
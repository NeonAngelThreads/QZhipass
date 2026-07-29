package org.microsoft.qintelipass.response;

public record EmailBindingSendCodeResponse(
        long expiresInSeconds,
        long cooldownSeconds
) {
}

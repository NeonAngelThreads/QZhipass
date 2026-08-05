package org.microsoft.qintelipass.dtos.response;


public record EmailBindingSendCodeResponse(
        long expiresInSeconds,
        long cooldownSeconds
) {
}
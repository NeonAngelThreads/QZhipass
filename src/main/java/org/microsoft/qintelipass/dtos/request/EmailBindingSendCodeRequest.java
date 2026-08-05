package org.microsoft.qintelipass.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.microsoft.qintelipass.services.auth.EmailBindingMessages;


public record EmailBindingSendCodeRequest(
        @NotBlank(message = EmailBindingMessages.EMAIL_UNAVAILABLE)
        @Size(max = 254, message = EmailBindingMessages.EMAIL_UNAVAILABLE)
        String email
) {
}
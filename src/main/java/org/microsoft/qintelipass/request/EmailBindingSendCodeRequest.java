package org.microsoft.qintelipass.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.microsoft.qintelipass.services.EmailBindingMessages;

public record EmailBindingSendCodeRequest(
        @NotBlank(message = EmailBindingMessages.EMAIL_UNAVAILABLE)
        @Size(max = 254, message = EmailBindingMessages.EMAIL_UNAVAILABLE)
        String email
) {
}

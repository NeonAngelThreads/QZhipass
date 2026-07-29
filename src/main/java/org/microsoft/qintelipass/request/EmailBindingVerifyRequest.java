package org.microsoft.qintelipass.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.microsoft.qintelipass.services.EmailBindingMessages;

public record EmailBindingVerifyRequest(
        @NotBlank(message = EmailBindingMessages.EMAIL_UNAVAILABLE)
        @Size(max = 254, message = EmailBindingMessages.EMAIL_UNAVAILABLE)
        String email,
        @NotBlank(message = EmailBindingMessages.CODE_INCORRECT)
        @Pattern(regexp = "\\d{6}", message = EmailBindingMessages.CODE_INCORRECT)
        String code
) {
}

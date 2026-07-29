package org.microsoft.qintelipass.exceptions;

import org.microsoft.qintelipass.response.ApiResponse;
import org.microsoft.qintelipass.response.EmailBindingCooldownData;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EmailBindingCooldownException.class)
    public ResponseEntity<ApiResponse<EmailBindingCooldownData>> handleEmailBindingCooldown(
            EmailBindingCooldownException exception
    ) {
        return ResponseEntity
                .status(exception.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getCooldownSeconds()))
                .body(new ApiResponse<>(
                        false,
                        exception.getMessage(),
                        new EmailBindingCooldownData(exception.getCooldownSeconds())
                ));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflictException(ConflictException exception) {
        return ResponseEntity
                .status(org.springframework.http.HttpStatus.CONFLICT)
                .body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
                ? "Request validation failed."
                : exception.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }
}

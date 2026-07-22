package org.microsoft.qintelipass.exceptions;

import org.microsoft.qintelipass.response.ApiResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(exception.getMessage()));
    }

    @ExceptionHandler({RedisConnectionFailureException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleRedisUnavailable(RuntimeException exception) {
        return ResponseEntity
                .status(503)
                .body(ApiResponse.error("缓存服务暂时不可用，请稍后重试"));
    }
}

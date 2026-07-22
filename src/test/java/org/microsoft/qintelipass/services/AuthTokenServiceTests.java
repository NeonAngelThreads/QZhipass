package org.microsoft.qintelipass.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTests {
    private static final String SECRET = "a-local-test-secret-that-is-longer-than-32-bytes";

    @Mock
    private RedisService redisService;

    @Test
    void issuesSignedJwtAndRequiresMatchingRedisSession() {
        AuthTokenService service = new AuthTokenService(redisService, SECRET, 60_000);
        String token = service.issueToken(1001L);
        when(redisService.getValue(anyString())).thenReturn("1001");

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(service.resolveUserId(token)).isEqualTo(Optional.of(1001L));
        assertThat(service.resolveUserId(token + "tampered")).isEmpty();
    }

    @Test
    void rejectsWeakJwtSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthTokenService(redisService, "too-short", 60_000));
    }
}

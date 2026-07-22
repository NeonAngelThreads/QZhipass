package org.microsoft.qintelipass.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTests {
    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private HttpServletRequest request;

    @Test
    void rejectsMissingAccessToken() {
        CurrentUserService service = new CurrentUserService(authTokenService);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeader("X-Access-Token")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> service.requireUserId(request));
    }

    @Test
    void resolvesBearerTokenToCurrentUser() {
        CurrentUserService service = new CurrentUserService(authTokenService);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-1");
        when(authTokenService.resolveUserId("token-1")).thenReturn(Optional.of(1001L));

        assertEquals(1001L, service.requireUserId(request));
    }

    @Test
    void resolvesCookieTokenToCurrentUser() {
        CurrentUserService service = new CurrentUserService(authTokenService);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeader("X-Access-Token")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("access_token", "token-2")});
        when(authTokenService.resolveUserId("token-2")).thenReturn(Optional.of(1002L));

        assertEquals(1002L, service.requireUserId(request));
    }

    @Test
    void rejectsInvalidAccessToken() {
        CurrentUserService service = new CurrentUserService(authTokenService);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-3");
        when(authTokenService.resolveUserId("token-3")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.requireUserId(request));
    }
}

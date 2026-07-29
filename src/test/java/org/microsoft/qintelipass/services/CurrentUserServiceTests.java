package org.microsoft.qintelipass.services;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.exceptions.UnauthorizedException;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.util.JwtUtil;
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
    private JwtUtil jwtUtil;

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    @Test
    void rejectsMissingAccessToken() {
        CurrentUserService service = service();
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeader("X-Access-Token")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> service.requireUserId(request));
    }

    @Test
    void resolvesBearerTokenToCurrentUser() {
        CurrentUserService service = service();
        when(request.getHeader("Authorization")).thenReturn("Bearer token-1");
        when(authTokenService.resolveUserId("token-1")).thenReturn(Optional.of(1001L));

        assertEquals(1001L, service.requireUserId(request));
    }

    @Test
    void resolvesCookieTokenToCurrentUser() {
        CurrentUserService service = service();
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeader("X-Access-Token")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("access_token", "token-2")});
        when(authTokenService.resolveUserId("token-2")).thenReturn(Optional.of(1002L));

        assertEquals(1002L, service.requireUserId(request));
    }

    @Test
    void rejectsInvalidAccessToken() {
        CurrentUserService service = service();
        when(request.getHeader("Authorization")).thenReturn("Bearer token-3");
        when(authTokenService.resolveUserId("token-3")).thenReturn(Optional.empty());
        when(jwtUtil.validateToken("token-3")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> service.requireUserId(request));
    }

    @Test
    void resolvesPortalJwtWithoutRedisSessionByUsername() throws Exception {
        CurrentUserService service = service();
        User user = User.builder().id(1003L).name("alice").build();
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getHeader("X-Access-Token")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("access_token", "portal-token")});
        when(authTokenService.resolveUserId("portal-token")).thenReturn(Optional.empty());
        when(jwtUtil.validateToken("portal-token")).thenReturn(true);
        when(jwtUtil.extractUserId("portal-token")).thenReturn(null);
        when(jwtUtil.extractUsername("portal-token")).thenReturn("alice");
        when(userService.findByUsername("alice")).thenReturn(user);

        assertEquals(1003L, service.requireUserId(request));
    }

    @Test
    void portalJwtFallbackStillWorksWhenRedisIsUnavailable() throws Exception {
        CurrentUserService service = service();
        User user = User.builder().id(1004L).name("bob").build();
        when(request.getHeader("Authorization")).thenReturn("Bearer portal-token");
        when(authTokenService.resolveUserId("portal-token"))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(jwtUtil.validateToken("portal-token")).thenReturn(true);
        when(jwtUtil.extractUserId("portal-token")).thenReturn(null);
        when(jwtUtil.extractUsername("portal-token")).thenReturn("bob");
        when(userService.findByUsername("bob")).thenReturn(user);

        assertEquals(1004L, service.requireUserId(request));
    }

    @Test
    void doesNotBypassRedisRevocationForUserIdTokens() throws Exception {
        CurrentUserService service = service();
        when(request.getHeader("Authorization")).thenReturn("Bearer revoked-session-token");
        when(authTokenService.resolveUserId("revoked-session-token")).thenReturn(Optional.empty());
        when(jwtUtil.validateToken("revoked-session-token")).thenReturn(true);
        when(jwtUtil.extractUserId("revoked-session-token")).thenReturn(1005L);

        assertThrows(UnauthorizedException.class, () -> service.requireUserId(request));
    }

    private CurrentUserService service() {
        return new CurrentUserService(authTokenService, jwtUtil, userService);
    }
}

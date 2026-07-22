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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTests {
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserService userService;
    @Mock
    private HttpServletRequest request;

    @Test
    void rejectsMissingAccessToken() {
        CurrentUserService service = new CurrentUserService(jwtUtil, userService);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> service.requireUserId(request));
    }

    @Test
    void resolvesBearerJwtToCurrentUser() throws Exception {
        CurrentUserService service = new CurrentUserService(jwtUtil, userService);
        User user = new User();
        user.setId(1001L);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-1");
        when(jwtUtil.validateToken("token-1")).thenReturn(true);
        when(jwtUtil.extractUserId("token-1")).thenReturn(1001L);
        when(userService.getUserById(1001L)).thenReturn(user);

        assertEquals(1001L, service.requireUserId(request));
    }

    @Test
    void resolvesCookieJwtToCurrentUser() throws Exception {
        CurrentUserService service = new CurrentUserService(jwtUtil, userService);
        User user = new User();
        user.setId(1002L);
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie("access_token", "token-2")});
        when(jwtUtil.validateToken("token-2")).thenReturn(true);
        when(jwtUtil.extractUserId("token-2")).thenReturn(1002L);
        when(userService.getUserById(1002L)).thenReturn(user);

        assertEquals(1002L, service.requireUserId(request));
    }

    @Test
    void rejectsInvalidAccessToken() {
        CurrentUserService service = new CurrentUserService(jwtUtil, userService);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-3");
        when(jwtUtil.validateToken("token-3")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> service.requireUserId(request));
    }
}

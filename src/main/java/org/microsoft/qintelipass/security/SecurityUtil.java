package org.microsoft.qintelipass.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

@Slf4j
public class SecurityUtil {
    private SecurityUtil() {
    }
    public static AuthenticatedUser getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser)) {
            log.warn("No authenticated user found");
            return null;
        }
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public static Long getCurrentUserId() {
        AuthenticatedUser user = getCurrentAuthenticatedUser();
        return user != null ? user.getUserId() : null;
    }

    public static UserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            log.warn("No authenticated user found");
            return null;
        }
        return (UserDetails) authentication.getPrincipal();
    }

    public static String getCurrentUsername() {
        UserDetails userDetails = getCurrentUserDetails();
        return userDetails != null ? userDetails.getUsername() : null;
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() &&
                !(authentication.getPrincipal() instanceof String) &&
                getCurrentUserId() != null;
    }

    public static void requireAuthentication() {
        if (!isAuthenticated()) {
            throw new SecurityException("User not authenticated");
        }
    }

    public static boolean isAdmin() {
        AuthenticatedUser user = getCurrentAuthenticatedUser();
        return user != null && user.getRole() != null
                && "ADMIN".equals(user.getRole().name());
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new SecurityException("Admin permission required");
        }
    }
}

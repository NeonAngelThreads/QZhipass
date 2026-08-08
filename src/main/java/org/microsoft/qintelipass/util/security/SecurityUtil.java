package org.microsoft.qintelipass.util.security;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.exceptions.UserNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityUtil {
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
        if (user != null){
            return user.getUserId();
        }
        throw new UserNotFoundException("你未登录哦~ 找不到该user_id");
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
        log.info("Auth Object: {}", authentication);
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
        log.info("User:{}", user);
        return user != null && user.getRole() != null
                && "ADMIN".equals(user.getRole().getName());
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new SecurityException("Admin permission required");
        }
    }
}

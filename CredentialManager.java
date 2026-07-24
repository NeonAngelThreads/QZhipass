package org.microsoft.qintelipass;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CredentialManager {
    @Autowired
    private UserService userService;
    private final JwtUtil jwtUtil;
    @Autowired
    public CredentialManager(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public boolean checkIfLogin(String token){
        try {
            return this.userService.findByUsername(jwtUtil.extractUsername(token)) != null;
        } catch (Exception e) {
            log.info("Token is invalid: {}, Exception: {}", token, e.getMessage());
        }
        return false;
    }
}

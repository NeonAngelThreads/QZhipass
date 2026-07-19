package org.microsoft.qintelipass.services;

import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userService.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
        return AuthenticatedUser.builder()
                .userId(user.getId())
                .username(user.getName())
                .password(user.getPasswordHash())
                .role(user.getRole())
                .build();
    }
}
package com.example.platform.security;

import com.example.platform.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static User get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return null;
        }
        return (User) authentication.getPrincipal();
    }

    public static Long id() {
        User user = get();
        return user == null ? null : user.getId();
    }

    public static boolean isAdmin() {
        User user = get();
        return user != null && "ADMIN".equals(user.getRole());
    }
}

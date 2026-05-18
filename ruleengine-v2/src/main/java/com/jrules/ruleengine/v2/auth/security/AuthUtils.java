package com.jrules.ruleengine.v2.auth.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtils {

    private AuthUtils() {}

    public static AuthenticatedUser currentUser() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        if (principal instanceof AuthenticatedUser u) return u;
        throw new IllegalStateException("No authenticated user in security context");
    }

    public static String currentUserName() {
        return currentUser().name();
    }
}

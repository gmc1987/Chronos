package com.chronos.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("iamAuthorization")
public class IamAuthorization {
    public boolean has(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        return authentication.getAuthorities().stream().anyMatch(authority -> {
            String value = authority.getAuthority();
            if (permission.equals(value) || "iam:*".equals(value) || "*:*".equals(value)) return true;
            return "ROLE_CODE_SUPER_ADMIN".equals(value);
        });
    }
}

package com.chronos.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("iamAuthorization")
public class IamAuthorization {
    public boolean has(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        return authentication.getAuthorities().stream().anyMatch(authority -> {
            String value = authority.getAuthority();
            if (permission.equals(value) || "*:*".equals(value)) return true;
            if (value != null && value.endsWith(":*") && permission.startsWith(value.substring(0, value.length() - 1))) return true;
            return "ROLE_CODE_SUPER_ADMIN".equals(value);
        });
    }

    public boolean any(Authentication authentication, String... permissions) {
        if (permissions == null) return false;
        for (String permission : permissions) if (has(authentication, permission)) return true;
        return false;
    }
}

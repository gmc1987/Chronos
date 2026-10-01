package com.chronos.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.ITemporaryGrantRepository;

@Component("iamAuthorization")
public class IamAuthorization {
    private final IAdminUserRepository users;
    private final ITemporaryGrantRepository grants;

    public IamAuthorization(IAdminUserRepository users, ITemporaryGrantRepository grants) {
        this.users = users;
        this.grants = grants;
    }

    public boolean has(Authentication authentication, String permission) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        boolean highRisk = permission != null
                && (permission.startsWith("iam:temporary-grant:")
                        || permission.endsWith(":high-risk")
                        || permission.equals("iam:access-review:complete"));
        if (highRisk && authentication.getAuthorities().stream()
                .noneMatch(authority -> "MFA_VERIFIED".equals(authority.getAuthority()))) {
            return false;
        }
        boolean direct = authentication.getAuthorities().stream().anyMatch(authority -> {
            String value = authority.getAuthority();
            if (permission.equals(value) || "*:*".equals(value)) return true;
            if (value != null && value.endsWith(":*") && permission.startsWith(value.substring(0, value.length() - 1))) return true;
            return "ROLE_CODE_SUPER_ADMIN".equals(value);
        });
        if (direct) return true;
        var user = users.findByUsername(authentication.getName());
        if (user == null) return false;
        LocalDateTime now = LocalDateTime.now();
        return !grants.findByUserIdAndPermissionCodeAndStatusAndValidFromLessThanEqualAndValidUntilGreaterThan(
                user.getId(), permission, "ACTIVE", now, now).isEmpty()
                || !grants.findByUserIdAndPermissionCodeAndStatusAndValidFromLessThanEqualAndValidUntilGreaterThan(
                user.getId(), permission, "APPROVED", now, now).isEmpty();
    }

    public boolean any(Authentication authentication, String... permissions) {
        if (permissions == null) return false;
        for (String permission : permissions) if (has(authentication, permission)) return true;
        return false;
    }
}

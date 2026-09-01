package com.chronos.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.commons.model.ResultData;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IRefreshTokenService;

@RestController
@RequestMapping("/auth/account")
public class AccountSecurityController {
    private final IAdminUserRepository users; private final PasswordEncoder encoder;
    private final IRefreshTokenService refreshTokens; private final IAuditLogService audit;
    public AccountSecurityController(IAdminUserRepository users,PasswordEncoder encoder,IRefreshTokenService refreshTokens,IAuditLogService audit){this.users=users;this.encoder=encoder;this.refreshTokens=refreshTokens;this.audit=audit;}
    @PostMapping("/change-password") @Transactional
    public ResultData<Void> changePassword(Principal principal,@RequestBody Map<String,String> body){
        var user=users.findByUsername(principal.getName());String oldPassword=body.get("oldPassword"),newPassword=body.get("newPassword");
        if(user==null||!encoder.matches(oldPassword,user.getPassword()))throw new IllegalArgumentException("原密码不正确");
        validatePassword(newPassword);user.setPassword(encoder.encode(newPassword));user.setPasswordChangedAt(LocalDateTime.now());user.setMustChangePassword(false);user.setTokenVersion(nextVersion(user.getTokenVersion()));users.save(user);refreshTokens.revokeAll(user.getUsername());audit.log(user.getUsername(),"PASSWORD_CHANGE","password changed and sessions revoked");return ok();
    }
    @PostMapping("/unlock") @Transactional @PreAuthorize("@iamAuthorization.has(authentication, 'iam:user:manage')")
    public ResultData<Void> unlock(@RequestParam String userId){var user=users.findById(userId).orElseThrow(()->new IllegalArgumentException("user not found"));user.setAccountLocked(false);user.setFailedLoginAttempts(0);user.setLockUntil(null);users.save(user);audit.log(user.getUsername(),"ACCOUNT_UNLOCK","account unlocked");return ok();}
    @PostMapping("/force-logout") @PreAuthorize("@iamAuthorization.has(authentication, 'iam:user:manage')")
    public ResultData<Void> forceLogout(@RequestParam String userId){var user=users.findById(userId).orElseThrow(()->new IllegalArgumentException("user not found"));user.setTokenVersion(nextVersion(user.getTokenVersion()));users.save(user);refreshTokens.revokeAll(user.getUsername());audit.log(user.getUsername(),"FORCE_LOGOUT","all tokens revoked");return ok();}
    @PostMapping("/reset-password") @Transactional @PreAuthorize("@iamAuthorization.has(authentication, 'iam:user:manage')")
    public ResultData<Void> resetPassword(@RequestBody Map<String,String> body){var user=users.findById(body.get("userId")).orElseThrow(()->new IllegalArgumentException("user not found"));String password=body.get("newPassword");validatePassword(password);user.setPassword(encoder.encode(password));user.setPasswordChangedAt(LocalDateTime.now());user.setMustChangePassword(true);user.setTokenVersion(nextVersion(user.getTokenVersion()));users.save(user);refreshTokens.revokeAll(user.getUsername());audit.log(user.getUsername(),"PASSWORD_RESET","password reset by administrator");return ok();}
    private void validatePassword(String value){if(value==null||value.length()<10||!value.matches(".*[A-Z].*")||!value.matches(".*[a-z].*")||!value.matches(".*\\d.*"))throw new IllegalArgumentException("密码至少10位，并包含大小写字母和数字");}
    private int nextVersion(Integer current){return (current==null?0:current)+1;}
    private ResultData<Void> ok(){return ResultData.<Void>builder().code("200").msg("ok").data(null).build();}
}

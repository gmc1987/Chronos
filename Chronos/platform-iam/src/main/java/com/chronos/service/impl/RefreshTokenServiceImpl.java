 package com.chronos.service.impl;
 
 import com.chronos.Idao.IRefreshTokenRepository;
 import com.chronos.model.pojo.RefreshToken;
 import com.chronos.service.iService.IRefreshTokenService;
 import java.time.LocalDateTime;
 import java.util.Optional;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 
 
 
 @Service("refreshTokenService")
 public class RefreshTokenServiceImpl
   implements IRefreshTokenService
 {
   @Autowired
   private IRefreshTokenRepository refreshTokenRepository;
   
   @Transactional
   public RefreshToken create(String token, String username, LocalDateTime expiryTime) {
     RefreshToken rt = new RefreshToken();
     rt.setToken(token);
     rt.setUsername(username);
     rt.setExpiryTime(expiryTime);
     rt.setRevoked(Boolean.valueOf(false));
     rt.setCreateTime(LocalDateTime.now());
     return (RefreshToken)this.refreshTokenRepository.save(rt);
   }
 
   
   public RefreshToken findByToken(String token) {
     Optional<RefreshToken> opt = this.refreshTokenRepository.findByToken(token);
     return opt.orElse(null);
   }
 
   
   @Transactional
  public void revoke(String token) {
     Optional<RefreshToken> opt = this.refreshTokenRepository.findByToken(token);
     if (opt.isPresent()) {
       RefreshToken rt = opt.get();
       rt.setRevoked(Boolean.valueOf(true));
       this.refreshTokenRepository.save(rt);
     }
  }

  @Transactional
  public void revokeAll(String username) {
    var tokens = refreshTokenRepository.findByUsernameAndRevokedFalse(username);
    tokens.forEach(token -> token.setRevoked(true));
    refreshTokenRepository.saveAll(tokens);
  }
 }

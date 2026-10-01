package com.chronos.service.iService;

import com.chronos.model.pojo.RefreshToken;
import java.time.LocalDateTime;

public interface IRefreshTokenService {
  RefreshToken create(String paramString1, String paramString2, LocalDateTime paramLocalDateTime);
  
  RefreshToken findByToken(String paramString);
  
  RefreshToken rotate(String presentedToken, String username, String replacementToken,
      LocalDateTime replacementExpiry);

  void revoke(String paramString);
  void revokeAll(String username);
}

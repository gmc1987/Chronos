package com.chronos.service.iService;

import com.chronos.model.pojo.RefreshToken;
import java.time.LocalDateTime;

public interface IRefreshTokenService {
  RefreshToken create(String paramString1, String paramString2, LocalDateTime paramLocalDateTime);
  
  RefreshToken findByToken(String paramString);
  
  void revoke(String paramString);
}



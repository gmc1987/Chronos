package com.chronos.Idao;

import com.chronos.model.pojo.RefreshToken;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("refreshTokenRepository")
public interface IRefreshTokenRepository extends JpaRepository<RefreshToken, String> {
  Optional<RefreshToken> findByToken(String paramString);
  List<RefreshToken> findByUsernameAndRevokedFalse(String username);
}

package com.chronos.Idao;

import com.chronos.model.pojo.RefreshToken;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Repository("refreshTokenRepository")
public interface IRefreshTokenRepository extends JpaRepository<RefreshToken, String> {
  Optional<RefreshToken> findByToken(String paramString);
  List<RefreshToken> findByUsernameAndRevokedFalse(String username);

  @Modifying
  @Transactional
  @Query("update RefreshToken token set token.revoked = true "
      + "where token.token = :token and token.username = :username and token.revoked = false")
  int revokeActiveDigest(@Param("token") String token, @Param("username") String username);
}

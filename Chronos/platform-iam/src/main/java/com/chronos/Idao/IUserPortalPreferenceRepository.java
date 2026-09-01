package com.chronos.Idao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.UserPortalPreference;

public interface IUserPortalPreferenceRepository extends JpaRepository<UserPortalPreference, String> {
    Optional<UserPortalPreference> findByUsername(String username);
}

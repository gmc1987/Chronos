package com.chronos.Idao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.UserPortalApplication;

public interface IUserPortalApplicationRepository extends JpaRepository<UserPortalApplication, String> {
    Optional<UserPortalApplication> findByUsernameAndApplicationId(String username, String applicationId);
    List<UserPortalApplication> findByUsernameAndFavoriteTrueOrderByFavoriteOrderAsc(String username);
    List<UserPortalApplication> findTop8ByUsernameAndLastVisitedAtIsNotNullOrderByLastVisitedAtDesc(String username);
}

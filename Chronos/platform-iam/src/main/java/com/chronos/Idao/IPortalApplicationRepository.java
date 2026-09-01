package com.chronos.Idao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.PortalApplication;

public interface IPortalApplicationRepository extends JpaRepository<PortalApplication, String> {
    List<PortalApplication> findByEnabledTrueOrderBySortOrderAsc();
    Optional<PortalApplication> findByAppCode(String appCode);
}

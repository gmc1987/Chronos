package com.chronos.Idao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.PortalWidget;

public interface IPortalWidgetRepository extends JpaRepository<PortalWidget, String> {
    List<PortalWidget> findByEnabledTrueOrderBySortOrderAsc();
    Optional<PortalWidget> findByWidgetCode(String widgetCode);
}

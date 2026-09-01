package com.chronos.service.iService;

import java.util.List;
import java.util.Map;
import com.chronos.model.pojo.PortalApplication;
import com.chronos.model.pojo.PortalWidget;

public interface IPortalService {
    Map<String, Object> userContext(String username);
    List<Map<String, Object>> applications(String username);
    List<Map<String, Object>> widgets(String username);
    Map<String, Object> preference(String username);
    Map<String, Object> savePreference(String username, Map<String, Object> request);
    List<Map<String, Object>> favorites(String username);
    void favorite(String username, String applicationId, boolean favorite);
    void recordVisit(String username, String applicationId);
    List<Map<String, Object>> recentVisits(String username);
    List<PortalApplication> allApplications();
    PortalApplication saveApplication(PortalApplication application);
    void deleteApplication(String id);
    List<PortalWidget> allWidgets();
    PortalWidget saveWidget(PortalWidget widget);
    void deleteWidget(String id);
}

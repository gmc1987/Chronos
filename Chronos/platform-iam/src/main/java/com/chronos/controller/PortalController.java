package com.chronos.controller;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.chronos.commons.model.ResultData;
import com.chronos.model.pojo.PortalApplication;
import com.chronos.model.pojo.PortalWidget;
import com.chronos.service.iService.IPortalService;
import com.chronos.service.iService.IDataScopeService;

@RestController
@RequestMapping("/portal")
public class PortalController {
    private final IPortalService portalService;
    private final IDataScopeService dataScopeService;

    public PortalController(IPortalService portalService, IDataScopeService dataScopeService) { this.portalService = portalService; this.dataScopeService = dataScopeService; }

    @GetMapping("/context")
    public ResultData<Object> context(Principal principal) { return ok(portalService.userContext(principal.getName())); }

    @GetMapping("/data-scope")
    public ResultData<Object> dataScope(Principal principal) { return ok(dataScopeService.resolve(principal.getName())); }

    @GetMapping("/applications")
    public ResultData<Object> applications(Principal principal) { return ok(portalService.applications(principal.getName())); }

    @GetMapping("/widgets")
    public ResultData<Object> widgets(Principal principal) { return ok(portalService.widgets(principal.getName())); }

    @GetMapping("/preference")
    public ResultData<Object> preference(Principal principal) { return ok(portalService.preference(principal.getName())); }

    @PutMapping("/preference")
    public ResultData<Object> savePreference(Principal principal, @RequestBody Map<String, Object> request) {
        return ok(portalService.savePreference(principal.getName(), request));
    }

    @PostMapping("/preference/reset")
    public ResultData<Object> resetPreference(Principal principal) {
        return ok(portalService.savePreference(principal.getName(), Map.of("theme", "LIGHT",
                "layout", java.util.List.of("quick-entry", "todo", "announcement", "notice", "meeting", "supervision", "ai-assistant", "data-card"))));
    }

    @GetMapping("/favorites")
    public ResultData<Object> favorites(Principal principal) { return ok(portalService.favorites(principal.getName())); }

    @PutMapping("/favorites/{applicationId}")
    public ResultData<Object> favorite(Principal principal, @PathVariable String applicationId) {
        portalService.favorite(principal.getName(), applicationId, true); return ok(null);
    }

    @DeleteMapping("/favorites/{applicationId}")
    public ResultData<Object> unfavorite(Principal principal, @PathVariable String applicationId) {
        portalService.favorite(principal.getName(), applicationId, false); return ok(null);
    }

    @PostMapping("/applications/{applicationId}/visit")
    public ResultData<Object> visit(Principal principal, @PathVariable String applicationId) {
        portalService.recordVisit(principal.getName(), applicationId); return ok(null);
    }

    @GetMapping("/recent-visits")
    public ResultData<Object> recentVisits(Principal principal) { return ok(portalService.recentVisits(principal.getName())); }

    @GetMapping("/admin/applications")
    @PreAuthorize("@iamAuthorization.has(authentication, 'portal:manage')")
    public ResultData<Object> allApplications() { return ok(portalService.allApplications()); }

    @PostMapping("/admin/applications")
    @PreAuthorize("@iamAuthorization.has(authentication, 'portal:manage')")
    public ResultData<Object> createApplication(@RequestBody PortalApplication application) {
        application.setId(null); return ok(portalService.saveApplication(application));
    }

    @PutMapping("/admin/applications/{id}")
    @PreAuthorize("@iamAuthorization.has(authentication, 'portal:manage')")
    public ResultData<Object> updateApplication(@PathVariable String id, @RequestBody PortalApplication application) {
        application.setId(id); return ok(portalService.saveApplication(application));
    }

    @DeleteMapping("/admin/applications/{id}")
    @PreAuthorize("@iamAuthorization.has(authentication, 'portal:manage')")
    public ResultData<Object> deleteApplication(@PathVariable String id) { portalService.deleteApplication(id); return ok(null); }

    @GetMapping("/admin/widgets")
    @PreAuthorize("@iamAuthorization.has(authentication, 'portal:manage')")
    public ResultData<Object> allWidgets() { return ok(portalService.allWidgets()); }

    @PostMapping("/admin/widgets")
    @PreAuthorize("@iamAuthorization.has(authentication, 'portal:manage')")
    public ResultData<Object> createWidget(@RequestBody PortalWidget widget) { widget.setId(null); return ok(portalService.saveWidget(widget)); }

    @PutMapping("/admin/widgets/{id}")
    @PreAuthorize("@iamAuthorization.has(authentication, 'portal:manage')")
    public ResultData<Object> updateWidget(@PathVariable String id, @RequestBody PortalWidget widget) {
        widget.setId(id); return ok(portalService.saveWidget(widget));
    }

    @DeleteMapping("/admin/widgets/{id}")
    @PreAuthorize("@iamAuthorization.has(authentication, 'portal:manage')")
    public ResultData<Object> deleteWidget(@PathVariable String id) { portalService.deleteWidget(id); return ok(null); }

    private ResultData<Object> ok(Object data) {
        return ResultData.builder().code(String.valueOf(HttpStatus.OK.value())).msg("ok").data(data).build();
    }
}

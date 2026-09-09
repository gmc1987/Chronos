package com.chronos.controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.portal.spi.PortalContribution;
import com.chronos.portal.spi.PortalContributionProvider;
import com.chronos.service.iService.IPortalService;

/**
 * 聚合平台通用门户数据。
 *
 * 该控制器必须位于公共平台模块，确保医院、教育等行业应用都具备相同的门户入口。
 * 行业或业务模块只需通过 PortalContributionProvider 插件补充自己的数据。
 */
@RestController
@RequestMapping("/portal")
public class PortalAggregationController {

    private final IPortalService portalService;
    private final Map<String, PortalContributionProvider> providers;

    public PortalAggregationController(
            IPortalService portalService,
            List<PortalContributionProvider> providers) {
        this.portalService = portalService;
        this.providers = providers.stream()
                .collect(Collectors.toMap(
                        PortalContributionProvider::providerCode,
                        Function.identity(),
                        (first, second) -> first));
    }

    @GetMapping("/bootstrap")
    public ResultData<Object> bootstrap(Principal principal) {
        String username = principal.getName();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", portalService.userContext(username));
        data.put("applications", portalService.applications(username));
        data.put("widgets", portalService.widgets(username));
        data.put("preference", portalService.preference(username));
        data.put("favorites", portalService.favorites(username));
        data.put("recentVisits", portalService.recentVisits(username));
        data.put("contributions", contributions(username));
        return ok(data);
    }

    @GetMapping("/home")
    public ResultData<Object> home(Principal principal) {
        return ok(Map.of("contributions", contributions(principal.getName())));
    }

    private Map<String, PortalContribution> contributions(String username) {
        Map<String, PortalContribution> result = new LinkedHashMap<>();
        result.put("PORTAL", new PortalContribution("PORTAL", true, "ok", Map.of()));
        add(result, username, "WORKFLOW", "流程中心尚未接入，接口已预留");
        add(result, username, "ANNOUNCEMENT", "公告模块尚未接入，接口已预留");
        add(result, username, "MESSAGE", "消息中心尚未接入，接口已预留");
        add(result, username, "MEETING", "会议中心尚未接入，接口已预留");
        add(result, username, "SUPERVISION", "督办中心尚未接入，接口已预留");
        add(result, username, "AI", "AI 助手尚未接入，接口已预留");
        add(result, username, "DATA", "数据中心尚未接入，接口已预留");
        return result;
    }

    private void add(
            Map<String, PortalContribution> result,
            String username,
            String code,
            String fallback) {
        PortalContributionProvider provider = providers.get(code);
        if (provider == null) {
            result.put(code, PortalContribution.unavailable(code, fallback));
            return;
        }
        try {
            // 单个业务模块故障不能导致整个门户首页无法加载。
            result.put(code, provider.load(username));
        } catch (RuntimeException exception) {
            result.put(code, PortalContribution.unavailable(code, "模块数据暂时不可用"));
        }
    }

    private ResultData<Object> ok(Object data) {
        return ResultData.builder()
                .code(String.valueOf(HttpStatus.OK.value()))
                .msg("ok")
                .data(data)
                .build();
    }
}

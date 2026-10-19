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
        Map<String, PortalContribution> contributions = contributions(username);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", portalService.userContext(username));
        data.put("applications", applicationsWithBadges(
                portalService.applications(username), contributions));
        data.put("widgets", portalService.widgets(username));
        data.put("preference", portalService.preference(username));
        data.put("favorites", portalService.favorites(username));
        data.put("recentVisits", portalService.recentVisits(username));
        data.put("contributions", contributions);
        return ok(data);
    }

    private List<Map<String, Object>> applicationsWithBadges(
            List<Map<String, Object>> applications,
            Map<String, PortalContribution> contributions) {
        return applications.stream().map(application -> {
            Map<String, Object> value = new LinkedHashMap<>(application);
            String feature = String.valueOf(value.getOrDefault("nativeFeatureCode", ""));
            String provider = switch (feature) {
                case "WORKFLOW_TASKS" -> "WORKFLOW";
                case "MEETINGS" -> "MEETING";
                case "AI_CENTER" -> "AI";
                default -> null;
            };
            int badge = provider == null ? 0 : contributionCount(contributions.get(provider));
            value.put("badgeCount", badge);
            value.put("category", applicationCategory(feature));
            return value;
        }).toList();
    }

    private int contributionCount(PortalContribution contribution) {
        if (contribution == null || !contribution.available() || contribution.data() == null) {
            return 0;
        }
        Object total = contribution.data().get("total");
        if (total instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        Object items = contribution.data().get("items");
        return items instanceof List<?> list ? list.size() : 0;
    }

    private String applicationCategory(String feature) {
        return switch (feature) {
            case "WORKFLOW_TASKS", "COLLABORATION", "DOCUMENTS", "FILES", "BUSINESS_TRIPS" -> "协同办公";
            case "TEACHING_CENTER" -> "教学管理";
            case "MEETINGS" -> "会议与日程";
            case "KNOWLEDGE", "AI_CENTER" -> "智能服务";
            default -> "其他应用";
        };
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

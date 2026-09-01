package com.chronos.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IPortalApplicationRepository;
import com.chronos.Idao.IPortalWidgetRepository;
import com.chronos.Idao.IUserPortalApplicationRepository;
import com.chronos.Idao.IUserPortalPreferenceRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IPositionRepository;
import com.chronos.Idao.IJobLevelRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.PortalApplication;
import com.chronos.model.pojo.PortalWidget;
import com.chronos.model.pojo.UserPortalApplication;
import com.chronos.model.pojo.UserPortalPreference;
import com.chronos.service.iService.IPortalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;

@Service
@Transactional(readOnly = true)
public class PortalServiceImpl implements IPortalService {
    private static final String DEFAULT_LAYOUT = "[\"quick-entry\",\"todo\",\"announcement\",\"notice\",\"meeting\",\"supervision\",\"ai-assistant\",\"data-card\"]";
    private final IAdminUserRepository userRepository;
    private final IOrganizationRepository organizationRepository;
    private final IPortalApplicationRepository applicationRepository;
    private final IPortalWidgetRepository widgetRepository;
    private final IUserPortalPreferenceRepository preferenceRepository;
    private final IUserPortalApplicationRepository userApplicationRepository;
    private final IEmployeeRepository employeeRepository;private final IEmployeeAssignmentRepository assignmentRepository;
    private final IOrganizationUnitRepository unitRepository;private final IPositionRepository positionRepository;private final IJobLevelRepository levelRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${chronos.portal.external-hosts:}") private String externalHosts;

    public PortalServiceImpl(IAdminUserRepository userRepository, IOrganizationRepository organizationRepository,
            IPortalApplicationRepository applicationRepository, IPortalWidgetRepository widgetRepository,
            IUserPortalPreferenceRepository preferenceRepository,
            IUserPortalApplicationRepository userApplicationRepository,IEmployeeRepository employeeRepository,
            IEmployeeAssignmentRepository assignmentRepository,IOrganizationUnitRepository unitRepository,
            IPositionRepository positionRepository,IJobLevelRepository levelRepository) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.applicationRepository = applicationRepository;
        this.widgetRepository = widgetRepository;
        this.preferenceRepository = preferenceRepository;
        this.userApplicationRepository = userApplicationRepository;
        this.employeeRepository=employeeRepository;this.assignmentRepository=assignmentRepository;this.unitRepository=unitRepository;
        this.positionRepository=positionRepository;this.levelRepository=levelRepository;
    }

    @Override
    public Map<String, Object> userContext(String username) {
        AdminUser user = requireUser(username);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("displayName", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("avatarUrl", user.getAvatarUrl());
        data.put("organizationId", user.getOrganizationId());
        data.put("positionName", user.getPositionName());
        if (user.getOrganizationId() != null) {
            organizationRepository.findById(user.getOrganizationId()).ifPresent(org -> {
                data.put("organizationName", org.getOrganizationName());
                data.put("organizationCode", org.getOrgCode());
            });
        }
        if (user.getEmployeeId() != null) employeeRepository.findById(user.getEmployeeId()).ifPresent(employee -> {
            data.put("employeeId",employee.getId());data.put("employeeCode",employee.getEmployeeCode());data.put("displayName",employee.getEmployeeName());
            assignmentRepository.findCurrentPrimaryAssignment(employee.getId(),LocalDateTime.now().toLocalDate()).ifPresent(a -> {
                data.put("organizationId",a.getOrganizationId());organizationRepository.findById(a.getOrganizationId()).ifPresent(o->{data.put("organizationName",o.getOrganizationName());data.put("organizationCode",o.getOrgCode());});
                data.put("departmentId",a.getOrganizationUnitId());unitRepository.findById(a.getOrganizationUnitId()).ifPresent(u->data.put("departmentName",u.getOrganizationUnitName()));
                if(a.getPositionId()!=null)positionRepository.findById(a.getPositionId()).ifPresent(p->{data.put("positionId",p.getId());data.put("positionName",p.getPositionName());});
                if(a.getJobLevelId()!=null)levelRepository.findById(a.getJobLevelId()).ifPresent(l->{data.put("jobLevelId",l.getId());data.put("jobLevelName",l.getLevelName());});
            });
        });
        data.put("roles", user.getRoles().stream().map(r -> r.getRoleName()).sorted().toList());
        data.put("permissions", currentPermissions());
        return data;
    }

    @Override
    public List<Map<String, Object>> applications(String username) {
        Set<String> permissions = currentPermissions();
        Set<String> favorites = userApplicationRepository.findByUsernameAndFavoriteTrueOrderByFavoriteOrderAsc(username)
                .stream().map(UserPortalApplication::getApplicationId).collect(Collectors.toSet());
        return applicationRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .filter(app -> allowed(app.getRequiredPermission(), permissions))
                .filter(app -> audienceAllowed(username,app.getAudienceRoleCodes(),app.getAudienceOrganizationIds(),app.getAudienceDepartmentIds()))
                .map(app -> applicationMap(app, favorites.contains(app.getId()))).toList();
    }

    @Override
    public List<Map<String, Object>> widgets(String username) {
        Set<String> permissions = currentPermissions();
        return widgetRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .filter(widget -> allowed(widget.getRequiredPermission(), permissions))
                .filter(widget -> audienceAllowed(username,widget.getAudienceRoleCodes(),widget.getAudienceOrganizationIds(),widget.getAudienceDepartmentIds()))
                .map(this::widgetMap).toList();
    }

    @Override
    public Map<String, Object> preference(String username) {
        UserPortalPreference preference = preferenceRepository.findByUsername(username).orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("theme", preference == null ? "LIGHT" : preference.getTheme());
        Object raw=parseLayout(preference == null ? DEFAULT_LAYOUT : preference.getLayoutJson());List<String> allowedCodes=widgets(username).stream().map(w->String.valueOf(w.get("code"))).toList();List<String> layout=new ArrayList<>();if(raw instanceof List<?> list)for(Object item:list){String code=String.valueOf(item);if(allowedCodes.contains(code)&&!layout.contains(code))layout.add(code);}if(preference==null)for(String code:allowedCodes)if(!layout.contains(code))layout.add(code);result.put("layout",layout);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> savePreference(String username, Map<String, Object> request) {
        UserPortalPreference preference = preferenceRepository.findByUsername(username).orElseGet(() -> {
            UserPortalPreference value = new UserPortalPreference();
            value.setUsername(username);
            return value;
        });
        Object layout = validateLayout(username,request.getOrDefault("layout", parseLayout(DEFAULT_LAYOUT)));
        try {
            preference.setLayoutJson(objectMapper.writeValueAsString(layout));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid portal layout", e);
        }
        preference.setTheme(String.valueOf(request.getOrDefault("theme", "LIGHT")));
        preferenceRepository.save(preference);
        return preference(username);
    }

    @Override
    public List<Map<String, Object>> favorites(String username) {
        Map<String, PortalApplication> applications = applicationRepository.findAll().stream()
                .collect(Collectors.toMap(PortalApplication::getId, app -> app));
        Set<String> permissions=currentPermissions();return userApplicationRepository.findByUsernameAndFavoriteTrueOrderByFavoriteOrderAsc(username).stream()
                .map(item -> applications.get(item.getApplicationId())).filter(app -> app != null)
                .filter(app->Boolean.TRUE.equals(app.getEnabled())&&allowed(app.getRequiredPermission(),permissions)&&audienceAllowed(username,app.getAudienceRoleCodes(),app.getAudienceOrganizationIds(),app.getAudienceDepartmentIds()))
                .map(app -> applicationMap(app, true)).toList();
    }

    @Override
    @Transactional
    public void favorite(String username, String applicationId, boolean favorite) {
        requireAllowedApplication(applicationId);
        UserPortalApplication value = userApplicationRepository.findByUsernameAndApplicationId(username, applicationId)
                .orElseGet(() -> newUserApplication(username, applicationId));
        value.setFavorite(favorite);
        if (favorite && value.getFavoriteOrder() == null) value.setFavoriteOrder(0);
        userApplicationRepository.save(value);
    }

    @Override
    @Transactional
    public void recordVisit(String username, String applicationId) {
        requireAllowedApplication(applicationId);
        UserPortalApplication value = userApplicationRepository.findByUsernameAndApplicationId(username, applicationId)
                .orElseGet(() -> newUserApplication(username, applicationId));
        value.setLastVisitedAt(LocalDateTime.now());
        value.setVisitCount((value.getVisitCount() == null ? 0L : value.getVisitCount()) + 1);
        userApplicationRepository.save(value);
    }

    @Override
    public List<Map<String, Object>> recentVisits(String username) {
        Map<String, PortalApplication> applications = applicationRepository.findAll().stream()
                .collect(Collectors.toMap(PortalApplication::getId, app -> app));
        Set<String> permissions=currentPermissions();List<Map<String, Object>> result = new ArrayList<>();
        for (UserPortalApplication item : userApplicationRepository.findTop8ByUsernameAndLastVisitedAtIsNotNullOrderByLastVisitedAtDesc(username)) {
            PortalApplication app = applications.get(item.getApplicationId());
            if (app == null||!Boolean.TRUE.equals(app.getEnabled())||!allowed(app.getRequiredPermission(),permissions)||!audienceAllowed(username,app.getAudienceRoleCodes(),app.getAudienceOrganizationIds(),app.getAudienceDepartmentIds())) continue;
            Map<String, Object> mapped = new LinkedHashMap<>(applicationMap(app, Boolean.TRUE.equals(item.getFavorite())));
            mapped.put("lastVisitedAt", item.getLastVisitedAt());
            mapped.put("visitCount", item.getVisitCount());
            result.add(mapped);
        }
        return result;
    }

    @Override public List<PortalApplication> allApplications() { return applicationRepository.findAll(); }
    @Override @Transactional public PortalApplication saveApplication(PortalApplication app) {
        PortalApplication target = app.getId() == null ? new PortalApplication()
                : applicationRepository.findById(app.getId()).orElseThrow(() -> new IllegalArgumentException("application not found"));
        requireText(app.getAppCode(),"应用编码");requireText(app.getAppName(),"应用名称");if(target.getId()!=null&&!java.util.Objects.equals(target.getAppCode(),app.getAppCode()))throw new IllegalArgumentException("应用编码创建后不允许修改");validateRoute(app);applicationRepository.findByAppCode(app.getAppCode()).filter(v->!v.getId().equals(app.getId())).ifPresent(v->{throw new IllegalArgumentException("应用编码已存在");});
        target.setAppCode(app.getAppCode()); target.setAppName(app.getAppName()); target.setDescription(app.getDescription());
        target.setIcon(app.getIcon()); target.setRoutePath(app.getRoutePath()); target.setOpenMode(app.getOpenMode());
        target.setRequiredPermission(app.getRequiredPermission()); target.setSortOrder(app.getSortOrder());
        target.setEnabled(app.getEnabled()); target.setRecommended(app.getRecommended());
        target.setAudienceRoleCodes(app.getAudienceRoleCodes());target.setAudienceOrganizationIds(app.getAudienceOrganizationIds());target.setAudienceDepartmentIds(app.getAudienceDepartmentIds());
        return applicationRepository.save(target);
    }
    @Override @Transactional public void deleteApplication(String id) { applicationRepository.deleteById(id); }
    @Override public List<PortalWidget> allWidgets() { return widgetRepository.findAll(); }
    @Override @Transactional public PortalWidget saveWidget(PortalWidget widget) {
        PortalWidget target = widget.getId() == null ? new PortalWidget()
                : widgetRepository.findById(widget.getId()).orElseThrow(() -> new IllegalArgumentException("widget not found"));
        requireText(widget.getWidgetCode(),"Widget编码");requireText(widget.getWidgetName(),"Widget名称");requireText(widget.getComponentName(),"前端组件");if(target.getId()!=null&&!java.util.Objects.equals(target.getWidgetCode(),widget.getWidgetCode()))throw new IllegalArgumentException("Widget编码创建后不允许修改");widgetRepository.findByWidgetCode(widget.getWidgetCode()).filter(v->!v.getId().equals(widget.getId())).ifPresent(v->{throw new IllegalArgumentException("Widget编码已存在");});
        target.setWidgetCode(widget.getWidgetCode()); target.setWidgetName(widget.getWidgetName());
        target.setDescription(widget.getDescription()); target.setComponentName(widget.getComponentName());
        target.setProviderCode(widget.getProviderCode()); target.setRequiredPermission(widget.getRequiredPermission());
        target.setDefaultSize(widget.getDefaultSize()); target.setSortOrder(widget.getSortOrder()); target.setEnabled(widget.getEnabled());
        target.setAudienceRoleCodes(widget.getAudienceRoleCodes());target.setAudienceOrganizationIds(widget.getAudienceOrganizationIds());target.setAudienceDepartmentIds(widget.getAudienceDepartmentIds());
        return widgetRepository.save(target);
    }
    @Override @Transactional public void deleteWidget(String id) { widgetRepository.deleteById(id); }

    private AdminUser requireUser(String username) {
        AdminUser user = userRepository.findByUsername(username);
        if (user == null) throw new IllegalArgumentException("user not found");
        return user;
    }

    private Set<String> currentPermissions() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return Set.of();
        return authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
    }

    private boolean allowed(String required, Set<String> permissions) {
        return required == null || required.isBlank() || permissions.contains(required) || permissions.contains("portal:manage");
    }

    private Map<String, Object> applicationMap(PortalApplication app, boolean favorite) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", app.getId()); data.put("code", app.getAppCode()); data.put("name", app.getAppName());
        data.put("description", app.getDescription()); data.put("icon", app.getIcon()); data.put("routePath", app.getRoutePath());
        data.put("openMode", app.getOpenMode()); data.put("recommended", app.getRecommended()); data.put("favorite", favorite);
        return data;
    }

    private Map<String, Object> widgetMap(PortalWidget widget) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", widget.getId()); data.put("code", widget.getWidgetCode()); data.put("name", widget.getWidgetName());
        data.put("description", widget.getDescription()); data.put("component", widget.getComponentName());
        data.put("provider", widget.getProviderCode()); data.put("defaultSize", widget.getDefaultSize());
        return data;
    }

    private UserPortalApplication newUserApplication(String username, String applicationId) {
        UserPortalApplication value = new UserPortalApplication();
        value.setUsername(username); value.setApplicationId(applicationId); value.setFavorite(false);
        value.setFavoriteOrder(0); value.setVisitCount(0L); return value;
    }

    private PortalApplication requireAllowedApplication(String id){PortalApplication app=applicationRepository.findById(id).orElseThrow(()->new IllegalArgumentException("application not found"));String username=SecurityContextHolder.getContext().getAuthentication().getName();if(!Boolean.TRUE.equals(app.getEnabled())||!allowed(app.getRequiredPermission(),currentPermissions())||!audienceAllowed(username,app.getAudienceRoleCodes(),app.getAudienceOrganizationIds(),app.getAudienceDepartmentIds()))throw new org.springframework.security.access.AccessDeniedException("无权访问该应用");return app;}
    private Object validateLayout(String username,Object layout){if(!(layout instanceof List<?> list))throw new IllegalArgumentException("门户布局必须是组件编码数组");if(list.size()>20)throw new IllegalArgumentException("门户布局最多20个组件");Set<String> enabled=widgets(username).stream().map(w->String.valueOf(w.get("code"))).collect(Collectors.toSet());List<String> result=new ArrayList<>();for(Object item:list){String code=String.valueOf(item);if(!enabled.contains(code))throw new IllegalArgumentException("无效、已停用或无权使用的Widget："+code);if(!result.contains(code))result.add(code);}return result;}
    private void validateRoute(PortalApplication app){String mode=app.getOpenMode()==null?"INTERNAL":app.getOpenMode();String route=app.getRoutePath();requireText(route,"访问地址");if("INTERNAL".equals(mode)){if(!route.startsWith("/")||route.startsWith("//")||route.contains("://"))throw new IllegalArgumentException("内部应用地址必须是站内绝对路径");return;}if(!"EXTERNAL".equals(mode))throw new IllegalArgumentException("打开方式无效");try{URI uri=URI.create(route);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)throw new IllegalArgumentException("外部应用仅允许HTTPS地址");Set<String> hosts=java.util.Arrays.stream(externalHosts.split(",")).map(String::trim).filter(v->!v.isBlank()).collect(Collectors.toSet());if(!hosts.isEmpty()&&!hosts.contains(uri.getHost()))throw new IllegalArgumentException("外部应用域名不在可信名单");}catch(IllegalArgumentException e){throw new IllegalArgumentException("外部应用地址不安全："+e.getMessage());}}
    private void requireText(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+"不能为空");}
    private boolean audienceAllowed(String username,String roleCodes,String organizationIds,String departmentIds){if((roleCodes==null||roleCodes.isBlank())&&(organizationIds==null||organizationIds.isBlank())&&(departmentIds==null||departmentIds.isBlank()))return true;AdminUser user=requireUser(username);Set<String> roles=user.getRoles().stream().map(r->r.getRoleCode()).filter(java.util.Objects::nonNull).collect(Collectors.toSet());if(matches(roleCodes,roles))return true;if(user.getEmployeeId()==null)return false;var primary=assignmentRepository.findCurrentPrimaryAssignment(user.getEmployeeId(),java.time.LocalDate.now());return primary.map(a->matches(organizationIds,Set.of(a.getOrganizationId()))||matches(departmentIds,Set.of(a.getOrganizationUnitId()))).orElse(false);}
    private boolean matches(String csv,Set<String> values){if(csv==null||csv.isBlank())return false;return java.util.Arrays.stream(csv.split(",")).map(String::trim).anyMatch(values::contains);}

    private Object parseLayout(String json) {
        try { return objectMapper.readValue(json, Object.class); }
        catch (JsonProcessingException e) { return new ArrayList<>(); }
    }
}

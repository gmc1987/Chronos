package com.chronos.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.EmployeeAssignmentDTO;
import com.chronos.model.dto.OrganizationUnitDTO;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.JobLevel;
import com.chronos.model.pojo.JobTitle;
import com.chronos.model.pojo.Position;
import com.chronos.model.pojo.BusinessNumberRule;
import com.chronos.model.vo.EmployeeAssignmentVO;
import com.chronos.model.vo.OrganizationUnitVO;
import com.chronos.service.iService.IIamDirectoryService;
import com.chronos.service.impl.DirectoryImportService;
import com.chronos.service.impl.BusinessNumberService;
import com.chronos.service.impl.EmployeeImportService;
import com.chronos.security.IamAuthorization;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.Idao.IPositionRepository;
import com.chronos.Idao.IJobLevelRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IOrganizationUnitRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/iam")
public class IamDirectoryController {

    private final IIamDirectoryService service;
    private final DirectoryImportService importService;
    private final BusinessNumberService businessNumbers;
    private final IamAuthorization iamAuthorization;
    private final DirectoryDataScopeGuard dataScope;
    private final IPositionRepository positions;
    private final IJobLevelRepository jobLevels;
    private final IEmployeeAssignmentRepository assignments;
    private final IOrganizationUnitRepository organizationUnits;

    public IamDirectoryController(
            IIamDirectoryService service,
            DirectoryImportService importService,
            BusinessNumberService businessNumbers,
            IamAuthorization iamAuthorization,
            DirectoryDataScopeGuard dataScope,
            IPositionRepository positions,
            IJobLevelRepository jobLevels,
            IEmployeeAssignmentRepository assignments,
            IOrganizationUnitRepository organizationUnits) {
        this.service = service;
        this.importService = importService;
        this.businessNumbers = businessNumbers;
        this.iamAuthorization = iamAuthorization;
        this.dataScope = dataScope;
        this.positions = positions;
        this.jobLevels = jobLevels;
        this.assignments = assignments;
        this.organizationUnits = organizationUnits;
    }

    @GetMapping("/organization-units")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage','iam:role:authorize')")
    public ResultData<List<OrganizationUnitVO>> units(@RequestParam String organizationId, Authentication authentication) {
        dataScope.requireOrganization(authentication, organizationId);
        return ok(service.organizationUnits(organizationId));
    }

    @PostMapping("/organization-units")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<OrganizationUnitVO> createUnit(
            @Valid @RequestBody OrganizationUnitDTO value, Authentication authentication) {
        dataScope.requireOrganization(authentication, value.getOrganizationId());
        value.setId(null);
        return ok(service.saveOrganizationUnit(value));
    }

    @PutMapping("/organization-units/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<OrganizationUnitVO> updateUnit(
            @PathVariable String id,
            @Valid @RequestBody OrganizationUnitDTO value, Authentication authentication) {
        value.setId(id);
        var existing = organizationUnits.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("organization unit not found"));
        dataScope.requireOrganization(authentication, existing.getOrgId());
        dataScope.requireOrganization(authentication, value.getOrganizationId());
        if (!existing.getOrgId().equals(value.getOrganizationId()))
            throw new IllegalArgumentException("部门不允许跨机构迁移");
        return ok(service.saveOrganizationUnit(value));
    }

    @DeleteMapping("/organization-units/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteUnit(@PathVariable String id, Authentication authentication) {
        dataScope.requireOrganization(authentication, organizationUnits.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("organization unit not found")).getOrgId());
        service.deleteOrganizationUnit(id);
        return ok(null);
    }

    @GetMapping("/positions")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage')")
    public ResultData<?> positions(
            @RequestParam String organizationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        dataScope.requireOrganization(authentication, organizationId);
        if (page == null) {
            return ok(service.positions(organizationId));
        }
        return ok(PageView.from(service.pagePositions(organizationId, pageable(page, size))));
    }

    @PostMapping("/positions")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<Position> createPosition(@RequestBody Position value, Authentication authentication) {
        value.setId(null);
        dataScope.requireOrganization(authentication, value.getOrganizationId());
        return ok(service.savePosition(value));
    }

    @PutMapping("/positions/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<Position> updatePosition(
            @PathVariable String id,
            @RequestBody Position value, Authentication authentication) {
        Position existing = positions.findById(id).orElseThrow(() -> new IllegalArgumentException("position not found"));
        dataScope.requireOrganization(authentication, existing.getOrganizationId());
        if (value.getOrganizationId() != null && !existing.getOrganizationId().equals(value.getOrganizationId()))
            throw new IllegalArgumentException("岗位不允许跨机构迁移");
        value.setOrganizationId(existing.getOrganizationId());
        value.setId(id);
        return ok(service.savePosition(value));
    }

    @DeleteMapping("/positions/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deletePosition(@PathVariable String id, Authentication authentication) {
        dataScope.requireOrganization(authentication, positions.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("position not found")).getOrganizationId());
        service.deletePosition(id);
        return ok(null);
    }

    @GetMapping("/job-titles")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage')")
    public ResultData<List<JobTitle>> jobTitles() {
        return ok(service.jobTitles());
    }

    @PostMapping("/job-titles")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<JobTitle> createJobTitle(@RequestBody JobTitle value) {
        value.setId(null);
        return ok(service.saveJobTitle(value));
    }

    @PutMapping("/job-titles/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<JobTitle> updateJobTitle(
            @PathVariable String id,
            @RequestBody JobTitle value) {
        value.setId(id);
        return ok(service.saveJobTitle(value));
    }

    @DeleteMapping("/job-titles/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteJobTitle(@PathVariable String id) {
        service.deleteJobTitle(id);
        return ok(null);
    }

    @GetMapping("/job-levels")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage')")
    public ResultData<?> jobLevels(
            @RequestParam String organizationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        dataScope.requireOrganization(authentication, organizationId);
        if (page == null) {
            return ok(service.jobLevels(organizationId));
        }
        return ok(PageView.from(service.pageJobLevels(organizationId, pageable(page, size))));
    }

    @PostMapping("/job-levels")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<JobLevel> createJobLevel(@RequestBody JobLevel value, Authentication authentication) {
        value.setId(null);
        dataScope.requireOrganization(authentication, value.getOrganizationId());
        return ok(service.saveJobLevel(value));
    }

    @PutMapping("/job-levels/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<JobLevel> updateJobLevel(
            @PathVariable String id,
            @RequestBody JobLevel value, Authentication authentication) {
        JobLevel existing = jobLevels.findById(id).orElseThrow(() -> new IllegalArgumentException("job level not found"));
        dataScope.requireOrganization(authentication, existing.getOrganizationId());
        if (value.getOrganizationId() != null && !existing.getOrganizationId().equals(value.getOrganizationId()))
            throw new IllegalArgumentException("职级不允许跨机构迁移");
        value.setOrganizationId(existing.getOrganizationId());
        value.setId(id);
        return ok(service.saveJobLevel(value));
    }

    @DeleteMapping("/job-levels/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteJobLevel(@PathVariable String id, Authentication authentication) {
        dataScope.requireOrganization(authentication, jobLevels.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("job level not found")).getOrganizationId());
        service.deleteJobLevel(id);
        return ok(null);
    }

    @GetMapping("/employees")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage','iam:role:authorize')")
    public ResultData<?> employees(
            @RequestParam String organizationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword,
            Authentication authentication) {
        dataScope.requireOrganization(authentication, organizationId);
        if (page == null) {
            return ok(service.employees(organizationId));
        }
        return ok(PageView.from(service.pageEmployees(organizationId, keyword, pageable(page, size))));
    }

    @PostMapping("/employees")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<Employee> createEmployee(@RequestBody Employee value, Authentication authentication) {
        value.setId(null);
        dataScope.requireOrganization(authentication, value.getNumberingOrganizationId());
        return ok(service.saveEmployee(value));
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<Employee> updateEmployee(
            @PathVariable String id,
            @RequestBody Employee value, Authentication authentication) {
        dataScope.requireEmployee(authentication, id);
        value.setId(id);
        return ok(service.saveEmployee(value));
    }

    @DeleteMapping("/employees/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteEmployee(@PathVariable String id, Authentication authentication) {
        dataScope.requireEmployee(authentication, id);
        service.deleteEmployee(id);
        return ok(null);
    }

    @GetMapping("/number-rules")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage')")
    public ResultData<List<BusinessNumberRule>> numberRules(Authentication authentication) {
        var allowed = dataScope.visibleOrganizationIds(authentication);
        if (allowed == null) return ok(businessNumbers.rules());
        return ok(businessNumbers.rules().stream()
                .filter(rule -> "GLOBAL".equals(rule.getOrganizationId())
                        || allowed.contains(rule.getOrganizationId()))
                .toList());
    }

    @PutMapping("/number-rules")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:manage')")
    public ResultData<BusinessNumberRule> saveNumberRule(
            @RequestBody BusinessNumberRule value, Authentication authentication) {
        if (value.getOrganizationId() == null || "GLOBAL".equals(value.getOrganizationId()))
            dataScope.requireFullAccess(authentication);
        else
            dataScope.requireOrganization(authentication, value.getOrganizationId());
        return ok(businessNumbers.saveRule(value));
    }

    @GetMapping("/import/templates/{type}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:template','iam:directory:manage')")
    public ResponseEntity<byte[]> template(@PathVariable String type) {
        byte[] content = importService.template(type);
        String filename = "chronos-" + type + "-import-template.xlsx";
        String encodedFilename = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping(value = "/import/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:import','iam:directory:manage')")
    public ResultData<Map<String, Object>> importData(
            @PathVariable String type,
            @RequestPart("file") MultipartFile file,
            @RequestParam String organizationId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean syncAccounts,
            @RequestParam(defaultValue = "true") boolean disableInactiveAccounts,
            @RequestParam(defaultValue = "EXCEL") String sourceSystem) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        dataScope.requireOrganization(authentication, organizationId);
        if (syncAccounts && !iamAuthorization.any(authentication,
                "iam:directory:account-provision", "iam:directory:manage")) {
            throw new org.springframework.security.access.AccessDeniedException("缺少批量开通账号权限");
        }
        return ok(importService.importFile(type, file, dryRun, organizationId,
                new EmployeeImportService.ImportOptions(syncAccounts,
                        disableInactiveAccounts, sourceSystem, dryRun)));
    }

    @GetMapping("/assignments")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage')")
    public ResultData<List<EmployeeAssignmentVO>> assignments(@RequestParam String employeeId, Authentication authentication) {
        dataScope.requireEmployee(authentication, employeeId);
        return ok(service.assignments(employeeId));
    }

    @PostMapping("/assignments")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<EmployeeAssignmentVO> createAssignment(
            @Valid @RequestBody EmployeeAssignmentDTO value, Authentication authentication) {
        dataScope.requireOrganization(authentication, value.getOrganizationId());
        dataScope.requireEmployee(authentication, value.getEmployeeId());
        value.setId(null);
        return ok(service.saveAssignment(value));
    }

    @PutMapping("/assignments/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<EmployeeAssignmentVO> updateAssignment(
            @PathVariable String id,
            @Valid @RequestBody EmployeeAssignmentDTO value, Authentication authentication) {
        var existing = assignments.findById(id).orElseThrow(() -> new IllegalArgumentException("assignment not found"));
        dataScope.requireOrganization(authentication, existing.getOrganizationId());
        dataScope.requireOrganization(authentication, value.getOrganizationId());
        dataScope.requireEmployee(authentication, existing.getEmployeeId());
        value.setId(id);
        return ok(service.saveAssignment(value));
    }

    @DeleteMapping("/assignments/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteAssignment(@PathVariable String id, Authentication authentication) {
        var existing = assignments.findById(id).orElseThrow(() -> new IllegalArgumentException("assignment not found"));
        dataScope.requireOrganization(authentication, existing.getOrganizationId());
        dataScope.requireEmployee(authentication, existing.getEmployeeId());
        service.deleteAssignment(id);
        return ok(null);
    }

    private <T> ResultData<T> ok(T data) {
        return ResultData.<T>builder()
                .code("200")
                .msg("ok")
                .data(data)
                .build();
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100));
    }
}

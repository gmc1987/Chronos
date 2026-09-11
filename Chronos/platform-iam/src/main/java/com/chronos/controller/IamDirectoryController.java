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
import com.chronos.model.vo.EmployeeAssignmentVO;
import com.chronos.model.vo.OrganizationUnitVO;
import com.chronos.service.iService.IIamDirectoryService;
import com.chronos.service.impl.DirectoryImportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/iam")
public class IamDirectoryController {

    private final IIamDirectoryService service;
    private final DirectoryImportService importService;

    public IamDirectoryController(
            IIamDirectoryService service,
            DirectoryImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    @GetMapping("/organization-units")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage','iam:role:authorize')")
    public ResultData<List<OrganizationUnitVO>> units(@RequestParam String organizationId) {
        return ok(service.organizationUnits(organizationId));
    }

    @PostMapping("/organization-units")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<OrganizationUnitVO> createUnit(
            @Valid @RequestBody OrganizationUnitDTO value) {
        value.setId(null);
        return ok(service.saveOrganizationUnit(value));
    }

    @PutMapping("/organization-units/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<OrganizationUnitVO> updateUnit(
            @PathVariable String id,
            @Valid @RequestBody OrganizationUnitDTO value) {
        value.setId(id);
        return ok(service.saveOrganizationUnit(value));
    }

    @DeleteMapping("/organization-units/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteUnit(@PathVariable String id) {
        service.deleteOrganizationUnit(id);
        return ok(null);
    }

    @GetMapping("/positions")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage')")
    public ResultData<?> positions(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size) {
        if (page == null) {
            return ok(service.positions());
        }
        return ok(PageView.from(service.pagePositions(pageable(page, size))));
    }

    @PostMapping("/positions")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<Position> createPosition(@RequestBody Position value) {
        value.setId(null);
        return ok(service.savePosition(value));
    }

    @PutMapping("/positions/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<Position> updatePosition(
            @PathVariable String id,
            @RequestBody Position value) {
        value.setId(id);
        return ok(service.savePosition(value));
    }

    @DeleteMapping("/positions/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deletePosition(@PathVariable String id) {
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
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size) {
        if (page == null) {
            return ok(service.jobLevels());
        }
        return ok(PageView.from(service.pageJobLevels(pageable(page, size))));
    }

    @PostMapping("/job-levels")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<JobLevel> createJobLevel(@RequestBody JobLevel value) {
        value.setId(null);
        return ok(service.saveJobLevel(value));
    }

    @PutMapping("/job-levels/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<JobLevel> updateJobLevel(
            @PathVariable String id,
            @RequestBody JobLevel value) {
        value.setId(id);
        return ok(service.saveJobLevel(value));
    }

    @DeleteMapping("/job-levels/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteJobLevel(@PathVariable String id) {
        service.deleteJobLevel(id);
        return ok(null);
    }

    @GetMapping("/employees")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage','iam:role:authorize')")
    public ResultData<?> employees(
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword) {
        if (page == null) {
            return ok(service.employees());
        }
        return ok(PageView.from(service.pageEmployees(keyword, pageable(page, size))));
    }

    @PostMapping("/employees")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<Employee> createEmployee(@RequestBody Employee value) {
        value.setId(null);
        return ok(service.saveEmployee(value));
    }

    @PutMapping("/employees/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<Employee> updateEmployee(
            @PathVariable String id,
            @RequestBody Employee value) {
        value.setId(id);
        return ok(service.saveEmployee(value));
    }

    @DeleteMapping("/employees/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteEmployee(@PathVariable String id) {
        service.deleteEmployee(id);
        return ok(null);
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
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return ok(importService.importFile(type, file, dryRun));
    }

    @GetMapping("/assignments")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:view','iam:directory:manage')")
    public ResultData<List<EmployeeAssignmentVO>> assignments(@RequestParam String employeeId) {
        return ok(service.assignments(employeeId));
    }

    @PostMapping("/assignments")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:create','iam:directory:manage')")
    public ResultData<EmployeeAssignmentVO> createAssignment(
            @Valid @RequestBody EmployeeAssignmentDTO value) {
        value.setId(null);
        return ok(service.saveAssignment(value));
    }

    @PutMapping("/assignments/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:update','iam:directory:manage')")
    public ResultData<EmployeeAssignmentVO> updateAssignment(
            @PathVariable String id,
            @Valid @RequestBody EmployeeAssignmentDTO value) {
        value.setId(id);
        return ok(service.saveAssignment(value));
    }

    @DeleteMapping("/assignments/{id}")
    @PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:delete','iam:directory:manage')")
    public ResultData<Void> deleteAssignment(@PathVariable String id) {
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

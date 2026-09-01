package com.chronos.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.chronos.commons.model.ResultData;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.JobTitle;
import com.chronos.model.pojo.JobLevel;
import com.chronos.model.pojo.OrganizationUnit;
import com.chronos.model.pojo.Position;
import com.chronos.service.iService.IIamDirectoryService;
import com.chronos.service.impl.DirectoryImportService;
import com.chronos.model.dto.OrganizationUnitDTO;
import com.chronos.model.dto.EmployeeAssignmentDTO;
import com.chronos.model.vo.OrganizationUnitVO;
import com.chronos.model.vo.EmployeeAssignmentVO;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/iam")
@PreAuthorize("@iamAuthorization.has(authentication, 'iam:directory:manage')")
public class IamDirectoryController {
    private final IIamDirectoryService service;private final DirectoryImportService importService;
    public IamDirectoryController(IIamDirectoryService service,DirectoryImportService importService){this.service=service;this.importService=importService;}
    @GetMapping("/organization-units") public ResultData<List<OrganizationUnitVO>> units(@RequestParam String organizationId){return ok(service.organizationUnits(organizationId));}
    @PostMapping("/organization-units") public ResultData<OrganizationUnitVO> createUnit(@Valid @RequestBody OrganizationUnitDTO v){v.setId(null);return ok(service.saveOrganizationUnit(v));}
    @PutMapping("/organization-units/{id}") public ResultData<OrganizationUnitVO> updateUnit(@PathVariable String id,@Valid @RequestBody OrganizationUnitDTO v){v.setId(id);return ok(service.saveOrganizationUnit(v));}
    @DeleteMapping("/organization-units/{id}") public ResultData<Void> deleteUnit(@PathVariable String id){service.deleteOrganizationUnit(id);return ok(null);}
    @GetMapping("/positions") public ResultData<List<Position>> positions(){return ok(service.positions());}
    @PostMapping("/positions") public ResultData<Position> createPosition(@RequestBody Position v){v.setId(null);return ok(service.savePosition(v));}
    @PutMapping("/positions/{id}") public ResultData<Position> updatePosition(@PathVariable String id,@RequestBody Position v){v.setId(id);return ok(service.savePosition(v));}
    @DeleteMapping("/positions/{id}") public ResultData<Void> deletePosition(@PathVariable String id){service.deletePosition(id);return ok(null);}
    @GetMapping("/job-titles") public ResultData<List<JobTitle>> jobTitles(){return ok(service.jobTitles());}
    @PostMapping("/job-titles") public ResultData<JobTitle> createJobTitle(@RequestBody JobTitle v){v.setId(null);return ok(service.saveJobTitle(v));}
    @PutMapping("/job-titles/{id}") public ResultData<JobTitle> updateJobTitle(@PathVariable String id,@RequestBody JobTitle v){v.setId(id);return ok(service.saveJobTitle(v));}
    @DeleteMapping("/job-titles/{id}") public ResultData<Void> deleteJobTitle(@PathVariable String id){service.deleteJobTitle(id);return ok(null);}
    @GetMapping("/job-levels") public ResultData<List<JobLevel>> jobLevels(){return ok(service.jobLevels());}
    @PostMapping("/job-levels") public ResultData<JobLevel> createJobLevel(@RequestBody JobLevel v){v.setId(null);return ok(service.saveJobLevel(v));}
    @PutMapping("/job-levels/{id}") public ResultData<JobLevel> updateJobLevel(@PathVariable String id,@RequestBody JobLevel v){v.setId(id);return ok(service.saveJobLevel(v));}
    @DeleteMapping("/job-levels/{id}") public ResultData<Void> deleteJobLevel(@PathVariable String id){service.deleteJobLevel(id);return ok(null);}
    @GetMapping("/employees") public ResultData<List<Employee>> employees(){return ok(service.employees());}
    @PostMapping("/employees") public ResultData<Employee> createEmployee(@RequestBody Employee v){v.setId(null);return ok(service.saveEmployee(v));}
    @PutMapping("/employees/{id}") public ResultData<Employee> updateEmployee(@PathVariable String id,@RequestBody Employee v){v.setId(id);return ok(service.saveEmployee(v));}
    @DeleteMapping("/employees/{id}") public ResultData<Void> deleteEmployee(@PathVariable String id){service.deleteEmployee(id);return ok(null);}
    @GetMapping("/import/templates/{type}") public ResponseEntity<byte[]> template(@PathVariable String type){byte[] content=importService.template(type);String filename="chronos-"+type+"-import-template.xlsx";return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(filename,StandardCharsets.UTF_8)).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(content);}
    @PostMapping(value="/import/{type}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public ResultData<Map<String,Object>> importData(@PathVariable String type,@RequestPart("file") MultipartFile file,@RequestParam(defaultValue="false") boolean dryRun){return ok(importService.importFile(type,file,dryRun));}
    @GetMapping("/assignments") public ResultData<List<EmployeeAssignmentVO>> assignments(@RequestParam String employeeId){return ok(service.assignments(employeeId));}
    @PostMapping("/assignments") public ResultData<EmployeeAssignmentVO> createAssignment(@Valid @RequestBody EmployeeAssignmentDTO v){v.setId(null);return ok(service.saveAssignment(v));}
    @PutMapping("/assignments/{id}") public ResultData<EmployeeAssignmentVO> updateAssignment(@PathVariable String id,@Valid @RequestBody EmployeeAssignmentDTO v){v.setId(id);return ok(service.saveAssignment(v));}
    @DeleteMapping("/assignments/{id}") public ResultData<Void> deleteAssignment(@PathVariable String id){service.deleteAssignment(id);return ok(null);}
    private <T> ResultData<T> ok(T data){return ResultData.<T>builder().code("200").msg("ok").data(data).build();}
}

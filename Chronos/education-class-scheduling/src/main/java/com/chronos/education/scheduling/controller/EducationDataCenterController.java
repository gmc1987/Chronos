package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataCenterService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/education/data-center")
public class EducationDataCenterController {
 private final EducationDataCenterService service;
 public EducationDataCenterController(EducationDataCenterService service){this.service=service;}
 @GetMapping("/metrics") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:view','education:data-center:report')") public ResultData<?> metrics(){return ok(service.metricDefinitions());}
 @GetMapping("/dashboards/{dashboard}") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:view')") public ResultData<?> dashboard(@PathVariable String dashboard,@RequestParam LocalDate date,@RequestParam(required=false) String campusId,Authentication user){return ok(service.dashboard(dashboard,date,campusId,user));}
 @PostMapping("/snapshots") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:snapshot')") public ResultData<?> snapshot(@RequestParam LocalDate date,@RequestParam(required=false) String campusId,Authentication user){return ok(service.takeSnapshot(date,campusId,user));}
 @PostMapping("/reports") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:report')") public ResultData<?> report(@RequestBody DataReportTask command,Authentication user){return ok(service.requestReport(command.getReportType(),command.getRequestedDate(),command.getCampusId(),user));}
 @GetMapping("/reports") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:view','education:data-center:report')") public ResultData<?> reports(Authentication user){return ok(service.reports(user));}
 @GetMapping("/reports/{id}/download") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:view','education:data-center:report')") public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable String id,Authentication user){
  var content=service.downloadReport(id,user);
  return org.springframework.http.ResponseEntity.ok()
    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
      org.springframework.http.ContentDisposition.attachment().filename(content.filename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
    .contentType(org.springframework.http.MediaType.parseMediaType(content.contentType())).body(content.content());
 }
 @PostMapping("/reports/{id}/retry") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:report')") public ResultData<?> retry(@PathVariable String id,Authentication user){return ok(service.retryReport(id,user));}
 @GetMapping("/quality-issues") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:view','education:data-center:quality')") public ResultData<?> issues(@RequestParam(required=false) String status,Authentication user){return ok(service.issues(status,user));}
 @PostMapping("/quality-issues/scan") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:quality')") public ResultData<?> scan(@RequestParam LocalDate date,@RequestParam(required=false) String campusId,Authentication user){return ok(service.scanQuality(date,campusId,user));}
 @PostMapping("/quality-issues") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:quality')") public ResultData<?> createIssue(@RequestBody DataQualityIssue issue,Authentication user){return ok(service.createIssue(issue,user));}
 @PutMapping("/quality-issues/{id}") @PreAuthorize("@iamAuthorization.any(authentication,'education:data-center:quality')") public ResultData<?> transition(@PathVariable String id,@RequestParam String status,@RequestBody(required=false) DataQualityIssue command,Authentication user){return ok(service.transitionIssue(id,status,command==null?null:command.getResolution(),user));}
 private <T> ResultData<T> ok(T value){return ResultData.<T>builder().code("200").msg("success").data(value).build();}
}

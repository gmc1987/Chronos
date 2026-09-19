package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataCenterService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/education/data-center")
public class EducationDataCenterController {
 private final EducationDataCenterService service;
 public EducationDataCenterController(EducationDataCenterService service){this.service=service;}
 @GetMapping("/metrics") public ResultData<?> metrics(){return ok(service.metricDefinitions());}
 @GetMapping("/dashboards/{dashboard}") public ResultData<?> dashboard(@PathVariable String dashboard,@RequestParam LocalDate date,@RequestParam(required=false) String campusId,Authentication user){return ok(service.dashboard(dashboard,date,campusId,user));}
 @PostMapping("/snapshots") public ResultData<?> snapshot(@RequestParam LocalDate date,@RequestParam(required=false) String campusId,Authentication user){return ok(service.takeSnapshot(date,campusId,user));}
 @PostMapping("/reports") public ResultData<?> report(@RequestBody DataReportTask command,Authentication user){return ok(service.requestReport(command.getReportType(),command.getRequestedDate(),command.getCampusId(),user));}
 @GetMapping("/reports") public ResultData<?> reports(){return ok(service.reports());}
 @GetMapping("/quality-issues") public ResultData<?> issues(@RequestParam(required=false) String status){return ok(service.issues(status));}
 @PostMapping("/quality-issues") public ResultData<?> createIssue(@RequestBody DataQualityIssue issue){return ok(service.createIssue(issue));}
 @PutMapping("/quality-issues/{id}") public ResultData<?> transition(@PathVariable String id,@RequestParam String status,@RequestBody(required=false) DataQualityIssue command){return ok(service.transitionIssue(id,status,command==null?null:command.getResolution()));}
 private <T> ResultData<T> ok(T value){return ResultData.<T>builder().code("200").msg("success").data(value).build();}
}

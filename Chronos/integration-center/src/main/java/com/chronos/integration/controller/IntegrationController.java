package com.chronos.integration.controller;

import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.chronos.commons.model.ResultData;
import com.chronos.integration.dao.*;
import com.chronos.integration.model.*;
import com.chronos.integration.service.IntegrationService;

@RestController
@RequestMapping("/admin/integrations")
public class IntegrationController {
 private final IntegrationService service; private final ConnectorRepository connectors; private final SyncJobRepository jobs; private final SyncRunRepository runs; private final SyncItemErrorRepository errors; private final DeadLetterRepository dead;
 public IntegrationController(IntegrationService service,ConnectorRepository connectors,SyncJobRepository jobs,SyncRunRepository runs,SyncItemErrorRepository errors,DeadLetterRepository dead){this.service=service;this.connectors=connectors;this.jobs=jobs;this.runs=runs;this.errors=errors;this.dead=dead;}
 @GetMapping("/connectors") @PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:view')") public ResultData<List<Connector>> connectors(){return ok(connectors.findAll());}
 @PostMapping("/connectors") @PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:manage')") public ResultData<Connector> save(@RequestBody Connector c,Principal p){return ok(service.saveConnector(c,p.getName()));}
 @PostMapping("/connectors/{id}/test") @PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:test')") public ResultData<Connector> test(@PathVariable String id){return ok(service.test(id));}
 @GetMapping("/jobs") @PreAuthorize("@iamAuthorization.has(authentication,'integration:job:view')") public ResultData<List<SyncJob>> jobs(){return ok(jobs.findAll());}
 @PostMapping("/jobs") @PreAuthorize("@iamAuthorization.has(authentication,'integration:job:manage')") public ResultData<SyncJob> saveJob(@RequestBody SyncJob j,Principal p){return ok(service.saveJob(j,p.getName()));}
 @PostMapping("/jobs/{id}/run") @PreAuthorize("@iamAuthorization.has(authentication,'integration:job:run')") public ResultData<SyncRun> run(@PathVariable String id,Principal p){return ok(service.execute(id,p.getName()));}
 @GetMapping("/jobs/{id}/runs") @PreAuthorize("@iamAuthorization.has(authentication,'integration:run:view')") public ResultData<List<SyncRun>> runs(@PathVariable String id){return ok(runs.findByJobIdOrderByStartedAtDesc(id));}
 @GetMapping("/runs") @PreAuthorize("@iamAuthorization.has(authentication,'integration:run:view')") public ResultData<List<SyncRun>> allRuns(){return ok(runs.findAll());}
 @GetMapping("/runs/{id}/errors") @PreAuthorize("@iamAuthorization.has(authentication,'integration:error:view')") public ResultData<List<SyncItemError>> errors(@PathVariable String id){return ok(errors.findByRunId(id));}
 @GetMapping("/dead-letters") @PreAuthorize("@iamAuthorization.has(authentication,'integration:dead-letter:view')") public ResultData<List<DeadLetter>> dead(){return ok(dead.findByStatus("PENDING"));}
 @PostMapping("/dead-letters/{id}/replay") @PreAuthorize("@iamAuthorization.has(authentication,'integration:dead-letter:replay')") public ResultData<DeadLetter> replay(@PathVariable String id,Principal p){return ok(service.replay(id,p.getName()));}
 private <T> ResultData<T> ok(T value){return ResultData.<T>builder().code("200").msg("ok").data(value).build();}
}

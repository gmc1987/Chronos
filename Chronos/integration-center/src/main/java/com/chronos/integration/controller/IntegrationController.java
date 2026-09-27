package com.chronos.integration.controller;

import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.chronos.commons.model.ResultData;
import com.chronos.integration.dao.*;
import com.chronos.integration.model.*;
import com.chronos.integration.service.IntegrationExtensionService;
import com.chronos.integration.service.IntegrationService;

@RestController
@RequestMapping("/admin/integrations")
public class IntegrationController {
 private final IntegrationService service; private final IntegrationExtensionService extension; private final FieldMappingRepository extensionMappingsRepository; private final ConnectorRepository connectors; private final SyncJobRepository jobs; private final SyncRunRepository runs; private final SyncItemErrorRepository errors; private final DeadLetterRepository dead;
 public IntegrationController(IntegrationService service, IntegrationExtensionService extension, FieldMappingRepository extensionMappingsRepository, ConnectorRepository connectors,SyncJobRepository jobs,SyncRunRepository runs,SyncItemErrorRepository errors,DeadLetterRepository dead){this.service=service;this.extension=extension;this.extensionMappingsRepository=extensionMappingsRepository;this.connectors=connectors;this.jobs=jobs;this.runs=runs;this.errors=errors;this.dead=dead;}
 @GetMapping("/connectors") @PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:view')") public ResultData<List<Connector>> connectors(){return ok(connectors.findAll());}
 @PostMapping("/connectors") @PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:manage')") public ResultData<Connector> save(@RequestBody Connector c,Principal p){return ok(service.saveConnector(c,p.getName()));}
 @PostMapping("/connectors/{id}/credentials") @PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:manage')") public ResultData<CredentialMetadata> credential(@PathVariable String id,@RequestBody CredentialCommand command,Principal p){return ok(metadata(service.configureCredential(id,command.keyName(),command.secret(),p.getName())));}
 @GetMapping("/connectors/{id}/credentials") @PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:view')") public ResultData<List<CredentialMetadata>> credentials(@PathVariable String id){return ok(service.listCredentials(id).stream().map(this::metadata).toList());}
 @PostMapping("/connectors/{id}/test") @PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:test')") public ResultData<Connector> test(@PathVariable String id){return ok(service.test(id));}
 @GetMapping("/jobs") @PreAuthorize("@iamAuthorization.has(authentication,'integration:job:view')") public ResultData<List<SyncJob>> jobs(){return ok(jobs.findAll());}
 @PostMapping("/jobs") @PreAuthorize("@iamAuthorization.has(authentication,'integration:job:manage')") public ResultData<SyncJob> saveJob(@RequestBody SyncJob j,Principal p){return ok(service.saveJob(j,p.getName()));}
 @PostMapping("/jobs/{id}/run") @PreAuthorize("@iamAuthorization.has(authentication,'integration:job:run')") public ResultData<SyncRun> run(@PathVariable String id,Principal p){return ok(service.execute(id,p.getName()));}
 @GetMapping("/jobs/{id}/runs") @PreAuthorize("@iamAuthorization.has(authentication,'integration:run:view')") public ResultData<List<SyncRun>> runs(@PathVariable String id){return ok(runs.findByJobIdOrderByStartedAtDesc(id));}
 @GetMapping("/runs") @PreAuthorize("@iamAuthorization.has(authentication,'integration:run:view')") public ResultData<List<SyncRun>> allRuns(){return ok(runs.findAll());}
 @GetMapping("/runs/{id}/errors") @PreAuthorize("@iamAuthorization.has(authentication,'integration:error:view')") public ResultData<List<SyncItemError>> errors(@PathVariable String id){return ok(errors.findByRunId(id));}
 @GetMapping("/dead-letters") @PreAuthorize("@iamAuthorization.has(authentication,'integration:dead-letter:view')") public ResultData<List<DeadLetter>> dead(){return ok(dead.findByStatus("PENDING"));}
 @PostMapping("/dead-letters/{id}/replay") @PreAuthorize("@iamAuthorization.has(authentication,'integration:dead-letter:replay')") public ResultData<DeadLetter> replay(@PathVariable String id,Principal p){return ok(service.replay(id,p.getName()));}
 @GetMapping("/connectors/{id}/mappings") @PreAuthorize("@iamAuthorization.has(authentication,'integration:mapping:view')") public ResultData<List<FieldMapping>> mappings(@PathVariable String id){return ok(extensionMappings(id));}
 @PostMapping("/connectors/{id}/mappings") @PreAuthorize("@iamAuthorization.has(authentication,'integration:mapping:manage')") public ResultData<FieldMapping> saveMapping(@PathVariable String id,@RequestBody MappingCommand command,Principal p){return ok(extension.saveMapping(null,new IntegrationExtensionService.FieldMappingCommand(id,command.objectType(),command.sourcePath(),command.targetField(),command.transformCode(),command.required(),command.versionNo()),p.getName()));}
 @PostMapping("/jobs/{id}/dry-run") @PreAuthorize("@iamAuthorization.has(authentication,'integration:dry-run')") public ResultData<IntegrationExtensionService.DryRunResult> dryRun(@PathVariable String id){return ok(extension.dryRun(id));}
 @PostMapping("/runs/{id}/reconcile") @PreAuthorize("@iamAuthorization.has(authentication,'integration:reconcile')") public ResultData<SyncReconciliation> reconcile(@PathVariable String id,@RequestBody ReconcileCommand command,Principal p){return ok(extension.reconcile(id,command.objectType(),command.sourceCount(),command.acceptedCount(),command.rejectedCount(),command.missingCount(),p.getName()));}
 private List<FieldMapping> extensionMappings(String connectorId){return extensionMappingsRepository.findByConnectorIdAndObjectTypeAndVersionNoOrderByTargetField(connectorId,"DEFAULT",1);}
 private <T> ResultData<T> ok(T value){return ResultData.<T>builder().code("200").msg("ok").data(value).build();}
 private CredentialMetadata metadata(Credential credential){return new CredentialMetadata(credential.getId(),credential.getConnectorId(),credential.getKeyName());}
 public record CredentialCommand(String keyName,@JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String secret) {}
 public record CredentialMetadata(String id,String connectorId,String keyName) {}
 public record MappingCommand(String objectType,String sourcePath,String targetField,String transformCode,Boolean required,Integer versionNo) {}
 public record ReconcileCommand(String objectType,long sourceCount,long acceptedCount,long rejectedCount,long missingCount) {}
}

package com.chronos.integration.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.chronos.commons.model.ResultData;
import com.chronos.integration.dao.ConnectorRepository;
import com.chronos.integration.dao.DeadLetterRepository;
import com.chronos.integration.dao.FieldMappingRepository;
import com.chronos.integration.dao.SyncItemErrorRepository;
import com.chronos.integration.dao.SyncJobRepository;
import com.chronos.integration.dao.SyncRunRepository;
import com.chronos.integration.model.Connector;
import com.chronos.integration.model.Credential;
import com.chronos.integration.model.DeadLetter;
import com.chronos.integration.model.FieldMapping;
import com.chronos.integration.model.SyncItemError;
import com.chronos.integration.model.SyncJob;
import com.chronos.integration.model.SyncReconciliation;
import com.chronos.integration.model.SyncRun;
import com.chronos.integration.service.IntegrationExtensionService;
import com.chronos.integration.service.IntegrationService;

@RestController
@RequestMapping("/admin/integrations")
public class IntegrationController {
	private final IntegrationService service;
	private final IntegrationExtensionService extension;
	private final FieldMappingRepository mappings;
	private final ConnectorRepository connectors;
	private final SyncJobRepository jobs;
	private final SyncRunRepository runs;
	private final SyncItemErrorRepository errors;
	private final DeadLetterRepository deadLetters;

	public IntegrationController(IntegrationService service, IntegrationExtensionService extension,
			FieldMappingRepository mappings, ConnectorRepository connectors, SyncJobRepository jobs,
			SyncRunRepository runs, SyncItemErrorRepository errors, DeadLetterRepository deadLetters) {
		this.service = service;
		this.extension = extension;
		this.mappings = mappings;
		this.connectors = connectors;
		this.jobs = jobs;
		this.runs = runs;
		this.errors = errors;
		this.deadLetters = deadLetters;
	}

	@GetMapping("/connectors")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:view')")
	public ResultData<List<Connector>> connectors() {
		return ok(connectors.findAll());
	}

	@PostMapping("/connectors")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:manage')")
	public ResultData<Connector> saveConnector(@RequestBody Connector connector, Principal principal) {
		return ok(service.saveConnector(connector, actor(principal)));
	}

	@PostMapping("/connectors/{id}/credentials")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:manage')")
	public ResultData<CredentialMetadata> saveCredential(@PathVariable String id,
			@RequestBody CredentialCommand command, Principal principal) {
		Credential saved = service.configureCredential(id, command.keyName(), command.secret(), actor(principal));
		return ok(metadata(saved));
	}

	@GetMapping("/connectors/{id}/credentials")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:view')")
	public ResultData<List<CredentialMetadata>> credentials(@PathVariable String id) {
		return ok(service.listCredentials(id).stream().map(this::metadata).toList());
	}

	@PostMapping("/connectors/{id}/test")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:connector:test')")
	public ResultData<Connector> test(@PathVariable String id) {
		return ok(service.test(id));
	}

	@GetMapping("/jobs")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:job:view')")
	public ResultData<List<SyncJob>> jobs() {
		return ok(jobs.findAll());
	}

	@PostMapping("/jobs")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:job:manage')")
	public ResultData<SyncJob> saveJob(@RequestBody SyncJob job, Principal principal) {
		return ok(service.saveJob(job, actor(principal)));
	}

	@PostMapping("/jobs/{id}/run")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:job:run')")
	public ResultData<SyncRun> run(@PathVariable String id, Principal principal) {
		return ok(extension.executeBatch(id, actor(principal)));
	}

	@PostMapping("/jobs/{id}/dry-run")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:dry-run')")
	public ResultData<IntegrationExtensionService.DryRunResult> dryRun(@PathVariable String id) {
		return ok(extension.dryRun(id));
	}

	@GetMapping("/jobs/{id}/runs")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:run:view')")
	public ResultData<List<SyncRun>> runs(@PathVariable String id) {
		return ok(runs.findByJobIdOrderByStartedAtDesc(id));
	}

	@GetMapping("/runs")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:run:view')")
	public ResultData<List<SyncRun>> allRuns() {
		return ok(runs.findAll());
	}

	@GetMapping("/runs/{id}/errors")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:error:view')")
	public ResultData<List<SyncItemError>> errors(@PathVariable String id) {
		return ok(errors.findByRunId(id));
	}

	@GetMapping("/runs/{id}/reconciliations")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:reconcile:view')")
	public ResultData<List<SyncReconciliation>> reconciliations(@PathVariable String id) {
		return ok(extension.reconciliations(id));
	}

	@PostMapping("/runs/{id}/reconcile")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:reconcile')")
	public ResultData<SyncReconciliation> reconcile(@PathVariable String id,
			@RequestBody ReconcileCommand command, Principal principal) {
		return ok(extension.reconcile(id, command.objectType(), command.sourceCount(),
				command.acceptedCount(), command.rejectedCount(), command.missingCount(), actor(principal)));
	}

	@GetMapping("/dead-letters")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:dead-letter:view')")
	public ResultData<List<DeadLetter>> deadLetters() {
		return ok(deadLetters.findByStatus("PENDING"));
	}

	@PostMapping("/dead-letters/{id}/replay")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:dead-letter:replay')")
	public ResultData<DeadLetter> replay(@PathVariable String id, Principal principal) {
		return ok(service.replay(id, actor(principal)));
	}

	@GetMapping("/connectors/{id}/mappings")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:mapping:view')")
	public ResultData<List<FieldMapping>> mappings(@PathVariable String id,
			@RequestParam(defaultValue = "DEFAULT") String objectType,
			@RequestParam(defaultValue = "1") Integer versionNo) {
		return ok(mappings.findByConnectorIdAndObjectTypeAndVersionNoOrderByTargetField(
				id, objectType, versionNo));
	}

	@PostMapping("/connectors/{id}/mappings")
	@PreAuthorize("@iamAuthorization.has(authentication,'integration:mapping:manage')")
	public ResultData<FieldMapping> saveMapping(@PathVariable String id,
			@RequestBody MappingCommand command, Principal principal) {
		IntegrationExtensionService.FieldMappingCommand request =
				new IntegrationExtensionService.FieldMappingCommand(id, command.objectType(),
						command.sourcePath(), command.targetField(), command.transformCode(),
						command.required(), command.versionNo());
		return ok(extension.saveMapping(null, request, actor(principal)));
	}

	private static String actor(Principal principal) {
		return principal == null || principal.getName() == null ? "system" : principal.getName();
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}

	private CredentialMetadata metadata(Credential credential) {
		return new CredentialMetadata(credential.getId(), credential.getConnectorId(), credential.getKeyName());
	}

	public record CredentialCommand(String keyName,
			@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String secret) {
	}

	public record CredentialMetadata(String id, String connectorId, String keyName) {
	}

	public record MappingCommand(String objectType, String sourcePath, String targetField,
			String transformCode, Boolean required, Integer versionNo) {
	}

	public record ReconcileCommand(String objectType, long sourceCount, long acceptedCount,
			long rejectedCount, long missingCount) {
	}
}

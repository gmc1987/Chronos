package com.chronos.integration.service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronos.integration.dao.*;
import com.chronos.integration.model.*;
import com.chronos.integration.security.PlatformSecretCipher;
import com.chronos.service.iService.IAuditLogService;

@Service
public class IntegrationService {
 private static final Logger log=LoggerFactory.getLogger(IntegrationService.class);
 private static final ObjectMapper JSON=new ObjectMapper();
 private final ConnectorRepository connectors; private final SyncJobRepository jobs; private final SyncRunRepository runs;
 private final SyncItemErrorRepository errors; private final DeadLetterRepository deadLetters; private final CredentialRepository credentials; private final PlatformSecretCipher cipher; private final IAuditLogService audit;
 private final WebClient client;
 public IntegrationService(ConnectorRepository connectors, SyncJobRepository jobs, SyncRunRepository runs, SyncItemErrorRepository errors, DeadLetterRepository deadLetters, CredentialRepository credentials, PlatformSecretCipher cipher, IAuditLogService audit, WebClient.Builder builder) { this.connectors=connectors;this.jobs=jobs;this.runs=runs;this.errors=errors;this.deadLetters=deadLetters;this.credentials=credentials;this.cipher=cipher;this.audit=audit;this.client=builder.build(); }
 @Transactional public Connector saveConnector(Connector connector, String actor) {
  validateConnector(connector);
  Connector saved=connectors.save(connector);
  audit.log(actor,"INTEGRATION_CONNECTOR_SAVE",saved.getId());
  return saved;
 }
 @Transactional
 public Credential configureCredential(String connectorId, String keyName, String plaintext, String actor) {
  if (!connectors.existsById(connectorId)) throw new IllegalArgumentException("connector not found");
  if (keyName == null || keyName.isBlank()) throw new IllegalArgumentException("credential key is required");
  if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("secret is required");
  Credential credential = credentials.findByConnectorIdAndKeyName(connectorId, keyName.trim()).orElseGet(Credential::new);
  credential.setConnectorId(connectorId);
  credential.setKeyName(keyName.trim());
  credential.setSecretCiphertext(cipher.encrypt(plaintext));
  Credential saved = credentials.save(credential);
  audit.log(actor, "INTEGRATION_CREDENTIAL_SAVE", connectorId);
  return saved;
 }
 public List<Credential> listCredentials(String connectorId) {
  if (!connectors.existsById(connectorId)) throw new IllegalArgumentException("connector not found");
  return credentials.findAllByConnectorIdOrderByKeyName(connectorId);
 }
 public Connector test(String id) {
  Connector c=connectors.findById(id).orElseThrow();
  validateConnector(c);
  Map<String, String> headers = credentials.findAllByConnectorIdOrderByKeyName(id).stream()
    .collect(java.util.stream.Collectors.toMap(Credential::getKeyName, value -> cipher.decrypt(value.getSecretCiphertext()), (first, ignored) -> first));
  client.get().uri(URI.create(c.getBaseUrl())).headers(target -> headers.forEach(target::set)).retrieve()
    .toBodilessEntity().timeout(Duration.ofMillis(Math.max(1000,c.getTimeoutMs()))).block();
  return c;
 }
 @Transactional public SyncJob saveJob(SyncJob job, String actor) {
  if (!connectors.existsById(job.getConnectorId())) throw new IntegrationBoundaryException("CONNECTOR_NOT_FOUND","connector not found");
  if (job.getCronExpression()==null || job.getCronExpression().isBlank()) throw new IntegrationBoundaryException("INVALID_JOB_CRON","cron expression is required");
  if (job.getRequestPath()==null || job.getRequestPath().isBlank() || !job.getRequestPath().startsWith("/")
    || job.getRequestPath().contains("\r") || job.getRequestPath().contains("\n")
    || URI.create(job.getRequestPath()).isAbsolute()) {
   throw new IntegrationBoundaryException("INVALID_REQUEST_PATH","request path must be a relative HTTP path");
  }
  job.setStatus(job.getStatus()==null?"ENABLED":job.getStatus());
  SyncJob saved=jobs.save(job); audit.log(actor,"INTEGRATION_SYNC_JOB_SAVE",saved.getId()); return saved;
 }
 /** Claims a lease in a short transaction; all network I/O happens after this method returns. */
 public SyncRun startRun(String jobId, String owner) { LocalDateTime now=LocalDateTime.now(); if(jobs.claimLease(jobId,owner,now,now.plusMinutes(5))!=1) throw new IllegalStateException("sync job is leased"); SyncRun run=new SyncRun();run.setJobId(jobId);run.setStartedAt(now);return runs.save(run); }
 public SyncRun execute(String jobId, String owner) {
  SyncRun run=startRun(jobId,owner);
  SyncJob job=jobs.findById(jobId).orElseThrow();
  Connector c=connectors.findById(job.getConnectorId()).orElseThrow();
  int max=Math.max(1,Math.min(10,c.getMaxRetries()==null?3:c.getMaxRetries()));
  String idempotencyKey=job.getIdempotencyKeyTemplate()==null||job.getIdempotencyKeyTemplate().isBlank()?run.getId():job.getIdempotencyKeyTemplate().replace("{runId}",run.getId());
  try {
   for(int attempt=1;attempt<=max;attempt++){
    run.setAttemptCount(attempt);
    try {
     client.get().uri(URI.create(c.getBaseUrl()+job.getRequestPath())).header(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE).header("Idempotency-Key",idempotencyKey).retrieve().toBodilessEntity().timeout(Duration.ofMillis(Math.max(1000,c.getTimeoutMs()))).block();
     run.setSuccessCount(1); run.setStatus("SUCCEEDED"); run.setFinishedAt(LocalDateTime.now()); return runs.save(run);
    } catch(Exception ex) {
     run.setErrorMessage(safeMessage(ex));
     log.warn("Integration request failed: connectorId={}, jobId={}, attempt={}, error={}",
       c.getId(), job.getId(), attempt, run.getErrorMessage());
     if(attempt<max) try { Thread.sleep(Math.min(30000L,250L << Math.min(attempt,7))); } catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
    }
   }
   String message=run.getErrorMessage()==null?"external request failed":run.getErrorMessage();
   SyncItemError item=new SyncItemError(); item.setRunId(run.getId()); item.setItemKey(job.getRequestPath()); item.setErrorMessage(message); item.setAttemptCount(run.getAttemptCount()); item.setStatus("DEAD_LETTER");
   SyncItemError savedError=errors.save(item);
   DeadLetter deadLetter=new DeadLetter(); deadLetter.setItemErrorId(savedError.getId()); deadLetter.setRunId(run.getId()); deadLetter.setLastResult(message); deadLetters.save(deadLetter);
   run.setFailureCount(1); run.setStatus("FAILED"); run.setFinishedAt(LocalDateTime.now()); return runs.save(run);
  } finally {
   releaseLease(jobId, owner);
  }
 }
 public DeadLetter replay(String id, String actor) {
  DeadLetter d=deadLetters.findById(id).orElseThrow();
  if(!"PENDING".equals(d.getStatus())) throw new IllegalStateException("dead letter already replayed");
  SyncRun original=runs.findById(d.getRunId()).orElseThrow();
  d.setReplayCount(d.getReplayCount()+1); d.setStatus("REPLAYING"); d.setLastResult("manual replay started");
  deadLetters.save(d);
  audit.log(actor,"INTEGRATION_DEAD_LETTER_REPLAY_STARTED",id);
  try {
   SyncRun replay=execute(original.getJobId(), actor);
   d.setStatus("SUCCEEDED".equals(replay.getStatus()) ? "REPLAYED" : "PENDING");
   d.setReplayedAt("SUCCEEDED".equals(replay.getStatus()) ? LocalDateTime.now() : null);
   d.setLastResult(replay.getStatus());
  } catch(RuntimeException failure) {
   d.setStatus("PENDING"); d.setLastResult(safeMessage(failure));
   throw failure;
  }
  DeadLetter saved=deadLetters.save(d);
  audit.log(actor,"INTEGRATION_DEAD_LETTER_REPLAY",id);
  return saved;
 }
 void releaseLease(String jobId, String owner) {
  jobs.findById(jobId).ifPresent(job -> {
   if(owner.equals(job.getLeaseOwner())) { job.setLeaseOwner(null); job.setLeaseUntil(null); jobs.save(job); }
  });
 }
 private static void validateConnector(Connector connector) {
  if (connector==null) throw new IntegrationBoundaryException("INVALID_CONNECTOR","connector is required");
  String type=connector.getType()==null?"":connector.getType().trim().toUpperCase(Locale.ROOT);
  if (!"HTTP".equals(type)) {
   throw new IntegrationBoundaryException("UNSUPPORTED_CONNECTOR_TYPE",
     "connector type is not supported by an installed provider: "+(type.isBlank()?"<blank>":type));
  }
  connector.setType(type);
  validateUrl(connector.getBaseUrl());
  validateConfig(connector.getConfigJson());
  if (connector.getTimeoutMs()==null || connector.getTimeoutMs()<1000 || connector.getTimeoutMs()>120000)
   throw new IntegrationBoundaryException("INVALID_CONNECTOR_CONFIG","timeoutMs must be between 1000 and 120000");
  if (connector.getMaxRetries()==null || connector.getMaxRetries()<0 || connector.getMaxRetries()>10)
   throw new IntegrationBoundaryException("INVALID_CONNECTOR_CONFIG","maxRetries must be between 0 and 10");
 }
 private static void validateConfig(String raw) {
  if (raw==null || raw.isBlank()) throw new IntegrationBoundaryException("INVALID_CONNECTOR_CONFIG","configJson is required");
  try {
   JsonNode config=JSON.readTree(raw);
   if (config==null || !config.isObject()) throw new IntegrationBoundaryException("INVALID_CONNECTOR_CONFIG","configJson must be a JSON object");
  } catch (JsonProcessingException e) {
   throw new IntegrationBoundaryException("INVALID_CONNECTOR_CONFIG","configJson must be valid JSON");
  }
 }
 private static void validateUrl(String raw){ try { URI u=URI.create(raw); if(!"http".equalsIgnoreCase(u.getScheme())&& !"https".equalsIgnoreCase(u.getScheme())) throw new IllegalArgumentException(); if(u.getHost()==null) throw new IllegalArgumentException(); } catch(IllegalArgumentException e){throw new IntegrationBoundaryException("INVALID_CONNECTOR_URL","baseUrl must be an HTTP(S) URL with a host");} }
 static String safeMessage(Throwable t){String m=t.getMessage();if(m==null)return t.getClass().getSimpleName();return m.replaceAll("(?i)(authorization|token|password|secret|api[-_]?key)\\s*[:=]\\s*[^,;]+","$1=[REDACTED]").replaceAll("(?i)bearer\\s+[^,;\\s]+","Bearer [REDACTED]").replaceAll("https?://[^\\s]+","[URL_REDACTED]");}
}

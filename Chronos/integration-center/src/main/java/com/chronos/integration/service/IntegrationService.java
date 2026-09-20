package com.chronos.integration.service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import com.chronos.integration.dao.*;
import com.chronos.integration.model.*;
import com.chronos.integration.security.PlatformSecretCipher;
import com.chronos.service.iService.IAuditLogService;

@Service
public class IntegrationService {
 private final ConnectorRepository connectors; private final SyncJobRepository jobs; private final SyncRunRepository runs;
 private final SyncItemErrorRepository errors; private final DeadLetterRepository deadLetters; private final PlatformSecretCipher cipher; private final IAuditLogService audit;
 private final WebClient client;
 public IntegrationService(ConnectorRepository connectors, SyncJobRepository jobs, SyncRunRepository runs, SyncItemErrorRepository errors, DeadLetterRepository deadLetters, PlatformSecretCipher cipher, IAuditLogService audit, WebClient.Builder builder) { this.connectors=connectors;this.jobs=jobs;this.runs=runs;this.errors=errors;this.deadLetters=deadLetters;this.cipher=cipher;this.audit=audit;this.client=builder.build(); }
 @Transactional public Connector saveConnector(Connector connector, String actor) { validateUrl(connector.getBaseUrl()); connector.setType("HTTP"); Connector saved=connectors.save(connector); audit.log(actor,"INTEGRATION_CONNECTOR_SAVE",saved.getId()); return saved; }
 /** Secrets are intentionally not accepted as a plain connector field. This operation fails until KMS is supplied. */
 public void configureCredential(String connectorId, String keyName, String plaintext, String actor) { if (plaintext==null || plaintext.isBlank()) throw new IllegalArgumentException("secret is required"); cipher.encrypt(plaintext); throw new IllegalStateException("credential persistence requires a configured platform cipher"); }
 public Connector test(String id) { Connector c=connectors.findById(id).orElseThrow(); validateUrl(c.getBaseUrl()); client.get().uri(URI.create(c.getBaseUrl())).retrieve().toBodilessEntity().timeout(Duration.ofMillis(Math.max(1000,c.getTimeoutMs()))).block(); return c; }
 @Transactional public SyncJob saveJob(SyncJob job, String actor) { if (!connectors.existsById(job.getConnectorId())) throw new IllegalArgumentException("connector not found"); if (job.getCronExpression()==null || job.getCronExpression().isBlank()) throw new IllegalArgumentException("cron expression is required"); job.setStatus(job.getStatus()==null?"ENABLED":job.getStatus()); SyncJob saved=jobs.save(job); audit.log(actor,"INTEGRATION_SYNC_JOB_SAVE",saved.getId()); return saved; }
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
 private static void validateUrl(String raw){ try { URI u=URI.create(raw); if(!"http".equalsIgnoreCase(u.getScheme())&& !"https".equalsIgnoreCase(u.getScheme())) throw new IllegalArgumentException("only HTTP(S) connectors are supported"); if(u.getHost()==null) throw new IllegalArgumentException("connector URL must include host"); } catch(IllegalArgumentException e){throw new IllegalArgumentException("invalid connector URL",e);} }
 private static String safeMessage(Throwable t){String m=t.getMessage();return m==null? t.getClass().getSimpleName():m.replaceAll("(?i)(authorization|token|password|secret)\\s*[:=]\\s*[^,; ]+","$1=[REDACTED]");}
}

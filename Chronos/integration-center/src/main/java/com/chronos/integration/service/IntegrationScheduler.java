package com.chronos.integration.service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import com.chronos.integration.dao.SyncJobRepository;
@Component
@EnableScheduling
public class IntegrationScheduler {
 private static final Logger log=LoggerFactory.getLogger(IntegrationScheduler.class);
 private final SyncJobRepository jobs;
 private final IntegrationExtensionService extension;
 private final long leaseMs;
 private final String owner = UUID.randomUUID().toString();
 public IntegrationScheduler(SyncJobRepository jobs, IntegrationExtensionService extension,
   @Value("${chronos.integration.lease-ms:300000}") long leaseMs){
  this.jobs=jobs;this.extension=extension;this.leaseMs=Math.max(1000L, leaseMs);
 }
 @Scheduled(fixedDelayString="${chronos.integration.scheduler-delay-ms:60000}")
 public void dispatch(){
  for(var job:jobs.findByStatus("ENABLED")){
   if(jobs.claimLease(job.getId(), owner, LocalDateTime.now(),
      LocalDateTime.now().plus(Duration.ofMillis(leaseMs))) != 1){
    continue;
   }
   try {
    extension.executeBatch(job.getId(),"integration-scheduler");
   } catch(RuntimeException failure) {
    log.error("Integration sync dispatch failed: jobId={}",job.getId(),failure);
   } finally {
    jobs.releaseLease(job.getId(), owner);
   }
  }
 }
}

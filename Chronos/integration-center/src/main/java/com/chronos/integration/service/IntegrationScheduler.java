package com.chronos.integration.service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chronos.integration.dao.SyncJobRepository;
@Component
@EnableScheduling
public class IntegrationScheduler { private static final Logger log=LoggerFactory.getLogger(IntegrationScheduler.class); private final SyncJobRepository jobs; private final IntegrationService service; public IntegrationScheduler(SyncJobRepository jobs,IntegrationService service){this.jobs=jobs;this.service=service;}
 @Scheduled(fixedDelayString="${chronos.integration.scheduler-delay-ms:60000}") public void dispatch(){ for(var job:jobs.findByStatus("ENABLED")){ try { service.execute(job.getId(),"integration-scheduler"); } catch(RuntimeException failure) { log.error("Integration sync dispatch failed: jobId={}",job.getId(),failure); } } }
}

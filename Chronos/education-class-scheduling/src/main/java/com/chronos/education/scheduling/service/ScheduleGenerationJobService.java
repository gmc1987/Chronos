package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.ScheduleGenerationJobRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 负责自动排课任务的持久化状态、租约所有权、后台执行与取消。 */
@Service
public class ScheduleGenerationJobService {
	private static final Logger LOG = LoggerFactory.getLogger(ScheduleGenerationJobService.class);
	private static final int LEASE_SECONDS = 90;
	private static final String INTERRUPTED_MESSAGE =
			"排课任务租约过期；候选方案可能已提交，请核实已生成的候选，再新建 AI Run 重新排课，勿在原 Run 重复提交";
	private static final String COMMITTED_MESSAGE =
			"排课任务租约过期；已有候选方案提交记录，但任务未确认成功，请核实已生成的候选，再新建 AI Run 重新排课，勿在原 Run 重复提交";
	private final ScheduleGenerationJobRepository jobs;
	private final AutoSchedulingService scheduling;
	private final IAuditLogService audit;
	private final TransactionTemplate transactions;
	private final TransactionTemplate progressTransactions;
	private final Executor executor;
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
	private final Map<String, Worker> workers = new ConcurrentHashMap<>();

	public ScheduleGenerationJobService(
			ScheduleGenerationJobRepository jobs,
			AutoSchedulingService scheduling,
			IAuditLogService audit,
			TransactionTemplate transactions,
			@Qualifier("scheduleGenerationExecutor") Executor executor) {
		this.jobs = jobs;
		this.scheduling = scheduling;
		this.audit = audit;
		this.transactions = transactions;
		this.progressTransactions = new TransactionTemplate(transactions.getTransactionManager());
		this.progressTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.executor = executor;
	}

	@PostConstruct
	@Scheduled(fixedDelayString = "${chronos.education.scheduling.job-recovery-ms:60000}")
	public void recoverInterruptedJobs() {
		LocalDateTime now = jobs.databaseTime();
		transactions.executeWithoutResult(status -> jobs
				.findByStatusInAndLeaseExpiresAtLessThanEqual(List.of("QUEUED", "RUNNING"), now)
				.forEach(job -> jobs.failExpired(job.getId(),
						job.getResultCandidateIds() == null ? INTERRUPTED_MESSAGE : COMMITTED_MESSAGE,
						now)));
	}

	@Scheduled(fixedDelayString = "${chronos.education.scheduling.job-heartbeat-ms:10000}")
	public void heartbeatWorkers() {
		workers.forEach((id, worker) -> {
			if (worker.claimed().get() && !worker.cancelled().get()) {
				renew(id, worker);
			}
		});
	}

	public ScheduleGenerationJob submit(AutoScheduleCommand command, String actor) {
		return submit(command, actor, null);
	}

	public ScheduleGenerationJob submit(
			AutoScheduleCommand command,
			String actor,
			String agentRunId) {
		return submit(command, actor, agentRunId, ScheduleRunConstraints.empty());
	}

	public ScheduleGenerationJob submit(
			AutoScheduleCommand command,
			String actor,
			String agentRunId,
			ScheduleRunConstraints runConstraints) {
		if (runConstraints == null || (agentRunId == null
				&& (!runConstraints.teacherSlots().isEmpty()
						|| !runConstraints.offeringDurations().isEmpty()
						|| !runConstraints.weekRules().isEmpty()
						|| !runConstraints.lockedEntries().isEmpty()
						|| !runConstraints.softPriorities().isEmpty()
						|| !runConstraints.slotExclusions().isEmpty()))) {
			throw new IllegalArgumentException("动态排课规则只能由 AI Run 提交");
		}
		String requestJson = agentRunId == null
				? write(command)
				: write(Map.of("command", command, "runConstraints", runConstraints));
		Submission submission;
		try {
			submission = transactions.execute(status -> {
				if (agentRunId != null) {
					var existing = jobs.findByAgentRunId(agentRunId);
					if (existing.isPresent()) {
						return new Submission(sameJob(existing.get(), requestJson, command, actor), false);
					}
				}
				ScheduleGenerationJob value = new ScheduleGenerationJob();
				value.setSemesterCode(command.semesterCode());
				value.setRequestJson(requestJson);
				value.setRequestedBy(actor);
				value.setAgentRunId(agentRunId);
				value.setLeaseExpiresAt(jobs.databaseTime().plusSeconds(LEASE_SECONDS));
				return new Submission(jobs.save(value), true);
			});
		} catch (DataIntegrityViolationException exception) {
			if (agentRunId == null) {
				throw exception;
			}
			ScheduleGenerationJob concurrent = jobs.findByAgentRunId(agentRunId)
					.orElseThrow(() -> exception);
			return sameJob(concurrent, requestJson, command, actor);
		}
		ScheduleGenerationJob job = submission.job();
		if (!submission.created()) {
			return job;
		}
		Runnable start = () -> start(job.getId(), command, actor, runConstraints);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					start.run();
				}
			});
		} else {
			start.run();
		}
		audit.log(actor, "EDUCATION_SCHEDULE_JOB_SUBMIT", "job=" + job.getId());
		return job;
	}

	private void start(String id, AutoScheduleCommand command, String actor,
			ScheduleRunConstraints runConstraints) {
		Worker worker = new Worker(UUID.randomUUID().toString(),
				new AtomicBoolean(false), new AtomicBoolean(false));
		workers.put(id, worker);
		try {
			executor.execute(() -> execute(id, command, actor, runConstraints, worker));
		} catch (RuntimeException exception) {
			workers.remove(id, worker);
			transactions.executeWithoutResult(status ->
					jobs.failQueued(id, safeMessage(exception), jobs.databaseTime()));
			throw exception;
		}
	}

	public ScheduleGenerationJob require(String id) {
		return jobs.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("排课任务不存在"));
	}

	public List<ScheduleGenerationJob> list(String semesterCode) {
		return jobs.findTop20BySemesterCodeOrderByCreateTimeDesc(semesterCode);
	}

	public ScheduleGenerationJob cancel(String id, String actor) {
		ScheduleGenerationJob job = transactions.execute(status -> {
			if (jobs.cancelActive(id, "由 " + actor + " 取消", jobs.databaseTime()) != 1) {
				require(id);
				throw new IllegalStateException("当前任务状态不能取消");
			}
			return require(id);
		});
		Worker worker = workers.get(id);
		if (worker != null) {
			worker.cancelled().set(true);
		}
		audit.log(actor, "EDUCATION_SCHEDULE_JOB_CANCEL", "job=" + id);
		return job;
	}

	private void execute(
			String id,
			AutoScheduleCommand command,
			String actor,
			ScheduleRunConstraints runConstraints,
			Worker worker) {
		try {
			LocalDateTime now = jobs.databaseTime();
			if (transactions.execute(status -> jobs.claim(id, worker.owner(), now,
					now.plusSeconds(LEASE_SECONDS))) != 1) {
				return;
			}
			worker.claimed().set(true);
			List<ScheduleCandidateView> result = scheduling.generate(
					command,
					actor,
					() -> worker.cancelled().get(),
					completed -> updateProgress(id, worker, 10 + completed * 80 / 100),
					runConstraints);
			// generate() commits its candidates before returning; a lost lease cannot claim success.
			LocalDateTime finished = jobs.databaseTime();
			String resultIds = write(result.stream().map(ScheduleCandidateView::id).toList());
			if (transactions.execute(status -> jobs.recordCommittedCandidates(id, worker.owner(),
					resultIds, finished, finished.plusSeconds(LEASE_SECONDS))) != 1) {
				worker.cancelled().set(true);
				return;
			}
			int updated = transactions.execute(status -> jobs.succeed(id, worker.owner(),
					resultIds, jobs.databaseTime()));
			if (updated != 1) {
				worker.cancelled().set(true);
			}
		} catch (RuntimeException exception) {
			if (!worker.cancelled().get()) {
				LocalDateTime failed = jobs.databaseTime();
				transactions.executeWithoutResult(status ->
						jobs.failOwned(id, worker.owner(), safeMessage(exception), failed));
			}
		} finally {
			workers.remove(id, worker);
		}
	}

	private void updateProgress(String id, Worker worker, int progress) {
		LocalDateTime now = jobs.databaseTime();
		int updated = progressTransactions.execute(status -> jobs.progress(id, worker.owner(),
				progress, now, now.plusSeconds(LEASE_SECONDS)));
		if (updated != 1) {
			worker.cancelled().set(true);
			throw new IllegalStateException("排课任务已取消或租约失效");
		}
	}

	private void renew(String id, Worker worker) {
		LocalDateTime now = jobs.databaseTime();
		try {
			if (progressTransactions.execute(status -> jobs.heartbeat(id, worker.owner(),
					now, now.plusSeconds(LEASE_SECONDS))) != 1) {
				worker.cancelled().set(true);
			}
		} catch (RuntimeException exception) {
			LOG.error("排课任务续租失败，任务将在租约失效后停止：job={}", id, exception);
		}
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("排课任务参数序列化失败", exception);
		}
	}

	private boolean sameRequest(String requested, String persisted) {
		try {
			return json.readTree(requested).equals(json.readTree(persisted));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("排课任务请求快照损坏", exception);
		}
	}

	private ScheduleGenerationJob sameJob(ScheduleGenerationJob previous, String request,
			AutoScheduleCommand command, String actor) {
		if (!actor.equals(previous.getRequestedBy())
				|| !command.semesterCode().equals(previous.getSemesterCode())
				|| !sameRequest(request, previous.getRequestJson())) {
			throw new IllegalStateException("AI Run 已提交不同的排课任务");
		}
		return previous;
	}

	private record Submission(ScheduleGenerationJob job, boolean created) {
	}

	private record Worker(String owner, AtomicBoolean cancelled, AtomicBoolean claimed) {
	}

	private String safeMessage(RuntimeException exception) {
		String message = exception.getMessage();
		return message == null ? "自动排课执行失败" : message.substring(0, Math.min(message.length(), 2000));
	}
}

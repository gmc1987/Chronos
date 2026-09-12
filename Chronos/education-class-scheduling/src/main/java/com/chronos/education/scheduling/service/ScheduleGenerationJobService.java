package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.ScheduleGenerationJobRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

/** 负责自动排课任务的持久化状态、后台执行与进程内取消。 */
@Service
public class ScheduleGenerationJobService {
	private final ScheduleGenerationJobRepository jobs;
	private final AutoSchedulingService scheduling;
	private final IAuditLogService audit;
	private final TransactionTemplate transactions;
	private final TransactionTemplate progressTransactions;
	private final Executor executor;
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
	private final Map<String, CompletableFuture<Void>> running = new ConcurrentHashMap<>();
	private final Map<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

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
	public void recoverInterruptedJobs() {
		transactions.executeWithoutResult(status -> jobs.findByStatusIn(List.of("QUEUED", "RUNNING"))
				.forEach(job -> {
					job.setStatus("FAILED");
					job.setErrorMessage("应用重启导致排课任务中断，请重新提交");
					job.setFinishedAt(LocalDateTime.now());
					jobs.save(job);
				}));
	}

	public ScheduleGenerationJob submit(AutoScheduleCommand command, String actor) {
		ScheduleGenerationJob job = transactions.execute(status -> {
			ScheduleGenerationJob value = new ScheduleGenerationJob();
			value.setSemesterCode(command.semesterCode());
			value.setRequestJson(write(command));
			value.setRequestedBy(actor);
			return jobs.save(value);
		});
		cancellationFlags.put(job.getId(), new AtomicBoolean(false));
		CompletableFuture<Void> future = CompletableFuture.runAsync(
				() -> execute(job.getId(), command, actor),
				executor);
		running.put(job.getId(), future);
		audit.log(actor, "EDUCATION_SCHEDULE_JOB_SUBMIT", "job=" + job.getId());
		return job;
	}

	public List<ScheduleGenerationJob> list(String semesterCode) {
		return jobs.findTop20BySemesterCodeOrderByCreateTimeDesc(semesterCode);
	}

	public ScheduleGenerationJob cancel(String id, String actor) {
		ScheduleGenerationJob job = transactions.execute(status -> {
			ScheduleGenerationJob value = require(id);
			if (!List.of("QUEUED", "RUNNING").contains(value.getStatus())) {
				throw new IllegalStateException("当前任务状态不能取消");
			}
			value.setStatus("CANCELLED");
			value.setErrorMessage("由 " + actor + " 取消");
			value.setFinishedAt(LocalDateTime.now());
			return jobs.save(value);
		});
		cancellationFlags.computeIfAbsent(id, key -> new AtomicBoolean()).set(true);
		audit.log(actor, "EDUCATION_SCHEDULE_JOB_CANCEL", "job=" + id);
		return job;
	}

	private void execute(String id, AutoScheduleCommand command, String actor) {
		try {
			update(id, "RUNNING", 10, null, null);
			List<ScheduleCandidateView> result = scheduling.generate(
					command,
					actor,
					() -> cancellationFlags.getOrDefault(id, new AtomicBoolean()).get(),
					completed -> updateProgress(id, 10 + completed * 80 / 100));
			if (isCancelled(id)) {
				return;
			}
			update(
					id,
					"SUCCEEDED",
					100,
					write(result.stream().map(ScheduleCandidateView::id).toList()),
					null);
		} catch (RuntimeException exception) {
			if (!isCancelled(id)) {
				update(id, "FAILED", 100, null, safeMessage(exception));
			}
		} finally {
			running.remove(id);
			cancellationFlags.remove(id);
		}
	}

	private void updateProgress(String id, int progress) {
		progressTransactions.executeWithoutResult(status -> {
			ScheduleGenerationJob job = require(id);
			if ("RUNNING".equals(job.getStatus())) {
				job.setProgress(progress);
				jobs.save(job);
			}
		});
	}

	private void update(
			String id,
			String state,
			int progress,
			String resultIds,
			String error) {
		transactions.executeWithoutResult(status -> {
			ScheduleGenerationJob job = require(id);
			if ("CANCELLED".equals(job.getStatus())) {
				return;
			}
			job.setStatus(state);
			job.setProgress(progress);
			job.setResultCandidateIds(resultIds);
			job.setErrorMessage(error);
			if ("RUNNING".equals(state)) {
				job.setStartedAt(LocalDateTime.now());
			}
			if (List.of("SUCCEEDED", "FAILED").contains(state)) {
				job.setFinishedAt(LocalDateTime.now());
			}
			jobs.save(job);
		});
	}

	private boolean isCancelled(String id) {
		return jobs.findById(id).map(job -> "CANCELLED".equals(job.getStatus())).orElse(true);
	}

	private ScheduleGenerationJob require(String id) {
		return jobs.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("排课任务不存在"));
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("排课任务参数序列化失败", exception);
		}
	}

	private String safeMessage(RuntimeException exception) {
		String message = exception.getMessage();
		return message == null ? "自动排课执行失败" : message.substring(0, Math.min(message.length(), 2000));
	}
}

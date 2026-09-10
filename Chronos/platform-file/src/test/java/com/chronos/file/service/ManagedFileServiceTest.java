package com.chronos.file.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.model.ManagedFile;
import com.chronos.file.storage.ManagedFileStorage;
import com.chronos.service.iService.IAuditLogService;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ManagedFileServiceTest {
	private ManagedFileRepository files;
	private ManagedFileStorage storage;
	private ManagedFileAccessPolicy policy;
	private ManagedFileService service;

	@BeforeEach
	void setUp() {
		files = mock(ManagedFileRepository.class);
		storage = mock(ManagedFileStorage.class);
		policy = mock(ManagedFileAccessPolicy.class);
		service = new ManagedFileService(
				files,
				storage,
				mock(IAuditLogService.class),
				List.of(policy),
				new ManagedFileSecurityValidator());
	}

	@Test
	void rejectsFileReferenceBoundToAnotherWorkflow() {
		ManagedFile file = workflowFile("workflow-other");
		when(files.findById("file-1")).thenReturn(Optional.of(file));

		assertThatThrownBy(() -> service.requireBound(
				List.of("file-1"),
				"WORKFLOW_FORM",
				"workflow-current"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("表单包含不属于当前业务的附件");
	}

	@Test
	void boundFileOwnerCannotDeleteAfterWorkflowBecomesReadOnly() {
		ManagedFile file = workflowFile("workflow-1");
		when(files.findById("file-1")).thenReturn(Optional.of(file));
		when(policy.canWrite("owner", "WORKFLOW_FORM", "workflow-1"))
				.thenReturn(false);

		assertThatThrownBy(() -> service.delete("file-1", authentication("owner")))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("当前业务状态不允许删除该文件");
		verify(storage, never()).delete(file.getStorageKey());
	}

	@Test
	void currentEditableFieldHandlerCanDeleteBoundFile() {
		ManagedFile file = workflowFile("workflow-1");
		when(files.findById("file-1")).thenReturn(Optional.of(file));
		when(policy.canWrite("approver", "WORKFLOW_FORM", "workflow-1", "file-1"))
				.thenReturn(true);

		service.delete("file-1", authentication("approver"));

		verify(storage).delete(file.getStorageKey());
		verify(files).save(file);
	}

	@Test
	void cleanupOnlyProcessesExpiredUnboundDraftsAndContinuesAfterStorageFailure() {
		ManagedFile first = draftFile("file-1");
		ManagedFile second = draftFile("file-2");
		LocalDateTime cutoff = LocalDateTime.of(2026, 9, 9, 12, 0);
		when(files.findTop100ByBusinessTypeAndBusinessIdIsNullAndStatusAndCreateTimeBeforeOrderByCreateTimeAsc(
				eq("WORKFLOW_FORM_DRAFT"),
				eq("ACTIVE"),
				eq(cutoff)))
				.thenReturn(List.of(first, second));
		doThrow(new IllegalStateException("MinIO unavailable"))
				.when(storage)
				.delete(first.getStorageKey());

		ManagedFileService.DraftCleanupResult result = service.cleanupExpiredDrafts(cutoff);

		assertThat(result.scanned()).isEqualTo(2);
		assertThat(result.deleted()).isEqualTo(1);
		assertThat(result.failed()).isEqualTo(1);
		assertThat(first.getStatus()).isEqualTo("ACTIVE");
		assertThat(second.getStatus()).isEqualTo("DELETED");
		verify(files).save(second);
		verify(files, never()).save(first);
	}

	private ManagedFile workflowFile(String workflowId) {
		ManagedFile file = new ManagedFile();
		file.setId("file-1");
		file.setStorageKey("2026-09-10/file-1.bin");
		file.setOwnerUsername("owner");
		file.setBusinessType("WORKFLOW_FORM");
		file.setBusinessId(workflowId);
		file.setStatus("ACTIVE");
		return file;
	}

	private ManagedFile draftFile(String id) {
		ManagedFile file = new ManagedFile();
		file.setId(id);
		file.setStorageKey("2026-09-10/" + id + ".bin");
		file.setOwnerUsername("owner");
		file.setBusinessType("WORKFLOW_FORM_DRAFT");
		file.setStatus("ACTIVE");
		return file;
	}

	private UsernamePasswordAuthenticationToken authentication(String username) {
		return new UsernamePasswordAuthenticationToken(username, "", List.of());
	}
}

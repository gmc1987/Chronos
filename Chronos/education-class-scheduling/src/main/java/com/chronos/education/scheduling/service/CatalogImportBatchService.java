package com.chronos.education.scheduling.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.education.academic.service.AcademicLifecycleService;
import com.chronos.education.academic.service.TermCoursePlanService;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.service.GradeCenterService;
import com.chronos.education.grade.service.GradeExcelService;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.service.AcademicCatalogImportService.Kind;
import com.chronos.education.scheduling.service.AcademicCatalogImportService.Result;
import com.chronos.file.service.ManagedFileService.FileContent;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.security.IamAuthorization;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.impl.DirectoryImportService;
import com.chronos.service.impl.EmployeeImportService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CatalogImportBatchService {
	private static final String VERSION = "1.0";
	private static final String BATCH_SCOPE = """
			kind = ? and organization_id = ? and term_code = ? and resource_id = ? and operator_name = ?
			""";

	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final IamAuthorization permissions;
	private final DirectoryDataScopeGuard directory;
	private final IOrganizationRepository organizations;
	private final AcademicTermRepository terms;
	private final AcademicCatalogImportService catalog;
	private final AcademicLifecycleService lifecycle;
	private final TermCoursePlanService plans;
	private final ClassroomImportService classrooms;
	private final AdmissionImportService admissions;
	private final AccommodationImportService accommodation;
	private final EducationImportArchive archive;
	private final IAuditLogService audit;
	private final GradeCenterService grades;
	private final GradeExcelService gradeExcel;
	private final GradebookRepository gradebooks;
	private final QuestionKnowledgeService questions;
	private final DirectoryImportService directoryImports;

	public CatalogImportBatchService(JdbcTemplate jdbc, ObjectMapper json, IamAuthorization permissions,
			DirectoryDataScopeGuard directory, IOrganizationRepository organizations, AcademicTermRepository terms,
			AcademicCatalogImportService catalog, AcademicLifecycleService lifecycle, TermCoursePlanService plans,
			ClassroomImportService classrooms, AdmissionImportService admissions, AccommodationImportService accommodation,
			EducationImportArchive archive, IAuditLogService audit, GradeCenterService grades, GradeExcelService gradeExcel,
			GradebookRepository gradebooks, QuestionKnowledgeService questions, DirectoryImportService directoryImports) {
		this.jdbc = jdbc;
		this.json = json;
		this.permissions = permissions;
		this.directory = directory;
		this.organizations = organizations;
		this.terms = terms;
		this.catalog = catalog;
		this.lifecycle = lifecycle;
		this.plans = plans;
		this.classrooms = classrooms;
		this.admissions = admissions;
		this.accommodation = accommodation;
		this.archive = archive;
		this.audit = audit;
		this.grades = grades;
		this.gradeExcel = gradeExcel;
		this.gradebooks = gradebooks;
		this.questions = questions;
		this.directoryImports = directoryImports;
	}

	public record Scope(String kind, String organizationId, String termCode, String resourceId) {}
	public record Batch(String id, String templateVersion, String managedFileId, String fileDigest,
			Instant expiresAt, Instant committedAt, Result result, List<Object> accountResults) {}
	private record StoredBatch(Batch batch, String revision) {}
	public record Summary(String id, int rowCount, int errorCount, Instant expiresAt, Instant committedAt) {}
	public record Page(List<Summary> content, boolean hasMore) {}

	@Transactional(rollbackFor = IOException.class)
	public Batch preview(Scope scope, String version, MultipartFile file, Authentication actor) throws IOException {
		scope = requireScope(scope, actor);
		requireVersion(version);
		if ("QUESTIONS".equals(scope.kind())) {
			archive.validateCsv(file);
		} else {
			archive.validate(file, "GRADEBOOKS".equals(scope.kind()) || scope.kind().startsWith("DIRECTORY_")
					? 10L * 1024 * 1024 : 5L * 1024 * 1024);
		}
		String revision = gradeRevision(scope, actor);
		Result result = apply(scope, file, true, actor, new java.util.ArrayList<>());
		String id = UUID.randomUUID().toString();
		var source = archive.store(id, file, actor);
		Instant expiry = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
		jdbc.update("""
				insert into edu_catalog_import_batch
				(id, kind, organization_id, term_code, resource_id, operator_name, template_version, source_file_id, file_digest,
				 expires_at, source_revision, result)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
				""", id, scope.kind(), scope.organizationId(), scope.termCode(), scope.resourceId(), actor.getName(), version, source.id(),
				source.sha256(), Timestamp.from(expiry), revision, json.writeValueAsString(result));
		return new Batch(id, version, source.id(), source.sha256(), expiry, null, result, List.of());
	}

	@Transactional(rollbackFor = IOException.class)
	public Batch commit(Scope scope, String id, String version, Authentication actor) throws IOException {
		scope = requireScope(scope, actor);
		requireVersion(version);
		StoredBatch stored = batch(scope, id, actor, true);
		Batch batch = stored.batch();
		if (!version.equals(batch.templateVersion())) throw new IllegalArgumentException("模板已更新，请重新预检");
		if (batch.committedAt() != null) return batch;
		if (!batch.result().errors().isEmpty() || !Instant.now().isBefore(batch.expiresAt())) {
			throw new IllegalArgumentException("预检未通过或已过期，请重新校验");
		}
		if ("GRADEBOOKS".equals(scope.kind())) {
			gradebooks.findByIdForUpdate(scope.resourceId()).orElseThrow(() -> new IllegalArgumentException("成绩册不存在"));
			if (!java.util.Objects.equals(stored.revision(), gradeRevision(scope, actor))) {
				throw new IllegalStateException("成绩册已发生变化，请重新预检");
			}
		}
		var source = archive.read(id, batch.managedFileId(), batch.fileDigest(), actor);
		var accountResults = new java.util.ArrayList<Object>();
		Result result = apply(scope, new ArchivedWorkbook(source), false, actor, accountResults);
		if (!result.errors().isEmpty()) {
			throw new IllegalArgumentException("数据已发生变化，请重新校验。" + result.errors().getFirst());
		}
		Instant committed = Instant.now().truncatedTo(ChronoUnit.MICROS);
		jdbc.update("update edu_catalog_import_batch set committed_at=?, result=cast(? as jsonb) where id=?",
				Timestamp.from(committed), json.writeValueAsString(result), id);
		audit.log(actor.getName(), "EDUCATION_CATALOG_IMPORT",
				"批次 " + id + "，" + scope.kind() + " " + result.validCount() + " 条", scope.organizationId());
		return new Batch(id, version, batch.managedFileId(), batch.fileDigest(), batch.expiresAt(), committed, result, List.copyOf(accountResults));
	}

	@Transactional(readOnly = true)
	public Batch detail(Scope scope, String id, Authentication actor) throws IOException {
		return batch(requireScope(scope, actor), id, actor, false).batch();
	}

	@Transactional(readOnly = true)
	public Page history(Scope scope, int page, Authentication actor) {
		scope = requireScope(scope, actor);
		int size = 20;
		var rows = jdbc.query("""
				select id, (result->>'rowCount')::int as row_count, jsonb_array_length(result->'errors') as error_count,
				       expires_at, committed_at from edu_catalog_import_batch
				where
				""" + BATCH_SCOPE + " order by expires_at desc, id limit ? offset ?",
				(row, index) -> new Summary(row.getString("id"), row.getInt("row_count"), row.getInt("error_count"),
						row.getTimestamp("expires_at").toInstant(), instant(row.getTimestamp("committed_at"))),
				scope.kind(), scope.organizationId(), scope.termCode(), scope.resourceId(), actor.getName(), size + 1, Math.max(0, page) * (long) size);
		return new Page(rows.stream().limit(size).toList(), rows.size() > size);
	}

	@Transactional(readOnly = true)
	public FileContent source(Scope scope, String id, Authentication actor) throws IOException {
		Batch batch = detail(scope, id, actor);
		return archive.read(id, batch.managedFileId(), batch.fileDigest(), actor);
	}

	private StoredBatch batch(Scope scope, String id, Authentication actor, boolean lock) throws IOException {
		var rows = jdbc.query("""
				select id, template_version, source_file_id, file_digest, expires_at, committed_at, result, source_revision
				from edu_catalog_import_batch where id = ? and
				""" + BATCH_SCOPE + (lock ? " for update" : ""),
				(row, index) -> new StoredBatch(new Batch(row.getString("id"), row.getString("template_version"), row.getString("source_file_id"),
						row.getString("file_digest"), row.getTimestamp("expires_at").toInstant(),
						instant(row.getTimestamp("committed_at")), readResult(row.getString("result")), List.of()), row.getString("source_revision")),
				id, scope.kind(), scope.organizationId(), scope.termCode(), scope.resourceId(), actor.getName());
		return rows.stream().findFirst().orElseThrow(() -> new AccessDeniedException("无权访问该导入批次"));
	}

	private Result readResult(String snapshot) {
		try {
			return json.readValue(snapshot, Result.class);
		} catch (IOException exception) {
			throw new IllegalStateException("导入结果无法读取", exception);
		}
	}

	private static Instant instant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	private Scope requireScope(Scope scope, Authentication actor) {
		String kind = scope.kind();
		if ("GRADEBOOKS".equals(kind)) {
			require(actor, "education:score:gradebook:import");
			String resource = requiredResource(scope, "请选择成绩册");
			String school = grades.gradebookSchool(resource, actor.getName());
			if (scope.organizationId() != null && !scope.organizationId().isBlank()
					&& !school.equals(scope.organizationId())) {
				throw new AccessDeniedException("成绩册不属于当前机构");
			}
			return new Scope(kind, school, "", resource);
		}
		if ("QUESTIONS".equals(kind)) {
			require(actor, "education:question-bank:manage", "education:teaching:manage");
			String resource = requiredResource(scope, "请选择题库");
			String school = questions.importBankSchool(resource, actor);
			if (scope.organizationId() != null && !scope.organizationId().isBlank()
					&& !school.equals(scope.organizationId())) {
				throw new AccessDeniedException("题库不属于当前机构");
			}
			return new Scope(kind, school, "", resource);
		}
		String organizationId = directory.resolveOrganization(actor, scope.organizationId());
		if (kind.startsWith("DIRECTORY_")) {
			require(actor, "iam:directory:import", "iam:directory:manage");
			directoryType(kind);
			String mode = scope.resourceId() == null || scope.resourceId().isBlank() ? "0:1:EXCEL" : scope.resourceId();
			var options = directoryOptions(mode, true);
			if (options.syncAccounts()) require(actor, "iam:directory:account-provision", "iam:directory:manage");
			return new Scope(kind, organizationId, "", mode);
		}
		String term = "";
		switch (kind) {
			case "ADMISSIONS" -> require(actor, "education:admission:import", "education:admission:manage");
			case "ACCOMMODATION_RESOURCES" -> require(actor, "education:accommodation:manage");
			case "PROGRAMS", "PROGRAM_COURSES" -> require(actor, "education:academic-program:manage");
			case "TERM_COURSE_PLANS" -> {
				require(actor, "education:term-course-plan:manage");
				term = scope.termCode();
				if (term == null || terms.findByTermCode(term).filter(value -> organizationId.equals(value.getOrganizationId())).isEmpty()) {
					throw new AccessDeniedException("学期不属于当前机构");
				}
			}
			case "CLASSROOMS" -> {
				require(actor, "education:resource:venue:create", "education:resource:venue:manage");
				var organization = organizations.findById(organizationId).orElseThrow(() -> new IllegalArgumentException("校区不存在"));
				if (!List.of("SCHOOL", "CAMPUS").contains(organization.getOrganizationType())) {
					throw new IllegalArgumentException("请选择学校或校区");
				}
			}
			default -> AcademicCatalogPermissions.requireCreate(permissions, Kind.valueOf(kind), actor);
		}
		return new Scope(kind, organizationId, term, "");
	}

	private String requiredResource(Scope scope, String message) {
		if (scope.resourceId() == null || scope.resourceId().isBlank()) throw new IllegalArgumentException(message);
		return scope.resourceId();
	}

	private String gradeRevision(Scope scope, Authentication actor) throws IOException {
		if (!"GRADEBOOKS".equals(scope.kind())) return null;
		var detail = grades.getGradebook(scope.resourceId(), actor.getName());
		return EducationImportArchive.sha256(json.writeValueAsBytes(List.of(
				detail.status(), detail.rowVersion(),
				detail.students().stream().sorted(java.util.Comparator.comparing(student -> student.studentId())).toList(),
				detail.components().stream().sorted(java.util.Comparator.comparing(component -> component.id())).toList(),
				detail.items().stream().sorted(java.util.Comparator.comparing(item -> item.id())).toList())));
	}

	private void require(Authentication actor, String... options) {
		if (!permissions.any(actor, options)) throw new AccessDeniedException("没有该业务的批量导入权限");
	}

	private void requireVersion(String version) {
		if (!VERSION.equals(version)) throw new IllegalArgumentException("模板版本不匹配，请重新下载");
	}

	private Result apply(Scope scope, MultipartFile file, boolean dryRun, Authentication actor,
			List<Object> accountResults) throws IOException {
		if (scope.kind().startsWith("DIRECTORY_")) {
			String type = directoryType(scope.kind());
			var options = directoryOptions(scope.resourceId(), dryRun);
			var result = dryRun
					? directoryImports.previewArchivedImport(type, file.getBytes(), scope.organizationId(), options)
					: directoryImports.importFile(type, file, false, scope.organizationId(), options);
			if (!dryRun && result.get("accountResults") instanceof List<?> accounts) accountResults.addAll(accounts);
			int count = ((Number) result.getOrDefault("successCount", 0)).intValue();
			List<String> errors = result.get("errors") instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
			return nonEmpty(count, count, errors);
		}
		if ("QUESTIONS".equals(scope.kind())) {
			String csv;
			try {
				csv = java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(file.getBytes())).toString();
			} catch (java.nio.charset.CharacterCodingException exception) {
				throw new IllegalArgumentException("CSV 文件请使用 UTF-8 编码", exception);
			}
			return questions.importScopedCsv(scope.resourceId(), csv, dryRun, actor);
		}
		if ("GRADEBOOKS".equals(scope.kind())) {
			var result = gradeExcel.importFile(scope.resourceId(), file, dryRun, actor.getName());
			var errors = ((List<?>) result.get("errors")).stream().map(String::valueOf).toList();
			int count = ((Number) result.get("imported")).intValue();
			return nonEmpty(count, count, errors);
		}
		if ("ADMISSIONS".equals(scope.kind())) {
			var result = admissions.importFile(file, dryRun, scope.organizationId());
			return nonEmpty(result.totalCount(), result.successCount(), result.errors());
		}
		if ("ACCOMMODATION_RESOURCES".equals(scope.kind())) {
			var result = accommodation.importFile(file, dryRun, scope.organizationId());
			return nonEmpty(result.totalCount(), result.successCount(), result.errors());
		}
		if ("CLASSROOMS".equals(scope.kind())) {
			var result = classrooms.run(scope.organizationId(), file, dryRun);
			return new Result(result.rowCount(), result.validCount(), result.errors());
		}
		Kind kind = Kind.valueOf(scope.kind());
		return switch (kind) {
			case PROGRAMS, PROGRAM_COURSES, TERM_COURSE_PLANS -> {
				var rows = catalog.readRows(kind, file);
				var errors = kind == Kind.TERM_COURSE_PLANS
						? plans.importRows(scope.organizationId(), scope.termCode(), rows, dryRun, actor.getName())
						: lifecycle.importProgramRows(scope.organizationId(), rows, kind == Kind.PROGRAM_COURSES, dryRun, actor.getName());
				yield new Result(rows.size(), rows.size() - errors.size(), errors);
			}
			default -> catalog.run(kind, file, dryRun, scope.organizationId(), actor,
					kind == Kind.ASSIGNMENTS && permissions.any(actor,
							"education:teaching-assignment:update", "education:teaching-assignment:manage"));
		};
	}

	private String directoryType(String kind) {
		return switch (kind) {
			case "DIRECTORY_DEPARTMENTS" -> "departments";
			case "DIRECTORY_POSITIONS" -> "positions";
			case "DIRECTORY_JOB_LEVELS" -> "job-levels";
			case "DIRECTORY_EMPLOYEES" -> "employees";
			default -> throw new IllegalArgumentException("不支持的组织目录导入类型");
		};
	}

	private EmployeeImportService.ImportOptions directoryOptions(String mode, boolean dryRun) {
		String[] parts = mode.split(":", -1);
		if (parts.length != 3 || !List.of("0", "1").contains(parts[0]) || !List.of("0", "1").contains(parts[1])
				|| !parts[2].matches("[A-Za-z0-9_\\-]{1,40}")) {
			throw new IllegalArgumentException("员工导入选项无效");
		}
		return new EmployeeImportService.ImportOptions("1".equals(parts[0]), "1".equals(parts[1]), parts[2], dryRun);
	}

	private Result nonEmpty(int total, int valid, List<String> errors) {
		return new Result(total, valid, total == 0 && errors.isEmpty() ? List.of("工作表没有可导入的数据") : errors);
	}

	private record ArchivedWorkbook(FileContent source) implements MultipartFile {
		@Override public String getName() { return "file"; }
		@Override public String getOriginalFilename() { return source.filename(); }
		@Override public String getContentType() { return source.contentType(); }
		@Override public boolean isEmpty() { return source.content().length == 0; }
		@Override public long getSize() { return source.content().length; }
		@Override public byte[] getBytes() { return source.content(); }
		@Override public ByteArrayInputStream getInputStream() { return new ByteArrayInputStream(source.content()); }
		@Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), source.content()); }
	}
}

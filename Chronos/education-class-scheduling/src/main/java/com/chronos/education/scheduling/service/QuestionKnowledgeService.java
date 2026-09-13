package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.model.dto.QuestionDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronos.file.service.ManagedFileService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Strongly typed third-slice application service. It deliberately has no
 * assignment, submission, grading or examination operations. */
@Service
@Transactional
public class QuestionKnowledgeService {
	private static final Set<String> TYPES = Set.of(
			"SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK",
			"SHORT_ANSWER", "PRACTICAL");
	private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
	private final QuestionBankRepository banks;
	private final QuestionRepository questions;
	private final QuestionOptionRepository options;
	private final QuestionKnowledgePointRepository links;
	private final KnowledgePointRepository points;
	private final QuestionVersionRepository versions;
	private final QuestionReferenceRepository references;
	private final QuestionFileRepository files;
	private final ManagedFileService managedFiles;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;
	private final ObjectMapper json;

	public QuestionKnowledgeService(QuestionBankRepository banks, QuestionRepository questions,
			QuestionOptionRepository options, QuestionKnowledgePointRepository links,
			KnowledgePointRepository points, QuestionVersionRepository versions,
			QuestionReferenceRepository references, QuestionFileRepository files,
			ManagedFileService managedFiles, CourseOfferingRepository offerings,
			EducationDataScopeService scopes, ObjectMapper json) {
		this.banks = banks; this.questions = questions; this.options = options; this.links = links;
		this.points = points; this.versions = versions; this.references = references;
		this.files = files; this.managedFiles = managedFiles;
		this.offerings = offerings; this.scopes = scopes; this.json = json;
	}

	public QuestionBank createBank(BankRequest request, Authentication user) {
		require(request != null && text(request.name()) != null, "题库名称不能为空");
		if (request.name().length() > 200) throw new IllegalArgumentException("题库名称不能超过200字");
		EducationDataScope scope = scopes.resolve(user.getName());
		validateOffering(request.offeringId(), request.courseId(), scope);
		QuestionBank bank = new QuestionBank();
		bank.setName(request.name().trim()); bank.setOfferingId(blank(request.offeringId()));
		bank.setCourseId(blank(request.courseId())); bank.setDescription(request.description());
		bank.setVisibility(defaultValue(request.visibility(), "PRIVATE"));
		return banks.save(bank);
	}

	public Question createQuestion(QuestionRequest request, Authentication user) {
		validateQuestion(request, user);
		QuestionBank bank = banks.findById(request.bankId()).orElseThrow(() -> new IllegalArgumentException("题库不存在"));
		authorizeBank(bank, user);
		Question q = new Question();
		copyQuestion(q, request);
		q.setBankId(bank.getId());
		q = questions.save(q);
		replaceChildren(q, request);
		bank.setQuestionCount(bank.getQuestionCount() + 1); banks.save(bank);
		return q;
	}

	public Question updateQuestion(String id, QuestionRequest request, Authentication user) {
		Question current = questions.findById(id).orElseThrow(() -> new IllegalArgumentException("题目不存在"));
		QuestionBank bank = banks.findById(current.getBankId()).orElseThrow(() -> new IllegalArgumentException("题库不存在"));
		authorizeBank(bank, user);
		validateQuestion(request, user);
		if (!Objects.equals(request.bankId(), current.getBankId())) throw new IllegalArgumentException("不能跨题库移动题目");
		if (references.existsByQuestionId(id)) {
			Question replacement = new Question(); copyQuestion(replacement, request);
			replacement.setBankId(current.getBankId()); replacement.setCurrentVersionNo(current.getCurrentVersionNo() + 1);
			replacement = questions.save(replacement);
			replaceChildren(replacement, request);
			persistVersion(replacement);
			current.setArchived(true); current.setStatus("ARCHIVED"); questions.save(current);
			return replacement;
		}
		copyQuestion(current, request); replaceChildren(current, request); return questions.save(current);
	}

	public KnowledgePoint createKnowledgePoint(KnowledgePointRequest request, Authentication user) {
		require(request != null && text(request.name()) != null, "知识点名称不能为空");
		EducationDataScope scope = scopes.resolve(user.getName()); scopes.assertFullAccess(scope);
		validateParent(request.parentId(), request.courseId(), null);
		KnowledgePoint value = new KnowledgePoint();
		value.setParentId(blank(request.parentId())); value.setSubjectId(blank(request.subjectId()));
		value.setCourseId(blank(request.courseId())); value.setCode(blank(request.code()));
		value.setName(request.name().trim()); value.setDescription(request.description());
		value.setLearningObjective(request.learningObjective()); value.setLevel(request.level());
		value.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
		return points.save(value);
	}

	public KnowledgePoint updateKnowledgePoint(String id, KnowledgePointRequest request, Authentication user) {
		EducationDataScope scope = scopes.resolve(user.getName()); scopes.assertFullAccess(scope);
		KnowledgePoint value = points.findById(id).orElseThrow(() -> new IllegalArgumentException("知识点不存在"));
		validateParent(request.parentId(), request.courseId(), id);
		value.setParentId(blank(request.parentId())); value.setSubjectId(blank(request.subjectId()));
		value.setCourseId(blank(request.courseId())); value.setCode(blank(request.code()));
		value.setName(request.name().trim()); value.setDescription(request.description());
		value.setLearningObjective(request.learningObjective()); value.setLevel(request.level());
		value.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
		return points.save(value);
	}

	public void disableKnowledgePoint(String id, Authentication user) {
		scopes.assertFullAccess(scopes.resolve(user.getName()));
		KnowledgePoint value = points.findById(id).orElseThrow(() -> new IllegalArgumentException("知识点不存在"));
		if (links.countByKnowledgePointId(id) > 0) throw new IllegalStateException("知识点已被题目引用，不能停用");
		value.setEnabled(false); value.setStatus("DISABLED"); points.save(value);
	}

	@Transactional(readOnly = true)
	public List<Question> questions(String bankId, Authentication user) {
		QuestionBank bank = banks.findById(bankId).orElseThrow(() -> new IllegalArgumentException("题库不存在"));
		authorizeBank(bank, user); return questions.findByBankIdAndArchivedFalseOrderByIdDesc(bankId);
	}

	@Transactional(readOnly = true)
	public List<KnowledgePoint> knowledgeTree(String courseId, Authentication user) {
		scopes.assertFullAccess(scopes.resolve(user.getName()));
		return points.findAll().stream().filter(p -> !p.isArchived() && Objects.equals(courseId, p.getCourseId()))
				.sorted(Comparator.comparing(KnowledgePoint::getSortOrder).thenComparing(KnowledgePoint::getName)).toList();
	}

	public ImportPreview precheckCsv(String csv) {
		List<RowError> errors = new ArrayList<>(); int accepted = 0;
		if (csv == null || csv.length() > 2_000_000) throw new IllegalArgumentException("CSV 不能为空且不得超过2MB");
		List<String> rows = csv.lines().toList();
		if (rows.size() > 2001) throw new IllegalArgumentException("CSV 最多2000行");
		for (int i = 1; i < rows.size(); i++) {
			try {
				List<String> c = parseCsv(rows.get(i));
				if (c.stream().allMatch(String::isBlank)) continue;
				if (c.size() < 8) throw new IllegalArgumentException("列数不足");
				for (String cell : c) if (formula(cell)) throw new IllegalArgumentException("检测到公式注入");
				if (!TYPES.contains(c.get(1).trim().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("题型无效");
				if (!DIFFICULTIES.contains(c.get(2).trim().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("难度无效");
				if (new java.math.BigDecimal(c.get(3)).signum() <= 0) throw new IllegalArgumentException("分值必须大于0");
				if (c.get(4).isBlank() || c.get(6).isBlank()) throw new IllegalArgumentException("题干和题库不能为空");
				accepted++;
			} catch (RuntimeException ex) { errors.add(new RowError(i + 1, "", ex.getMessage())); }
		}
		return new ImportPreview(accepted, List.copyOf(errors));
	}

	public List<Question> importCsv(String csv, Authentication user) {
		ImportPreview preview = precheckCsv(csv);
		if (!preview.errors().isEmpty()) throw new IllegalArgumentException("CSV预检存在错误: " + preview.errors());
		List<Question> result = new ArrayList<>();
		for (String row : csv.lines().skip(1).toList()) {
			if (row.isBlank()) continue; List<String> c = parseCsv(row);
			result.add(createQuestion(new QuestionRequest(c.get(6), c.get(1), c.get(2), c.get(4),
					new java.math.BigDecimal(c.get(3)), c.get(5), c.get(7), null, null, null, List.of(), List.of(), List.of()), user));
		}
		return result;
	}

	private void validateQuestion(QuestionRequest r, Authentication user) {
		require(r != null && r.bankId() != null, "题库不能为空");
		require(text(r.stem()) != null, "题干不能为空"); if (r.stem().length() > 100000) throw new IllegalArgumentException("题干过长");
		if (!TYPES.contains(upper(r.questionType()))) throw new IllegalArgumentException("题型无效");
		if (!DIFFICULTIES.contains(upper(r.difficulty()))) throw new IllegalArgumentException("难度无效");
		if (r.score() == null || r.score().signum() <= 0) throw new IllegalArgumentException("分值必须大于0");
		if (r.usableFrom() != null && r.usableUntil() != null && r.usableFrom().isAfter(r.usableUntil())) throw new IllegalArgumentException("有效期无效");
		List<OptionRequest> os = r.options() == null ? List.of() : r.options();
		if (upper(r.questionType()).equals("SINGLE_CHOICE") || upper(r.questionType()).equals("MULTIPLE_CHOICE")) {
			if (os.size() < 2) throw new IllegalArgumentException("选择题至少需要两个选项");
			long correct = os.stream().filter(o -> Boolean.TRUE.equals(o.correct())).count();
			if (upper(r.questionType()).equals("SINGLE_CHOICE") && correct != 1 || upper(r.questionType()).equals("MULTIPLE_CHOICE") && correct < 2)
				throw new IllegalArgumentException("选择题正确答案数量不匹配");
		}
		if (r.knowledgePointIds() == null || r.knowledgePointIds().isEmpty()) throw new IllegalArgumentException("至少关联一个知识点");
		EducationDataScope scope = scopes.resolve(user.getName());
		QuestionBank b = banks.findById(r.bankId()).orElseThrow(() -> new IllegalArgumentException("题库不存在"));
		authorizeBank(b, user); for (String id : r.knowledgePointIds()) points.findById(id).orElseThrow(() -> new IllegalArgumentException("知识点不存在"));
	}

	private void replaceChildren(Question q, QuestionRequest r) {
		options.deleteByQuestionId(q.getId()); links.deleteByQuestionId(q.getId()); files.deleteByQuestionId(q.getId());
		if (r.options() != null) for (OptionRequest o : r.options()) {
			QuestionOption x = new QuestionOption(); x.setId(UUID.randomUUID().toString()); x.setQuestionId(q.getId());
			x.setOptionKey(o.key()); x.setOptionText(o.text()); x.setSortOrder(o.sortOrder() == null ? 0 : o.sortOrder()); options.save(x);
		}
		for (String id : r.knowledgePointIds()) {
			QuestionKnowledgePoint link = new QuestionKnowledgePoint();
			link.setQuestionId(q.getId()); link.setKnowledgePointId(id); links.save(link);
		}
		if (r.fileIds() != null && !r.fileIds().isEmpty()) {
			managedFiles.requireBound(r.fileIds(), "QUESTION", q.getId());
			for (String id : r.fileIds()) {
				QuestionFile file = new QuestionFile(); file.setQuestionId(q.getId()); file.setFileId(id); files.save(file);
			}
		}
	}
	private void copyQuestion(Question q, QuestionRequest r) {
		q.setQuestionType(upper(r.questionType())); q.setDifficulty(upper(r.difficulty())); q.setStem(r.stem().trim());
		q.setScore(r.score()); q.setAnswer(r.answer()); q.setAnalysis(r.analysis()); q.setAnswerSchemaJson(r.answerSchemaJson());
		q.setUsableFrom(r.usableFrom()); q.setUsableUntil(r.usableUntil()); q.setStatus("DRAFT");
	}
	private void persistVersion(Question q) {
		try {
			Map<String, Object> snapshot = new LinkedHashMap<>();
			snapshot.put("questionType", q.getQuestionType()); snapshot.put("difficulty", q.getDifficulty());
			snapshot.put("stem", q.getStem()); snapshot.put("answer", q.getAnswer()); snapshot.put("analysis", q.getAnalysis());
			String body = json.writeValueAsString(snapshot);
			QuestionVersion version = new QuestionVersion(); version.setQuestionId(q.getId());
			version.setVersionNo(q.getCurrentVersionNo()); version.setSnapshotJson(body); version.setSnapshotHash(hash(body));
			versions.save(version);
		} catch (JsonProcessingException ex) { throw new IllegalStateException("题目版本快照失败", ex); }
	}
	private void authorizeBank(QuestionBank b, Authentication u) {
		if (b.getOfferingId() != null) scopes.assertOfferingAccess(scopes.resolve(u.getName()), b.getOfferingId());
		else scopes.assertFullAccess(scopes.resolve(u.getName()));
	}
	private void validateOffering(String offeringId, String courseId, EducationDataScope scope) {
		if (offeringId != null) { scopes.assertOfferingAccess(scope, offeringId); var o = offerings.findById(offeringId).orElseThrow(); if (courseId != null && !courseId.equals(o.getCourseCode())) throw new IllegalArgumentException("课程与教学班不一致"); }
		else scopes.assertFullAccess(scope);
	}
	private void validateParent(String parent, String course, String self) {
		Set<String> seen = new HashSet<>(); String current = parent;
		while (current != null) {
			if (!seen.add(current) || current.equals(self)) throw new IllegalArgumentException("知识点父级关系形成循环");
			KnowledgePoint p = points.findById(current).orElseThrow(() -> new IllegalArgumentException("父知识点不存在"));
			if (course != null && p.getCourseId() != null && !course.equals(p.getCourseId())) throw new IllegalArgumentException("父知识点课程不一致");
			current = p.getParentId();
		}
	}
	private List<String> parseCsv(String line) {
		if (line.length() > 100_000) throw new IllegalArgumentException("CSV单行超过100KB");
		List<String> out = new ArrayList<>(); StringBuilder b = new StringBuilder(); boolean quoted = false;
		for (int i=0;i<line.length();i++) { char c=line.charAt(i); if(c=='"') { if(quoted && i+1<line.length() && line.charAt(i+1)=='"'){b.append('"');i++;} else quoted=!quoted; } else if(c==','&&!quoted){out.add(b.toString());b.setLength(0);} else b.append(c); }
		if (quoted) throw new IllegalArgumentException("CSV引号未闭合"); out.add(b.toString()); return out;
	}
	private boolean formula(String s) { String v=s.trim(); return v.startsWith("=")||v.startsWith("+")||v.startsWith("-")||v.startsWith("@"); }
	private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e){throw new IllegalStateException(e);} }
	private static String upper(String s){return s==null?null:s.trim().toUpperCase(Locale.ROOT);}
	private static String text(String s){return s==null||s.isBlank()?null:s.trim();}
	private static String blank(String s){return text(s);}
	private static String defaultValue(String s,String d){return text(s)==null?d:upper(s);}
	private static void require(boolean ok,String msg){if(!ok)throw new IllegalArgumentException(msg);}
}

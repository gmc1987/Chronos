package com.chronos.education.scheduling.service;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 教学中心领域 CRUD。领域表虽然各自有 repository（供其他模块组合查询），
 * 这里用白名单 JPQL 统一分页，避免把九套近似接口做成不一致的契约。
 */
@Service
@Transactional
public class TeachingDomainService {
	private static final Map<String, Class<?>> TYPES = Map.ofEntries(
			Map.entry("PLAN", TeachingPlan.class), Map.entry("LESSON_PLAN", LessonPlan.class),
			Map.entry("PREPARATION", Preparation.class), Map.entry("COURSEWARE", Courseware.class),
			Map.entry("MATERIAL", TeachingMaterial.class), Map.entry("QUESTION_BANK", QuestionBank.class),
			Map.entry("QUESTION", Question.class), Map.entry("KNOWLEDGE_POINT", KnowledgePoint.class),
			Map.entry("ERROR_BOOK", ErrorBook.class), Map.entry("MISTAKE", ErrorBook.class),
			Map.entry("RESEARCH", ResearchGroup.class), Map.entry("RESEARCH_ACTIVITY", ResearchActivity.class));
	@PersistenceContext private EntityManager entityManager;
	private final EducationDataScopeService scopes;
	private final com.chronos.education.scheduling.dao.ScheduleEntryRepository scheduleEntries;
	private final com.chronos.education.scheduling.dao.CourseOfferingRepository offerings;

	public TeachingDomainService(EducationDataScopeService scopes,
			com.chronos.education.scheduling.dao.ScheduleEntryRepository scheduleEntries,
			com.chronos.education.scheduling.dao.CourseOfferingRepository offerings) {
		this.scopes = scopes;
		this.scheduleEntries = scheduleEntries;
		this.offerings = offerings;
	}

	@Transactional(readOnly = true)
	public PageView<?> page(String type, String offeringId, int page, int size,
			Authentication authentication) {
		Class<?> entity = entity(type);
		EducationDataScope scope = scopes.resolve(authentication.getName());
		checkOffering(scope, offeringId, type, false);
		if (!hasOffering(type) && offeringId == null) {
			// 没有教学班字段的领域表不能套用班级过滤，必须先确认全校数据权限。
			scopes.assertFullAccess(scope);
		}
		List<String> visibleOfferingIds = null;
		if (hasOffering(type) && offeringId == null && !scope.fullAccess()) {
			visibleOfferingIds = scopes.visibleOfferings(scope, offerings.findAll()).stream()
					.map(CourseOffering::getId)
					.toList();
			if (visibleOfferingIds.isEmpty()) {
				return PageView.from(List.of(), page, size);
			}
		}
		String archivedProperty = "archived";
		String jpql = "select e from " + entity.getSimpleName() + " e where e." + archivedProperty
				+ " = false" + (hasOffering(type) && offeringId != null ? " and e.offeringId = :offeringId" : "")
				+ (visibleOfferingIds != null ? " and e.offeringId in :visibleOfferingIds" : "")
				+ " order by e.id desc";
		var query = entityManager.createQuery(jpql, entity);
		if (hasOffering(type) && offeringId != null) query.setParameter("offeringId", offeringId);
		if (visibleOfferingIds != null) query.setParameter("visibleOfferingIds", visibleOfferingIds);
		List<?> values = query.getResultList();
		return PageView.from(values, page, size);
	}

	public Object create(String type, Map<String, Object> body, Authentication authentication) {
		Class<?> clazz = entity(type);
		EducationDataScope scope = scopes.resolve(authentication.getName());
		assertDraftPayload(body);
		validateAndAuthorize(type, body, scope);
		Object value;
		try { value = clazz.getDeclaredConstructor().newInstance(); }
		catch (ReflectiveOperationException e) { throw new IllegalStateException("教学实体不可创建", e); }
		apply(type, value, body);
		entityManager.persist(value);
		return value;
	}

	public Object update(String type, String id, Map<String, Object> body,
			Authentication authentication) {
		Class<?> clazz = entity(type);
		EducationDataScope scope = scopes.resolve(authentication.getName());
		Object value = find(clazz, id);
		authorizeExisting(type, value, scope);
		assertDraftPayload(body);
		String currentStatus = textValue(value, "status");
		if (currentStatus != null && !"DRAFT".equals(currentStatus)) {
			throw new IllegalStateException("仅草稿状态允许编辑教学资源");
		}
		validateAndAuthorize(type, body, scope);
		if ("KNOWLEDGE_POINT".equals(type) && Objects.equals(id, text(body, "parentId")))
			throw new IllegalArgumentException("知识点不能以自身为父级");
		apply(type, value, body);
		return entityManager.merge(value);
	}

	@Transactional(readOnly = true)
	public Object get(String type, String id, Authentication authentication) {
		Object value = find(entity(type), id);
		authorizeExisting(type, value, scopes.resolve(authentication.getName()));
		return value;
	}

	public Object archive(String type, String id, Authentication authentication) {
		Class<?> clazz = entity(type);
		Object value = find(clazz, id);
		authorizeExisting(type, value, scopes.resolve(authentication.getName()));
		set(value, "archived", true);
		try { set(value, "status", "ARCHIVED"); } catch (IllegalArgumentException ignored) { /* 知识点以 enabled 表示归档 */ }
		if (value instanceof KnowledgePoint point) point.setEnabled(false);
		return entityManager.merge(value);
	}

	public Object status(String type, String id, String status, Authentication authentication) {
		if (status == null || status.isBlank() || status.length() > 24)
			throw new IllegalArgumentException("状态不能为空");
		if (!Set.of("DRAFT", "SUBMITTED", "REVIEWING", "PUBLISHED", "ARCHIVED").contains(status.trim().toUpperCase(Locale.ROOT)))
			throw new IllegalArgumentException("不支持的状态");
		Object value = find(entity(type), id);
		authorizeExisting(type, value, scopes.resolve(authentication.getName()));
		String current = Objects.toString(read(value, "status"), "DRAFT");
		String target = status.trim().toUpperCase(Locale.ROOT);
		if ("PUBLISHED".equals(target))
			throw new IllegalStateException("教学资源必须通过审核流程发布");
		if (!allowedTransition(type, current, target))
			throw new IllegalStateException("不允许从 " + current + " 流转到 " + target);
		try { set(value, "status", status.trim().toUpperCase(Locale.ROOT)); }
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("该实体不支持状态字段");
		}
		return entityManager.merge(value);
	}

		/** 稳定的 CSV 交换格式，限制行数和单行大小，避免误把大文件当请求体。 */
		@Transactional(readOnly = true)
		public String exportCsv(String type, String offeringId, Authentication authentication) {
			List<?> rows = page(type, offeringId, 0, 2000, authentication).content();
			if (rows.size() > 2000) throw new IllegalArgumentException("CSV 导出最多 2000 行");
			StringBuilder csv = new StringBuilder("id,name,title,offeringId,status\n");
			for (Object row : rows) {
				csv.append(csvCell(id(row))).append(',')
						.append(csvCell(textValue(row, "name"))).append(',')
						.append(csvCell(textValue(row, "title"))).append(',')
						.append(csvCell(offering(row))).append(',')
						.append(csvCell(textValue(row, "status"))).append('\n');
			}
			return csv.toString();
		}

		public List<?> importCsv(String type, String csv, Authentication authentication) {
			if (csv == null || csv.length() > 2_000_000) throw new IllegalArgumentException("CSV 不能为空且不得超过 2MB");
			List<String> lines = csv.lines().toList();
			if (lines.size() > 2001) throw new IllegalArgumentException("CSV 最多 2000 行");
			if (lines.isEmpty()) return List.of();
			Set<String> keys = new HashSet<>();
			List<Object> result = new ArrayList<>();
			for (int i = 1; i < lines.size(); i++) {
				List<String> cells = parseCsvLine(lines.get(i));
				if (cells.size() < 5 || cells.stream().allMatch(String::isBlank)) continue;
				String key = cells.get(1).trim() + "\u0000" + cells.get(2).trim();
				if (!keys.add(key)) throw new IllegalArgumentException("CSV 存在重复记录: 第 " + (i + 1) + " 行");
				Map<String, Object> body = new HashMap<>();
				body.put("name", cells.get(1)); body.put("title", cells.get(2));
				body.put("offeringId", cells.get(3)); body.put("status", cells.get(4));
				result.add(create(type, body, authentication));
			}
			return result;
		}
	@Transactional(readOnly = true)
	public List<?> exportData(String type, String offeringId, Authentication authentication) {
		return page(type, offeringId, 0, 200, authentication).content();
	}

	private void validateAndAuthorize(String type, Map<String, Object> body, EducationDataScope scope) {
		String offeringId = text(body, "offeringId");
		checkOffering(scope, offeringId, type, true);
		String scheduleEntryId = text(body, "scheduleEntryId");
		if (scheduleEntryId != null && !scheduleEntryId.isBlank()) {
			var entry = scheduleEntries.findById(scheduleEntryId)
					.orElseThrow(() -> new IllegalArgumentException("课表项不存在"));
			if (!Objects.equals(offeringId, entry.getOfferingId()))
				throw new IllegalArgumentException("课表项不属于该教学任务");
			scopes.assertScheduleEntryAccess(scope, scheduleEntryId);
		}
		if (offeringId == null && requiresGlobalScope(type)) scopes.assertFullAccess(scope);
		if (Set.of("ERROR_BOOK", "MISTAKE").contains(type) && text(body, "studentId") != null)
			scopes.assertStudentAccess(scope, text(body, "studentId"));
		if ("KNOWLEDGE_POINT".equals(type) && text(body, "parentId") != null) {
			String parent = text(body, "parentId");
			Set<String> seen = new HashSet<>();
			while (parent != null && !parent.isBlank()) {
				if (!seen.add(parent)) throw new IllegalArgumentException("知识点父级关系形成循环");
				KnowledgePoint node = entityManager.find(KnowledgePoint.class, parent);
				parent = node == null ? null : node.getParentId();
			}
		}
	}

	/** 状态只能由审核流程或专用状态接口推进，不能通过通用 CRUD 绕过审核。 */
	private void assertDraftPayload(Map<String, Object> body) {
		String status = text(body, "status");
		if (status != null && !status.isBlank() && !"DRAFT".equals(status)) {
			throw new IllegalArgumentException("通用保存接口只接受草稿状态");
		}
	}

	private void authorizeExisting(String type, Object value, EducationDataScope scope) {
		String offering = offering(value);
		if (offering != null) scopes.assertOfferingAccess(scope, offering);
		else if (value instanceof ErrorBook book) scopes.assertStudentAccess(scope, book.getStudentId());
		else if (requiresGlobalScope(type)) scopes.assertFullAccess(scope);
		if (Boolean.TRUE.equals(read(value, "archived")))
			throw new IllegalStateException("已归档实体不可修改");
	}

	private void checkOffering(EducationDataScope scope, String offeringId, String type, boolean required) {
		if (hasOffering(type) && (offeringId == null || offeringId.isBlank())) {
			if (required) throw new IllegalArgumentException("offeringId 不能为空");
			return;
		}
		if (offeringId != null && !offeringId.isBlank()) scopes.assertOfferingAccess(scope, offeringId);
	}

	private boolean hasOffering(String type) { return Set.of("PLAN","LESSON_PLAN","PREPARATION","COURSEWARE","MATERIAL","QUESTION_BANK").contains(type); }
	private boolean requiresGlobalScope(String type) { return Set.of("PLAN","QUESTION_BANK","QUESTION","KNOWLEDGE_POINT","RESEARCH","RESEARCH_ACTIVITY").contains(type); }
	private Class<?> entity(String type) {
		Class<?> result = TYPES.get(type == null ? "" : type.trim().toUpperCase(Locale.ROOT));
		if (result == null) throw new IllegalArgumentException("不支持的教学领域类型");
		return result;
	}
	private Object find(Class<?> type, String id) {
		Object value = entityManager.find(type, id);
		if (value == null) throw new IllegalArgumentException("教学实体不存在");
		return value;
	}
	private boolean allowedTransition(String type, String current, String target) {
		if ("ARCHIVED".equals(target) || Objects.equals(current, target)) return true;
		return "DRAFT".equals(current) && Set.of("SUBMITTED", "REVIEWING").contains(target)
				|| "SUBMITTED".equals(current) && "REVIEWING".equals(target)
				|| "REVIEWING".equals(current) && "PUBLISHED".equals(target);
	}
	private boolean publishable(Object value) {
		return Stream.of("name", "title", "stem").map(p -> textValue(value, p))
				.anyMatch(v -> v != null && !v.isBlank());
	}
	private String id(Object value) { return textValue(value, "id"); }
	private String textValue(Object value, String property) {
		try { Object v = value.getClass().getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1)).invoke(value); return v == null ? null : v.toString(); }
		catch (ReflectiveOperationException e) { return null; }
	}
	private String csvCell(String value) {
		if (value == null) return "";
		String escaped = value.replace("\"", "\"\"");
		return escaped.indexOf(',') >= 0 || escaped.indexOf('"') >= 0 || escaped.indexOf('\n') >= 0 ? "\"" + escaped + "\"" : escaped;
	}
	private List<String> parseCsvLine(String line) {
		if (line.length() > 100_000) throw new IllegalArgumentException("CSV 单行超过 100KB");
		List<String> out = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
		for (int i = 0; i < line.length(); i++) { char c = line.charAt(i);
			if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = !quoted; }
			else if (c == ',' && !quoted) { out.add(cell.toString()); cell.setLength(0); } else cell.append(c);
		}
		if (quoted) throw new IllegalArgumentException("CSV 引号未闭合");
		out.add(cell.toString()); return out;
	}
	private String offering(Object value) {
		try { return (String) value.getClass().getMethod("getOfferingId").invoke(value); }
		catch (ReflectiveOperationException e) { return null; }
	}

	public String offeringId(Object value) {
		return offering(value);
	}
	private Object read(Object value, String property) {
		try { return value.getClass().getMethod("is" + Character.toUpperCase(property.charAt(0)) + property.substring(1)).invoke(value); }
		catch (ReflectiveOperationException e) {
			try { return value.getClass().getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1)).invoke(value); }
			catch (ReflectiveOperationException ignored) { return null; }
		}
	}
	private void set(Object value, String property, Object data) {
		try {
			var method = Arrays.stream(value.getClass().getMethods())
					.filter(m -> m.getName().equals("set" + Character.toUpperCase(property.charAt(0)) + property.substring(1)))
					.findFirst().orElseThrow();
			method.invoke(value, data);
		} catch (ReflectiveOperationException e) { throw new IllegalArgumentException("字段不可写: " + property); }
	}
	private void apply(String type, Object value, Map<String, Object> body) {
		Map<String, String> fields = switch (type) {
			case "PLAN" -> Map.of("offeringId","offeringId","name","name","subject","subject","grade","grade","status","status");
			case "LESSON_PLAN" -> Map.of("offeringId","offeringId","scheduleEntryId","scheduleEntryId","title","title","status","status");
			case "PREPARATION" -> Map.of("offeringId","offeringId","title","title","preparationType","preparationType","status","status","conclusion","conclusion");
			case "COURSEWARE" -> Map.of("offeringId","offeringId","title","title","status","status","shareScope","shareScope");
			case "MATERIAL" -> Map.of("offeringId","offeringId","title","title","materialType","materialType","shareScope","shareScope");
			case "QUESTION_BANK" -> Map.of("offeringId","offeringId","name","name","subject","subject","status","status");
			case "QUESTION" -> Map.of("bankId","bankId","questionType","questionType","difficulty","difficulty","stem","stem","status","status","answer","answer","analysis","analysis");
			case "KNOWLEDGE_POINT" -> Map.of("parentId","parentId","subjectId","subjectId","courseId","courseId","name","name","sortOrder","sortOrder","enabled","enabled");
			case "ERROR_BOOK", "MISTAKE" -> Map.of("studentId","studentId","name","name");
			case "RESEARCH" -> Map.of("name","name","subjectId","subjectId","status","status");
			case "RESEARCH_ACTIVITY" -> Map.of("groupId","groupId","title","title","status","status","activityTime","activityTime","content","content");
			default -> Map.of();
		};
		fields.forEach((request, property) -> {
			if (!body.containsKey(request) || body.get(request) == null) return;
			Object raw = body.get(request);
			if ("sortOrder".equals(property) && raw instanceof Number n) raw = n.intValue();
			if (raw instanceof String s && s.isBlank()) return;
			set(value, property, raw);
		});
	}
	private String text(Map<String,Object> body, String key) {
		Object value = body.get(key); return value == null ? null : value.toString().trim();
	}
}

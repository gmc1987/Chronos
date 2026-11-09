package com.chronos.education.operationagent.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves the target semester before an operation-agent run is planned. */
@Service
public class OperationAgentSemesterResolver {
	private static final Pattern TERM_CODE = Pattern.compile(
			"(?<!\\d)(20\\d{2}\\s*[-—至]\\s*20\\d{2}\\s*[-—]\\s*[12])(?!\\d)");
	private static final Pattern TERM_NAME = Pattern.compile(
			"(?<!\\d)(20\\d{2})\\s*[-—至]\\s*(20\\d{2})\\s*(?:学年)?\\s*第?([一二12])学期");
	private static final Pattern SCHOOL_YEAR_TERM_NAME = Pattern.compile(
			"(?<!\\d)(20\\d{2})\\s*(?:年度|学年)\\s*第?([一二12])学期");
	private final JdbcTemplate jdbc;

	public OperationAgentSemesterResolver(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Resolution resolve(String request, String suppliedCode,
			boolean confirmNonCurrent, boolean createMissingTerm) {
		return resolve(request, suppliedCode, confirmNonCurrent, createMissingTerm, null);
	}

	public Resolution resolve(String request, String suppliedCode,
			boolean confirmNonCurrent, boolean createMissingTerm, String organizationId) {
		String text = request == null ? "" : request.trim();
		Term current = currentTerm(organizationId);
		String requestedCode = normalize(suppliedCode);
		String mentionedCode = text.isEmpty() ? null : mentionedTermCode(text, organizationId);
		if (requestedCode != null && mentionedCode != null && !sameReference(requestedCode, mentionedCode)) {
			return new Resolution("CONFLICT", requestedCode, null,
					current == null ? null : current.code(),
					"学期参数与自然语言中的学期不一致，请只保留一个目标学期。", true, false);
		}
		if (requestedCode == null) requestedCode = mentionedCode;
		if (requestedCode == null && containsCurrentReference(text)) {
			requestedCode = current == null ? null : current.code();
		}
		if (requestedCode == null) {
			if (createMissingTerm) {
				return new Resolution("NEED_TERM_SELECTION", null, null,
						current == null ? null : current.code(),
						"已识别你要创建学年学期数据。请告诉我目标学期，例如“创建 2027-2028 学年第一学期”。",
						true, true);
			}
			if (current == null) {
				return new Resolution("NEED_TERM_SELECTION", null, null, null,
						"系统目前没有当前启用学期。请告诉我要处理的学期，例如“创建 2027-2028 学年第一学期”，我可以继续协助创建。",
						true, false);
			}
			return new Resolution("READY", current.code(), current.name(), current.code(),
					"未指定学期，将按当前启用学期处理：" + current.code(), false, false,
					current.id(), current.organizationId());
		}

		List<Term> matches = findTerms(requestedCode, organizationId);
		if (matches.size() > 1) {
			return new Resolution("NEED_ORGANIZATION", requestedCode, null,
					current == null ? null : current.code(),
					"多个机构存在学期 " + requestedCode + "，请先选择本次任务所属机构。", true, false);
		}
		Term requested = matches.isEmpty() ? null : matches.getFirst();
		if (requested == null) {
			if (!createMissingTerm) {
				return new Resolution("CONFIRM_CREATE", requestedCode, null,
						current == null ? null : current.code(),
						"未找到学期 " + requestedCode + "。是否需要创建这个新学期？", true, true);
			}
			return new Resolution("READY", requestedCode, null,
					current == null ? null : current.code(),
					"已识别创建学期 " + requestedCode + "，将生成候选数据并在正式写入前请你确认。", false, true);
		}
		if (current != null && !current.code().equals(requested.code()) && !confirmNonCurrent) {
			return new Resolution("CONFIRM_NON_CURRENT", requested.code(), requested.name(), current.code(),
					"你指定的是非当前学期 " + requested.code() + "，当前启用学期为 "
							+ current.code() + "。是否继续处理该学期？", true, false);
		}
		return new Resolution("READY", requested.code(), requested.name(),
				current == null ? null : current.code(),
				"已确定按学期 " + requested.code() + " 处理。", false, false,
				requested.id(), requested.organizationId());
	}

	private Term currentTerm(String organizationId) {
		List<Term> terms = jdbc.query("""
				select id, term_code, local_term_code, term_name, organization_id
				from edu_academic_term
				where current_term=true and status='ACTIVE'
				  and (cast(? as varchar) is null or organization_id = ?)
				order by start_date desc
				limit 1
				""", (rs, rowNum) -> term(rs), organizationId, organizationId);
		return terms.isEmpty() ? null : terms.getFirst();
	}

	private List<Term> findTerms(String code, String organizationId) {
		return jdbc.query("""
				select id, term_code, local_term_code, term_name, organization_id
				from edu_academic_term
				where (term_code=? or local_term_code=?)
				  and (cast(? as varchar) is null or organization_id=?)
				order by start_date desc
				""", (rs, rowNum) -> term(rs), code, code, organizationId, organizationId);
	}

	private String mentionedTermCode(String text, String organizationId) {
		Matcher matcher = TERM_CODE.matcher(text);
		if (matcher.find()) return normalize(matcher.group(1));
		Matcher nameMatcher = TERM_NAME.matcher(text);
		if (nameMatcher.find()) {
			String termNo = Set.of("一", "1").contains(nameMatcher.group(3)) ? "1" : "2";
			return nameMatcher.group(1) + "-" + nameMatcher.group(2) + "-" + termNo;
		}
		Matcher schoolYearMatcher = SCHOOL_YEAR_TERM_NAME.matcher(text);
		if (schoolYearMatcher.find()) {
			int startYear = Integer.parseInt(schoolYearMatcher.group(1));
			String termNo = Set.of("一", "1").contains(schoolYearMatcher.group(2)) ? "1" : "2";
			return startYear + "-" + (startYear + 1) + "-" + termNo;
		}
		List<Map<String, Object>> terms = jdbc.queryForList(
				"select term_code, local_term_code, term_name from edu_academic_term where status='ACTIVE' and (cast(? as varchar) is null or organization_id=?)",
				organizationId, organizationId);
		for (Map<String, Object> term : terms) {
			String code = String.valueOf(term.get("term_code"));
			String localCode = String.valueOf(term.get("local_term_code"));
			String name = term.get("term_name") == null ? "" : String.valueOf(term.get("term_name"));
			if (text.contains(code) || text.contains(localCode) || (!name.isBlank() && text.contains(name))) return code;
		}
		return null;
	}

	private boolean containsCurrentReference(String text) {
		return text.contains("本学期") || text.contains("当前学期") || text.contains("现学期")
				|| text.contains("这个学期");
	}

	private String normalize(String value) {
		if (value == null || value.isBlank()) return null;
		return value.replace('—', '-').replace('至', '-').replaceAll("\\s+", "").trim();
	}

	/** 将用户输入的校内编码转换成不会跨机构冲突的机构学期编码。 */
	public String qualify(String value, String organizationId) {
		String code = normalize(value);
		if (code == null || organizationId == null || organizationId.isBlank()) return code;
		List<Term> existing = findTerms(code, organizationId);
		if (!existing.isEmpty()) return existing.getFirst().code();
		if (code.contains(":")) return code;
		String orgCode = jdbc.queryForObject("select org_code from t_organization where id=?", String.class, organizationId);
		if (orgCode == null || orgCode.isBlank()) throw new IllegalArgumentException("目标机构缺少机构编码");
		return orgCode.trim() + ":" + code;
	}

	boolean sameReference(String left, String right) {
		if (left == null || right == null) return false;
		return left.equals(right) || ((!left.contains(":") || !right.contains(":"))
				&& localPart(left).equals(localPart(right)));
	}

	private String localPart(String value) {
		int separator = value.indexOf(':');
		return separator < 0 ? value : value.substring(separator + 1);
	}

	private Term term(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new Term(rs.getString("id"), rs.getString("term_code"), rs.getString("local_term_code"),
				rs.getString("term_name"), rs.getString("organization_id"));
	}

	private record Term(String id, String code, String localCode, String name, String organizationId) {}

	public record Resolution(String status, String termCode, String termName,
			String currentTermCode, String message, boolean confirmationRequired,
			boolean creationRequested, String termId, String organizationId) {
		public Resolution(String status, String termCode, String termName, String currentTermCode,
				String message, boolean confirmationRequired, boolean creationRequested) {
			this(status, termCode, termName, currentTermCode, message, confirmationRequired, creationRequested, null, null);
		}
	}
}

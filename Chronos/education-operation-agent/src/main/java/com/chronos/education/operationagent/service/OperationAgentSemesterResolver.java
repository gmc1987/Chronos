package com.chronos.education.operationagent.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Resolves the target semester before an operation-agent run is planned. */
@Service
public class OperationAgentSemesterResolver {
	private static final Pattern TERM_CODE = Pattern.compile(
			"(?<!\\d)(20\\d{2}\\s*[-—至]\\s*20\\d{2}\\s*[-—]\\s*[12])(?!\\d)");
	private final JdbcTemplate jdbc;

	public OperationAgentSemesterResolver(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Resolution resolve(String request, String suppliedCode,
			boolean confirmNonCurrent, boolean createMissingTerm) {
		String text = request == null ? "" : request.trim();
		Term current = currentTerm();
		String requestedCode = normalize(suppliedCode);
		String mentionedCode = mentionedTermCode(text);
		if (requestedCode != null && mentionedCode != null && !requestedCode.equals(mentionedCode)) {
			return new Resolution("CONFLICT", requestedCode, null,
					current == null ? null : current.code(),
					"学期参数与自然语言中的学期不一致，请只保留一个目标学期。", true, false);
		}
		if (requestedCode == null) requestedCode = mentionedCode;
		if (requestedCode == null && containsCurrentReference(text)) {
			requestedCode = current == null ? null : current.code();
		}
		if (requestedCode == null) {
			if (current == null) {
				return new Resolution("NEED_CURRENT_TERM", null, null, null,
						"系统没有当前启用学期，请先维护学期后再继续。", true, false);
			}
			return new Resolution("READY", current.code(), current.name(), current.code(),
					"未指定学期，将按当前启用学期处理：" + current.code(), false, false);
		}

		Term requested = findTerm(requestedCode);
		if (requested == null) {
			if (!createMissingTerm) {
				return new Resolution("CONFIRM_CREATE", requestedCode, null,
						current == null ? null : current.code(),
						"未找到学期 " + requestedCode + "。是否需要创建这个新学期？", true, true);
			}
			return new Resolution("CREATE_TERM_INPUT_REQUIRED", requestedCode, null,
					current == null ? null : current.code(),
					"已确认创建学期 " + requestedCode
							+ "，请继续提供开始日期、结束日期和教学周数。", true, true);
		}
		if (current != null && !current.code().equals(requested.code()) && !confirmNonCurrent) {
			return new Resolution("CONFIRM_NON_CURRENT", requested.code(), requested.name(), current.code(),
					"你指定的是非当前学期 " + requested.code() + "，当前启用学期为 "
							+ current.code() + "。是否继续处理该学期？", true, false);
		}
		return new Resolution("READY", requested.code(), requested.name(),
				current == null ? null : current.code(),
				"已确定按学期 " + requested.code() + " 处理。", false, false);
	}

	private Term currentTerm() {
		List<Term> terms = jdbc.query("""
				select term_code, term_name
				from edu_academic_term
				where current_term=true and status='ACTIVE'
				order by start_date desc
				limit 1
				""", (rs, rowNum) -> new Term(rs.getString("term_code"), rs.getString("term_name")));
		return terms.isEmpty() ? null : terms.getFirst();
	}

	private Term findTerm(String code) {
		List<Term> terms = jdbc.query("select term_code, term_name from edu_academic_term where term_code=?",
				(rs, rowNum) -> new Term(rs.getString("term_code"), rs.getString("term_name")), code);
		return terms.isEmpty() ? null : terms.getFirst();
	}

	private String mentionedTermCode(String text) {
		Matcher matcher = TERM_CODE.matcher(text);
		if (matcher.find()) return normalize(matcher.group(1));
		List<Map<String, Object>> terms = jdbc.queryForList(
				"select term_code, term_name from edu_academic_term where status='ACTIVE'");
		for (Map<String, Object> term : terms) {
			String code = String.valueOf(term.get("term_code"));
			String name = term.get("term_name") == null ? "" : String.valueOf(term.get("term_name"));
			if (text.contains(code) || (!name.isBlank() && text.contains(name))) return code;
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

	private record Term(String code, String name) {}

	public record Resolution(String status, String termCode, String termName,
			String currentTermCode, String message, boolean confirmationRequired,
			boolean creationRequested) {}
}

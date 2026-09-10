package com.chronos.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAuditLogRepository;
import com.chronos.model.pojo.AuditLog;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * 审计日志只提供只读查询。按业务域维护动作前缀，避免前端自行拼装条件，
 * 行业模块增加新动作时也可以在这里统一归类。
 */
@Service
@Transactional(readOnly = true)
public class AuditLogQueryService {
	private static final int MAX_PAGE_SIZE = 100;
	private final IAuditLogRepository repository;

	public AuditLogQueryService(IAuditLogRepository repository) {
		this.repository = repository;
	}

	public Page<AuditLog> query(AuditLogQuery query, int page, int size) {
		PageRequest pageable = PageRequest.of(
				Math.max(0, page),
				Math.min(Math.max(1, size), MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "createTime"));
		return repository.findAll(specification(query), pageable);
	}

	Specification<AuditLog> specification(AuditLogQuery query) {
		return (root, criteriaQuery, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			contains(predicates, builder, root.get("username"), query.username());
			contains(predicates, builder, root.get("action"), query.action());
			contains(predicates, builder, root.get("detail"), query.keyword());
			if (query.startTime() != null) {
				predicates.add(builder.greaterThanOrEqualTo(
						root.get("createTime"),
						query.startTime()));
			}
			if (query.endTime() != null) {
				predicates.add(builder.lessThanOrEqualTo(
						root.get("createTime"),
						query.endTime()));
			}
			Predicate domainPredicate = domainPredicate(query.domain(), root, builder);
			if (domainPredicate != null) {
				predicates.add(domainPredicate);
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private void contains(
			List<Predicate> predicates,
			CriteriaBuilder builder,
			Path<String> path,
			String value) {
		if (value != null && !value.isBlank()) {
			predicates.add(builder.like(
					builder.lower(path),
					"%" + value.trim().toLowerCase(Locale.ROOT) + "%"));
		}
	}

	private Predicate domainPredicate(
			String domain,
			Root<AuditLog> root,
			CriteriaBuilder builder) {
		List<String> prefixes = switch (domain == null ? "ALL" : domain.toUpperCase(Locale.ROOT)) {
			case "EDUCATION" -> List.of("EDUCATION_", "SCHEDULING_AGENT_", "ACADEMIC_AGENT_");
			case "WORKFLOW" -> List.of("WORKFLOW_");
			case "KNOWLEDGE" -> List.of("KNOWLEDGE_");
			case "MESSAGE" -> List.of("MESSAGE_", "PUBLICATION_");
			case "IAM" -> List.of(
					"IAM_",
					"LOGIN",
					"PASSWORD_",
					"EMPLOYEE_",
					"DEPARTMENT_",
					"AUDIT_");
			default -> List.of();
		};
		if (prefixes.isEmpty()) {
			return null;
		}
		return builder.or(prefixes.stream()
				.map(prefix -> builder.like(root.get("action"), prefix + "%"))
				.toArray(Predicate[]::new));
	}

	public record AuditLogQuery(
			String domain,
			String username,
			String action,
			String keyword,
			LocalDateTime startTime,
			LocalDateTime endTime) {
	}
}

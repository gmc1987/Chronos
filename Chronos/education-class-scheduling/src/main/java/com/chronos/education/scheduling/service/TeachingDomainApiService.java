package com.chronos.education.scheduling.service;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.model.TeachingDomainCommand;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Named domain facade. The legacy type facade remains available for imports
 * and existing portal clients, while new callers use named domain operations. */
@Service
@Transactional
public class TeachingDomainApiService {
	private static final Map<String, String> DOMAINS = Map.of(
			"plans", "PLAN", "lesson-plans", "LESSON_PLAN", "preparations", "PREPARATION",
			"coursewares", "COURSEWARE", "materials", "MATERIAL", "question-banks", "QUESTION_BANK",
			"questions", "QUESTION", "knowledge-points", "KNOWLEDGE_POINT", "mistakes", "MISTAKE",
			"research", "RESEARCH");
	private final TeachingDomainService delegate;

	public TeachingDomainApiService(TeachingDomainService delegate) {
		this.delegate = delegate;
	}

	private String type(String domain) {
		String result = DOMAINS.get(domain == null ? "" : domain.toLowerCase(Locale.ROOT));
		if (result == null) throw new IllegalArgumentException("不支持的教学领域");
		return result;
	}

	@Transactional(readOnly = true)
	public PageView<?> page(String domain, String offeringId, int page, int size, Authentication user) {
		return delegate.page(type(domain), offeringId, page, size, user);
	}

	@Transactional(readOnly = true)
	public Object detail(String domain, String id, Authentication user) {
		return delegate.get(type(domain), id, user);
	}

	public Object create(String domain, TeachingDomainCommand command, Authentication user) {
		return delegate.create(type(domain), command.toMap(), user);
	}

	public Object update(String domain, String id, TeachingDomainCommand command, Authentication user) {
		return delegate.update(type(domain), id, command.toMap(), user);
	}

	public Object archive(String domain, String id, Authentication user) {
		return delegate.archive(type(domain), id, user);
	}

	public Object transition(String domain, String id, String status, Authentication user) {
		return delegate.status(type(domain), id, status, user);
	}
}

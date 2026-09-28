package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchedulingAgentCatalogServiceTest {
	private final EntityManager manager = mock(EntityManager.class);
	private final EducationDataScopeService scopes = mock(EducationDataScopeService.class);
	private final AcademicTermRepository terms = mock(AcademicTermRepository.class);
	private final SchedulingAgentCatalogService catalog =
			new SchedulingAgentCatalogService(manager, scopes, terms);

	@Test
	void boundedReadEscapesSearchWildcardsAndReturnsOnlyIdentifiers() {
		@SuppressWarnings("unchecked")
		TypedQuery<Object[]> query = mock(TypedQuery.class);
		when(manager.createQuery(anyString(), org.mockito.ArgumentMatchers.eq(Object[].class)))
				.thenReturn(query);
		when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
		when(query.setFirstResult(10)).thenReturn(query);
		when(query.setMaxResults(11)).thenReturn(query);
		when(query.getResultList()).thenReturn(List.<Object[]>of(
				new Object[] { "teacher-1", "T001", "张老师" }));
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));

		var result = catalog.lookup("admin", "TEACHER", "2026-2027-1", "T_!%", 1, 10);
		assertThat(result.items()).containsExactly(
				new SchedulingAgentCatalogService.Item("teacher-1", "T001", "张老师"));
		verify(query).setParameter("pattern", "%t!_!!!%%");
		verify(query).setFirstResult(10);
	}

	@Test
	void invalidTypeOrOversizedPageIsRejected() {
		assertThatThrownBy(() -> catalog.lookup("admin", "OTHER", "2026-2027-1", "", 0, 10))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> catalog.lookup("admin", "TEACHER", "2026-2027-1", "", 0, 51))
				.isInstanceOf(IllegalArgumentException.class);
	}
}

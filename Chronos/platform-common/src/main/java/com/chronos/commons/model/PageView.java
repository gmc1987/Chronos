package com.chronos.commons.model;

import java.util.List;
import org.springframework.data.domain.Page;

/** 稳定分页响应契约，避免业务接口暴露 Spring Data 内部 Page 实现。 */
public record PageView<T>(
		List<T> content,
		long totalElements,
		int totalPages,
		int number,
		int size,
		boolean first,
		boolean last) {
	public static <T> PageView<T> from(Page<T> page) {
		return new PageView<>(
				page.getContent(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.getNumber(),
				page.getSize(),
				page.isFirst(),
				page.isLast());
	}

	public static <T> PageView<T> from(List<T> values, int page, int size) {
		int normalizedPage = Math.max(0, page);
		int normalizedSize = Math.min(Math.max(1, size), 200);
		int from = Math.min(values.size(), normalizedPage * normalizedSize);
		int to = Math.min(values.size(), from + normalizedSize);
		int totalPages = values.isEmpty()
				? 0
				: (int) Math.ceil((double) values.size() / normalizedSize);
		return new PageView<>(
				values.subList(from, to),
				values.size(),
				totalPages,
				normalizedPage,
				normalizedSize,
				normalizedPage == 0,
				normalizedPage >= Math.max(0, totalPages - 1));
	}
}

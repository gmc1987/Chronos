package com.chronos.education.classgroup.dto;

import java.time.LocalDateTime;

public final class ClassGroupDtos {
	private ClassGroupDtos() {}
	public record GroupResponse(String id, String classId, String className, String name,
			String status, long memberCount, long rowVersion) {}
	public record MemberResponse(String id, String memberType, String memberId, String username,
			String role, String source, String status) {}
	public record MemberCommand(String memberType, String memberId, String username, String role) {}
	public record StatusCommand(String status, Long rowVersion) {}
	public record SyncCommand(String idempotencyKey) {}
	public record AuditResponse(String action, String memberType, String memberId,
			String detail, String actor, LocalDateTime createTime) {}
}

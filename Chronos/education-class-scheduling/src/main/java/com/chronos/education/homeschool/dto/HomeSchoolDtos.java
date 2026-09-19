package com.chronos.education.homeschool.dto;
import java.time.LocalDateTime;
public final class HomeSchoolDtos {
	private HomeSchoolDtos() {}
	public record ParentBindingCommand(String parentId, String username) {}
	public record ParentBindingResponse(String id, String parentId, String username, String status,
			LocalDateTime verifiedAt, LocalDateTime invalidatedAt) {}
	public record NoticeCommand(String schoolId, String classId, String title, String content,
			Boolean receiptRequired, LocalDateTime expireAt) {}
	public record NoticeResponse(String id, String schoolId, String classId, String title, String content,
			Boolean receiptRequired, LocalDateTime publishAt, LocalDateTime expireAt, String status,
			String publisherUsername) {}
	public record NoticeTargetResponse(String id, String noticeId, String studentId, String parentId,
			String deliveryStatus, LocalDateTime readAt, String receiptStatus, LocalDateTime receiptAt,
			String receiptComment) {}
	public record ReceiptCommand(String comment) {}
	public record ChildResponse(String id, String studentNo, String studentName, String classId,
			String relationship, Boolean primaryGuardian) {}
	public record FamilyNoticeResponse(String id, String studentId, String title, String content,
			Boolean receiptRequired, LocalDateTime publishAt, LocalDateTime expireAt,
			LocalDateTime readAt, String receiptStatus, LocalDateTime receiptAt, String receiptComment) {}
}

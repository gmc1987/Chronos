package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowNotificationService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 班主任家校通知发布、收件人快照和家长回执闭环。 */
@Service
public class ClassNoticeService {
	private final ClassNoticeRepository notices; private final ClassNoticeRecipientRepository recipients;
	private final AdministrativeClassRepository classes; private final StudentProfileRepository students;
	private final StudentGuardianRepository guardians; private final EducationUserBindingRepository bindings;
	private final HeadTeacherWorkbenchService workbench; private final WorkflowNotificationService notifications;
	private final IAuditLogService audit;
	public ClassNoticeService(ClassNoticeRepository notices, ClassNoticeRecipientRepository recipients,
			AdministrativeClassRepository classes, StudentProfileRepository students, StudentGuardianRepository guardians,
			EducationUserBindingRepository bindings, HeadTeacherWorkbenchService workbench,
			WorkflowNotificationService notifications, IAuditLogService audit) {
		this.notices=notices; this.recipients=recipients; this.classes=classes; this.students=students;
		this.guardians=guardians; this.bindings=bindings; this.workbench=workbench;
		this.notifications=notifications; this.audit=audit;
	}
	@Transactional(readOnly=true)
	public List<Map<String,Object>> teacherNotices(String username,String classId){ assertHeadTeacher(username,classId); return notices.findByClassIdOrderByCreateTimeDesc(classId).stream().map(this::summary).toList(); }
	@Transactional
	public ClassNotice create(String username,String classId,ClassNotice command){ assertHeadTeacher(username,classId); ClassNotice value=new ClassNotice(); value.setClassId(classId); value.setTitle(required(command.getTitle(),"标题不能为空")); value.setContent(required(command.getContent(),"正文不能为空")); value.setRequireReceipt(!Boolean.FALSE.equals(command.getRequireReceipt())); value.setReceiptDeadline(command.getReceiptDeadline()); value.setPublisherUsername(username); return notices.save(value); }
	@Transactional
	public ClassNotice publish(String username,String id){ ClassNotice value=notices.findById(id).orElseThrow(()->new IllegalArgumentException("班级通知不存在")); assertHeadTeacher(username,value.getClassId()); if("PUBLISHED".equals(value.getStatus())) return value; List<String> studentIds=students.findByAdministrativeClassId(value.getClassId()).stream().filter(s->"ACTIVE".equals(s.getEnrollmentStatus())).map(StudentProfile::getId).toList(); for(StudentGuardianRelation relation: guardians.findByStudentIdIn(studentIds)){ Optional<EducationUserBinding> binding=bindings.findByProfileTypeAndProfileId("PARENT",relation.getParentId()).filter(b->"ACTIVE".equals(b.getStatus())); ClassNoticeRecipient recipient=new ClassNoticeRecipient(); recipient.setNoticeId(id); recipient.setStudentId(relation.getStudentId()); recipient.setParentId(relation.getParentId()); binding.ifPresent(b->{ recipient.setRecipientUsername(b.getUsername()); recipient.setDeliveredAt(LocalDateTime.now()); }); recipients.save(recipient); binding.ifPresent(b->notifications.enqueueUserEvent("EDUCATION_CLASS_NOTICE",id,b.getUsername(),value.getTitle(),value.getContent(),"PUBLISHED:"+relation.getStudentId()+":"+relation.getParentId())); } value.setStatus("PUBLISHED"); value.setPublishedAt(LocalDateTime.now()); audit.log(username,"CLASS_NOTICE_PUBLISH","noticeId="+id); return notices.save(value); }
	@Transactional(readOnly=true)
	public List<Map<String,Object>> parentNotices(String username){ return recipients.findByRecipientUsernameOrderByCreateTimeDesc(username).stream().map(r->parentItem(r,notices.findById(r.getNoticeId()).orElse(null))).filter(Objects::nonNull).toList(); }
	@Transactional
	public ClassNoticeRecipient acknowledge(String username,String noticeId,String comment){ List<ClassNoticeRecipient> values=recipients.findByNoticeIdAndRecipientUsername(noticeId,username); if(values.isEmpty())throw new AccessDeniedException("无权访问该班级通知"); ClassNotice notice=notices.findById(noticeId).orElseThrow(()->new IllegalArgumentException("班级通知不存在")); if(notice.getReceiptDeadline()!=null&&LocalDateTime.now().isAfter(notice.getReceiptDeadline())) throw new IllegalStateException("通知回执已截止"); LocalDateTime now=LocalDateTime.now(); for(ClassNoticeRecipient value:values){if(value.getReadAt()==null)value.setReadAt(now);if(value.getAcknowledgedAt()==null)value.setAcknowledgedAt(now);value.setAcknowledgement(comment==null?null:comment.trim());recipients.save(value);} audit.log(username,"CLASS_NOTICE_ACKNOWLEDGE","noticeId="+noticeId); return values.getFirst(); }
	private void assertHeadTeacher(String username,String classId){ boolean allowed=workbench.classes(username).stream().anyMatch(c->classId.equals(c.get("id"))); if(!allowed)throw new AccessDeniedException("无权管理该班级通知"); }
	private Map<String,Object> summary(ClassNotice n){ long total=recipients.countByNoticeId(n.getId()),ack=recipients.countByNoticeIdAndAcknowledgedAtIsNotNull(n.getId()); Map<String,Object> m=new LinkedHashMap<>(); m.put("notice",n);m.put("recipientCount",total);m.put("acknowledgedCount",ack);return m; }
	private Map<String,Object> parentItem(ClassNoticeRecipient r,ClassNotice n){ if(n==null||!"PUBLISHED".equals(n.getStatus()))return null; Map<String,Object> m=new LinkedHashMap<>();m.put("notice",n);m.put("recipient",r);return m; }
	private String required(String v,String message){if(v==null||v.isBlank())throw new IllegalArgumentException(message);return v.trim();}
}

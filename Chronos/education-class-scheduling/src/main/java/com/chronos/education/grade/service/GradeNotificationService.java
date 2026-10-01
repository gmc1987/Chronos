package com.chronos.education.grade.service;
import com.chronos.workflow.WorkflowNotificationService; import org.springframework.stereotype.Service;
@Service public class GradeNotificationService { private final WorkflowNotificationService notifications; public GradeNotificationService(WorkflowNotificationService notifications){this.notifications=notifications;} public void published(String gradebookId,String studentId,String offeringName,String occurrence){ notifications.enqueueUserEvent("EDU_GRADE_PUBLISHED",gradebookId,studentId,"成绩已发布",offeringName+"成绩已发布",occurrence); } }

package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.model.dto.CollaborationDtos.*;
import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.service.ManagedFileService;
import com.chronos.commons.model.PageView;
import java.time.*; import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** 第二切片应用服务；只引用教务主数据，绝不创建平行教师/教学班/课表实体。 */
@Service @Transactional
public class TeachingCollaborationService {
 private final PreparationRepository preparations; private final PreparationMemberRepository members;
 private final PreparationMaterialRepository prepMaterials; private final PreparationCommentRepository comments;
 private final CoursewareRepository coursewares; private final CoursewareVersionRepository coursewareVersions;
 private final TeachingMaterialRepository materials; private final TeachingMaterialVersionRepository materialVersions;
 private final CourseOfferingRepository offerings; private final ScheduleEntryRepository schedules;
 private final EducationDataScopeService scopes; private final ManagedFileRepository files;
 private final ManagedFileService managedFiles;
 private final TeachingReviewService reviews;
 private EducationIdentityService identities;
 private TeachingCollaborationNotificationService notifications;
 @PersistenceContext private EntityManager em;
 public TeachingCollaborationService(PreparationRepository p,PreparationMemberRepository m,PreparationMaterialRepository pm,
   PreparationCommentRepository c,CoursewareRepository cw,CoursewareVersionRepository cv,TeachingMaterialRepository tm,
   TeachingMaterialVersionRepository tv,CourseOfferingRepository o,ScheduleEntryRepository s,EducationDataScopeService ds,
   ManagedFileRepository f,ManagedFileService managedFiles, TeachingReviewService reviewService) {
   preparations=p;members=m;prepMaterials=pm;comments=c;coursewares=cw;coursewareVersions=cv;
   materials=tm;materialVersions=tv;offerings=o;schedules=s;scopes=ds;files=f;
   this.managedFiles=managedFiles; reviews=reviewService; }

 @Autowired
 public TeachingCollaborationService(PreparationRepository p,PreparationMemberRepository m,PreparationMaterialRepository pm,
   PreparationCommentRepository c,CoursewareRepository cw,CoursewareVersionRepository cv,TeachingMaterialRepository tm,
   TeachingMaterialVersionRepository tv,CourseOfferingRepository o,ScheduleEntryRepository s,EducationDataScopeService ds,
   ManagedFileRepository f,ManagedFileService managedFiles,TeachingReviewService reviewService,
   EducationIdentityService identities,TeachingCollaborationNotificationService notifications) {
   this(p,m,pm,c,cw,cv,tm,tv,o,s,ds,f,managedFiles,reviewService);
   this.identities=identities;
   this.notifications=notifications;
 }
 private CourseOffering offering(String id,Authentication a){ var o=offerings.findById(id).orElseThrow(()->new NoSuchElementException("教学任务不存在")); scopes.assertOfferingAccess(scopes.resolve(a.getName()),o); return o; }
 private void file(String id,Authentication a){
   if(id==null||id.isBlank()||id.contains("/")||id.contains("\\")) throw new IllegalArgumentException("fileId无效");
   var f=files.findById(id).orElseThrow(()->new IllegalArgumentException("文件不存在"));
   if(!a.getName().equals(f.getOwnerUsername())) throw new org.springframework.security.access.AccessDeniedException("只能绑定本人上传的文件");
   if(!"ACTIVE".equals(f.getStatus())||!"PENDING_BIND".equals(f.getBindState())) throw new IllegalArgumentException("文件不可绑定");
 }
 public Preparation createPreparation(PreparationCreateRequest r,Authentication a){ var o=offering(r.offeringId(),a); if(r.scheduleEntryId()!=null){var e=schedules.findById(r.scheduleEntryId()).orElseThrow(); if(!o.getId().equals(e.getOfferingId())||!"PUBLISHED".equals(e.getStatus())) throw new IllegalArgumentException("只能关联本教学任务的已发布课表项");}
   var p=new Preparation(); p.setOfferingId(o.getId());p.setCampusId(o.getCampusId());p.setOwnerTeacherId(o.getTeacherId());p.setScheduleEntryId(r.scheduleEntryId());p.setTitle(r.title());p.setPreparationType(r.preparationType());p.setLocation(r.location());p.setAgenda(r.agenda());p.setScheduledAt(r.scheduledAt());p.setCreateBy(a.getName());return preparations.save(p); }
 public Preparation getPreparation(String id,Authentication a){var p=preparations.findById(id).orElseThrow();var scope=scopes.resolve(a.getName());if(scope.fullAccess())return p;try{scopes.assertOfferingAccess(scope,p.getOfferingId());return p;}catch(org.springframework.security.access.AccessDeniedException denied){var teacherIds=currentTeacherIds(a);if(members.findByPreparationId(id).stream().noneMatch(m->teacherIds.contains(m.getTeacherId())&&Set.of("PENDING","ACCEPT").contains(m.getInvitationStatus())))throw denied;return p;}}
 public Preparation submitPreparation(String id,Authentication a){var p=getPreparation(id,a);assertPreparationOwner(p,a);if(!"DRAFT".equals(p.getStatus()))throw new IllegalStateException("只有草稿备课可以提交审核");if("COLLECTIVE".equals(p.getPreparationType())){var accepted=members.findByPreparationId(id).stream().filter(x->"ACCEPT".equals(x.getInvitationStatus())).count();if(accepted<2)throw new IllegalStateException("集体备课至少需要两名已接受成员");if(p.getScheduledAt()==null||p.getAgenda()==null||p.getAgenda().isBlank())throw new IllegalStateException("集体备课必须填写时间和议程");}var review=reviews.submit("PREPARATION",id,p.getOfferingId(),Map.of("preparationType",p.getPreparationType()),a);p.setStatus("SUBMITTED");return preparations.save(p);}
 public PreparationMember invite(String id,MemberInviteRequest r,Authentication a){var p=getPreparation(id,a);assertPreparationOwner(p,a);scopes.assertTeacherAccess(scopes.resolve(a.getName()),r.teacherId());var m=new PreparationMember();m.setId(UUID.randomUUID().toString());m.setPreparationId(id);m.setTeacherId(r.teacherId());m.setRole(r.role());m.setInvitedBy(a.getName());m.setInvitedAt(Instant.now());var saved=members.save(m);if(notifications!=null)notifications.preparationInvited(p,r.teacherId());return saved;}
 public PreparationMember respond(String memberId,MemberResponseRequest r,Authentication a){var m=members.findById(memberId).orElseThrow();if(!Set.of("ACCEPT","DECLINE").contains(r.response())) throw new IllegalArgumentException("响应必须为ACCEPT或DECLINE");if(!currentTeacherIds(a).contains(m.getTeacherId())&&!scopes.resolve(a.getName()).fullAccess())throw new org.springframework.security.access.AccessDeniedException("只能响应发给本人的邀请");getPreparation(m.getPreparationId(),a);m.setInvitationStatus(r.response());m.setResponseComment(r.comment());m.setRespondedAt(Instant.now());if("ACCEPT".equals(r.response()))m.setJoinedAt(Instant.now());return members.save(m);}
 public PreparationMaterial addMaterial(String id,MaterialRequest r,Authentication a){getPreparation(id,a);file(r.fileId(),a);var m=new PreparationMaterial();m.setId(UUID.randomUUID().toString());m.setPreparationId(id);m.setTitle(r.title());m.setFileId(r.fileId());m.setMetadataJson(r.metadataJson());m.setCreateBy(a.getName());m.setCreateTime(Instant.now());m.setBindState("PENDING_BIND");var saved=prepMaterials.save(m);managedFiles.bind(List.of(r.fileId()),"EDUCATION_TEACHING",saved.getId(),a.getName());saved.setBindState("BOUND");saved.setBoundAt(Instant.now());return prepMaterials.save(saved);}
 public PreparationComment comment(String id,CommentRequest r,Authentication a){getPreparation(id,a);var c=new PreparationComment();c.setId(UUID.randomUUID().toString());c.setPreparationId(id);c.setAuthorId(a.getName());c.setContent(r.content());c.setCreateTime(Instant.now());c.setAuditAction("CREATE");return comments.save(c);}
 public Preparation conclude(String id,ConclusionRequest r,Authentication a){var p=getPreparation(id,a);assertPreparationOwner(p,a);if(!"COLLECTIVE".equals(p.getPreparationType())) throw new IllegalArgumentException("只有集体备课可以形成结论");if(members.findByPreparationId(id).stream().filter(x->"ACCEPT".equals(x.getInvitationStatus())).count()<2) throw new IllegalStateException("集体备课至少需要两名已接受成员");if(r.lessonPlanId()!=null){var lesson=em.find(LessonPlan.class,r.lessonPlanId());if(lesson==null||!p.getOfferingId().equals(lesson.getOfferingId())) throw new IllegalArgumentException("结论只能关联同一教学班教案");}p.setConclusion(r.conclusion());p.setConclusionLessonPlanId(r.lessonPlanId());p.setStatus("CONCLUDED");return preparations.save(p);}

 private Set<String> currentTeacherIds(Authentication a){return identities==null?Set.of(a.getName()):identities.teacherIds(a.getName());}
 private void assertPreparationOwner(Preparation p,Authentication a){if(!a.getName().equals(p.getCreateBy())&&!scopes.resolve(a.getName()).fullAccess())throw new org.springframework.security.access.AccessDeniedException("只有备课主持人可以执行该操作");}
 public Object createResource(boolean courseware,ResourceCreateRequest r,Authentication a){var o=offering(r.offeringId(),a);if(courseware){var x=new Courseware();x.setOfferingId(o.getId());x.setCampusId(o.getCampusId());x.setOwnerTeacherId(o.getTeacherId());x.setTitle(r.title());x.setShareScope(scope(r.shareScope()));x.setPreparationId(blank(r.preparationId()));x.setLessonPlanId(blank(r.lessonPlanId()));x.setPlanItemId(blank(r.planItemId()));x.setSourceType(blank(r.sourceType()));x.setCreateBy(a.getName());return coursewares.save(x);}var x=new TeachingMaterial();x.setOfferingId(o.getId());x.setCampusId(o.getCampusId());x.setOwnerTeacherId(o.getTeacherId());x.setTitle(r.title());x.setMaterialType(r.materialType());x.setShareScope(scope(r.shareScope()));x.setPreparationId(blank(r.preparationId()));x.setLessonPlanId(blank(r.lessonPlanId()));x.setPlanItemId(blank(r.planItemId()));x.setSourceType(blank(r.sourceType()));x.setCreateBy(a.getName());return materials.save(x);}
 public Courseware createCoursewareFromPreparation(String preparationId,ResourceCopyRequest r,Authentication a){
   var preparation=preparations.findById(preparationId).orElseThrow(()->new NoSuchElementException("备课不存在"));
   scopes.assertOfferingAccess(scopes.resolve(a.getName()),preparation.getOfferingId());
   if(preparation.isArchived()) throw new IllegalStateException("已归档备课不能生成课件");
   if(!preparation.getOfferingId().equals(r.offeringId())) throw new IllegalArgumentException("课件教学任务必须与备课一致");
   var o=offering(r.offeringId(),a);
   var x=new Courseware();
   x.setOfferingId(o.getId()); x.setCampusId(o.getCampusId()); x.setOwnerTeacherId(o.getTeacherId());
   x.setTitle(r.title()); x.setShareScope(scope(r.shareScope())); x.setPreparationId(preparationId);
   x.setLessonPlanId(blank(r.lessonPlanId())); x.setPlanItemId(blank(r.planItemId())); x.setSourceType("GENERATED");
   x.setCreateBy(a.getName());
   return coursewares.save(x);
 }
 public Object copyResource(boolean courseware,String id,ResourceCopyRequest r,Authentication a){var target=offering(r.offeringId(),a);if(courseware){var source=coursewares.findById(id).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),source.getOfferingId());var x=new Courseware();x.setOfferingId(target.getId());x.setCampusId(target.getCampusId());x.setOwnerTeacherId(target.getTeacherId());x.setTitle(r.title());x.setShareScope(scope(r.shareScope()));x.setPreparationId(blank(r.preparationId()));x.setLessonPlanId(blank(r.lessonPlanId()));x.setPlanItemId(blank(r.planItemId()));x.setSourceType("COPIED");x.setCreateBy(a.getName());return coursewares.save(x);}var source=materials.findById(id).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),source.getOfferingId());var x=new TeachingMaterial();x.setOfferingId(target.getId());x.setCampusId(target.getCampusId());x.setOwnerTeacherId(target.getTeacherId());x.setTitle(r.title());x.setMaterialType(source.getMaterialType());x.setShareScope(scope(r.shareScope()));x.setPreparationId(blank(r.preparationId()));x.setLessonPlanId(blank(r.lessonPlanId()));x.setPlanItemId(blank(r.planItemId()));x.setSourceType("COPIED");x.setCreateBy(a.getName());return materials.save(x);}
 public Object updateResource(boolean courseware,String id,ResourceCreateRequest r,Authentication a){var o=offering(r.offeringId(),a);if(courseware){var x=coursewares.findById(id).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());if(!x.getOfferingId().equals(o.getId()))throw new IllegalArgumentException("资源不能跨教学任务移动");x.setTitle(r.title());x.setShareScope(scope(r.shareScope()));x.setPreparationId(blank(r.preparationId()));x.setLessonPlanId(blank(r.lessonPlanId()));x.setPlanItemId(blank(r.planItemId()));return coursewares.save(x);}var x=materials.findById(id).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());if(!x.getOfferingId().equals(o.getId()))throw new IllegalArgumentException("资源不能跨教学任务移动");x.setTitle(r.title());x.setMaterialType(r.materialType());x.setShareScope(scope(r.shareScope()));x.setPreparationId(blank(r.preparationId()));x.setLessonPlanId(blank(r.lessonPlanId()));x.setPlanItemId(blank(r.planItemId()));return materials.save(x);}
 private String blank(String value){return value==null||value.isBlank()?null:value;}
 private String scope(String s){if(s==null||s.isBlank())return "PRIVATE";String v=s.trim().toUpperCase(Locale.ROOT);if("OFFERING".equals(v))v="TEACHING_GROUP";if("CAMPUS".equals(v)||"PUBLIC".equals(v))v="SCHOOL";if(!Set.of("PRIVATE","TEACHING_GROUP","SCHOOL","STUDENT_CLASS").contains(v))throw new IllegalArgumentException("共享范围无效");return v;}
 public Object addVersion(boolean cw,String id,VersionRequest r,Authentication a){file(r.fileId(),a); int n;if(cw){var x=coursewares.findById(id).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());n=coursewareVersions.findByCoursewareIdOrderByVersionNoDesc(id).stream().findFirst().map(v->v.getVersionNo()+1).orElse(1);var v=new CoursewareVersion();v.setCoursewareId(id);v.setVersionNo(n);v.setFileId(r.fileId());v.setMetadataJson(r.metadataJson());v.setCreateBy(a.getName());v.setCreateTime(java.time.LocalDateTime.now());var saved=coursewareVersions.save(v);managedFiles.bind(List.of(r.fileId()),"EDUCATION_TEACHING",saved.getId(),a.getName());saved.setBindState("BOUND");saved.setBoundAt(Instant.now());return coursewareVersions.save(saved);}var x=materials.findById(id).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());n=materialVersions.findByMaterialIdOrderByVersionNoDesc(id).stream().findFirst().map(v->v.getVersionNo()+1).orElse(1);var v=new TeachingMaterialVersion();v.setMaterialId(id);v.setVersionNo(n);v.setFileId(r.fileId());v.setMetadataJson(r.metadataJson());v.setCreateBy(a.getName());v.setCreateTime(java.time.LocalDateTime.now());var saved=materialVersions.save(v);managedFiles.bind(List.of(r.fileId()),"EDUCATION_TEACHING",saved.getId(),a.getName());saved.setBindState("BOUND");saved.setBoundAt(Instant.now());return materialVersions.save(saved);}
 public Object submitVersionReview(boolean cw,String versionId,Authentication a){
   if(cw){
     var v=coursewareVersions.findById(versionId).orElseThrow();var x=coursewares.findById(v.getCoursewareId()).orElseThrow();
     scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());
     requireSubmittable(v.getStatus(),v.getBindState());
     v.setStatus("SUBMITTED");coursewareVersions.save(v);
     return reviews.submit("COURSEWARE",x.getId(),x.getOfferingId(),Map.of("versionId",v.getId()),a);
   }
   var v=materialVersions.findById(versionId).orElseThrow();var x=materials.findById(v.getMaterialId()).orElseThrow();
   scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());
   requireSubmittable(v.getStatus(),v.getBindState());
   v.setStatus("SUBMITTED");materialVersions.save(v);
   return reviews.submit("MATERIAL",x.getId(),x.getOfferingId(),Map.of("versionId",v.getId()),a);
 }

 private void requireSubmittable(String status,String bindState){
   if(!Set.of("DRAFT","REJECTED").contains(status)) throw new IllegalStateException("只有草稿或驳回版本可以提交审核");
   if(!"BOUND".equals(bindState)) throw new IllegalStateException("附件未完成业务绑定");
 }
 public Object approveVersionReview(boolean cw,String versionId,Authentication a){if(cw){var v=coursewareVersions.findById(versionId).orElseThrow();var x=coursewares.findById(v.getCoursewareId()).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());return reviews.approve("COURSEWARE",x.getId(),v.getId(),a);}var v=materialVersions.findById(versionId).orElseThrow();var x=materials.findById(v.getMaterialId()).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());return reviews.approve("MATERIAL",x.getId(),v.getId(),a);}
 public Object transition(boolean cw,String versionId,String action,Authentication a){if(cw){var v=coursewareVersions.findById(versionId).orElseThrow();var x=coursewares.findById(v.getCoursewareId()).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());return transition(v,action);}var v=materialVersions.findById(versionId).orElseThrow();var x=materials.findById(v.getMaterialId()).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());return transition(v,action);}
 public Object setCurrent(boolean cw,String versionId,Authentication a){if(cw){var v=coursewareVersions.findById(versionId).orElseThrow();var x=coursewares.findById(v.getCoursewareId()).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());if(!"PUBLISHED".equals(v.getStatus()))throw new IllegalStateException("只有已发布版本可以设为当前");x.setPublishedVersionNo(v.getVersionNo());return coursewares.save(x);}var v=materialVersions.findById(versionId).orElseThrow();var x=materials.findById(v.getMaterialId()).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());if(!"PUBLISHED".equals(v.getStatus()))throw new IllegalStateException("只有已发布版本可以设为当前");x.setPublishedVersionNo(v.getVersionNo());return materials.save(x);}
 public List<?> versions(boolean cw,String id,Authentication a){if(cw){var x=coursewares.findById(id).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());return coursewareVersions.findByCoursewareIdOrderByVersionNoDesc(id);}var x=materials.findById(id).orElseThrow();scopes.assertOfferingAccess(scopes.resolve(a.getName()),x.getOfferingId());return materialVersions.findByMaterialIdOrderByVersionNoDesc(id);}
 public Object readPublished(boolean cw,String id,Authentication a){return versions(cw,id,a).stream().filter(v->"PUBLISHED".equals(cw?((CoursewareVersion)v).getStatus():((TeachingMaterialVersion)v).getStatus())).findFirst().orElse(null);}
 private <T> T transition(T v,String action){
   Instant now=Instant.now();
   if(v instanceof CoursewareVersion x){
     transitionCoursewareVersion(x,action,now);return (T)coursewareVersions.save(x);
   }
   var x=(TeachingMaterialVersion)v;transitionMaterialVersion(x,action,now);return (T)materialVersions.save(x);
 }

 private void transitionCoursewareVersion(CoursewareVersion version,String action,Instant now){
   switch(action){
     case "submit" -> throw new IllegalStateException("请使用提交审核操作");
     case "revise" -> {if(!"REJECTED".equals(version.getStatus()))throw new IllegalStateException("只有驳回版本可以修订");version.setStatus("DRAFT");}
     case "publish" -> {if(!Set.of("APPROVED","REVIEWED").contains(version.getStatus()))throw new IllegalStateException("仅审核通过版本可发布");version.setStatus("PUBLISHED");version.setPublishedAt(now);var parent=coursewares.findById(version.getCoursewareId()).orElseThrow();parent.setPublishedVersionNo(version.getVersionNo());coursewares.save(parent);}
     case "archive" -> {if(!Set.of("DRAFT","REJECTED","APPROVED").contains(version.getStatus()))throw new IllegalStateException("当前版本不能归档");version.setStatus("ARCHIVED");version.setArchivedAt(now);}
     case "bind" -> {if(!"PENDING_BIND".equals(version.getBindState()))throw new IllegalStateException("文件已完成绑定");version.setBindState("BOUND");version.setBoundAt(now);}
     default -> throw new IllegalArgumentException("不支持的版本操作");
   }
 }

 private void transitionMaterialVersion(TeachingMaterialVersion version,String action,Instant now){
   switch(action){
     case "submit" -> throw new IllegalStateException("请使用提交审核操作");
     case "revise" -> {if(!"REJECTED".equals(version.getStatus()))throw new IllegalStateException("只有驳回版本可以修订");version.setStatus("DRAFT");}
     case "publish" -> {if(!Set.of("APPROVED","REVIEWED").contains(version.getStatus()))throw new IllegalStateException("仅审核通过版本可发布");version.setStatus("PUBLISHED");version.setPublishedAt(now);var parent=materials.findById(version.getMaterialId()).orElseThrow();parent.setPublishedVersionNo(version.getVersionNo());materials.save(parent);}
     case "archive" -> {if(!Set.of("DRAFT","REJECTED","APPROVED").contains(version.getStatus()))throw new IllegalStateException("当前版本不能归档");version.setStatus("ARCHIVED");version.setArchivedAt(now);}
     case "bind" -> {if(!"PENDING_BIND".equals(version.getBindState()))throw new IllegalStateException("文件已完成绑定");version.setBindState("BOUND");version.setBoundAt(now);}
     default -> throw new IllegalArgumentException("不支持的版本操作");
   }
 }
 @Scheduled(fixedDelayString="${education.teaching.file-reconcile-ms:900000}") public void reconcilePendingFiles(){var cutoff=LocalDateTime.now().minusHours(24); for(var f:files.findTop100ByBusinessTypeAndBusinessIdIsNullAndStatusAndCreateTimeBeforeOrderByCreateTimeAsc("EDUCATION_TEACHING","PENDING_BIND",cutoff)){f.setStatus("EXPIRED");files.save(f);}}
}

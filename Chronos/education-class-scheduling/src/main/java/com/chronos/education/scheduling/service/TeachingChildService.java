package com.chronos.education.scheduling.service;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 子对象维护服务：所有关系先校验主对象，再执行白名单字段更新。 */
@Service @Transactional
public class TeachingChildService {
 private record Spec(Class<?> type, String parentField, Class<?> parentType, String parentIdField) {}
 private static final Map<String,Spec> SPECS=Map.ofEntries(
  Map.entry("PLAN_ITEM",new Spec(TeachingPlanItem.class,"planId",TeachingPlan.class,"id")),
  Map.entry("PLAN_VERSION",new Spec(TeachingPlanVersion.class,"planId",TeachingPlan.class,"id")),
  Map.entry("LESSON_VERSION",new Spec(LessonPlanVersion.class,"lessonPlanId",LessonPlan.class,"id")),
  Map.entry("LESSON_REVIEW",new Spec(LessonPlanReview.class,"lessonPlanVersionId",LessonPlanVersion.class,"id")),
  Map.entry("PREPARATION_MEMBER",new Spec(PreparationMember.class,"preparationId",Preparation.class,"id")),
  Map.entry("PREPARATION_MATERIAL",new Spec(PreparationMaterial.class,"preparationId",Preparation.class,"id")),
  Map.entry("PREPARATION_COMMENT",new Spec(PreparationComment.class,"preparationId",Preparation.class,"id")),
  Map.entry("QUESTION_OPTION",new Spec(QuestionOption.class,"questionId",Question.class,"id")),
  Map.entry("QUESTION_KNOWLEDGE_POINT",new Spec(QuestionKnowledgePoint.class,"questionId",Question.class,"id")),
  Map.entry("RESEARCH_GROUP_MEMBER",new Spec(ResearchGroupMember.class,"groupId",ResearchGroup.class,"id")),
  Map.entry("RESEARCH_ACTIVITY_MEMBER",new Spec(ResearchActivityMember.class,"activityId",ResearchActivity.class,"id")),
  Map.entry("RESEARCH_MATERIAL",new Spec(ResearchMaterial.class,"activityId",ResearchActivity.class,"id")),
  Map.entry("RESEARCH_RESULT",new Spec(ResearchResult.class,"activityId",ResearchActivity.class,"id")));
 @PersistenceContext private EntityManager em; private final EducationDataScopeService scopes;
 public TeachingChildService(EducationDataScopeService scopes){this.scopes=scopes;}
 private Spec spec(String type){Spec s=SPECS.get(type==null?"":type.trim().toUpperCase(Locale.ROOT)); if(s==null) throw new IllegalArgumentException("不支持的教学子对象类型"); return s;}
 @Transactional(readOnly=true) public PageView<?> page(String type,String parentId,int page,int size,Authentication a){
  Spec s=spec(type); Object parent=parent(s,parentId); authorize(parent,a);
  var q=em.createQuery("select e from "+s.type.getSimpleName()+" e where e."+s.parentField+" = :parentId order by e.id desc",s.type).setParameter("parentId",parentId);
  return PageView.from(q.getResultList(),page,size);
 }
 public Object create(String type,Map<String,Object> body,Authentication a){Spec s=spec(type); String parentId=text(body,s.parentField); if(parentId==null) throw new IllegalArgumentException("父对象不能为空"); authorize(parent(s,parentId),a); Object e=inst(s.type); set(e,"id",text(body,"id")!=null?text(body,"id"):UUID.randomUUID().toString()); set(e,s.parentField,parentId); apply(e,body); defaults(e,a); em.persist(e); return e;}
 public Object update(String type,String id,Map<String,Object> body,Authentication a){Spec s=spec(type); Object e=find(s.type,id); String p=textValue(e,s.parentField); authorize(parent(s,p),a); body=new HashMap<>(body); body.remove(s.parentField); apply(e,body); return e;}
 public Object archive(String type,String id,Authentication a){Spec s=spec(type); Object e=find(s.type,id); authorize(parent(s,textValue(e,s.parentField)),a); if(has(e,"archived")) set(e,"archived",true); else em.remove(e); return e;}
 private Object parent(Spec s,String id){if(id==null||id.isBlank())throw new IllegalArgumentException("父对象不能为空"); Object p=em.find(s.parentType,id); if(p==null)throw new IllegalArgumentException("父对象不存在"); return p;}
 private void authorize(Object p,Authentication a){String offering=textValue(p,"offeringId"); if(offering!=null)scopes.assertOfferingAccess(scopes.resolve(a.getName()),offering); else scopes.assertFullAccess(scopes.resolve(a.getName())); if(Boolean.TRUE.equals(read(p,"archived")))throw new IllegalStateException("主对象已归档");}
 private Object find(Class<?> c,String id){Object e=em.find(c,id);if(e==null)throw new IllegalArgumentException("子对象不存在");return e;}
 private Object inst(Class<?> c){try{return c.getDeclaredConstructor().newInstance();}catch(Exception e){throw new IllegalStateException(e);}}
 private void defaults(Object e,Authentication a){if(has(e,"createBy"))set(e,"createBy",a.getName());if(has(e,"createTime"))set(e,"createTime",Instant.now());if(has(e,"reviewedAt"))set(e,"reviewedAt",Instant.now());}
 private void apply(Object e,Map<String,Object>b){b.forEach((k,v)->{if(v==null||k.equals("id"))return; if(!has(e,k))return; set(e,k,convert(v,e.getClass(),k));});}
 private Object convert(Object v,Class<?> c,String p){try{var m=Arrays.stream(c.getMethods()).filter(x->x.getName().equals("get"+Character.toUpperCase(p.charAt(0))+p.substring(1))).findFirst().orElse(null);if(m==null)return v;Class<?> t=m.getReturnType();if(t==Integer.class||t==int.class)return v instanceof Number n?n.intValue():Integer.valueOf(v.toString());if(t==Instant.class)return Instant.parse(v.toString());return v;}catch(Exception ex){throw new IllegalArgumentException("字段格式错误: "+p,ex);}}
 private boolean has(Object e,String p){return Arrays.stream(e.getClass().getMethods()).anyMatch(m->m.getName().equals("set"+Character.toUpperCase(p.charAt(0))+p.substring(1))&&m.getParameterCount()==1);}
 private void set(Object e,String p,Object v){try{var m=Arrays.stream(e.getClass().getMethods()).filter(x->x.getName().equals("set"+Character.toUpperCase(p.charAt(0))+p.substring(1))&&x.getParameterCount()==1).findFirst().orElseThrow();m.invoke(e,v);}catch(Exception ex){throw new IllegalArgumentException("字段不可写: "+p,ex);}}
 private Object read(Object e,String p){try{return e.getClass().getMethod("is"+Character.toUpperCase(p.charAt(0))+p.substring(1)).invoke(e);}catch(Exception x){return null;}}
 private String text(Map<String,Object>b,String k){Object v=b.get(k);return v==null?null:v.toString().trim();}
 private String textValue(Object e,String p){try{Object v=e.getClass().getMethod("get"+Character.toUpperCase(p.charAt(0))+p.substring(1)).invoke(e);return v==null?null:v.toString();}catch(Exception x){return null;}}
}

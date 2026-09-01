 package com.chronos.service.impl;
 
 import com.chronos.Idao.IOrganizationRepository;
 import com.chronos.Idao.IOrganizationUnitRepository;
 import com.chronos.Idao.IEmployeeAssignmentRepository;
 import com.chronos.Idao.IAdminUserRepository;
 import com.chronos.commons.utils.BeanCopyUtil;
 import com.chronos.model.dto.OrganizationDTO;
 import com.chronos.model.pojo.Organization;
 import com.chronos.model.vo.OrganizationVO;
 import com.chronos.service.iService.IOrganizationService;
 import com.chronos.service.iService.IAuditLogService;
 import java.time.LocalDateTime;
 import java.util.Optional;
 import java.util.Map;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.Pageable;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 
 
 @Service("organizationService")
 public class OrganizationServiceImpl
   implements IOrganizationService
 {
   @Autowired
   private IOrganizationRepository organizationRepository;
   @Autowired private IOrganizationUnitRepository unitRepository;
   @Autowired private IEmployeeAssignmentRepository assignmentRepository;
   @Autowired private IAdminUserRepository userRepository;
   @Autowired private IAuditLogService audit;
   
   public Page<Organization> pageOrganizations(OrganizationDTO dto, Pageable pageable) {
     return this.organizationRepository.findAll(pageable);
   }
 
   
   public OrganizationVO getById(String id) {
     Optional<Organization> opt = this.organizationRepository.findById(id);
     if (!opt.isPresent()) return null; 
     Organization o = opt.get();
     return OrganizationVO.builder()
       .id(o.getId())
       .organizationName(o.getOrganizationName())
       .orgCode(o.getOrgCode())
       .organizationType(o.getOrganizationType())
       .shortName(o.getShortName()).timezone(o.getTimezone()).status(o.getStatus()).sortOrder(o.getSortOrder())
       .parentOrganizationId(o.getParentOrgId() == null ? null : o.getParentOrgId().getId())
       .description(o.getDescription())
       .mailingAddress(o.getMailingAddress())
       .tel(o.getTel())
       .organizationManager(o.getOrganizationManager())
       .industries(o.getIndustries())
       .registerTime(o.getRegisterTime())
       .lastUpdateTime(o.getLastUpdateTime())
       .build();
   }
 
   
   @Transactional
   public void save(OrganizationDTO dto) {
     Organization o = new Organization();
     BeanCopyUtil.copyNonNullProperties(dto, o);
     if (dto.getParentOrganizationId() != null) o.setParentOrgId(organizationRepository.findById(dto.getParentOrganizationId()).orElseThrow(() -> new IllegalArgumentException("parent organization not found")));
     if (o.getRegisterTime() == null) o.setRegisterTime(LocalDateTime.now()); 
     this.organizationRepository.save(o);
     audit.log(actor(),"ORGANIZATION_CREATE","code="+o.getOrgCode()+", name="+o.getOrganizationName());
   }
 
   
   @Transactional
   public void update(OrganizationDTO dto) {
     if (dto == null || dto.getId() == null) throw new IllegalArgumentException("id required"); 
     Optional<Organization> opt = this.organizationRepository.findById(dto.getId());
     if (!opt.isPresent()) throw new IllegalArgumentException("organization not found"); 
     Organization o = opt.get();
     if (dto.getOrgCode() != null && !dto.getOrgCode().equals(o.getOrgCode())) throw new IllegalArgumentException("机构编码创建后不允许修改");
     BeanCopyUtil.copyNonNullProperties(dto, o);
     if (dto.getParentOrganizationId() != null) {
       if (dto.getId().equals(dto.getParentOrganizationId())) throw new IllegalArgumentException("parent organization cannot be self");
       o.setParentOrgId(organizationRepository.findById(dto.getParentOrganizationId()).orElseThrow(() -> new IllegalArgumentException("parent organization not found")));
     }
     o.setLastUpdateTime(LocalDateTime.now());
     this.organizationRepository.save(o);
     audit.log(actor(),"ORGANIZATION_UPDATE","id="+o.getId()+", code="+o.getOrgCode()+", status="+o.getStatus());
   }
 
   
   @Transactional
   public void delete(String id) {
     Organization organization=organizationRepository.findById(id).orElseThrow(()->new IllegalArgumentException("organization not found"));
     organization.setStatus(0);organization.setLastUpdateTime(LocalDateTime.now());organizationRepository.save(organization);
     audit.log(actor(),"ORGANIZATION_DISABLE","id="+id+", impact="+impact(id));
   }

   public Map<String,Long> impact(String id){organizationRepository.findById(id).orElseThrow(()->new IllegalArgumentException("organization not found"));return Map.of("departments",unitRepository.countByOrgId(id),"assignments",assignmentRepository.countByOrganizationId(id),"accounts",userRepository.countByOrganizationId(id));}
   private String actor(){var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();return auth==null?"system":auth.getName();}
 }

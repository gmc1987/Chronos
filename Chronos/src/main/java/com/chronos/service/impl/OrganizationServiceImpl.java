 package com.chronos.service.impl;
 
 import com.chronos.Idao.IOrganizationRepository;
 import com.chronos.commons.utils.BeanCopyUtil;
 import com.chronos.model.dto.OrganizationDTO;
 import com.chronos.model.pojo.Organization;
 import com.chronos.model.vo.OrganizationVO;
 import com.chronos.service.iService.IOrganizationService;
 import java.time.LocalDateTime;
 import java.util.Optional;
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
     if (o.getRegisterTime() == null) o.setRegisterTime(LocalDateTime.now()); 
     this.organizationRepository.save(o);
   }
 
   
   @Transactional
   public void update(OrganizationDTO dto) {
     if (dto == null || dto.getId() == null) throw new IllegalArgumentException("id required"); 
     Optional<Organization> opt = this.organizationRepository.findById(dto.getId());
     if (!opt.isPresent()) throw new IllegalArgumentException("organization not found"); 
     Organization o = opt.get();
     BeanCopyUtil.copyNonNullProperties(dto, o);
     o.setLastUpdateTime(LocalDateTime.now());
     this.organizationRepository.save(o);
   }
 
   
   @Transactional
   public void delete(String id) {
     this.organizationRepository.deleteById(id);
   }
 }



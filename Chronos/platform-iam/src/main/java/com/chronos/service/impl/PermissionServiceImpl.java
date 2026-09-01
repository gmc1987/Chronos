 package com.chronos.service.impl;
 
 import com.chronos.Idao.IPermissionRepository;
 import com.chronos.commons.utils.BeanCopyUtil;
 import com.chronos.model.dto.PermissionDTO;
 import com.chronos.model.pojo.Permission;
 import com.chronos.model.vo.PermissionVO;
 import com.chronos.service.iService.IPermissionService;
 import java.time.LocalDateTime;
 import java.util.List;
 import java.util.Optional;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.Pageable;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 
 
 
 @Service("permissionService")
 public class PermissionServiceImpl
   implements IPermissionService
 {
   @Autowired
   private IPermissionRepository permissionRepository;
   
   public Page<Permission> pagePermissions(PermissionDTO dto, Pageable pageable) {
     return this.permissionRepository.findAll(pageable);
   }
 
   
   public PermissionVO getPermissionById(String id) {
     Optional<Permission> opt = this.permissionRepository.findById(id);
     if (!opt.isPresent()) return null; 
     Permission p = opt.get();
     PermissionVO vo = PermissionVO.builder().id(p.getId()).permissionName(p.getPermissionName()).permissionCode(p.getPermissionCode())
         .permissionType(p.getPermissionType()).resourcePattern(p.getResourcePattern()).httpMethod(p.getHttpMethod())
         .status(p.getStatus()).description(p.getDescription()).build();
     return vo;
   }
 
   
   @Transactional
  public void save(PermissionDTO dto) {
     ensureCodeAvailable(dto.getPermissionCode(), null);
     Permission p = new Permission();
     BeanCopyUtil.copyNonNullProperties(dto, p);
     if (p.getCreateTime() == null) p.setCreateTime(LocalDateTime.now()); 
     this.permissionRepository.save(p);
   }
 
   
   @Transactional
   public void update(PermissionDTO dto) {
     if (dto == null || dto.getId() == null) throw new IllegalArgumentException("id required"); 
     Optional<Permission> opt = this.permissionRepository.findById(dto.getId());
     if (!opt.isPresent()) throw new IllegalArgumentException("permission not found"); 
     Permission p = opt.get();
     ensureCodeAvailable(dto.getPermissionCode(), p.getId());
     BeanCopyUtil.copyNonNullProperties(dto, p);
     this.permissionRepository.save(p);
   }
 
   
   @Transactional
  public void delete(String id) {
     this.permissionRepository.deleteById(id);
  }

  private void ensureCodeAvailable(String code, String currentId) {
    if (code == null || code.isBlank()) return;
    Permission existing = permissionRepository.findByPermissionCode(code.trim());
    if (existing != null && !existing.getId().equals(currentId)) {
      throw new IllegalArgumentException("permission code already exists");
    }
  }
 
   
   @Transactional
   public void saveAll(List<PermissionDTO> dtos) {
     if (dtos == null || dtos.isEmpty()) {
       return;
     }
 
 
     
     List<Permission> permissions = dtos.stream().map(dto -> { Permission p = new Permission(); BeanCopyUtil.copyNonNullProperties(dto, p); if (p.getCreateTime() == null) p.setCreateTime(LocalDateTime.now());  return p; }).toList();
     this.permissionRepository.saveAll(permissions);
   }
 }

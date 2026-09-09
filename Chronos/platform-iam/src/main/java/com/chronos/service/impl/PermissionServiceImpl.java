 package com.chronos.service.impl;
 
 import com.chronos.Idao.IPermissionRepository;
 import com.chronos.Idao.IRoleMenuPermissionRepository;
 import com.chronos.Idao.IRolePermissionRepository;
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
  @Autowired
  private IRolePermissionRepository rolePermissionRepository;
  @Autowired
  private IRoleMenuPermissionRepository roleMenuPermissionRepository;
   
   public Page<Permission> pagePermissions(PermissionDTO dto, Pageable pageable) {
     return this.permissionRepository.findAll((root, query, cb) -> {
       var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
       if (dto != null) {
         if (dto.getPermissionType() != null && !dto.getPermissionType().isBlank()) predicates.add(cb.equal(cb.upper(root.<String>get("permissionType")), dto.getPermissionType().trim().toUpperCase()));
         if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
           String keyword = "%" + dto.getKeyword().trim().toLowerCase() + "%";
           predicates.add(cb.or(
               cb.like(cb.lower(root.<String>get("permissionName")), keyword),
               cb.like(cb.lower(root.<String>get("permissionCode")), keyword)));
         }
         if (dto.getPermissionName() != null && !dto.getPermissionName().isBlank()) predicates.add(cb.like(cb.lower(root.<String>get("permissionName")), "%" + dto.getPermissionName().trim().toLowerCase() + "%"));
         if (dto.getPermissionCode() != null && !dto.getPermissionCode().isBlank()) predicates.add(cb.like(cb.lower(root.<String>get("permissionCode")), "%" + dto.getPermissionCode().trim().toLowerCase() + "%"));
         if (dto.getStatus() != null) predicates.add(cb.equal(root.get("status"), dto.getStatus()));
       }
       return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
     }, pageable);
   }
 
   
   public PermissionVO getPermissionById(String id) {
     Optional<Permission> opt = this.permissionRepository.findById(id);
     if (!opt.isPresent()) return null; 
     Permission p = opt.get();
     PermissionVO vo = PermissionVO.builder().id(p.getId()).permissionName(p.getPermissionName()).permissionCode(p.getPermissionCode())
         .permissionType(p.getPermissionType()).menuId(p.getMenuId()).resourcePattern(p.getResourcePattern()).httpMethod(p.getHttpMethod())
         .actionType(p.getActionType()).resourceType(p.getResourceType()).scopeType(p.getScopeType()).configJson(p.getConfigJson())
         .status(p.getStatus()).description(p.getDescription()).build();
     return vo;
   }
 
   
   @Transactional
  public void save(PermissionDTO dto) {
     validate(dto);
     ensureCodeAvailable(dto.getPermissionCode(), null);
     Permission p = new Permission();
     BeanCopyUtil.copyNonNullProperties(dto, p);
     p.setBuiltIn(false);
     if (p.getCreateTime() == null) p.setCreateTime(LocalDateTime.now()); 
     this.permissionRepository.save(p);
   }
 
   
   @Transactional
   public void update(PermissionDTO dto) {
     if (dto == null || dto.getId() == null) throw new IllegalArgumentException("id required"); 
     Optional<Permission> opt = this.permissionRepository.findById(dto.getId());
     if (!opt.isPresent()) throw new IllegalArgumentException("permission not found"); 
     Permission p = opt.get();
     if (Boolean.TRUE.equals(p.getBuiltIn())) {
       dto.setPermissionCode(p.getPermissionCode());
       dto.setPermissionType(p.getPermissionType());
     }
     validate(dto);
     ensureCodeAvailable(dto.getPermissionCode(), p.getId());
     BeanCopyUtil.copyNonNullProperties(dto, p);
     this.permissionRepository.save(p);
   }
 
   
   @Transactional
  public void delete(String id) {
     Permission permission = permissionRepository.findById(id)
         .orElseThrow(() -> new IllegalArgumentException("permission not found"));
     if (Boolean.TRUE.equals(permission.getBuiltIn())) {
       throw new IllegalArgumentException("built-in permission cannot be deleted");
     }
     if (rolePermissionRepository.existsByPermissionId(id)
         || roleMenuPermissionRepository.existsByPermissionId(id)) {
       throw new IllegalArgumentException("permission is assigned to a role; revoke it before deletion");
     }
     this.permissionRepository.deleteById(id);
  }

  private void ensureCodeAvailable(String code, String currentId) {
    if (code == null || code.isBlank()) return;
    Permission existing = permissionRepository.findByPermissionCode(code.trim());
    if (existing != null && !existing.getId().equals(currentId)) {
      throw new IllegalArgumentException("permission code already exists");
    }
  }

  private void validate(PermissionDTO dto) {
    if (dto == null) throw new IllegalArgumentException("permission required");
    String type = dto.getPermissionType() == null ? "MENU_ACTION" : dto.getPermissionType().trim().toUpperCase();
    if (!java.util.Set.of("MENU_ACTION", "WORKFLOW", "DATA").contains(type)) throw new IllegalArgumentException("invalid permission type");
    dto.setPermissionType(type);
    if ("MENU_ACTION".equals(type) && (dto.getMenuId() == null || dto.getMenuId().isBlank())) throw new IllegalArgumentException("menu action permission requires menuId");
    if ("MENU_ACTION".equals(type) && (dto.getActionType() == null || dto.getActionType().isBlank())) throw new IllegalArgumentException("menu action permission requires actionType");
    if ("WORKFLOW".equals(type) && (dto.getResourceType() == null || dto.getResourceType().isBlank())) throw new IllegalArgumentException("workflow permission requires resourceType");
    if ("DATA".equals(type) && (dto.getScopeType() == null || dto.getScopeType().isBlank())) throw new IllegalArgumentException("data permission requires scopeType");
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

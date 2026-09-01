package com.chronos.service.iService;

import com.chronos.model.dto.RoleDTO;
import com.chronos.model.pojo.Role;
import com.chronos.model.vo.RoleDetailVO;
import com.chronos.model.vo.RoleVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IRoleService {
  Page<Role> pageRoles(RoleDTO paramRoleDTO, Pageable paramPageable);
  
  RoleVO getRoleById(String paramString);
  
  RoleDetailVO getRoleDetail(String paramString);
  
  void save(RoleDTO paramRoleDTO);
  
  void update(RoleDTO paramRoleDTO);
  
  void delete(String paramString);
}


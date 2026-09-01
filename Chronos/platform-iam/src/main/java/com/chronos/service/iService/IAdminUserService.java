package com.chronos.service.iService;

import com.chronos.model.dto.AdminUserDTO;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.vo.AdminUserVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IAdminUserService {
  Page<AdminUser> pageUsers(AdminUserDTO paramAdminUserDTO, Pageable paramPageable);
  
  AdminUserVO getUserById(String paramString);

  AdminUserVO getUserByEmployeeId(String employeeId);
  
  void save(AdminUserDTO paramAdminUserDTO);
  
  void update(AdminUserDTO paramAdminUserDTO);
  
  void delete(String paramString);
}

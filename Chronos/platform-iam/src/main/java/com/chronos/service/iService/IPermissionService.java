package com.chronos.service.iService;

import com.chronos.model.dto.PermissionDTO;
import com.chronos.model.pojo.Permission;
import com.chronos.model.vo.PermissionVO;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IPermissionService {
  Page<Permission> pagePermissions(PermissionDTO paramPermissionDTO, Pageable paramPageable);
  
  PermissionVO getPermissionById(String paramString);
  
  void save(PermissionDTO paramPermissionDTO);
  
  void saveAll(List<PermissionDTO> paramList);
  
  void update(PermissionDTO paramPermissionDTO);
  
  void delete(String paramString);
}


package com.chronos.service.iService;

import com.chronos.model.dto.OrganizationDTO;
import com.chronos.model.pojo.Organization;
import com.chronos.model.vo.OrganizationVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IOrganizationService {
  Page<Organization> pageOrganizations(OrganizationDTO paramOrganizationDTO, Pageable paramPageable);
  
  OrganizationVO getById(String paramString);
  
  void save(OrganizationDTO paramOrganizationDTO);
  
  void update(OrganizationDTO paramOrganizationDTO);
  
  void delete(String paramString);
}



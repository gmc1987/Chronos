package com.chronos.service.iService;

import com.chronos.model.dto.ConsumerUserDTO;
import com.chronos.model.pojo.ConsumerUser;
import com.chronos.model.vo.ConsumerUserVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IConsumerUserService {
  ConsumerUserVO registerByAccount(ConsumerUserDTO paramConsumerUserDTO);
  
  Page<ConsumerUser> pageUsers(ConsumerUserDTO paramConsumerUserDTO, Pageable paramPageable);
  
  ConsumerUserVO getById(String paramString);
  
  void save(ConsumerUserDTO paramConsumerUserDTO);
  
  void update(ConsumerUserDTO paramConsumerUserDTO);
  
  void delete(String paramString);
}


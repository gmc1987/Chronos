package com.chronos.Idao;

import com.chronos.model.pojo.ConsumerUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("consumerUserRepository")
public interface IConsumerUserRepository extends JpaRepository<ConsumerUser, String> {
  ConsumerUser findByUsername(String paramString);
  
  ConsumerUser findByPhone(String paramString);
  
  Page<ConsumerUser> findByCustomerTypeContaining(String paramString, Pageable paramPageable);
  
  Page<ConsumerUser> findByUsernameContaining(String paramString, Pageable paramPageable);
  
  Page<ConsumerUser> findByCustomerTypeContainingAndUsernameContaining(String paramString1, String paramString2, Pageable paramPageable);
}


 package com.chronos.service.impl;
 
 import com.chronos.Idao.IConsumerUserRepository;
 import com.chronos.Idao.IRoleRepository;
 import com.chronos.commons.utils.BeanCopyUtil;
 import com.chronos.model.dto.ConsumerUserDTO;
 import com.chronos.model.pojo.ConsumerUser;
 import com.chronos.model.pojo.Role;
 import com.chronos.model.vo.ConsumerUserVO;
 import com.chronos.service.iService.IConsumerUserService;
 import jakarta.persistence.EntityManager;
 import jakarta.persistence.PersistenceContext;
 import java.time.LocalDateTime;
 import java.util.Optional;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.data.domain.Page;
 import org.springframework.data.domain.Pageable;
 import org.springframework.security.crypto.password.PasswordEncoder;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 
 
 
 
 
 
 @Service("consumerUserService")
 public class ConsumerUserServiceImpl
   implements IConsumerUserService
 {
   @Autowired
   private IConsumerUserRepository consumerUserRepository;
   @Autowired
   private IRoleRepository roleRepository;
   @Autowired
   private PasswordEncoder passwordEncoder;
   @PersistenceContext
   private EntityManager entityManager;
   
   @Transactional
   public ConsumerUserVO registerByAccount(ConsumerUserDTO dto) {
     if (dto.getUsername() == null || dto.getUsername().isEmpty() || dto.getPassword() == null || dto.getPassword().isEmpty()) {
       throw new IllegalArgumentException("username/password required");
     }
     
     if (this.consumerUserRepository.findByUsername(dto.getUsername()) != null) {
       throw new IllegalArgumentException("username exists");
     }
     if (dto.getPhone() != null && !dto.getPhone().isEmpty() && 
       this.consumerUserRepository.findByPhone(dto.getPhone()) != null) {
       throw new IllegalArgumentException("phone exists");
     }
     
     ConsumerUser u = new ConsumerUser();
     BeanCopyUtil.copyNonNullProperties(dto, u);
     u.setCreateTime(LocalDateTime.now());
     u.setPassword(this.passwordEncoder.encode(dto.getPassword()));
     u.setStatus(Integer.valueOf(1));
     u.setCustomerType((dto.getCustomerType() == null) ? "0" : dto.getCustomerType());
     this.consumerUserRepository.save(u);
 
     
     Role defaultRole = this.roleRepository.findByRoleName("ROLE_PLATFORM_USER");
     if (defaultRole == null) {
       throw new IllegalStateException("default role ROLE_PLATFORM_USER not found");
     }
     this.entityManager.createNativeQuery("INSERT INTO t_user_role (user_id, role_id) VALUES (?, ?)")
       .setParameter(1, u.getId())
       .setParameter(2, defaultRole.getId())
       .executeUpdate();
     
     ConsumerUserVO vo = ConsumerUserVO.builder().id(u.getId()).username(u.getUsername()).email(u.getEmail()).phone(u.getPhone()).status(u.getStatus()).customerType(u.getCustomerType()).createTime(u.getCreateTime()).build();
     return vo;
   }
 
   
   public Page<ConsumerUser> pageUsers(ConsumerUserDTO dto, Pageable pageable) {
     if (dto != null) {
       if (dto.getCustomerType() != null && dto.getUsername() != null && !dto.getUsername().isEmpty()) {
         return this.consumerUserRepository.findByCustomerTypeContainingAndUsernameContaining(dto.getCustomerType(), dto.getUsername(), pageable);
       }
       if (dto.getCustomerType() != null) {
         return this.consumerUserRepository.findByCustomerTypeContaining(dto.getCustomerType(), pageable);
       }
       if (dto.getUsername() != null && !dto.getUsername().isEmpty()) {
         return this.consumerUserRepository.findByUsernameContaining(dto.getUsername(), pageable);
       }
     } 
     return this.consumerUserRepository.findAll(pageable);
   }
 
   
   public ConsumerUserVO getById(String id) {
     Optional<ConsumerUser> opt = this.consumerUserRepository.findById(id);
     if (!opt.isPresent()) return null; 
     ConsumerUser u = opt.get();
     ConsumerUserVO vo = ConsumerUserVO.builder().id(u.getId()).username(u.getUsername()).email(u.getEmail()).phone(u.getPhone()).status(u.getStatus()).customerType(u.getCustomerType()).createTime(u.getCreateTime()).build();
     return vo;
   }
 
   
   @Transactional
   public void save(ConsumerUserDTO dto) {
     if (dto == null || dto.getUsername() == null || dto.getPassword() == null) throw new IllegalArgumentException("username/password required"); 
     if (this.consumerUserRepository.findByUsername(dto.getUsername()) != null) throw new IllegalArgumentException("username exists"); 
     if (dto.getPhone() != null && !dto.getPhone().isEmpty() && 
       this.consumerUserRepository.findByPhone(dto.getPhone()) != null) throw new IllegalArgumentException("phone exists");
     
     ConsumerUser u = new ConsumerUser();
     BeanCopyUtil.copyNonNullProperties(dto, u);
     u.setCreateTime(LocalDateTime.now());
     u.setPassword(this.passwordEncoder.encode(dto.getPassword()));
     if (u.getStatus() == null) u.setStatus(Integer.valueOf(1)); 
     if (u.getCustomerType() == null || u.getCustomerType().isEmpty()) u.setCustomerType("0"); 
     this.consumerUserRepository.save(u);
   }
 
   
   @Transactional
   public void update(ConsumerUserDTO dto) {
     if (dto == null || dto.getId() == null) throw new IllegalArgumentException("id required"); 
     Optional<ConsumerUser> opt = this.consumerUserRepository.findById(dto.getId());
     if (!opt.isPresent()) throw new IllegalArgumentException("user not found"); 
     ConsumerUser u = opt.get();
     if (dto.getUsername() != null) u.setUsername(dto.getUsername()); 
     if (dto.getEmail() != null) u.setEmail(dto.getEmail()); 
     if (dto.getPhone() != null) u.setPhone(dto.getPhone()); 
     if (dto.getStatus() != null) u.setStatus(dto.getStatus()); 
     if (dto.getCustomerType() != null) u.setCustomerType(dto.getCustomerType()); 
     if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
       u.setPassword(this.passwordEncoder.encode(dto.getPassword()));
     }
     u.setLastUpdateTime(LocalDateTime.now());
     this.consumerUserRepository.save(u);
   }
 
   
   @Transactional
   public void delete(String id) {
     this.consumerUserRepository.deleteById(id);
   }
 }



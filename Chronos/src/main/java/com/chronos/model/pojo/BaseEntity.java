 package com.chronos.model.pojo;
 
 import java.io.Serializable;
import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
 
 @MappedSuperclass
 @EntityListeners({AuditingEntityListener.class})
 @Getter
 @Setter
 public abstract class BaseEntity implements Serializable {
   private static final long serialVersionUID = 1L;
   @Id
   @UuidGenerator
   @Column(name = "id", unique = true, nullable = false, length = 64)
   private String id;
   
   @CreatedBy
   @Column(name = "create_by", length = 128, nullable = false)
   private String createBy;
   
   @CreatedDate @Column(name = "create_time", nullable = false) 
   private LocalDateTime createTime; 
   
   @LastModifiedBy 
   @Column(name = "last_update_by", length = 128, nullable = true) 
   private String lastUpdateBy; @LastModifiedDate 
   
   @Column(name = "last_update_time", nullable = true) 
   private LocalDateTime lastUpdateTime; 
 }

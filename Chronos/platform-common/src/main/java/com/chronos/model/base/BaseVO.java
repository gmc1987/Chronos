package com.chronos.model.base;
 
 import java.io.Serializable;
 import java.time.LocalDateTime;
 
 
 
 
 
 
 
 
 
 
 
 public class BaseVO
   implements Serializable
 {
   protected String id;
   protected LocalDateTime createTime;
   protected LocalDateTime lastUpdateTime;
   protected String createBy;
   protected String lastUpdateBy;
   
   public String getId() {
     return this.id;
   }
   
   public void setId(String id) {
     this.id = id;
   }
   
   public LocalDateTime getCreateTime() {
     return this.createTime;
   }
   
   public void setCreateTime(LocalDateTime createTime) {
     this.createTime = createTime;
   }
   
   public LocalDateTime getLastUpdateTime() {
     return this.lastUpdateTime;
   }
   
   public void setLastUpdateTime(LocalDateTime lastUpdateTime) {
     this.lastUpdateTime = lastUpdateTime;
   }
   
   public String getCreateBy() {
     return this.createBy;
   }
   
   public void setCreateBy(String createBy) {
     this.createBy = createBy;
   }
   
   public String getLastUpdateBy() {
     return this.lastUpdateBy;
   }
   
   public void setLastUpdateBy(String lastUpdateBy) {
     this.lastUpdateBy = lastUpdateBy;
   }
 }


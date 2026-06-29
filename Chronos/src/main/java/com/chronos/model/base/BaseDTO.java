 package com.chronos.model.base;
 
 import java.io.Serializable;
 import java.time.LocalDateTime;
 
 
 
 
 
 
 
 
 
 
 
 
 public class BaseDTO
   implements Serializable
 {
   protected Integer pageNumber;
   protected Integer pageSize;
   protected String searchKey;
   protected LocalDateTime startTime;
   protected LocalDateTime endTime;
   
   public Integer getPageNumber() {
     return this.pageNumber;
   }
   
   public void setPageNumber(Integer pageNumber) {
     this.pageNumber = pageNumber;
   }
   
   public Integer getPageSize() {
     return this.pageSize;
   }
   
   public void setPageSize(Integer pageSize) {
     this.pageSize = pageSize;
   }
   
   public String getSearchKey() {
     return this.searchKey;
   }
   
   public void setSearchKey(String searchKey) {
     this.searchKey = searchKey;
   }
   
   public LocalDateTime getStartTime() {
     return this.startTime;
   }
   
   public void setStartTime(LocalDateTime startTime) {
     this.startTime = startTime;
   }
   
   public LocalDateTime getEndTime() {
     return this.endTime;
   }
   
   public void setEndTime(LocalDateTime endTime) {
     this.endTime = endTime;
   }
 }



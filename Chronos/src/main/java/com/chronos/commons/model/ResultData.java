 package com.chronos.commons.model;
 
 import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

 
 
@SuppressWarnings("serial")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultData<T> implements Serializable {
   private String code;
   private String msg;
   private T data;
}

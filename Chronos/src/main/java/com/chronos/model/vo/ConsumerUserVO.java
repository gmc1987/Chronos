package com.chronos.model.vo;
 
import java.io.Serializable;
import java.time.LocalDateTime;

import com.chronos.model.base.BaseVO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
 
@SuppressWarnings("serial")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter
public class ConsumerUserVO extends BaseVO implements Serializable {
   private String id;
   private String username;
   private String email;
   private String phone; 
   private Integer status; 
   private String customerType; 
   private LocalDateTime createTime; 
}

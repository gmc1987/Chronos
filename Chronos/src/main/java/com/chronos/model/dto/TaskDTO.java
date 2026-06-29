package com.chronos.model.dto;

import java.io.Serializable;
import java.util.Map;

import com.chronos.model.base.BaseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@SuppressWarnings("serial")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TaskDTO extends BaseDTO implements Serializable {
	private String id;
	private String apiId;
	private String apiCodes;
	private String accountId;
	private String requestData;
	private String responseData;
	private String status;
	private String resultUrl;
	private String errorMsg;

	private String externalTaskId;
	private String requestId;
	private Integer retryCount;
	private Map<String, String> headers;
	private Map<String, String> queryParams;
	private Map<String, Object> params;
	private String modelId;
	private String apiCode;
	private String userId;
	
	private Integer complletionTokens;
	
	private Integer totalTokens;

}

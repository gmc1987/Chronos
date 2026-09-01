/**
 * 
 */
package com.chronos.commons.eumns;

/**
 * 
 */
public enum LLMProviderEnum {
	
	DEEPSEEK("deepseek"),
	DOUBAO("doubao");
	
	private final String code;
	   
	LLMProviderEnum(String code) {
	     this.code = code;
	   }
	   
	   public String getCode() {
	     return this.code;
	   }

}

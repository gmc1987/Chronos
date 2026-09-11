/**
 * 
 */
package com.chronos.service.factory;

/**
 * @see LLM模型统一接口，提供不同LLM服务的实现类需要实现该接口，以便通过工厂类进行统一调用
 */
public interface LLMServiceStrategy {

	String provider();

	/** 是否已经注入可用的厂商模型客户端；不得通过该接口暴露密钥。 */
	boolean available();

	String chat(String message);
}

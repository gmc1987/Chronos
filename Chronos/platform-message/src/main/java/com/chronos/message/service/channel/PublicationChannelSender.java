package com.chronos.message.service.channel;

import com.chronos.message.model.Publication;

/** 外部渠道扩展点。邮件、短信、企业微信等实现必须自行保证供应商请求幂等。 */
public interface PublicationChannelSender {
	String channel();

	void send(Publication publication, String username);
}

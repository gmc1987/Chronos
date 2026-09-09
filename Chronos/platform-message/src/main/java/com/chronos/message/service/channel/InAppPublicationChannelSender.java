package com.chronos.message.service.channel;

import org.springframework.stereotype.Component;

import com.chronos.message.model.Publication;

@Component
public class InAppPublicationChannelSender implements PublicationChannelSender {
	@Override
	public String channel() {
		return "IN_APP";
	}

	@Override
	public void send(Publication publication, String username) {
		// 站内渠道的数据已在同一事务落库，调度器确认即可完成可靠投递。
	}
}

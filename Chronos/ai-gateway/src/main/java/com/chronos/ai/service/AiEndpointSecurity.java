package com.chronos.ai.service;

import java.net.InetAddress;
import java.net.URI;

final class AiEndpointSecurity {
	private AiEndpointSecurity() {
	}

	static String validate(String value) {
		try {
			URI uri = URI.create(value);
			if (!"https".equalsIgnoreCase(uri.getScheme())
					|| uri.getHost() == null
					|| uri.getUserInfo() != null
					|| uri.getQuery() != null
					|| uri.getFragment() != null) {
				throw new IllegalArgumentException();
			}
			for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
				if (address.isAnyLocalAddress() || address.isLoopbackAddress()
						|| address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
					throw new IllegalArgumentException();
				}
			}
			return value;
		} catch (Exception exception) {
			throw new AiModelConfigurationException("Base URL 必须是可信的 HTTPS 公网地址");
		}
	}
}

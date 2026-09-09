package com.chronos.message.portal;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.chronos.portal.spi.PortalContribution;
import com.chronos.portal.spi.PortalContributionProvider;
import com.chronos.message.service.iService.IPublicationService;

@Component
public class PublicationPortalContributionProvider implements PortalContributionProvider {
	private final IPublicationService service;

	public PublicationPortalContributionProvider(IPublicationService service) {
		this.service = service;
	}

	@Override
	public String providerCode() {
		return "ANNOUNCEMENT";
	}

	@Override
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:read')")
	public PortalContribution load(String username) {
		var values = service.visible(username, "ANNOUNCEMENT", null, false, PageRequest.of(0, 6));
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("total", values.getTotalElements());
		data.put("unread", values.getContent().stream().filter(value -> !value.read()).count());
		data.put("items", values.getContent());
		data.put("allRoute", "/portal/publications?type=ANNOUNCEMENT");
		return new PortalContribution(providerCode(), true, "ok", data);
	}
}

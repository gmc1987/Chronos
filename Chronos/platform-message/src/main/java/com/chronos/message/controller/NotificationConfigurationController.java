package com.chronos.message.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.message.model.NotificationChannelPolicy;
import com.chronos.message.model.NotificationChannelPreference;
import com.chronos.message.model.NotificationTemplate;
import com.chronos.message.model.PublicationDelivery;
import com.chronos.message.Idao.IPublicationDeliveryRepository;
import com.chronos.message.service.impl.NotificationConfigurationService;
import com.chronos.message.service.impl.PublicationDeliveryService;

/** 通知渠道公共配置 API；外部供应商发送器通过既有渠道 SPI 后续接入。 */
@RestController
public class NotificationConfigurationController {

	private final NotificationConfigurationService service;
	private final IPublicationDeliveryRepository deliveries;
	private final PublicationDeliveryService deliveryService;

	public NotificationConfigurationController(
			NotificationConfigurationService service,
			IPublicationDeliveryRepository deliveries,
			PublicationDeliveryService deliveryService) {
		this.service = service;
		this.deliveries = deliveries;
		this.deliveryService = deliveryService;
	}

	@GetMapping("/admin/message/deliveries/dead")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:manage')")
	public ResultData<PageView<PublicationDelivery>> deadDeliveries(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ok(PageView.from(deliveries.findByStatus(
				"DEAD",
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))));
	}

	@PostMapping("/admin/message/deliveries/{id}/retry")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:manage')")
	public ResultData<PublicationDelivery> retryDelivery(@PathVariable String id) {
		return ok(deliveryService.retryDead(id));
	}

	@GetMapping("/message/channel-preferences")
	public ResultData<List<NotificationChannelPreference>> preferences(Principal principal) {
		return ok(service.preferences(principal.getName()));
	}

	@PutMapping("/message/channel-preferences/{channel}")
	public ResultData<NotificationChannelPreference> savePreference(
			@PathVariable String channel,
			@RequestBody NotificationChannelPreference command,
			Principal principal) {
		command.setChannel(channel);
		return ok(service.savePreference(principal.getName(), command));
	}

	@GetMapping("/admin/message/templates")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:view','message:publication:manage')")
	public ResultData<PageView<NotificationTemplate>> templates(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String channel,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ok(PageView.from(service.templates(keyword, channel, PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(size, 1), 100),
				Sort.by("templateCode")))));
	}

	@PostMapping("/admin/message/templates")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:manage')")
	public ResultData<NotificationTemplate> createTemplate(@RequestBody NotificationTemplate command) {
		return ok(service.saveTemplate(null, command));
	}

	@PutMapping("/admin/message/templates/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:manage')")
	public ResultData<NotificationTemplate> updateTemplate(
			@PathVariable String id,
			@RequestBody NotificationTemplate command) {
		return ok(service.saveTemplate(id, command));
	}

	@DeleteMapping("/admin/message/templates/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:manage')")
	public ResultData<Void> deleteTemplate(@PathVariable String id) {
		service.deleteTemplate(id);
		return ok(null);
	}

	@GetMapping("/admin/message/channel-policies")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:manage')")
	public ResultData<List<NotificationChannelPolicy>> policies() {
		return ok(service.policies());
	}

	@PutMapping("/admin/message/channel-policies/{channel}")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:manage')")
	public ResultData<NotificationChannelPolicy> savePolicy(
			@PathVariable String channel,
			@RequestBody NotificationChannelPolicy command) {
		command.setChannel(channel);
		return ok(service.savePolicy(command));
	}

	@GetMapping("/admin/message/monitor")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:manage')")
	public ResultData<java.util.Map<String, Object>> monitor() {
		long pending = deliveries.countByStatus("PENDING");
		long dead = deliveries.countByStatus("DEAD");
		long delivered = deliveries.countByStatus("DELIVERED");
		return ok(java.util.Map.of(
				"pending", pending,
				"dead", dead,
				"delivered", delivered,
				"deliveryRate", delivered + dead == 0
						? 100D
						: Math.round(delivered * 10000D / (delivered + dead)) / 100D,
				"alert", dead > 0 || pending > 1000));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}

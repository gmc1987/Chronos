package com.chronos.delivery;

import com.chronos.commons.model.ResultData;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/delivery")
@PreAuthorize("isAuthenticated()")
class DeliveryAccessController {
	private final CustomerLicense license;
	private final CustomerDelegation delegation;

	DeliveryAccessController(CustomerLicense license, CustomerDelegation delegation) {
		this.license = license;
		this.delegation = delegation;
	}

	@GetMapping("/status")
	ResultData<?> status() {
		return SalesDeliveryController.ok(license.status());
	}

	@GetMapping("/accounts")
	ResultData<List<Map<String, Object>>> accounts(Authentication actor) {
		requireActive();
		return SalesDeliveryController.ok(delegation.accounts(actor.getName(), owner(actor)));
	}

	@PostMapping("/schools")
	ResultData<String> school(@RequestBody CustomerDelegation.School school, Authentication actor) {
		requireActive();
		return SalesDeliveryController.ok(delegation.createSchool(school, actor.getName(), owner(actor)));
	}

	@PostMapping("/accounts")
	ResultData<Void> account(@RequestBody CustomerDelegation.Account account, Authentication actor) {
		requireActive();
		delegation.createAccount(account, actor.getName(), owner(actor));
		return SalesDeliveryController.ok(null);
	}

	@PutMapping("/accounts/grants")
	ResultData<Void> grant(@RequestBody CustomerDelegation.Grant command, Authentication actor) {
		requireActive();
		delegation.delegate(command, actor.getName(), owner(actor), license.status().modules());
		return SalesDeliveryController.ok(null);
	}

	static boolean owner(Authentication actor) {
		return actor != null && actor.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_CODE_SUPER_ADMIN".equals(authority.getAuthority()));
	}

	private void requireActive() {
		if (!license.customer() || !license.status().active()) throw new AccessDeniedException("客户授权尚未生效");
	}
}

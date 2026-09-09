package com.chronos.workflow;

import com.chronos.Idao.*;
import com.chronos.model.pojo.*;
import com.chronos.model.workflow.*;
import com.fasterxml.jackson.databind.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WorkflowAssigneeResolver {
	private final IAdminUserRepository users;
	private final IRoleRepository roles;
	private final IEmployeeAssignmentRepository assignments;
	private final ObjectMapper json = new ObjectMapper();

	public WorkflowAssigneeResolver(IAdminUserRepository users, IRoleRepository roles,
			IEmployeeAssignmentRepository assignments) {
		this.users = users;
		this.roles = roles;
		this.assignments = assignments;
	}

	public List<String> resolve(WorkflowNode node, WorkflowInstance instance, String fallback) {
		try {
			JsonNode p = json.readTree(node.getPropertiesJson() == null ? "{}" : node.getPropertiesJson());
			String mode = p.path("assigneeMode").asText("USER"),
					value = p.path("assigneeValue").asText(p.path("assignee").asText());
			List<String> result = switch (mode) {
			case "ROLE" -> byRole(value);
			case "INITIATOR_MANAGER" -> manager(instance.getInitiator());
			case "FORM_FIELD" -> byVariable(instance.getVariablesJson(), value);
			default -> value.isBlank() ? List.of() : List.of(value);
			};
			return result.stream().filter(x -> x != null && !x.isBlank()).distinct().sorted().toList();
		} catch (Exception e) {
			return List.of(fallback);
		}
	}

	private List<String> byRole(String code) {
		Role role = roles.findByRoleCode(code);
		return role == null ? List.of()
				: users.findByRoles_Id(role.getId()).stream().filter(u -> Integer.valueOf(1).equals(u.getStatus()))
						.map(AdminUser::getUsername).toList();
	}

	private List<String> manager(String initiator) {
		AdminUser user = users.findByUsername(initiator);
		if (user == null || user.getEmployeeId() == null)
			return List.of();
		return assignments.findCurrentPrimaryAssignment(user.getEmployeeId(), LocalDate.now()).stream()
				.flatMap(a -> assignments
						.findByOrganizationUnitIdAndDepartmentLeaderTrueAndStatus(a.getOrganizationUnitId(), 1)
						.stream())
				.map(a -> users.findByEmployeeId(a.getEmployeeId()).orElse(null)).filter(Objects::nonNull)
				.map(AdminUser::getUsername).toList();
	}

	private List<String> byVariable(String variables, String key) {
		try {
			JsonNode value = json.readTree(variables == null ? "{}" : variables).path(key);
			if (value.isArray()) {
				List<String> result = new ArrayList<>();
				value.forEach(x -> result.add(x.asText()));
				return result;
			}
			return value.isMissingNode() ? List.of() : List.of(value.asText());
		} catch (Exception e) {
			return List.of();
		}
	}

	public List<Map<String, String>> directory() {
		return users.findAll().stream().filter(u -> Integer.valueOf(1).equals(u.getStatus()))
				.sorted(Comparator.comparing(AdminUser::getUsername)).map(u -> {
					Map<String, String> item = new LinkedHashMap<>();
					item.put("username", u.getUsername());
					item.put("displayName", u.getDisplayName() == null || u.getDisplayName().isBlank() ? u.getUsername()
							: u.getDisplayName());
					return item;
				}).toList();
	}

	public boolean canStart(WorkflowDefinition definition, String actor) {
		try {
			JsonNode scope = json.readTree(
					definition.getStarterScopeJson() == null ? "{\"type\":\"ALL\"}" : definition.getStarterScopeJson());
			String type = scope.path("type").asText("ALL").toUpperCase();
			if ("ALL".equals(type))
				return true;
			AdminUser user = users.findByUsername(actor);
			if (user == null)
				return false;
			Set<String> values = new HashSet<>();
			JsonNode raw = scope.path("values");
			if (raw.isArray())
				raw.forEach(x -> values.add(x.asText()));
			else if (!raw.isMissingNode())
				values.add(raw.asText());
			if (scope.hasNonNull("value"))
				values.add(scope.path("value").asText());
			return switch (type) {
			case "USER" -> values.contains(actor);
			case "ROLE" -> user.getRoles().stream().map(Role::getRoleCode).anyMatch(values::contains);
			case "ORGANIZATION", "DEPARTMENT" -> {
				if (user.getEmployeeId() == null)
					yield false;
				var assignment = assignments.findCurrentPrimaryAssignment(user.getEmployeeId(), LocalDate.now());
				yield assignment.map(a -> "ORGANIZATION".equals(type) ? values.contains(a.getOrganizationId())
						: values.contains(a.getOrganizationUnitId())).orElse(false);
			}
			default -> false;
			};
		} catch (Exception e) {
			return false;
		}
	}
}

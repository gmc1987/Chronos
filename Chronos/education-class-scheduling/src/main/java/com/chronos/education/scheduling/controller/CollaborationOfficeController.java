package com.chronos.education.scheduling.controller;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IPositionRepository;
import com.chronos.education.scheduling.service.CollaborationOfficeService;
import com.chronos.education.scheduling.service.CollaborationOfficeService.DocumentCommand;
import com.chronos.education.scheduling.service.CollaborationOfficeService.OfficeRequestCommand;
import com.chronos.education.scheduling.service.CollaborationOfficeService.ResourceCommand;
import com.chronos.education.scheduling.service.CollaborationOfficeService.ShareCommand;
import com.chronos.commons.model.ResultData;
import com.chronos.model.pojo.Employee;
import com.chronos.service.iService.IIamDirectoryService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portal/collaboration")
@PreAuthorize("isAuthenticated()")
public class CollaborationOfficeController {
	private final CollaborationOfficeService collaboration;
	private final IIamDirectoryService directory;
	private final IEmployeeAssignmentRepository assignments;
	private final IOrganizationUnitRepository units;
	private final IPositionRepository positions;
	private final IAdminUserRepository accounts;

	public CollaborationOfficeController(
			CollaborationOfficeService collaboration,
			IIamDirectoryService directory,
			IEmployeeAssignmentRepository assignments,
			IOrganizationUnitRepository units,
			IPositionRepository positions,
			IAdminUserRepository accounts) {
		this.collaboration = collaboration;
		this.directory = directory;
		this.assignments = assignments;
		this.units = units;
		this.positions = positions;
		this.accounts = accounts;
	}

	@GetMapping("/resources")
	public ResultData<?> resources(@RequestParam String type) {
		return ok(collaboration.resources(type));
	}

	@PostMapping("/requests")
	public ResultData<?> createRequest(Principal principal, @RequestBody OfficeRequestCommand command) {
		return ok(collaboration.createRequest(principal.getName(), command));
	}

	@GetMapping("/requests/my")
	public ResultData<?> myRequests(Principal principal) {
		return ok(collaboration.myRequests(principal.getName()));
	}

	@PostMapping("/requests/{id}/withdraw")
	public ResultData<?> withdraw(Principal principal, @PathVariable String id) {
		return ok(collaboration.withdraw(principal.getName(), id));
	}

	@PostMapping("/requests/{id}/complete")
	@PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:manage','education:scheduling:manage')")
	public ResultData<?> complete(Principal principal, @PathVariable String id) {
		return ok(collaboration.completeExecution(principal.getName(), id));
	}

	@GetMapping("/documents/my")
	public ResultData<?> myDocuments(Principal principal) {
		return ok(collaboration.myDocuments(principal.getName()));
	}

	@GetMapping("/documents/issued")
	public ResultData<?> issuedDocuments() {
		return ok(collaboration.issuedDocuments());
	}

	@PostMapping("/documents")
	public ResultData<?> createDocument(Principal principal, @RequestBody DocumentCommand command) {
		return ok(collaboration.createDocument(principal.getName(), command));
	}

	@PostMapping("/documents/{id}/file")
	public ResultData<?> attachDocument(
			Principal principal, @PathVariable String id, @RequestBody FileCommand command) {
		return ok(collaboration.attachDocument(principal.getName(), id, command.fileId()));
	}

	@PostMapping("/documents/{id}/submit")
	public ResultData<?> submitDocument(Principal principal, @PathVariable String id) {
		return ok(collaboration.submitDocument(principal.getName(), id));
	}

	@PostMapping("/documents/{id}/archive")
	public ResultData<?> archiveDocument(Principal principal, @PathVariable String id) {
		return ok(collaboration.archiveDocument(principal.getName(), id));
	}

	@PostMapping("/files/shares")
	public ResultData<?> createShare(Principal principal, @RequestBody ShareCommand command) {
		return ok(collaboration.createShare(principal.getName(), command));
	}

	@PostMapping("/files/shares/{id}/file")
	public ResultData<?> attachShare(
			Principal principal, @PathVariable String id, @RequestBody FileCommand command) {
		return ok(collaboration.attachShare(principal.getName(), id, command.fileId()));
	}

	@GetMapping("/files/shares")
	public ResultData<?> visibleShares(Principal principal) {
		var account = accounts.findByUsername(principal.getName());
		String departmentId = account == null || account.getEmployeeId() == null ? null
				: assignments.findCurrentPrimaryAssignment(account.getEmployeeId(), LocalDate.now())
						.map(value -> value.getOrganizationUnitId())
						.orElse(null);
		return ok(collaboration.visibleShares(
				principal.getName(), departmentId));
	}

	@GetMapping("/contacts")
	public ResultData<?> contacts(@RequestParam(defaultValue = "") String keyword) {
		String normalized = keyword.trim().toLowerCase(Locale.ROOT);
		List<ContactView> result = directory.employees().stream()
				.filter(employee -> "ACTIVE".equals(employee.getEmploymentStatus()))
				.filter(employee -> normalized.isEmpty()
						|| contains(employee.getEmployeeName(), normalized)
						|| contains(employee.getEmployeeCode(), normalized)
						|| contains(employee.getEmail(), normalized))
				.sorted(Comparator.comparing(Employee::getEmployeeName))
				.limit(100)
				.map(this::toContact)
				.toList();
		return ok(result);
	}

	@GetMapping("/departments")
	public ResultData<?> departments(Principal principal) {
		var account = accounts.findByUsername(principal.getName());
		if (account == null || account.getOrganizationId() == null) {
			return ok(List.of());
		}
		return ok(units.findByOrgIdAndStatusOrderBySortOrderAsc(account.getOrganizationId(), 1).stream()
				.map(value -> new DepartmentView(value.getId(), value.getOrganizationUnitName()))
				.toList());
	}

	@PostMapping("/admin/resources")
	@PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:manage','education:scheduling:manage')")
	public ResultData<?> createResource(@RequestBody ResourceCommand command) {
		return ok(collaboration.saveResource(null, command));
	}

	@GetMapping("/admin/resources")
	@PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:manage','education:scheduling:manage')")
	public ResultData<?> managedResources(@RequestParam String type) {
		return ok(collaboration.managedResources(type));
	}

	@GetMapping("/admin/requests")
	@PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:manage','education:scheduling:manage')")
	public ResultData<?> managedRequests() {
		return ok(collaboration.managedRequests());
	}

	@PutMapping("/admin/resources/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:manage','education:scheduling:manage')")
	public ResultData<?> updateResource(@PathVariable String id, @RequestBody ResourceCommand command) {
		return ok(collaboration.saveResource(id, command));
	}

	@DeleteMapping("/admin/resources/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'iam:directory:manage','education:scheduling:manage')")
	public ResultData<?> disableResource(@PathVariable String id) {
		return ok(collaboration.disableResource(id));
	}

	private ContactView toContact(Employee employee) {
		var assignment = assignments.findCurrentPrimaryAssignment(employee.getId(), LocalDate.now()).orElse(null);
		String department = assignment == null ? null : units.findById(assignment.getOrganizationUnitId())
				.map(value -> value.getOrganizationUnitName()).orElse(null);
		String position = assignment == null ? null : positions.findById(assignment.getPositionId())
				.map(value -> value.getPositionName()).orElse(null);
		return new ContactView(
				employee.getEmployeeCode(),
				employee.getEmployeeName(),
				department,
				position,
				employee.getPhone(),
				employee.getEmail());
	}

	private boolean contains(String value, String keyword) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
	}

	private <T> ResultData<T> ok(T data) {
		ResultData<T> result = new ResultData<>();
		result.setCode("200");
		result.setMsg("success");
		result.setData(data);
		return result;
	}

	public record FileCommand(String fileId) {}
	public record ContactView(
			String employeeCode,
			String employeeName,
			String departmentName,
			String positionName,
			String workPhone,
			String workEmail) {}
	public record DepartmentView(String id, String name) {}
}

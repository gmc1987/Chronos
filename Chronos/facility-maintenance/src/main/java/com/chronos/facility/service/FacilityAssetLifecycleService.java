package com.chronos.facility.service;

import com.chronos.facility.dao.FacilityAssetRepository;
import com.chronos.facility.service.FacilityAssetGovernanceService.AssetCommand;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacilityAssetLifecycleService {
	private static final Set<String> OPERATIONS = Set.of("ACQUIRE", "TRANSFER", "RETIRE", "DESIGN");
	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;
	private final DirectoryDataScopeGuard scope;
	private final FacilityAssetRepository assets;
	private final FacilityAssetGovernanceService governance;
	private final IAuditLogService audit;

	public FacilityAssetLifecycleService(JdbcTemplate jdbc, ObjectMapper mapper, DirectoryDataScopeGuard scope,
			FacilityAssetRepository assets, FacilityAssetGovernanceService governance, IAuditLogService audit) {
		this.jdbc = jdbc;
		this.mapper = mapper;
		this.scope = scope;
		this.assets = assets;
		this.governance = governance;
		this.audit = audit;
	}

	public record Request(String operation, String organizationId, String destinationId, String assetId,
			String title, String reason, String destinationLocation, AssetCommand asset) { }

	public record Decision(boolean approved, String comment) { }

	@Transactional(readOnly = true)
	public List<Map<String, Object>> list(String organizationId, int offset, int limit, Authentication actor) {
		String organization = scope.resolveOrganization(actor, organizationId);
		Set<String> visible = scope.visibleOrganizationIds(actor);
		String allowedDestination = visible == null ? "" : " AND (destination_id IS NULL OR destination_id IN (:visible))";
		return new NamedParameterJdbcTemplate(jdbc).queryForList("""
				SELECT id,operation,organization_id,destination_id,asset_id,title,reason,status,requested_by,requested_at
				FROM fm_asset_lifecycle_request WHERE organization_id=:organization
				""" + allowedDestination + " ORDER BY requested_at DESC,id DESC OFFSET :offset LIMIT :limit",
				Map.of("organization", organization, "visible", visible == null ? Set.of() : visible,
						"offset", Math.max(0, offset), "limit", Math.clamp(limit, 1, 100)));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> detail(String requestId, Authentication actor) {
		Map<String, Object> request = find(requestId, false);
		requireScope(request, actor);
		request.put("details", decode(request));
		request.put("decisions", jdbc.queryForList("""
				SELECT review_level, reviewer, approved, comment_text, decided_at
				FROM fm_asset_lifecycle_decision WHERE request_id=? ORDER BY review_level
				""", requestId));
		return request;
	}

	@Transactional
	public String submit(Request command, Authentication actor) {
		String operation = required(command.operation(), 16, "申请类型");
		if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("申请类型无效");
		String organization = scope.resolveOrganization(actor, command.organizationId());
		String destination = null;
		String assetId = null;
		Long assetVersion = null;
		String destinationLocation = null;
		AssetCommand acquisition = null;
		if ("ACQUIRE".equals(operation)) {
			var asset = command.asset();
			if (asset == null) throw new IllegalArgumentException("请填写入库资产信息");
			acquisition = new AssetCommand(required(asset.assetCode(), 64, "资产编码"),
					required(asset.assetName(), 200, "资产名称"), required(asset.category(), 64, "资产分类"),
					optional(asset.serialNumber(), 128), organization, required(asset.location(), 300, "存放位置"),
					asset.purchaseDate(), asset.warrantyExpiry(), "IN_SERVICE");
			if (assets.findByAssetCode(acquisition.assetCode()).isPresent()) {
				throw new IllegalArgumentException("资产编码已存在");
			}
		} else if (!"DESIGN".equals(operation)) {
			assetId = required(command.assetId(), 64, "资产");
			var asset = assets.findById(assetId).orElseThrow(() -> new IllegalArgumentException("资产不存在"));
			if (!organization.equals(asset.getOrganizationId())) throw new AccessDeniedException("资产不属于当前机构");
			if (Set.of("RETIRED", "UNDER_REPAIR").contains(asset.getStatus())) {
				throw new IllegalStateException("当前资产状态不允许调拨或报废");
			}
			assetVersion = asset.getRowVersion();
			if ("TRANSFER".equals(operation)) {
				destination = required(command.destinationId(), 64, "调入机构");
				scope.requireOrganization(actor, destination);
				destinationLocation = required(command.destinationLocation(), 300, "调入位置");
			}
		}
		String id = UUID.randomUUID().toString();
		String title = required(command.title(), 200, "申请标题");
		String reason = required(command.reason(), 2000, "申请说明");
		var normalized = new Request(operation, organization, destination, assetId, title, reason,
				destinationLocation, acquisition);
		jdbc.update("""
				INSERT INTO fm_asset_lifecycle_request(id,operation,organization_id,destination_id,asset_id,asset_version,
				    title,reason,details,status,requested_by)
				VALUES (?,?,?,?,?,?,?,?,CAST(? AS jsonb),'FIRST_REVIEW',?)
				""", id, operation, organization, destination, assetId, assetVersion, title, reason,
				encode(normalized), actor.getName());
		audit.log(actor.getName(), "FACILITY_ASSET_REQUEST", "requestId=" + id + ",operation=" + operation);
		return id;
	}

	@Transactional
	public void decide(String requestId, Decision decision, Authentication actor) {
		Map<String, Object> request = find(requestId, true);
		requireScope(request, actor);
		int stage = switch (request.get("status").toString()) {
			case "FIRST_REVIEW" -> 1;
			case "SECOND_REVIEW" -> 2;
			default -> throw new IllegalStateException("该单据已结束");
		};
		String username = actor.getName();
		if (username.equals(request.get("requested_by")) || Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM fm_asset_lifecycle_decision WHERE request_id=? AND reviewer=?)
				""", Boolean.class, requestId, username))) {
			throw new AccessDeniedException("发起人和已审核人员不能审批此级");
		}
		String comment = required(decision.comment(), 2000, "审批意见");
		jdbc.update("""
				INSERT INTO fm_asset_lifecycle_decision(request_id,review_level,reviewer,approved,comment_text)
				VALUES (?,?,?,?,?)
				""", requestId, stage, username, decision.approved(), comment);
		if (decision.approved() && stage == 2) apply(requestId, request, username);
		String status = !decision.approved() ? "REJECTED" : stage == 1 ? "SECOND_REVIEW" : "COMPLETED";
		jdbc.update("UPDATE fm_asset_lifecycle_request SET status=? WHERE id=?", status, requestId);
		audit.log(username, "FACILITY_ASSET_DECISION", "requestId=" + requestId + ",level=" + stage + ",status=" + status);
	}

	private void apply(String requestId, Map<String, Object> row, String actor) {
		Request request = decode(row);
		switch (request.operation()) {
			case "ACQUIRE" -> {
				String id = governance.saveAsset(null, request.asset()).getId();
				assets.flush();
				jdbc.update("UPDATE fm_asset_lifecycle_request SET asset_id=? WHERE id=?", id, requestId);
			}
			case "TRANSFER", "RETIRE" -> {
				// The reviewed snapshot must still match the live asset at final approval.
				String changes = "TRANSFER".equals(request.operation())
						? "organization_id=?, location=?" : "status='RETIRED'";
				var values = new java.util.ArrayList<Object>();
				if ("TRANSFER".equals(request.operation())) {
					values.add(request.destinationId());
					values.add(request.destinationLocation());
				}
				values.add(actor);
				values.add(request.assetId());
				values.add(request.organizationId());
				values.add(row.get("asset_version"));
				int changed = jdbc.update("UPDATE fm_asset SET " + changes
						+ ", row_version=row_version+1, last_update_by=?, last_update_time=CURRENT_TIMESTAMP"
						+ " WHERE id=? AND organization_id=? AND row_version=? AND status IN ('IN_SERVICE','OUT_OF_SERVICE')",
						values.toArray());
				if (changed != 1) throw new IllegalStateException("资产资料或状态已变更，请重新提交申请");
			}
			case "DESIGN" -> { }
			default -> throw new IllegalStateException("资产申请类型无效");
		}
	}

	private Map<String, Object> find(String id, boolean lock) {
		var rows = jdbc.queryForList("SELECT * FROM fm_asset_lifecycle_request WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
		if (rows.isEmpty()) throw new IllegalArgumentException("资产申请不存在");
		return rows.getFirst();
	}

	private void requireScope(Map<String, Object> request, Authentication actor) {
		scope.requireOrganization(actor, request.get("organization_id").toString());
		if (request.get("destination_id") != null) {
			scope.requireOrganization(actor, request.get("destination_id").toString());
		}
	}

	private String encode(Request request) {
		try {
			return mapper.writeValueAsString(request);
		} catch (JsonProcessingException error) {
			throw new IllegalStateException("资产申请记录无法保存", error);
		}
	}

	private Request decode(Map<String, Object> row) {
		try {
			return mapper.readValue(row.get("details").toString(), Request.class);
		} catch (JsonProcessingException error) {
			throw new IllegalStateException("资产申请记录无法读取", error);
		}
	}

	private String required(String value, int maximum, String label) {
		if (value == null || value.isBlank() || value.strip().length() > maximum) {
			throw new IllegalArgumentException(label + "不能为空或超过" + maximum + "个字符");
		}
		return value.strip();
	}

	private String optional(String value, int maximum) {
		return value == null || value.isBlank() ? null : required(value, maximum, "序列号");
	}
}

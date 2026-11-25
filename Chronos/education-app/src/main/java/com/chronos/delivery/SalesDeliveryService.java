package com.chronos.delivery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SalesDeliveryService {
	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;
	private final LicenseDocument licenses;
	private final String signingKey;
	private final String keyId;
	private final Path artifacts;

	SalesDeliveryService(JdbcTemplate jdbc, ObjectMapper mapper, LicenseDocument licenses,
			@Value("${chronos.delivery.signing-key:}") String signingKey,
			@Value("${chronos.delivery.signing-key-id:chronos-release}") String keyId,
			@Value("${chronos.delivery.artifact-dir:./data/delivery}") String artifacts) {
		this.jdbc = jdbc;
		this.mapper = mapper;
		this.licenses = licenses;
		this.signingKey = signingKey;
		this.keyId = keyId;
		this.artifacts = Path.of(artifacts);
	}

	record Order(String orderNo, String customer, String contractNo, UUID parentOrderId,
			BigDecimal amount, List<String> modules) {
	}

	record Deployment(String environment, String publicOrigin, int hostPort,
			String tlsMode, String architecture, String version, String groupAdmin) {
	}

	record DeliveryEvent(String state, String note) {
	}

	record CatalogSettings(String name, Boolean enabled, Integer sortOrder) {
	}

	record CatalogItem(String code, String name, boolean enabled, int sortOrder, List<String> dependencies,
			boolean issuable, String pendingReason, List<String> endpoints, List<String> menus,
			Set<String> readPermissions, Set<String> writePermissions) {
	}

	List<CatalogItem> catalog() {
		var catalog = new java.util.ArrayList<CatalogItem>();
		for (var product : DeliveryCatalog.PRODUCTS) {
			var rows = jdbc.query("""
					SELECT display_name, enabled, sort_order FROM cd_product_catalog WHERE module_code=?
					""", (row, index) -> new CatalogItem(product.code(), row.getString("display_name"),
					product.issuable() && row.getBoolean("enabled"), row.getInt("sort_order"),
						product.dependencies(), product.issuable(), product.pendingReason(), product.endpoints(),
						product.menus(), product.readPermissions(), product.writePermissions()), product.code());
			catalog.addAll(rows);
		}
		return catalog.stream().sorted(java.util.Comparator.comparingInt(CatalogItem::sortOrder)
				.thenComparing(CatalogItem::code)).toList();
	}

	@Transactional
	void updateCatalog(String code, CatalogSettings settings, String actor) {
		var product = DeliveryCatalog.require(code);
		if (Boolean.TRUE.equals(settings.enabled()) && !product.issuable()) {
			throw new IllegalArgumentException("产品暂不可上架：" + product.pendingReason());
		}
		String name = required(settings.name(), 100, "产品名称");
		if (settings.enabled() == null || settings.sortOrder() == null
				|| settings.sortOrder() < 0 || settings.sortOrder() > 10000) {
			throw new IllegalArgumentException("请选择销售状态并填写 0 至 10000 的排序");
		}
		jdbc.update("""
				INSERT INTO cd_product_catalog(module_code,display_name,enabled,sort_order,updated_by,updated_at)
				VALUES (?,?,?,?,?,now())
				ON CONFLICT (module_code) DO UPDATE SET display_name=excluded.display_name, enabled=excluded.enabled,
				sort_order=excluded.sort_order, updated_by=excluded.updated_by, updated_at=excluded.updated_at
				""", code, name, settings.enabled(), settings.sortOrder(), actor);
	}

	boolean signingKeyConfigured() {
		if (signingKey.isBlank()) return false;
		Path key = Path.of(signingKey);
		return Files.isRegularFile(key) && Files.isReadable(key);
	}

	List<Map<String, Object>> orders(String keyword, int page, int size) {
		String search = keyword == null ? "" : keyword.strip().toLowerCase(java.util.Locale.ROOT);
		int sizeLimit = Math.max(1, Math.min(100, size));
		return jsonRows("""
				SELECT * FROM cd_sale_order WHERE strpos(lower(order_no || ' ' || customer || ' ' || contract_no), ?) > 0
				OR ? = '' ORDER BY created_at DESC LIMIT ? OFFSET ?
				""", search, search, sizeLimit, (long) Math.max(page, 0) * sizeLimit);
	}

	@Transactional
	UUID create(Order command, String actor) {
		String number = required(command.orderNo(), 80, "订单编号");
		String customer = required(command.customer(), 160, "客户名称");
		String contract = required(command.contractNo(), 100, "合同编号");
		if (command.amount() == null || command.amount().signum() < 0
				|| command.amount().scale() > 2 || command.amount().compareTo(new BigDecimal("100000000000000")) >= 0) {
			throw new IllegalArgumentException("请输入有效的订单金额");
		}
		DeliveryCatalog.resolve(command.modules());
		var selected = new TreeSet<>(command.modules());
		for (String code : selected) {
			var active = jdbc.queryForList("""
					SELECT enabled FROM cd_product_catalog WHERE module_code=? FOR SHARE
					""", Boolean.class, code);
			if (active.isEmpty() || !Boolean.TRUE.equals(active.getFirst())) {
				throw new IllegalArgumentException("所选模块已停止单独销售，请刷新目录后重新选择");
			}
		}
		if (command.parentOrderId() != null) {
			var parent = order(command.parentOrderId());
			if (!confirmed(parent) || parent.get("parent_order_id") != null || !customer.equals(parent.get("customer"))) {
				throw new IllegalArgumentException("增购必须关联同一客户的已确认首购订单");
			}
		}
		UUID id = UUID.randomUUID();
		if (jdbc.update("""
				INSERT INTO cd_sale_order(id,order_no,customer,contract_no,parent_order_id,amount,modules,created_by)
				VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (order_no) DO NOTHING
				""", id, number, customer, contract, command.parentOrderId(), command.amount(),
				json(selected), actor) != 1) {
			throw new IllegalStateException("该订单编号已存在");
		}
		return id;
	}

	@Transactional
	void confirm(UUID id, String actor) {
		order(id);
		jdbc.update("""
				UPDATE cd_sale_order SET confirmed=true, confirmed_by=?, confirmed_at=now()
				WHERE id=? AND confirmed=false
				""", actor, id);
	}

	Map<String, Object> detail(UUID orderId) {
		var order = order(orderId);
		UUID initial = initialOrderId(order);
		return Map.of("order", order, "deployments", jsonRows(
				"SELECT * FROM cd_deployment WHERE initial_order_id=? ORDER BY environment", initial));
	}

	Map<String, Object> deployment(UUID id) {
		return Map.of("deployment", one("SELECT * FROM cd_deployment WHERE id=?", id),
				"configs", jsonRows("SELECT * FROM cd_deployment_config WHERE deployment_id=? ORDER BY revision DESC", id),
				"licenses", jsonRows("""
						SELECT deployment_id,revision,order_id,payload,issued_by,issued_at FROM cd_license
						WHERE deployment_id=? ORDER BY revision DESC
						""", id),
				"jobs", jsonRows("SELECT * FROM cd_package_job WHERE deployment_id=? ORDER BY created_at DESC", id),
				"events", jsonRows("""
						SELECT e.* FROM cd_delivery_event e JOIN cd_package_job j ON j.id=e.job_id
						WHERE j.deployment_id=? ORDER BY e.recorded_at DESC
						""", id));
	}

	@Transactional
	UUID configure(UUID orderId, UUID existing, Deployment requested, String actor) {
		var order = order(orderId);
		if (!confirmed(order)) throw new IllegalStateException("请先确认销售订单");
		var config = validate(requested);
		UUID initial = initialOrderId(order);
		UUID id = existing == null ? UUID.randomUUID() : existing;
		if (existing == null) {
			if (order.get("parent_order_id") != null) throw new IllegalStateException("请为增购选择已有部署实例");
			if (jdbc.update("""
					INSERT INTO cd_deployment(id,initial_order_id,environment) VALUES (?,?,?)
					ON CONFLICT (initial_order_id,environment) DO NOTHING
					""", id, initial, config.environment()) != 1) {
				throw new IllegalStateException("该环境已建档，请编辑现有实例");
			}
		}
		var deployment = lockDeployment(id);
		if (!initial.equals(deployment.get("initial_order_id"))
				|| !config.environment().equals(deployment.get("environment"))) {
			throw new IllegalArgumentException("部署实例与客户或环境不匹配");
		}
		var old = jsonRows("SELECT config FROM cd_deployment_config WHERE deployment_id=? ORDER BY revision DESC LIMIT 1", id);
		if (!old.isEmpty()) {
			Deployment previous = parse((String) old.getFirst().get("config"), Deployment.class);
			if (!previous.groupAdmin().equals(config.groupAdmin())) {
				throw new IllegalArgumentException("首位集团管理员只能在创建部署实例时指定");
			}
			if (previous.equals(config)) return id;
		}
		jdbc.update("""
				INSERT INTO cd_deployment_config(deployment_id,revision,config,created_by)
				VALUES (?,(SELECT coalesce(max(revision),0)+1 FROM cd_deployment_config WHERE deployment_id=?),?,?)
				""", id, id, json(config), actor);
		return id;
	}

	@Transactional
	int sign(UUID deploymentId, UUID orderId, String actor) {
		var deployment = lockDeployment(deploymentId);
		var order = order(orderId);
		if (!confirmed(order) || !initialOrderId(order).equals(deployment.get("initial_order_id"))) {
			throw new IllegalArgumentException("订单未确认或与部署实例不匹配");
		}
		var existing = jdbc.queryForList(
				"SELECT revision FROM cd_license WHERE deployment_id=? AND order_id=?", Integer.class, deploymentId, orderId);
		if (!existing.isEmpty()) return existing.getFirst();
		if (!signingKeyConfigured()) throw new IllegalStateException("签发服务尚未配置可读取的签名密钥");
		var last = jsonRows("SELECT revision,payload FROM cd_license WHERE deployment_id=? ORDER BY revision DESC LIMIT 1",
				deploymentId);
		if (last.isEmpty() && order.get("parent_order_id") != null) {
			throw new IllegalStateException("请先为该实例签发首购授权");
		}
		var modules = new TreeSet<String>();
		if (!last.isEmpty()) {
			modules.addAll(parse((String) last.getFirst().get("payload"), LicenseDocument.Payload.class).modules());
		}
		@SuppressWarnings("unchecked")
		Collection<String> selected = parse((String) order.get("modules"), List.class);
		modules.addAll(DeliveryCatalog.resolve(selected));
		int revision = last.isEmpty() ? 1 : ((Number) last.getFirst().get("revision")).intValue() + 1;
		var signed = licenses.sign(deploymentId, revision, List.copyOf(modules), keyId, Path.of(signingKey));
		jdbc.update("""
				INSERT INTO cd_license(deployment_id,revision,order_id,payload,document,issued_by) VALUES (?,?,?,?,?,?)
				""", deploymentId, revision, orderId, json(signed.payload()), signed.document(), actor);
		return revision;
	}

	String signedDocument(UUID deployment, int revision) {
		return (String) one("SELECT document FROM cd_license WHERE deployment_id=? AND revision=?",
				deployment, revision).get("document");
	}

	@Transactional
	UUID queue(UUID deployment, int config, int license, String actor) {
		lockDeployment(deployment);
		one("SELECT revision FROM cd_deployment_config WHERE deployment_id=? AND revision=?", deployment, config);
		int latest = jdbc.queryForObject(
				"SELECT coalesce(max(revision),0) FROM cd_license WHERE deployment_id=?", Integer.class, deployment);
		if (license < 1 || license != latest) throw new IllegalStateException("请选择当前最新签发的授权");
		var existing = jsonRows("""
				SELECT id,status FROM cd_package_job WHERE deployment_id=? AND config_revision=? AND license_revision=?
				ORDER BY created_at DESC, id DESC LIMIT 1
				""", deployment, config, license);
		if (!existing.isEmpty()) {
			var row = existing.getFirst();
			UUID id = (UUID) row.get("id");
			if ("FAILED".equals(row.get("status"))) {
				jdbc.update("""
						UPDATE cd_package_job SET status='QUEUED',phase=NULL,error=NULL,worker_token=NULL,
						updated_at=now() WHERE id=? AND status='FAILED'
						""", id);
			}
			if (!"READY".equals(row.get("status"))) {
				return id;
			}
		}
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO cd_package_job(id,deployment_id,config_revision,license_revision,created_by)
				VALUES (?,?,?,?,?)
				""", id, deployment, config, license, actor);
		return id;
	}

	@Transactional
	void record(UUID job, DeliveryEvent command, String actor) {
		var row = one("SELECT status FROM cd_package_job WHERE id=? FOR UPDATE", job);
		if (!"READY".equals(row.get("status"))) throw new IllegalStateException("请先完成打包");
		String state = required(command.state(), 24, "交付状态");
		if (!List.of("DELIVERED", "INSTALLED", "INSTALL_FAILED").contains(state)) {
			throw new IllegalArgumentException("未知交付状态");
		}
		if (!"DELIVERED".equals(state) && !Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM cd_delivery_event WHERE job_id=? AND state='DELIVERED')
				""", Boolean.class, job))) throw new IllegalStateException("请先登记交付");
		jdbc.update("INSERT INTO cd_delivery_event(id,job_id,state,note,actor) VALUES (?,?,?,?,?)",
				UUID.randomUUID(), job, state, required(command.note(), 500, "交付说明"), actor);
	}

	Path archive(UUID job, boolean checksum) {
		if (!"READY".equals(one("SELECT status FROM cd_package_job WHERE id=?", job).get("status"))) {
			throw new IllegalStateException("部署包尚未生成");
		}
		Path file = artifacts.resolve(job + (checksum ? ".tar.gz.sha256" : ".tar.gz"));
		if (!Files.isRegularFile(file)) throw new IllegalStateException("交付归档不可用");
		return file;
	}

	private Deployment validate(Deployment command) {
		String environment = required(command.environment(), 32, "部署环境");
		if (!environment.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("部署环境编码无效");
		String origin = required(command.publicOrigin(), 250, "对外访问地址");
		URI uri = URI.create(origin);
		if (uri.getHost() == null || !uri.getHost().matches("[A-Za-z0-9.-]+") || uri.getUserInfo() != null
				|| !uri.getRawPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null
				|| uri.getPort() == 0 || uri.getPort() > 65535 || !List.of("http","https").contains(uri.getScheme())) {
			throw new IllegalArgumentException("请输入不带路径的 HTTP 或 HTTPS 域名或 IPv4 地址");
		}
		String mode = required(command.tlsMode(), 20, "TLS 模式");
		if (!List.of("HTTP", "GATEWAY", "NGINX").contains(mode)
				|| ("HTTP".equals(mode) != "http".equals(uri.getScheme()))
				|| command.hostPort() < 1 || command.hostPort() > 65535) {
			throw new IllegalArgumentException("访问协议、TLS 模式或端口不匹配");
		}
		int advertisedPort = uri.getPort() == -1 ? ("http".equals(uri.getScheme()) ? 80 : 443) : uri.getPort();
		if (!"GATEWAY".equals(mode) && command.hostPort() != advertisedPort) {
			throw new IllegalArgumentException("直连模式下端口必须与对外访问地址一致");
		}
		String version = required(command.version(), 64, "部署包版本");
		String username = required(command.groupAdmin(), 100, "集团管理员账号");
		if (!version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}") || version.contains("..")
				|| !username.matches("[A-Za-z0-9._@-]{3,100}") || "admin".equalsIgnoreCase(username)
				|| !"linux/amd64".equals(command.architecture())) {
			throw new IllegalArgumentException("发布版本、集团管理员账号或镜像架构无效");
		}
		return new Deployment(environment, origin, command.hostPort(), mode, command.architecture(), version, username);
	}

	private Map<String, Object> lockDeployment(UUID id) {
		return one("SELECT * FROM cd_deployment WHERE id=? FOR UPDATE", id);
	}

	private Map<String, Object> order(UUID id) {
		return one("SELECT * FROM cd_sale_order WHERE id=?", id);
	}

	private UUID initialOrderId(Map<String, Object> order) {
		UUID parent = (UUID) order.get("parent_order_id");
		return parent == null ? (UUID) order.get("id") : parent;
	}

	private boolean confirmed(Map<String, Object> order) {
		return Boolean.TRUE.equals(order.get("confirmed"));
	}

	private Map<String, Object> one(String sql, Object... parameters) {
		var rows = jsonRows(sql, parameters);
		if (rows.isEmpty()) throw new IllegalArgumentException("找不到对应业务记录");
		return rows.getFirst();
	}

	private List<Map<String, Object>> jsonRows(String sql, Object... parameters) {
		return jdbc.queryForList(sql, parameters);
	}

	private String json(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException failure) {
			throw new IllegalStateException("无法保存交付快照", failure);
		}
	}

	private <T> T parse(String value, Class<T> type) {
		try {
			return mapper.readValue(value, type);
		} catch (JsonProcessingException failure) {
			throw new IllegalStateException("交付快照无法读取", failure);
		}
	}

	static String required(String value, int max, String name) {
		if (value == null || value.isBlank() || value.strip().length() > max || value.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException(name + "格式不正确");
		}
		return value.strip();
	}
}

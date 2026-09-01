package com.chronos.form;

import com.chronos.Idao.form.*;
import com.chronos.model.form.*;
import java.util.List;
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormService {
	private final IFormDefinitionRepository definitions;
	private final IFormFieldRepository fields;
	private final IFormInstanceRepository instances;
	private final ObjectMapper json = new ObjectMapper();

	public FormService(IFormDefinitionRepository definitions, IFormFieldRepository fields,
			IFormInstanceRepository instances) {
		this.definitions = definitions;
		this.fields = fields;
		this.instances = instances;
	}

	public Page<FormDefinition> list(Pageable pageable) {
		return definitions.findAll(pageable);
	}

	public List<FormField> fields(String formId) {
		require(formId);
		return fields.findByFormIdOrderBySortOrderAscCreateTimeAsc(formId);
	}

	@Transactional
	public FormDefinition save(FormDefinition v) {
		v.setId(null);
		validate(v);
		if (definitions.existsByFormKeyAndVersion(v.getFormKey(), v.getVersion()))
			throw new IllegalArgumentException("表单编码和版本已存在");
		v.setStatus("DRAFT");
		v.setPublishedAt(null);
		return definitions.save(v);
	}

	@Transactional
	public FormDefinition update(FormDefinition v) {
		FormDefinition old = require(v.getId());
		ensureDraft(old);
		old.setFormName(required(v.getFormName(), "表单名称不能为空"));
		old.setDescription(v.getDescription());
		return definitions.save(old);
	}

	@Transactional
	public FormField saveField(FormField v) {
		v.setId(null);
		ensureDraft(require(v.getFormId()));
		validate(v);
		return fields.save(v);
	}

	@Transactional
	public FormField updateField(FormField v) {
		FormField old = fields.findById(v.getId()).orElseThrow(() -> new IllegalArgumentException("表单字段不存在"));
		ensureDraft(require(old.getFormId()));
		v.setFormId(old.getFormId());
		validate(v);
		old.setFieldKey(v.getFieldKey());
		old.setFieldLabel(v.getFieldLabel());
		old.setFieldType(v.getFieldType());
		old.setSortOrder(v.getSortOrder());
		old.setRequired(Boolean.TRUE.equals(v.getRequired()));
		old.setOptionsJson(v.getOptionsJson());
		return fields.save(old);
	}

	@Transactional
	public void delete(String id) {
		FormDefinition d = require(id);
		ensureDraft(d);
		fields.deleteByFormId(id);
		definitions.deleteById(id);
	}

	@Transactional
	public void deleteField(String id) {
		FormField f = fields.findById(id).orElseThrow(() -> new IllegalArgumentException("表单字段不存在"));
		ensureDraft(require(f.getFormId()));
		fields.delete(f);
	}

	@Transactional
	public FormDefinition publish(String id) {
		FormDefinition d = require(id);
		ensureDraft(d);
		if (fields(d.getId()).isEmpty())
			throw new IllegalArgumentException("空表单不能发布");
		d.setStatus("PUBLISHED");
		d.setPublishedAt(LocalDateTime.now());
		return definitions.save(d);
	}

	@Transactional
	public FormDefinition createVersion(String id, String version) {
		FormDefinition source = require(id);
		String next = required(version, "新版本号不能为空");
		if (definitions.existsByFormKeyAndVersion(source.getFormKey(), next))
			throw new IllegalArgumentException("该表单版本已存在");
		FormDefinition target = new FormDefinition();
		target.setFormKey(source.getFormKey());
		target.setFormName(source.getFormName());
		target.setVersion(next);
		target.setDescription(source.getDescription());
		target.setStatus("DRAFT");
		target = definitions.save(target);
		for (FormField field : fields(id)) {
			FormField copy = new FormField();
			copy.setFormId(target.getId());
			copy.setFieldKey(field.getFieldKey());
			copy.setFieldLabel(field.getFieldLabel());
			copy.setFieldType(field.getFieldType());
			copy.setSortOrder(field.getSortOrder());
			copy.setRequired(field.getRequired());
			copy.setOptionsJson(field.getOptionsJson());
			fields.save(copy);
		}
		return target;
	}

	public FormDefinition definition(String id) {
		return require(id);
	}

	public Optional<FormInstance> instance(String workflowInstanceId, String formId, String nodeKey) {
		return instances.findByWorkflowInstanceIdAndFormIdAndNodeKey(workflowInstanceId, formId, nodeKey);
	}

	@Transactional
	public FormInstance saveRuntime(String workflowInstanceId, String formId, String nodeKey, String role, String owner,
			Map<String, Object> input, Map<String, String> permissions, Set<String> required, boolean draft) {
		FormDefinition definition = require(formId);
		if (!"PUBLISHED".equals(definition.getStatus()))
			throw new IllegalArgumentException("只能填写已发布表单");
		List<FormField> schema = fields(formId);
		Map<String, FormField> byKey = new LinkedHashMap<>();
		schema.forEach(f -> byKey.put(f.getFieldKey(), f));
		FormInstance value = instance(workflowInstanceId, formId, nodeKey).orElseGet(FormInstance::new);
		Map<String, Object> merged = readMap(value.getDataJson());
		for (var entry : input.entrySet()) {
			if (!byKey.containsKey(entry.getKey()))
				throw new IllegalArgumentException("未知表单字段：" + entry.getKey());
			String permission = permissions.getOrDefault(formId + "." + entry.getKey(), "READ");
			if (!"EDIT".equals(permission))
				throw new IllegalArgumentException("字段无编辑权限：" + entry.getKey());
			merged.put(entry.getKey(), entry.getValue());
		}
		if (!draft) {
			for (FormField field : schema) {
				String key = formId + "." + field.getFieldKey();
				boolean must = required.contains(key) || (Boolean.TRUE.equals(field.getRequired())
						&& "EDIT".equals(permissions.getOrDefault(key, "READ")));
				if (must && empty(merged.get(field.getFieldKey())))
					throw new IllegalArgumentException("字段必填：" + field.getFieldLabel());
			}
		}
		value.setWorkflowInstanceId(workflowInstanceId);
		value.setFormId(formId);
		value.setNodeKey(nodeKey);
		value.setFormRole(role);
		value.setOwner(owner);
		value.setStatus(draft ? "DRAFT" : "SUBMITTED");
		try {
			value.setDataJson(json.writeValueAsString(merged));
		} catch (Exception e) {
			throw new IllegalArgumentException("表单数据无法序列化");
		}
		return instances.save(value);
	}

	private Map<String, Object> readMap(String value) {
		try {
			return json.readValue(value == null ? "{}" : value,
					new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
					});
		} catch (Exception e) {
			return new LinkedHashMap<>();
		}
	}

	private boolean empty(Object value) {
		return value == null || (value instanceof String s && s.isBlank())
				|| (value instanceof Collection<?> c && c.isEmpty());
	}

	private FormDefinition require(String id) {
		return definitions.findById(id).orElseThrow(() -> new IllegalArgumentException("表单不存在"));
	}

	private void validate(FormDefinition v) {
		v.setFormKey(required(v.getFormKey(), "表单Key不能为空"));
		v.setFormName(required(v.getFormName(), "表单名称不能为空"));
		if (v.getVersion() == null || v.getVersion().isBlank())
			v.setVersion("v1");
		if (v.getStatus() == null || v.getStatus().isBlank())
			v.setStatus("DRAFT");
	}

	private void validate(FormField v) {
		v.setFieldKey(required(v.getFieldKey(), "字段Key不能为空"));
		v.setFieldLabel(required(v.getFieldLabel(), "字段名称不能为空"));
		if (v.getFieldType() == null || v.getFieldType().isBlank())
			v.setFieldType("TEXT");
		if (v.getSortOrder() == null)
			v.setSortOrder(0);
	}

	private void ensureDraft(FormDefinition value) {
		if (!"DRAFT".equals(value.getStatus()))
			throw new IllegalArgumentException("只有草稿表单可以修改");
	}

	private String required(String v, String message) {
		if (v == null || v.isBlank())
			throw new IllegalArgumentException(message);
		return v.trim();
	}
}

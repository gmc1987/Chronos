package com.chronos.ai.service;

import com.chronos.Idao.IDictRepository;
import com.chronos.model.pojo.DictItem;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolve model capabilities from the same dictionary values used by model management. */
@Component
public class AiModelTypes {
	private final IDictRepository dictionaries;

	public AiModelTypes(IDictRepository dictionaries) {
		this.dictionaries = dictionaries;
	}

	public boolean isText(String modelType) {
		List<DictItem> values = dictionaries.findByDictCode("DICT_MODEL_TEXT");
		if (values.size() != 1 || !Integer.valueOf(1).equals(values.getFirst().getStatus())
				|| values.getFirst().getDictValue() == null
				|| values.getFirst().getDictValue().isBlank()) {
			throw new AiModelConfigurationException("文本模型类型字典未配置或无效");
		}
		return modelType != null && values.getFirst().getDictValue().trim()
				.equalsIgnoreCase(modelType.trim());
	}
}

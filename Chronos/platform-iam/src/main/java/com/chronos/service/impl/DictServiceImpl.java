package com.chronos.service.impl;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IDictRepository;
import com.chronos.commons.utils.BeanCopyUtil;
import com.chronos.model.dto.DictDTO;
import com.chronos.model.pojo.DictItem;
import com.chronos.model.vo.DictVO;
import com.chronos.service.iService.IDictService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("dictService")
public class DictServiceImpl implements IDictService {
	private static final String CACHE_KEY_TREE = "dict:tree";
	@Autowired
	private IDictRepository dictRepository;
	@Autowired
	private StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public List<DictVO> getTree() {
		String cached = stringRedisTemplate.opsForValue().get("dict:tree");
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		if (cached != null && !cached.isEmpty()) {
			try {
				return objectMapper.readValue(cached, new TypeReference<List<DictVO>>() {
				});
			} catch (Exception e) {
				// 建议打日志，而不是吞掉
				 log.error("读取缓存异常", e);
			}
		}

		List<DictItem> all = dictRepository.findAll();
		List<DictVO> tree = buildTree(all);

		try {
			stringRedisTemplate.opsForValue().set("dict:tree", objectMapper.writeValueAsString(tree));
		} catch (Exception e) {
			 log.error("缓存写入失败", e);
		}

		return tree;
	}

	public List<DictVO> listByCode(String dictCode) {
		List<DictItem> items = this.dictRepository.findByDictCode(dictCode);
		if (items == null || items.isEmpty())
			return new ArrayList<>();

		List<DictItem> children = (List<DictItem>) items.stream()
				.filter(i -> (i.getParentId() != null && !i.getParentId().isEmpty())).collect(Collectors.toList());
		if (!children.isEmpty()) {
			return (List<DictVO>) children.stream().map(this::toVO).collect(Collectors.toList());
		}

		List<DictVO> result = new ArrayList<>();
		for (DictItem root : items) {
			if (root == null || root.getId() == null)
				continue;
			List<DictItem> byParent = this.dictRepository.findByParentId(root.getId());
			if (byParent != null && !byParent.isEmpty()) {
				result.addAll(
						(Collection<? extends DictVO>) byParent.stream().map(this::toVO).collect(Collectors.toList()));
			}
		}
		return result;
	}

	public DictVO getById(String id) {
		Optional<DictItem> opt = this.dictRepository.findById(id);
		return opt.<DictVO>map(this::toVO).orElse(null);
	}

	@Transactional
	public void save(DictDTO dto) {
		DictItem item = new DictItem();
		BeanCopyUtil.copyNonNullProperties(dto, item);
		if (item.getCreateTime() == null)
			item.setCreateTime(LocalDateTime.now());
		this.dictRepository.save(item);
		this.stringRedisTemplate.delete("dict:tree");
	}

	@Transactional
	public void update(DictDTO dto) {
		if (dto == null || dto.getId() == null)
			throw new IllegalArgumentException("id required");
		Optional<DictItem> opt = this.dictRepository.findById(dto.getId());
		if (!opt.isPresent())
			throw new IllegalArgumentException("dict not found");
		DictItem item = opt.get();
		BeanCopyUtil.copyNonNullProperties(dto, item);
		item.setLastUpdateTime(LocalDateTime.now());
		this.dictRepository.save(item);
		this.stringRedisTemplate.delete("dict:tree");
	}

	@Transactional
	public void delete(String id) {
		this.dictRepository.deleteById(id);
		this.stringRedisTemplate.delete("dict:tree");
	}

	private List<DictVO> buildTree(List<DictItem> all) {
		Map<String, DictVO> map = new HashMap<>();
		for (DictItem item : all) {
			map.put(item.getId(), toVO(item));
		}
		List<DictVO> roots = new ArrayList<>();
		for (DictItem item : all) {
			DictVO vo = map.get(item.getId());
			if (item.getParentId() == null || item.getParentId().isEmpty()) {
				roots.add(vo);
				continue;
			}
			DictVO parent = map.get(item.getParentId());
			if (parent != null) {
				try {
					Field f = DictVO.class.getDeclaredField("children");
					f.setAccessible(true);
					List<DictVO> children = (List<DictVO>) f.get(parent);
					if (children == null)
						children = new ArrayList<>();
					children.add(vo);
					f.set(parent, children);
				} catch (Exception exception) {
				}
				continue;
			}
			roots.add(vo);
		}

		return roots;
	}

	private DictVO toVO(DictItem item) {
		return DictVO.builder().id(item.getId()).dictCode(item.getDictCode()).dictName(item.getDictName())
				.parentId(item.getParentId()).dictValue(item.getDictValue()).status(item.getStatus())
				.createTime(item.getCreateTime()).lastUpdateTime(item.getLastUpdateTime()).build();
	}
}

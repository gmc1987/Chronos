package com.chronos.service.impl;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
	private static final String CACHE_KEY_CODE_PREFIX = "dict:code:";
	@Autowired
	private IDictRepository dictRepository;
	@Autowired
	private StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public DictServiceImpl() {
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	/**
	 * 应用外部 SQL 可能绕过字典服务直接写库。启动完成后以数据库为准重建全部
	 * 字典缓存，避免 Redis 中残留的旧字典树导致管理页面数据不完整。
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void warmDictionaryCachesOnStartup() {
		List<DictItem> allItems = dictRepository.findAll();
		writeCache(CACHE_KEY_TREE, buildTree(allItems));

		allItems.stream()
				.map(DictItem::getDictCode)
				.filter(code -> code != null && !code.isBlank())
				.distinct()
				.forEach(code -> writeCache(
						cacheKeyForCode(code),
						loadByCodeFromDatabase(code)));
		log.info("字典缓存预热完成，dictCodeCount={}", allItems.stream()
				.map(DictItem::getDictCode)
				.filter(code -> code != null && !code.isBlank())
				.distinct()
				.count());
	}

	public List<DictVO> getTree() {
		String cached = readCache(CACHE_KEY_TREE);
		if (cached != null && !cached.isEmpty()) {
			try {
				return objectMapper.readValue(cached, new TypeReference<List<DictVO>>() {
				});
			} catch (Exception e) {
				log.warn("字典树缓存内容无效，将回源数据库", e);
			}
		}

		List<DictItem> all = dictRepository.findAll();
		List<DictVO> tree = buildTree(all);
		writeCache(CACHE_KEY_TREE, tree);
		return tree;
	}

	public List<DictVO> listByCode(String dictCode) {
		if (dictCode == null || dictCode.isBlank()) {
			return new ArrayList<>();
		}

		String cacheKey = cacheKeyForCode(dictCode);
		String cached = readCache(cacheKey);
		if (cached != null && !cached.isEmpty()) {
			try {
				return objectMapper.readValue(cached, new TypeReference<List<DictVO>>() {
				});
			} catch (Exception e) {
				log.warn("字典编码缓存内容无效，将回源数据库，dictCode={}", dictCode, e);
			}
		}

		List<DictVO> result = loadByCodeFromDatabase(dictCode);
		writeCache(cacheKey, result);
		return result;
	}

	private List<DictVO> loadByCodeFromDatabase(String dictCode) {
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
		Set<String> affectedCodes = new LinkedHashSet<>();
		if (item.getDictCode() != null) {
			affectedCodes.add(item.getDictCode());
		}
		refreshCacheAfterCommit(affectedCodes);
	}

	@Transactional
	public void update(DictDTO dto) {
		if (dto == null || dto.getId() == null)
			throw new IllegalArgumentException("id required");
		Optional<DictItem> opt = this.dictRepository.findById(dto.getId());
		if (!opt.isPresent())
			throw new IllegalArgumentException("dict not found");
		DictItem item = opt.get();
		String originalDictCode = item.getDictCode();
		BeanCopyUtil.copyNonNullProperties(dto, item);
		item.setLastUpdateTime(LocalDateTime.now());
		this.dictRepository.save(item);
		Set<String> affectedCodes = new LinkedHashSet<>();
		affectedCodes.add(originalDictCode);
		affectedCodes.add(item.getDictCode());
		refreshCacheAfterCommit(affectedCodes);
	}

	@Transactional
	public void delete(String id) {
		String dictCode = this.dictRepository.findById(id)
				.map(DictItem::getDictCode)
				.orElse(null);
		this.dictRepository.deleteById(id);
		Set<String> affectedCodes = new LinkedHashSet<>();
		if (dictCode != null) {
			affectedCodes.add(dictCode);
		}
		refreshCacheAfterCommit(affectedCodes);
	}

	/**
	 * 数据事务提交后再刷新缓存，避免并发请求在事务提交前把旧数据重新写回 Redis。
	 */
	private void refreshCacheAfterCommit(Set<String> dictCodes) {
		Runnable refreshAction = () -> {
			List<DictVO> tree = buildTree(dictRepository.findAll());
			writeCache(CACHE_KEY_TREE, tree);
			dictCodes.stream()
					.filter(code -> code != null && !code.isBlank())
					.forEach(code -> writeCache(
							cacheKeyForCode(code),
							loadByCodeFromDatabase(code)));
		};

		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			refreshAction.run();
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(
				new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						refreshAction.run();
					}
				});
	}

	private String cacheKeyForCode(String dictCode) {
		return CACHE_KEY_CODE_PREFIX + dictCode;
	}

	private String readCache(String cacheKey) {
		try {
			return stringRedisTemplate.opsForValue().get(cacheKey);
		} catch (Exception e) {
			log.warn("Redis 不可用，字典查询降级到数据库，cacheKey={}", cacheKey, e);
			return null;
		}
	}

	private void writeCache(String cacheKey, Object value) {
		try {
			stringRedisTemplate.opsForValue().set(
					cacheKey,
					objectMapper.writeValueAsString(value));
		} catch (Exception e) {
			log.warn("字典缓存写入失败，不影响本次数据库结果，cacheKey={}", cacheKey, e);
		}
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

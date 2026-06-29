package com.chronos.service.impl;

import com.chronos.Idao.IMenuRepository;
import com.chronos.commons.utils.BeanCopyUtil;
import com.chronos.model.dto.MenuDTO;
import com.chronos.model.pojo.Menu;
import com.chronos.model.vo.MenuVO;
import com.chronos.service.iService.IMenuService;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("menuService")
public class MenuServiceImpl implements IMenuService {
	@Autowired
	private IMenuRepository menuRepository;

	public Page<Menu> pageMenus(MenuDTO dto, Pageable pageable) {
		PageRequest pageRequest = null;
		if (pageable.getSort().isUnsorted()) {
			pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
					Sort.by(Sort.Direction.ASC, new String[] { "orderNum" }));
		}
		return this.menuRepository.findAll((Pageable) pageRequest);
	}

	public MenuVO getMenuById(String id) {
		Optional<Menu> opt = this.menuRepository.findById(id);
		if (!opt.isPresent())
			return null;
		Menu m = opt.get();
		MenuVO vo = MenuVO.builder().id(m.getId()).menuName(m.getMenuName()).path(m.getPath()).parentId(m.getParentId())
				.orderNum(m.getOrderNum()).build();
		return vo;
	}

	@Transactional
	public void save(MenuDTO dto) {
		Menu m = new Menu();
		BeanCopyUtil.copyNonNullProperties(dto, m);
		if (m.getCreateTime() == null)
			m.setCreateTime(LocalDateTime.now());
		this.menuRepository.save(m);
	}

	@Transactional
	public void update(MenuDTO dto) {
		if (dto == null || dto.getId() == null)
			throw new IllegalArgumentException("id required");
		Optional<Menu> opt = this.menuRepository.findById(dto.getId());
		if (!opt.isPresent())
			throw new IllegalArgumentException("menu not found");
		Menu m = opt.get();
		BeanCopyUtil.copyNonNullProperties(dto, m);
		this.menuRepository.save(m);
	}

	@Transactional
	public void delete(String id) {
		this.menuRepository.deleteById(id);
	}

	public List<MenuVO> getMenuTree() {
		List<Menu> all = this.menuRepository.findAll();

		Map<String, MenuVO> map = new HashMap<>();
		for (Menu m : all) {
			MenuVO vo = MenuVO.builder().id(m.getId()).menuName(m.getMenuName()).path(m.getPath())
					.parentId(m.getParentId()).orderNum(m.getOrderNum()).build();
			map.put(vo.getId(), vo);
		}
		List<MenuVO> roots = new ArrayList<>();
		for (Menu m : all) {
			String pid = m.getParentId();
			MenuVO vo = map.get(m.getId());
			if (pid == null || pid.isEmpty()) {
				roots.add(vo);
				continue;
			}
			MenuVO parent = map.get(pid);
			if (parent != null) {
				if (parent.getChildren() == null) {

					try {
						Field f = MenuVO.class.getDeclaredField("children");
						f.setAccessible(true);
						List<MenuVO> children = (List<MenuVO>) f.get(parent);
						if (children == null)
							children = new ArrayList<>();
						children.add(vo);
						f.set(parent, children);
					} catch (Exception exception) {
					}

					continue;
				}
				try {
					Field f = MenuVO.class.getDeclaredField("children");
					f.setAccessible(true);
					List<MenuVO> children = (List<MenuVO>) f.get(parent);
					children.add(vo);
				} catch (Exception exception) {
				}

				continue;
			}

			roots.add(vo);
		}

		return sortMenuTree(roots);
	}

	private List<MenuVO> sortMenuTree(List<MenuVO> roots) {
		if (roots == null)
			return roots;
		Comparator<MenuVO> cmp = (a, b) -> {
			Integer oa = Integer.valueOf((a.getOrderNum() == null) ? 0 : a.getOrderNum().intValue());
			Integer ob = Integer.valueOf((b.getOrderNum() == null) ? 0 : b.getOrderNum().intValue());
			return oa.compareTo(ob);
		};
		roots.sort(cmp);
		for (MenuVO r : roots) {
			List<MenuVO> children = r.getChildren();
			if (children != null && !children.isEmpty()) {
				children.sort(cmp);

				sortMenuTree(children);
				try {
					Field f = MenuVO.class.getDeclaredField("children");
					f.setAccessible(true);
					f.set(r, children);
				} catch (Exception exception) {
				}
			}
		}

		return roots;
	}
}


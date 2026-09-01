package com.chronos.service.iService;

import com.chronos.model.dto.MenuDTO;
import com.chronos.model.pojo.Menu;
import com.chronos.model.vo.MenuVO;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IMenuService {
  Page<Menu> pageMenus(MenuDTO paramMenuDTO, Pageable paramPageable);
  
  MenuVO getMenuById(String paramString);
  
  void save(MenuDTO paramMenuDTO);
  
  void update(MenuDTO paramMenuDTO);
  
  void delete(String paramString);
  
  List<MenuVO> getMenuTree();
}


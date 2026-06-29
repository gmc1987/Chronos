package com.chronos.service.iService;

import com.chronos.model.dto.DictDTO;
import com.chronos.model.vo.DictVO;
import java.util.List;

public interface IDictService {
  List<DictVO> getTree();
  
  List<DictVO> listByCode(String paramString);
  
  DictVO getById(String paramString);
  
  void save(DictDTO paramDictDTO);
  
  void update(DictDTO paramDictDTO);
  
  void delete(String paramString);
}



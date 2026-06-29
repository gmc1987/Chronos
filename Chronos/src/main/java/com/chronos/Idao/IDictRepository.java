package com.chronos.Idao;

import com.chronos.model.pojo.DictItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository("dictRepository")
public interface IDictRepository extends JpaRepository<DictItem, String> {
  List<DictItem> findByDictCode(String paramString);
  
  List<DictItem> findByParentId(String paramString);
}



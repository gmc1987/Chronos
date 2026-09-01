package com.chronos.Idao;

import com.chronos.model.pojo.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository("menuRepository")
public interface IMenuRepository extends JpaRepository<Menu, String> {
  List<Menu> findByParentId(String parentId);
}

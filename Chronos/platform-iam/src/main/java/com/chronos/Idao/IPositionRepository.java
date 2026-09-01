package com.chronos.Idao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.Position;
public interface IPositionRepository extends JpaRepository<Position,String> { List<Position> findByStatusOrderBySortOrderAsc(Integer status); Position findByPositionCode(String code); }

package com.chronos.Idao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.chronos.model.pojo.Position;
public interface IPositionRepository extends JpaRepository<Position,String> {
    List<Position> findByStatusOrderBySortOrderAsc(Integer status);
    List<Position> findByOrganizationIdOrderBySortOrderAsc(String organizationId);
    Page<Position> findByOrganizationId(String organizationId, Pageable pageable);
    Optional<Position> findByOrganizationIdAndPositionCode(String organizationId, String code);
    boolean existsByOrganizationIdAndPositionCodeAndIdNot(String organizationId, String code, String id);
    /** Legacy global lookup retained only for migration-compatible callers. */
    Position findByPositionCode(String code);
}

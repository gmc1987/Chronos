package com.chronos.Idao;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.chronos.model.pojo.JobLevel;
public interface IJobLevelRepository extends JpaRepository<JobLevel,String> {
    Optional<JobLevel> findByLevelCode(String levelCode);
    Optional<JobLevel> findByOrganizationIdAndLevelCode(String organizationId, String levelCode);
    List<JobLevel> findByOrganizationIdOrderBySortOrderAsc(String organizationId);
    Page<JobLevel> findByOrganizationId(String organizationId, Pageable pageable);
    boolean existsByOrganizationIdAndLevelCodeAndIdNot(String organizationId, String code, String id);
}

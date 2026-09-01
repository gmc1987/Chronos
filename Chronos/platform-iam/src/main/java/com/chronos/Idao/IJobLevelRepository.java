package com.chronos.Idao;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.JobLevel;
public interface IJobLevelRepository extends JpaRepository<JobLevel,String> {
    Optional<JobLevel> findByLevelCode(String levelCode);
}

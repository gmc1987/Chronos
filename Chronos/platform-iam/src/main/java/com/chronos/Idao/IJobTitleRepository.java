package com.chronos.Idao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.JobTitle;
public interface IJobTitleRepository extends JpaRepository<JobTitle,String> { List<JobTitle> findByStatusOrderBySortOrderAsc(Integer status); }

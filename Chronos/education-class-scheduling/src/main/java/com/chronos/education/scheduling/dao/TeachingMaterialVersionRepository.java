package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.TeachingMaterialVersion; import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TeachingMaterialVersionRepository extends JpaRepository<TeachingMaterialVersion,String> {
 List<TeachingMaterialVersion> findByMaterialIdOrderByVersionNoDesc(String id);
}

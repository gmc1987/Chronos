package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.CoursewareVersion; import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CoursewareVersionRepository extends JpaRepository<CoursewareVersion,String> {
 List<CoursewareVersion> findByCoursewareIdOrderByVersionNoDesc(String id);
}

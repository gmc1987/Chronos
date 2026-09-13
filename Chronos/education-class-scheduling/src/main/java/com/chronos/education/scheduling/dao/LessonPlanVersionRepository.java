package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.LessonPlanVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LessonPlanVersionRepository extends JpaRepository<LessonPlanVersion, String> {
	List<LessonPlanVersion> findByLessonPlanIdOrderByVersionNoDesc(String lessonPlanId);
}

package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.ErrorBook;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ErrorBookRepository extends JpaRepository<ErrorBook, String> {
	List<ErrorBook> findByCourseIdAndSemesterIdAndStudentIdInAndArchivedFalse(
			String courseId, String semesterId, Collection<String> studentIds);
}

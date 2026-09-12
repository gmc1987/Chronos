package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.Courseware;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CoursewareRepository extends JpaRepository<Courseware, String> {}

package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.EducationUserBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationUserBindingRepository extends JpaRepository<EducationUserBinding, String> {
	List<EducationUserBinding> findByUsernameAndStatusOrderByProfileType(String username, String status);

	List<EducationUserBinding> findByProfileTypeAndProfileIdInAndStatus(
			String profileType,
			List<String> profileIds,
			String status);
}

package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.EducationUserBinding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationUserBindingRepository extends JpaRepository<EducationUserBinding, String> {
	List<EducationUserBinding> findByUsernameAndStatusOrderByProfileType(String username, String status);

	List<EducationUserBinding> findByProfileTypeAndProfileIdInAndStatus(
			String profileType,
			List<String> profileIds,
			String status);

	Optional<EducationUserBinding> findByUsernameAndProfileType(String username, String profileType);
	Optional<EducationUserBinding> findByProfileTypeAndProfileId(String profileType, String profileId);
}

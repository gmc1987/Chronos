package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.EducationUserBinding;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationUserBindingRepository extends JpaRepository<EducationUserBinding, String> {
	List<EducationUserBinding> findByUsernameAndStatusOrderByProfileType(String username, String status);

	List<EducationUserBinding> findByProfileTypeAndProfileIdInAndStatus(
			String profileType,
			List<String> profileIds,
			String status);

	Optional<EducationUserBinding> findByUsernameAndProfileType(String username, String profileType);
	Optional<EducationUserBinding> findByUsername(String username);
	Optional<EducationUserBinding> findByProfileTypeAndProfileId(String profileType, String profileId);
	Optional<EducationUserBinding> findByUsernameAndProfileTypeAndStatus(String username, String profileType, String status);
	Optional<EducationUserBinding> findByProfileTypeAndProfileIdAndStatus(String profileType, String profileId, String status);
	List<EducationUserBinding> findAllByProfileTypeOrderByCreateTimeDesc(String profileType);
	Page<EducationUserBinding> findByProfileType(String profileType, Pageable pageable);
}

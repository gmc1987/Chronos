package com.chronos.Idao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.PersonExternalIdentity;

public interface IPersonExternalIdentityRepository extends JpaRepository<PersonExternalIdentity, String> {
	Optional<PersonExternalIdentity> findByOrganizationIdAndPersonTypeAndSourceSystemAndExternalPersonKey(
			String organizationId, String personType, String sourceSystem, String externalPersonKey);
}

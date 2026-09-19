package com.chronos.integration.dao;

import com.chronos.integration.model.Credential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, String> {
	Optional<Credential> findByConnectorIdAndKeyName(String connectorId, String keyName);
}

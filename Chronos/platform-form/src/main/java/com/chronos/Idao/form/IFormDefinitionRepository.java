package com.chronos.Idao.form;

import com.chronos.model.form.FormDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IFormDefinitionRepository extends JpaRepository<FormDefinition, String> {
	boolean existsByFormKeyAndVersion(String formKey, String version);
}

package com.chronos.Idao.form;

import com.chronos.model.form.FormDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IFormDefinitionRepository extends JpaRepository<FormDefinition, String> {
	boolean existsByFormKeyAndVersion(String formKey, String version);

	List<FormDefinition> findByFormKeyOrderByCreateTimeDesc(String formKey);
}

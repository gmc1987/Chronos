package com.chronos.Idao.form;

import com.chronos.model.form.FormField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IFormFieldRepository extends JpaRepository<FormField, String> {
	List<FormField> findByFormIdOrderBySortOrderAscCreateTimeAsc(String formId);

	void deleteByFormId(String formId);
}

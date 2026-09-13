package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.PreparationMaterial; import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PreparationMaterialRepository extends JpaRepository<PreparationMaterial,String> { List<PreparationMaterial> findByPreparationId(String id); }

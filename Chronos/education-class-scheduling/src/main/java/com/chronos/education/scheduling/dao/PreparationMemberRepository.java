package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.PreparationMember; import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PreparationMemberRepository extends JpaRepository<PreparationMember,String> { List<PreparationMember> findByPreparationId(String id); }

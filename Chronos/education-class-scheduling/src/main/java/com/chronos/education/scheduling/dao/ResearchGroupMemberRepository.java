package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.ResearchGroupMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResearchGroupMemberRepository extends JpaRepository<ResearchGroupMember,String> {
	List<ResearchGroupMember> findByGroupId(String groupId);
	boolean existsByGroupIdAndTeacherId(String groupId,String teacherId);
}

package com.chronos.education.scheduling.dao;
import com.chronos.education.scheduling.model.ResearchActivityMember;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ResearchActivityMemberRepository extends JpaRepository<ResearchActivityMember,String> {
	Optional<ResearchActivityMember> findByActivityIdAndTeacherId(String activityId,String teacherId);
	java.util.List<ResearchActivityMember> findByActivityId(String activityId);
	void deleteByActivityIdAndTeacherId(String activityId, String teacherId);
}

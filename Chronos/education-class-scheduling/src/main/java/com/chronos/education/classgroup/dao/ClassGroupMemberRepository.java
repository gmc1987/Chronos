package com.chronos.education.classgroup.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.classgroup.model.ClassGroupMember;

public interface ClassGroupMemberRepository extends JpaRepository<ClassGroupMember, String> {
	Page<ClassGroupMember> findByGroupIdAndStatusOrderByMemberTypeAscCreateTimeAsc(
			String groupId, String status, Pageable pageable);
	List<ClassGroupMember> findByGroupIdAndStatus(String groupId, String status);
	Optional<ClassGroupMember> findByGroupIdAndMemberTypeAndMemberId(
			String groupId, String memberType, String memberId);
}

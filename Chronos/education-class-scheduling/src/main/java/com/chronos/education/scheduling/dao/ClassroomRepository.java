package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.Classroom;

public interface ClassroomRepository extends JpaRepository<Classroom, String> {
	List<Classroom> findByCampusIdOrderByRoomCode(String campusId);
	boolean existsByRoomCodeAndIdNot(String roomCode, String id);
	boolean existsByFloorIdAndRoomNumberAndIdNot(String floorId, String roomNumber, String id);

	@org.springframework.data.jpa.repository.Query(value = """
			select exists(select 1 from edu_classroom_reservation where classroom_id = :id)
			    or exists(select 1 from edu_classroom_unavailable_slot where classroom_id = :id)
			    or exists(select 1 from edu_exam_room where classroom_id = :id)
			    or exists(select 1 from edu_schedule_date_exception where target_classroom_id = :id)
			""", nativeQuery = true)
	boolean hasOtherBusinessReferences(@org.springframework.data.repository.query.Param("id") String id);

	@org.springframework.data.jpa.repository.Query(value = """
			select c.* from edu_classroom c
			where c.campus_id = :campusId
			  and (:unclassified = false or c.floor_id is null)
			  and (:floorId = '' or c.floor_id = :floorId)
			  and (:buildingId = '' or c.floor_id in
			       (select f.id from edu_teaching_floor f where f.building_id = :buildingId))
			order by c.room_code, c.id
			""", nativeQuery = true)
	Page<Classroom> findCatalogPage(String campusId, String buildingId, String floorId,
			boolean unclassified, Pageable pageable);

	List<Classroom> findByEnabledTrueOrderByRoomCode();
	Page<Classroom> findByEnabledTrueOrderByRoomCode(Pageable pageable);
}

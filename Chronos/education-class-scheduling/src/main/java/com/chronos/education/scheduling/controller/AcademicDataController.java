package com.chronos.education.scheduling.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CourseCatalog;
import com.chronos.education.scheduling.model.EducationGrade;
import com.chronos.education.scheduling.model.Major;
import com.chronos.education.scheduling.model.ParentProfile;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.model.Subject;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeacherTeachingAssignment;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.service.AcademicDataService;

@RestController
public class AcademicDataController {
	private final AcademicDataService service;

	public AcademicDataController(AcademicDataService service) {
		this.service = service;
	}

	@GetMapping("/admin/education/terms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:view','education:term:manage')")
	public ResultData<?> terms(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.terms()) : ok(service.terms(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/terms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:create','education:term:manage')")
	public ResultData<AcademicTerm> createTerm(@RequestBody AcademicTerm command) {
		return ok(service.saveTerm(null, command));
	}

	@PutMapping("/admin/education/terms/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:term:update','education:term:manage')")
	public ResultData<AcademicTerm> updateTerm(@PathVariable String id, @RequestBody AcademicTerm command) {
		return ok(service.saveTerm(id, command));
	}

	@GetMapping("/admin/education/grades")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:grade:view','education:grade:manage')")
	public ResultData<?> grades(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.grades()) : ok(service.grades(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/grades")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:grade:create','education:grade:manage')")
	public ResultData<EducationGrade> createGrade(@RequestBody EducationGrade command) {
		return ok(service.saveGrade(null, command));
	}

	@PutMapping("/admin/education/grades/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:grade:update','education:grade:manage')")
	public ResultData<EducationGrade> updateGrade(
			@PathVariable String id,
			@RequestBody EducationGrade command) {
		return ok(service.saveGrade(id, command));
	}

	@DeleteMapping("/admin/education/grades/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:grade:delete','education:grade:manage')")
	public ResultData<Void> deleteGrade(@PathVariable String id) {
		service.deleteGrade(id);
		return ok(null);
	}

	@GetMapping("/admin/education/subjects")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:subject:view','education:subject:manage')")
	public ResultData<?> subjects(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.subjects()) : ok(service.subjects(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/subjects")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:subject:create','education:subject:manage')")
	public ResultData<Subject> createSubject(@RequestBody Subject command) {
		return ok(service.saveSubject(null, command));
	}

	@PutMapping("/admin/education/subjects/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:subject:update','education:subject:manage')")
	public ResultData<Subject> updateSubject(
			@PathVariable String id,
			@RequestBody Subject command) {
		return ok(service.saveSubject(id, command));
	}

	@DeleteMapping("/admin/education/subjects/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:subject:delete','education:subject:manage')")
	public ResultData<Void> deleteSubject(@PathVariable String id) {
		service.deleteSubject(id);
		return ok(null);
	}

	@GetMapping("/admin/education/courses")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:course:view','education:course:manage')")
	public ResultData<?> courses(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.courses()) : ok(service.courses(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/courses")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:course:create','education:course:manage')")
	public ResultData<CourseCatalog> createCourse(@RequestBody CourseCatalog command) {
		return ok(service.saveCourse(null, command));
	}

	@PutMapping("/admin/education/courses/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:course:update','education:course:manage')")
	public ResultData<CourseCatalog> updateCourse(@PathVariable String id, @RequestBody CourseCatalog command) {
		return ok(service.saveCourse(id, command));
	}

	@GetMapping("/admin/education/majors")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:class:view','education:class:manage')")
	public ResultData<?> majors(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.majors()) : ok(service.majors(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/majors")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:class:create','education:class:manage')")
	public ResultData<Major> createMajor(@RequestBody Major command) {
		return ok(service.saveMajor(null, command));
	}

	@PutMapping("/admin/education/majors/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:class:update','education:class:manage')")
	public ResultData<Major> updateMajor(@PathVariable String id, @RequestBody Major command) {
		return ok(service.saveMajor(id, command));
	}

	@DeleteMapping("/admin/education/majors/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:class:delete','education:class:manage')")
	public ResultData<Void> deleteMajor(@PathVariable String id) {
		service.deleteMajor(id);
		return ok(null);
	}

	@GetMapping("/admin/education/administrative-classes")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:class:view','education:class:manage')")
	public ResultData<?> administrativeClasses(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.administrativeClasses()) : ok(service.administrativeClasses(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/administrative-classes")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:class:create','education:class:manage')")
	public ResultData<AdministrativeClass> createAdministrativeClass(@RequestBody AdministrativeClass command) {
		return ok(service.saveAdministrativeClass(null, command));
	}

	@PutMapping("/admin/education/administrative-classes/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:class:update','education:class:manage')")
	public ResultData<AdministrativeClass> updateAdministrativeClass(
			@PathVariable String id,
			@RequestBody AdministrativeClass command) {
		return ok(service.saveAdministrativeClass(id, command));
	}

	@DeleteMapping("/admin/education/administrative-classes/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:class:delete','education:class:manage')")
	public ResultData<Void> deleteAdministrativeClass(@PathVariable String id) {
		service.deleteAdministrativeClass(id);
		return ok(null);
	}

	@GetMapping("/admin/education/students")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:view','education:student:manage')")
	public ResultData<?> students(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.students()) : ok(service.students(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/students")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:create','education:student:manage')")
	public ResultData<StudentProfile> createStudent(@RequestBody StudentProfile command) {
		return ok(service.saveStudent(null, command));
	}

	@PutMapping("/admin/education/students/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:update','education:student:manage')")
	public ResultData<StudentProfile> updateStudent(@PathVariable String id, @RequestBody StudentProfile command) {
		return ok(service.saveStudent(id, command));
	}

	@GetMapping("/admin/education/teachers")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:view','education:teacher:business:manage')")
	public ResultData<?> teachers(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.teachers()) : ok(service.teachers(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/teachers")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:create','education:teacher:business:manage')")
	public ResultData<TeacherAcademicProfile> createTeacher(@RequestBody TeacherAcademicProfile command) {
		return ok(service.saveTeacher(null, command));
	}

	@PutMapping("/admin/education/teachers/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teacher:business:update','education:teacher:business:manage')")
	public ResultData<TeacherAcademicProfile> updateTeacher(
			@PathVariable String id,
			@RequestBody TeacherAcademicProfile command) {
		return ok(service.saveTeacher(id, command));
	}

	@GetMapping("/admin/education/parents")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:home-school:parent:view','education:home-school:parent:manage')")
	public ResultData<?> parents(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.parents()) : ok(service.parents(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/parents")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:home-school:parent:create','education:home-school:parent:manage')")
	public ResultData<ParentProfile> createParent(@RequestBody ParentProfile command) {
		return ok(service.saveParent(null, command));
	}

	@PutMapping("/admin/education/parents/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:home-school:parent:update','education:home-school:parent:manage')")
	public ResultData<ParentProfile> updateParent(
			@PathVariable String id,
			@RequestBody ParentProfile command) {
		return ok(service.saveParent(id, command));
	}

	@DeleteMapping("/admin/education/parents/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:home-school:parent:delete','education:home-school:parent:manage')")
	public ResultData<Void> deleteParent(@PathVariable String id) {
		service.deleteParent(id);
		return ok(null);
	}

	@GetMapping("/admin/education/student-guardians")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:view','education:student:manage')")
	public ResultData<List<StudentGuardianRelation>> guardians(
			@RequestParam(required = false) String studentId,
			@RequestParam(required = false) String parentId) {
		if (parentId != null && !parentId.isBlank()) {
			return ok(service.guardiansByParent(parentId));
		}
		return ok(service.guardians(studentId));
	}

	@PostMapping("/admin/education/student-guardians")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:update','education:student:manage')")
	public ResultData<StudentGuardianRelation> createGuardian(
			@RequestBody StudentGuardianRelation command) {
		return ok(service.saveGuardian(null, command));
	}

	@PutMapping("/admin/education/student-guardians/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:update','education:student:manage')")
	public ResultData<StudentGuardianRelation> updateGuardian(
			@PathVariable String id,
			@RequestBody StudentGuardianRelation command) {
		return ok(service.saveGuardian(id, command));
	}

	@DeleteMapping("/admin/education/student-guardians/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:student:update','education:student:manage')")
	public ResultData<Void> deleteGuardian(@PathVariable String id) {
		service.deleteGuardian(id);
		return ok(null);
	}

	@GetMapping("/admin/education/teaching-assignments")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching-assignment:view','education:teaching-assignment:manage')")
	public ResultData<?> teachingAssignments(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return page == null && size == null ? ok(service.teachingAssignments()) : ok(service.teachingAssignments(page == null ? 0 : page, size == null ? 10 : size));
	}

	@PostMapping("/admin/education/teaching-assignments")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching-assignment:create','education:teaching-assignment:manage')")
	public ResultData<TeacherTeachingAssignment> createTeachingAssignment(
			@RequestBody TeacherTeachingAssignment command) {
		return ok(service.saveTeachingAssignment(null, command));
	}

	@PutMapping("/admin/education/teaching-assignments/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching-assignment:update','education:teaching-assignment:manage')")
	public ResultData<TeacherTeachingAssignment> updateTeachingAssignment(
			@PathVariable String id,
			@RequestBody TeacherTeachingAssignment command) {
		return ok(service.saveTeachingAssignment(id, command));
	}

	@DeleteMapping("/admin/education/teaching-assignments/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching-assignment:delete','education:teaching-assignment:manage')")
	public ResultData<Void> deleteTeachingAssignment(@PathVariable String id) {
		service.deleteTeachingAssignment(id);
		return ok(null);
	}

	@GetMapping("/admin/education/teaching-class-members")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:course:view','education:course:manage')")
	public ResultData<List<TeachingClassMember>> members(@RequestParam String offeringId) {
		return ok(service.members(offeringId));
	}

	@PostMapping("/admin/education/course-offerings/{offeringId}/enroll")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:course:update','education:course:manage')")
	public ResultData<TeachingClassMember> enroll(
			@PathVariable String offeringId,
			@RequestBody Map<String, String> command) {
		return ok(service.enroll(offeringId, command.get("studentId")));
	}

	@PostMapping("/admin/education/course-offerings/{offeringId}/withdraw")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:course:update','education:course:manage')")
	public ResultData<TeachingClassMember> withdraw(
			@PathVariable String offeringId,
			@RequestBody Map<String, String> command) {
		return ok(service.withdraw(offeringId, command.get("studentId")));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}

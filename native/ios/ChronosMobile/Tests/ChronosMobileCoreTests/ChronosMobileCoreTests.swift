import XCTest
@testable import ChronosMobileCore

final class ChronosMobileCoreTests: XCTestCase {
    func testManagementRoleTakesPriorityOverTeacherProfile() throws {
        let bootstrap = try decodeBootstrap(
            roles: ["SCHOOL_ADMIN"],
            permissions: ["portal:admin:view"],
            profileTypes: ["TEACHER"]
        )

        XCTAssertEqual(WorkbenchResolver.resolve(bootstrap: bootstrap), .management)
    }

    func testStudentProfileBuildsStudentWorkbench() throws {
        let bootstrap = try decodeBootstrap(
            roles: ["PORTAL_USER"],
            permissions: [],
            profileTypes: ["STUDENT"]
        )

        XCTAssertEqual(WorkbenchResolver.resolve(bootstrap: bootstrap), .student)
    }

    func testScheduleCalendarContractDecodesPublishedOccurrence() throws {
        let payload = """
        {
          "selectedStudentId": "student-1",
          "studentContexts": [{
            "studentId": "student-1",
            "studentNo": "20260001",
            "studentName": "测试学生",
            "relationship": "本人",
            "primary": true
          }],
          "startDate": "2026-10-12",
          "endDate": "2026-10-18",
          "termName": "2026 秋季学期",
          "occurrences": [{
            "occurrenceKey": "entry-1@2026-10-12",
            "date": "2026-10-12",
            "entry": {
              "id": "entry-1",
              "offeringId": "offering-1",
              "courseName": "高等数学",
              "teachingClassName": "高数 A 班",
              "teacherName": "张老师",
              "classroomName": "教学楼 101",
              "durationPeriods": 2
            },
            "occurrenceStatus": "NORMAL",
            "effectivePeriodNo": 1,
            "effectiveTeacherName": "张老师",
            "startTime": "08:00:00",
            "endTime": "09:40:00"
          }]
        }
        """

        let calendar = try JSONDecoder().decode(ScheduleCalendar.self, from: Data(payload.utf8))

        XCTAssertEqual(calendar.termName, "2026 秋季学期")
        XCTAssertEqual(calendar.occurrences.first?.entry.courseName, "高等数学")
        XCTAssertEqual(calendar.occurrences.first?.effectivePeriodNo, 1)
    }

    private func decodeBootstrap(
        roles: [String],
        permissions: [String],
        profileTypes: [String]
    ) throws -> PortalBootstrap {
        let rolesJSON = roles.map { "\"\($0)\"" }.joined(separator: ",")
        let permissionsJSON = permissions.map { "\"\($0)\"" }.joined(separator: ",")
        let profilesJSON = profileTypes.map { "\"\($0)\"" }.joined(separator: ",")
        let payload = """
        {
          "user": {
            "username": "tester",
            "displayName": "测试用户",
            "roles": [\(rolesJSON)],
            "permissions": [\(permissionsJSON)]
          },
          "applications": [],
          "widgets": [],
          "contributions": {
            "DATA": {
              "providerCode": "DATA",
              "available": true,
              "message": "ok",
              "data": { "profileTypes": [\(profilesJSON)] }
            }
          }
        }
        """
        return try JSONDecoder().decode(PortalBootstrap.self, from: Data(payload.utf8))
    }
}

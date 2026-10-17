import Foundation

public struct ScheduleCalendar: Decodable, Sendable {
    public let selectedStudentId: String?
    public let studentContexts: [StudentContext]
    public let startDate: String
    public let endDate: String
    public let termName: String?
    public let occurrences: [ScheduleOccurrence]

    private enum CodingKeys: String, CodingKey {
        case selectedStudentId, studentContexts, startDate, endDate, termName, occurrences
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        selectedStudentId = try container.decodeIfPresent(String.self, forKey: .selectedStudentId)
        studentContexts = try container.decodeIfPresent([StudentContext].self, forKey: .studentContexts) ?? []
        startDate = try container.decode(String.self, forKey: .startDate)
        endDate = try container.decode(String.self, forKey: .endDate)
        termName = try container.decodeIfPresent(String.self, forKey: .termName)
        occurrences = try container.decodeIfPresent(
            [ScheduleOccurrence].self,
            forKey: .occurrences
        ) ?? []
    }
}

public struct StudentContext: Decodable, Identifiable, Sendable {
    public let studentId: String
    public let studentNo: String?
    public let studentName: String
    public let relationship: String?
    public let primary: Bool?
    public var id: String { studentId }
}

public struct ScheduleOccurrence: Decodable, Identifiable, Sendable {
    public let occurrenceKey: String
    public let date: String
    public let entry: ScheduleEntry
    public let occurrenceStatus: String
    public let exceptionType: String?
    public let reason: String?
    public let effectivePeriodNo: Int
    public let effectiveClassroomId: String?
    public let effectiveTeacherName: String?
    public let startTime: String?
    public let endTime: String?
    public var id: String { occurrenceKey }
}

public struct ScheduleEntry: Decodable, Identifiable, Sendable {
    public let id: String
    public let semesterCode: String?
    public let offeringId: String
    public let offeringCode: String?
    public let courseName: String
    public let teachingClassName: String?
    public let teacherName: String?
    public let classroomId: String?
    public let classroomName: String?
    public let dayOfWeek: Int?
    public let periodNo: Int?
    public let durationPeriods: Int?
    public let status: String?
}

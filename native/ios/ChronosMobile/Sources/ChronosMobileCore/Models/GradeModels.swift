import Foundation

public struct GradeView: Decodable, Identifiable, Sendable {
    public let id: String
    public let semesterCode: String?
    public let courseCode: String?
    public let courseName: String
    public let offeringName: String?
    public let totalScore: Decimal?
    public let gradeLevel: String?
    public let gradePoint: Decimal?
    public let passed: Bool?
    public let publishedAt: String?
}

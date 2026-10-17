import Foundation

public struct ExamView: Decodable, Identifiable, Sendable {
    public let candidateId: String
    public let planName: String
    public let subjectName: String
    public let examDate: String
    public let startTime: String
    public let endTime: String
    public let classroomName: String?
    public let seatNo: Int?
    public var id: String { candidateId }
}

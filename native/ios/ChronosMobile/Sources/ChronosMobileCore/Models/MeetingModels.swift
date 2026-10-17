import Foundation

public struct MeetingView: Decodable, Identifiable, Sendable {
    public let meeting: Meeting
    public let currentParticipant: MeetingParticipant?
    public var id: String { meeting.id }
}

public struct Meeting: Decodable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let startTime: String
    public let endTime: String
    public let meetingType: String?
    public let meetingProvider: String?
    public let joinUrl: String?
    public let status: String
}

public struct MeetingParticipant: Decodable, Sendable {
    public let responseStatus: String?
}

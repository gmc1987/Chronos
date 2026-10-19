import Foundation

public struct FamilyChild: Decodable, Identifiable, Sendable {
    public let id: String
    public let studentNo: String?
    public let studentName: String
    public let classId: String?
    public let relationship: String?
    public let primaryGuardian: Bool?
}

public struct FamilyNotice: Decodable, Identifiable, Sendable {
    public let id: String
    public let studentId: String
    public let title: String
    public let content: String?
    public let receiptRequired: Bool?
    public let publishAt: String?
    public let expireAt: String?
    public let expired: Bool?
    public let readAt: String?
    public let receiptStatus: String?
    public let receiptAt: String?
    public let receiptComment: String?
}

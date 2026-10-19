import Foundation

public struct PublicationPage: Decodable, Sendable {
    public let content: [PublicationView]
    public let totalElements: Int
    public let last: Bool
}

public struct PublicationView: Decodable, Identifiable, Sendable {
    public let id: String
    public let publicationType: String
    public let title: String
    public let summary: String?
    public let content: String?
    public let contentType: String?
    public let status: String?
    public let importance: String?
    public let pinned: Bool?
    public let mustRead: Bool?
    public let publishedAt: String?
    public let read: Bool
    public let readDeadline: String?
    public let attachments: [PublicationAttachment]

    private enum CodingKeys: String, CodingKey {
        case id, publicationType, title, summary, content, contentType, status, importance,
             pinned, mustRead, publishedAt, read, readDeadline, attachments
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        publicationType = try container.decodeIfPresent(String.self, forKey: .publicationType) ?? "NOTICE"
        title = try container.decodeIfPresent(String.self, forKey: .title) ?? "未命名通知"
        summary = try container.decodeIfPresent(String.self, forKey: .summary)
        content = try container.decodeIfPresent(String.self, forKey: .content)
        contentType = try container.decodeIfPresent(String.self, forKey: .contentType)
        status = try container.decodeIfPresent(String.self, forKey: .status)
        importance = try container.decodeIfPresent(String.self, forKey: .importance)
        pinned = try container.decodeIfPresent(Bool.self, forKey: .pinned)
        mustRead = try container.decodeIfPresent(Bool.self, forKey: .mustRead)
        publishedAt = try container.decodeIfPresent(String.self, forKey: .publishedAt)
        read = try container.decodeIfPresent(Bool.self, forKey: .read) ?? false
        readDeadline = try container.decodeIfPresent(String.self, forKey: .readDeadline)
        attachments = try container.decodeIfPresent([PublicationAttachment].self, forKey: .attachments) ?? []
    }
}

public struct PublicationAttachment: Decodable, Identifiable, Sendable {
    public let id: String
    public let originalName: String
    public let contentType: String?
    public let fileSize: Int64?
    public let primaryContent: Bool?
}

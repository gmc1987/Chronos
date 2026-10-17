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
    public let importance: String?
    public let pinned: Bool?
    public let mustRead: Bool?
    public let publishedAt: String?
    public let read: Bool
}

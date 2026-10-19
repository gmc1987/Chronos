import Foundation

enum AnnouncementsAPI {
    static func list(type: String?, keyword: String, unreadOnly: Bool, page: Int) -> String {
        var components = URLComponents()
        components.path = "/publications"
        components.queryItems = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "size", value: "20"),
            URLQueryItem(name: "unreadOnly", value: String(unreadOnly))
        ]
        if let type { components.queryItems?.append(URLQueryItem(name: "type", value: type)) }
        if !keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            components.queryItems?.append(URLQueryItem(name: "keyword", value: keyword))
        }
        return components.string ?? "/publications?page=\(page)&size=20"
    }

    static func detail(_ id: String) -> String { "/publications/\(id)" }
    static func markRead(_ id: String) -> String { "/publications/\(id)/read" }
    static func attachment(_ id: String) -> String { "/publications/attachments/\(id)" }
}

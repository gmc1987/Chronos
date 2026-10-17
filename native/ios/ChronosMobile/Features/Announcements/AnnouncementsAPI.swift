enum AnnouncementsAPI {
    static func list(type: String?) -> String {
        let typeQuery = type.map { "&type=\($0)" } ?? ""
        return "/publications?page=0&size=30\(typeQuery)"
    }
}

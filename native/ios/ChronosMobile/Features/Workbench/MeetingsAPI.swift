enum MeetingsAPI {
    static let list = "/portal/education/meetings"
    static func response(_ id: String) -> String { "/portal/education/meetings/\(id)/response" }
    static func checkIn(_ id: String) -> String { "/portal/education/meetings/\(id)/check-in" }
}

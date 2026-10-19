enum FamilyAPI {
    static let children = "/portal/education/family/children"
    static let notices = "/portal/education/family/notices"
    static let grades = "/portal/education/family/grades"
    static let feedback = "/portal/education/family/feedback"
    static let communications = "/portal/education/family/communications"
    static func receipt(_ id: String) -> String { "/portal/education/family/notices/\(id)/receipt" }
}

enum ScheduleAPI {
    static func calendar(startDate: String, endDate: String) -> String {
        "/portal/education/schedule/calendar?startDate=\(startDate)&endDate=\(endDate)"
    }
}

import Foundation
import SwiftUI

extension WorkbenchKind {
    var displayName: String {
        switch self {
        case .student: return "学生"
        case .parent: return "家长"
        case .teacher: return "教师"
        case .staff: return "教职工"
        case .management: return "管理"
        }
    }
}

extension ISO8601DateFormatter {
    static let chronosDateTime: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}

let todayText: String = {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "zh_CN")
    formatter.dateFormat = "M月d日 EEEE"
    return formatter.string(from: Date())
}()

func isoDate(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: date)
}

func timeText(_ lesson: ScheduleOccurrence) -> String {
    if let start = lesson.startTime, !start.isEmpty,
       let end = lesson.endTime, !end.isEmpty {
        return "\(String(start.prefix(5)))-\(String(end.prefix(5)))"
    }
    return "第 \(lesson.effectivePeriodNo) 节"
}

func statusText(_ status: String) -> String {
    switch status.uppercased() {
    case "CANCELLED": return "停课"
    case "MOVED_OUT": return "已调出"
    case "MOVED_IN": return "调课"
    case "SUBSTITUTED": return "代课"
    case "ADDED": return "临时课程"
    default: return "正常"
    }
}

func statusColor(_ status: String) -> Color {
    switch status.uppercased() {
    case "CANCELLED", "MOVED_OUT": return .red
    case "MOVED_IN", "SUBSTITUTED", "ADDED": return ChronosPalette.orange
    default: return ChronosPalette.green
    }
}

func isCancelled(_ status: String) -> Bool {
    ["CANCELLED", "MOVED_OUT"].contains(status.uppercased())
}

func decimalText(_ value: Decimal?) -> String {
    guard let value else { return "--" }
    return NSDecimalNumber(decimal: value).stringValue
}

func displayDateTime(_ value: String?) -> String {
    guard let value, !value.isEmpty else { return "时间待定" }
    return value
        .replacingOccurrences(of: "T", with: " ")
        .prefix(16)
        .description
}

func appMatches(_ application: PortalApplication, _ markers: [String]) -> Bool {
    let haystack = [
        application.name,
        application.code ?? "",
        application.description ?? "",
        application.routePath
    ]
    .joined(separator: " ")
    .lowercased()
    return markers.contains { haystack.contains($0.lowercased()) }
}

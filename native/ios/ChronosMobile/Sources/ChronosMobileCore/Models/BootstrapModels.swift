import Foundation

public struct PortalBootstrap: Decodable, Sendable {
    public let user: PortalUserContext
    public let applications: [PortalApplication]
    public let widgets: [PortalWidget]
    public let contributions: [String: PortalContribution]

    private enum CodingKeys: String, CodingKey {
        case user, applications, widgets, contributions
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        user = try container.decode(PortalUserContext.self, forKey: .user)
        applications = try container.decodeIfPresent([PortalApplication].self, forKey: .applications) ?? []
        widgets = try container.decodeIfPresent([PortalWidget].self, forKey: .widgets) ?? []
        contributions = try container.decodeIfPresent(
            [String: PortalContribution].self,
            forKey: .contributions
        ) ?? [:]
    }
}

public struct PortalUserContext: Decodable, Sendable {
    public let username: String
    public let displayName: String?
    public let employeeId: String?
    public let positionName: String?
    public let roles: [String]
    public let permissions: [String]
}

public struct PortalApplication: Decodable, Identifiable, Sendable {
    public let id: String
    public let code: String?
    public let name: String
    public let description: String?
    public let icon: String?
    public let routePath: String
    public let openMode: String?
    public let recommended: Bool
    public let favorite: Bool

    private enum CodingKeys: String, CodingKey {
        case id, code, name, appName, description, icon, routePath, openMode, recommended, favorite
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        code = try container.decodeIfPresent(String.self, forKey: .code)
        name = try container.decodeIfPresent(String.self, forKey: .name)
            ?? container.decodeIfPresent(String.self, forKey: .appName)
            ?? "应用"
        description = try container.decodeIfPresent(String.self, forKey: .description)
        icon = try container.decodeIfPresent(String.self, forKey: .icon)
        routePath = try container.decodeIfPresent(String.self, forKey: .routePath) ?? ""
        openMode = try container.decodeIfPresent(String.self, forKey: .openMode)
        recommended = try container.decodeIfPresent(Bool.self, forKey: .recommended) ?? false
        favorite = try container.decodeIfPresent(Bool.self, forKey: .favorite) ?? false
    }
}

public struct PortalWidget: Decodable, Identifiable, Sendable {
    public let id: String
    public let code: String
    public let name: String
    public let description: String?
    public let provider: String
}

public struct PortalContribution: Decodable, Sendable {
    public let providerCode: String?
    public let available: Bool
    public let message: String?
    public let data: PortalContributionData?
}

public struct PortalContributionData: Decodable, Sendable {
    public let profileTypes: [String]?
    public let metrics: [PortalMetric]?
    public let termName: String?
    public let teacherSummary: TeacherSummary?
    public let total: Int?
    public let overdue: Int?
    public let claimable: Int?
    public let unread: Int?
    public let items: [PortalSummaryItem]?
    public let allRoute: String?
    public let mySchedule: [ScheduleEntry]?
}

public struct PortalMetric: Decodable, Identifiable, Sendable {
    public let label: String
    public let value: Int
    public var id: String { label }
}

public struct TeacherSummary: Decodable, Sendable {
    public let teacherName: String?
    public let maxWeeklyLessons: Int?
    public let teachingCourseCount: Int?
    public let leaveStatus: String?
    public let notificationStatus: String?
    public let aiDescription: String?
}

public struct PortalSummaryItem: Decodable, Identifiable, Sendable {
    public let id: String?
    public let taskId: String?
    public let instanceId: String?
    public let title: String?
    public let summary: String?
    public let flowName: String?
    public let nodeName: String?
    public let businessKey: String?
    public let slaStatus: String?
    public let dueAt: String?
    public let read: Bool?
    public let pendingCount: Int?
    public let overdue: Bool?
    public let claimable: Bool?
    public let route: String?

    public var stableID: String {
        id ?? taskId ?? instanceId ?? "\(title ?? flowName ?? "item")-\(businessKey ?? "")"
    }
}

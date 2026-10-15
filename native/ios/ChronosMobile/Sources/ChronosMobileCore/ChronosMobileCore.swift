import Foundation

public struct ChronosAPIConfiguration: Sendable {
    public let baseURL: URL

    public init(baseURL: URL) {
        self.baseURL = baseURL
    }

    public static var fromEnvironment: Self {
        let raw = ProcessInfo.processInfo.environment["CHRONOS_API_BASE_URL"] ?? "http://192.168.0.104:8080"
        return Self(baseURL: URL(string: raw) ?? URL(string: "http://192.168.0.104:8080")!)
    }
}

public struct ResultData<T: Decodable>: Decodable {
    public let code: String
    public let msg: String?
    public let data: T?
}

public struct SessionTokens: Codable, Sendable {
    public let accessToken: String
    public let refreshToken: String?
}

public struct Role: Codable, Hashable, Sendable {
    public let roleName: String?
    public let roleCode: String?
}

public struct Permission: Codable, Sendable {
    public let permissionCode: String?
}

public struct LoginPayload: Codable, Sendable {
    public let accessToken: String
    public let refreshToken: String?
    public let roles: [Role]?
    public let permissions: [Permission]?
}

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

public enum WorkbenchKind: String, Sendable {
    case student
    case teacher
    case staff
    case management
}

public enum WorkbenchResolver {
    public static func resolve(bootstrap: PortalBootstrap) -> WorkbenchKind {
        let profileTypes = Set(
            (bootstrap.contributions["DATA"]?.data?.profileTypes ?? [])
                .map { $0.uppercased() }
        )
        let roles = bootstrap.user.roles.map { $0.uppercased() }
        let permissions = Set(bootstrap.user.permissions.map { $0.lowercased() })

        let managementRoleMarkers = [
            "ADMIN", "SUPER", "LEADER", "PRINCIPAL", "PRESIDENT", "DIRECTOR",
            "MANAGER", "校领导", "管理员", "领导"
        ]
        let isManagementRole = roles.contains { role in
            managementRoleMarkers.contains { role.contains($0) }
        }
        let hasManagementPermission = permissions.contains("portal:manage")
            || permissions.contains("portal:admin:view")
            || permissions.contains { $0.hasPrefix("data:overview:") }

        if isManagementRole || hasManagementPermission {
            return .management
        }
        if profileTypes.contains("STUDENT") {
            return .student
        }
        if profileTypes.contains("TEACHER") {
            return .teacher
        }
        return .staff
    }
}

public struct ScheduleCalendar: Decodable, Sendable {
    public let selectedStudentId: String?
    public let studentContexts: [StudentContext]
    public let startDate: String
    public let endDate: String
    public let termName: String?
    public let occurrences: [ScheduleOccurrence]

    private enum CodingKeys: String, CodingKey {
        case selectedStudentId, studentContexts, startDate, endDate, termName, occurrences
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        selectedStudentId = try container.decodeIfPresent(String.self, forKey: .selectedStudentId)
        studentContexts = try container.decodeIfPresent([StudentContext].self, forKey: .studentContexts) ?? []
        startDate = try container.decode(String.self, forKey: .startDate)
        endDate = try container.decode(String.self, forKey: .endDate)
        termName = try container.decodeIfPresent(String.self, forKey: .termName)
        occurrences = try container.decodeIfPresent(
            [ScheduleOccurrence].self,
            forKey: .occurrences
        ) ?? []
    }
}

public struct StudentContext: Decodable, Identifiable, Sendable {
    public let studentId: String
    public let studentNo: String?
    public let studentName: String
    public let relationship: String?
    public let primary: Bool?
    public var id: String { studentId }
}

public struct ScheduleOccurrence: Decodable, Identifiable, Sendable {
    public let occurrenceKey: String
    public let date: String
    public let entry: ScheduleEntry
    public let occurrenceStatus: String
    public let exceptionType: String?
    public let reason: String?
    public let effectivePeriodNo: Int
    public let effectiveClassroomId: String?
    public let effectiveTeacherName: String?
    public let startTime: String?
    public let endTime: String?
    public var id: String { occurrenceKey }
}

public struct ScheduleEntry: Decodable, Identifiable, Sendable {
    public let id: String
    public let semesterCode: String?
    public let offeringId: String
    public let offeringCode: String?
    public let courseName: String
    public let teachingClassName: String?
    public let teacherName: String?
    public let classroomId: String?
    public let classroomName: String?
    public let dayOfWeek: Int?
    public let periodNo: Int?
    public let durationPeriods: Int?
    public let status: String?
}

public struct GradeView: Decodable, Identifiable, Sendable {
    public let id: String
    public let semesterCode: String?
    public let courseCode: String?
    public let courseName: String
    public let offeringName: String?
    public let totalScore: Decimal?
    public let gradeLevel: String?
    public let gradePoint: Decimal?
    public let passed: Bool?
    public let publishedAt: String?
}

public struct ExamView: Decodable, Identifiable, Sendable {
    public let candidateId: String
    public let planName: String
    public let subjectName: String
    public let examDate: String
    public let startTime: String
    public let endTime: String
    public let classroomName: String?
    public let seatNo: Int?
    public var id: String { candidateId }
}

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

public struct MeetingView: Decodable, Identifiable, Sendable {
    public let meeting: Meeting
    public let currentParticipant: MeetingParticipant?
    public var id: String { meeting.id }
}

public struct Meeting: Decodable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let startTime: String
    public let endTime: String
    public let meetingType: String?
    public let meetingProvider: String?
    public let joinUrl: String?
    public let status: String
}

public struct MeetingParticipant: Decodable, Sendable {
    public let responseStatus: String?
}

public enum ChronosAPIError: Error, LocalizedError {
    case invalidResponse
    case unauthorized
    case forbidden
    case notFound
    case server(String)

    public var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "服务器响应无效"
        case .unauthorized:
            return "会话已过期，请重新登录"
        case .forbidden:
            return "当前账号无权访问"
        case .notFound:
            return "功能接口不存在，请联系管理员检查配置"
        case .server(let message):
            return message
        }
    }
}

public actor ChronosSession {
    private let configuration: ChronosAPIConfiguration
    private var tokens: SessionTokens?
    private let decoder = JSONDecoder()
    private let tokenStoreKey = "chronos.mobile.session.tokens"

    public init(configuration: ChronosAPIConfiguration = .fromEnvironment) {
        self.configuration = configuration
    }

    public func restore(_ tokens: SessionTokens) {
        self.tokens = tokens
        persist(tokens)
    }

    public func restorePersisted() -> SessionTokens? {
        guard let data = UserDefaults.standard.data(forKey: tokenStoreKey),
              let saved = try? decoder.decode(SessionTokens.self, from: data) else {
            return nil
        }
        tokens = saved
        return saved
    }

    public func clear() {
        tokens = nil
        UserDefaults.standard.removeObject(forKey: tokenStoreKey)
    }

    public func login(username: String, password: String, consumer: Bool = false) async throws -> LoginPayload {
        let path = consumer ? "/consumer/users/login" : "/auth/login"
        let data = try await request(
            path: path,
            method: "POST",
            body: ["username": username, "password": password],
            authenticated: false
        )
        guard let result = try decode(LoginPayload.self, from: data) else {
            throw ChronosAPIError.server("登录响应缺少令牌")
        }
        let sessionTokens = SessionTokens(
            accessToken: result.accessToken,
            refreshToken: result.refreshToken
        )
        tokens = sessionTokens
        persist(sessionTokens)
        return result
    }

    public func refresh() async throws {
        guard let refreshToken = tokens?.refreshToken else {
            throw ChronosAPIError.unauthorized
        }
        let data = try await request(
            path: "/auth/refresh",
            method: "POST",
            body: ["refreshToken": refreshToken],
            authenticated: false
        )
        guard let result = try decode(SessionTokens.self, from: data) else {
            throw ChronosAPIError.server("刷新响应无效")
        }
        let refreshed = SessionTokens(
            accessToken: result.accessToken,
            refreshToken: result.refreshToken ?? refreshToken
        )
        tokens = refreshed
        persist(refreshed)
    }

    public func revoke() async {
        guard let refreshToken = tokens?.refreshToken else {
            clear()
            return
        }
        _ = try? await request(
            path: "/auth/revoke",
            method: "POST",
            body: ["refreshToken": refreshToken],
            authenticated: true,
            retry: false
        )
        clear()
    }

    public func get<T: Decodable>(_ path: String, as type: T.Type) async throws -> T {
        let data = try await request(path: path, method: "GET", body: nil, authenticated: true)
        guard let value = try decode(T.self, from: data) else {
            throw ChronosAPIError.server("接口返回数据为空")
        }
        return value
    }

    private func request(
        path: String,
        method: String,
        body: [String: String]?,
        authenticated: Bool,
        retry: Bool = true
    ) async throws -> Data {
        var urlRequest = URLRequest(url: try makeURL(path))
        urlRequest.httpMethod = method
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if authenticated, let accessToken = tokens?.accessToken {
            urlRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            urlRequest.httpBody = try JSONSerialization.data(withJSONObject: body)
        }

        let (data, response) = try await URLSession.shared.data(for: urlRequest)
        guard let http = response as? HTTPURLResponse else {
            throw ChronosAPIError.invalidResponse
        }
        if http.statusCode == 401 && authenticated && retry {
            try await refresh()
            return try await request(
                path: path,
                method: method,
                body: body,
                authenticated: true,
                retry: false
            )
        }
        switch http.statusCode {
        case 200..<300:
            return data
        case 401:
            throw ChronosAPIError.unauthorized
        case 403:
            throw ChronosAPIError.forbidden
        case 404:
            throw ChronosAPIError.notFound
        default:
            let message = (try? decoder.decode(ResultData<Empty>.self, from: data))?.msg
            throw ChronosAPIError.server(message ?? "请求失败（\(http.statusCode)）")
        }
    }

    private func makeURL(_ path: String) throws -> URL {
        guard let relative = URLComponents(string: path) else {
            throw ChronosAPIError.invalidResponse
        }
        var components = URLComponents(
            url: configuration.baseURL,
            resolvingAgainstBaseURL: false
        )
        let basePath = components?.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")) ?? ""
        let requestPath = relative.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        components?.path = "/" + [basePath, requestPath].filter { !$0.isEmpty }.joined(separator: "/")
        components?.queryItems = relative.queryItems
        guard let url = components?.url else {
            throw ChronosAPIError.invalidResponse
        }
        return url
    }

    private func persist(_ tokens: SessionTokens) {
        if let data = try? JSONEncoder().encode(tokens) {
            UserDefaults.standard.set(data, forKey: tokenStoreKey)
        }
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T? {
        let result = try decoder.decode(ResultData<T>.self, from: data)
        guard result.code == "200" || result.code == "201" else {
            throw ChronosAPIError.server(result.msg ?? "请求失败")
        }
        return result.data
    }
}

private struct Empty: Decodable {}

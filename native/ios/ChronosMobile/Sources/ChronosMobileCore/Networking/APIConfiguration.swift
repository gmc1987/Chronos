import Foundation

public struct ChronosAPIConfiguration: Sendable {
    public let baseURL: URL

    public init(baseURL: URL) {
        self.baseURL = baseURL
    }

    public static var fromEnvironment: Self {
        let raw = ProcessInfo.processInfo.environment["CHRONOS_API_BASE_URL"] ?? "http://localhost:8080"
        return Self(baseURL: URL(string: raw) ?? URL(string: "http://localhost:8080")!)
    }
}

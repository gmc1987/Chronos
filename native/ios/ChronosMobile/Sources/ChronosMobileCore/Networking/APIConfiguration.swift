import Foundation

public struct ChronosAPIConfiguration: Sendable {
    public let baseURL: URL
    public let portalBaseURL: URL

    public init(baseURL: URL, portalBaseURL: URL? = nil) {
        self.baseURL = baseURL
        self.portalBaseURL = portalBaseURL ?? baseURL
    }

    public static var fromEnvironment: Self {
        #if DEBUG
        let defaultAPI = "http://192.168.0.104:8080"
        let defaultPortal = "http://192.168.0.104:5173"
        #else
        let defaultAPI = "https://api.example.invalid"
        let defaultPortal = "https://portal.example.invalid"
        #endif
        let configuredAPI = Bundle.main.object(forInfoDictionaryKey: "ChronosAPIBaseURL") as? String
        let configuredPortal = Bundle.main.object(forInfoDictionaryKey: "ChronosPortalBaseURL") as? String
        let raw = ProcessInfo.processInfo.environment["CHRONOS_API_BASE_URL"]
            ?? configuredAPI ?? defaultAPI
        let baseURL = URL(string: raw) ?? URL(string: defaultAPI)!
        let portalRaw = ProcessInfo.processInfo.environment["CHRONOS_PORTAL_BASE_URL"]
            ?? configuredPortal ?? defaultPortal
        var inferred = URLComponents(url: baseURL, resolvingAgainstBaseURL: false)
        if inferred?.port == 8080 { inferred?.port = 5173 }
        return Self(
            baseURL: baseURL,
            portalBaseURL: URL(string: portalRaw) ?? inferred?.url ?? baseURL
        )
    }

    public func portalURL(for route: String) -> URL? {
        if let absolute = URL(string: route), absolute.scheme != nil { return absolute }
        guard !route.isEmpty else { return nil }
        return URL(string: route.hasPrefix("/") ? String(route.dropFirst()) : route,
                   relativeTo: portalBaseURL)?.absoluteURL
    }
}

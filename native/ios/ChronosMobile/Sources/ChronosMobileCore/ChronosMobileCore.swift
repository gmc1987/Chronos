import Foundation

public struct ChronosAPIConfiguration: Sendable {
    public let baseURL: URL
    public init(baseURL: URL) { self.baseURL = baseURL }
    public static var fromEnvironment: Self {
        let raw = ProcessInfo.processInfo.environment["CHRONOS_API_BASE_URL"] ?? "http://localhost:8080"
        return Self(baseURL: URL(string: raw) ?? URL(string: "http://localhost:8080")!)
    }
}
public struct ResultData<T: Decodable>: Decodable { public let code: String; public let msg: String?; public let data: T? }
public struct SessionTokens: Codable, Sendable { public let accessToken: String; public let refreshToken: String? }
public struct Role: Codable, Sendable { public let roleName: String?; public let roleCode: String? }
public struct LoginPayload: Codable, Sendable { public let accessToken: String; public let refreshToken: String?; public let roles: [Role]?; public let permissions: [Permission]? }
public struct Permission: Codable, Sendable { public let permissionCode: String? }
public enum ChronosAPIError: Error, LocalizedError {
    case invalidResponse, server(String)
    public var errorDescription: String? { switch self { case .invalidResponse: return "服务器响应无效"; case .server(let message): return message } }
}
public actor ChronosSession {
    private let configuration: ChronosAPIConfiguration
    private var tokens: SessionTokens?
    private let decoder = JSONDecoder()
    public init(configuration: ChronosAPIConfiguration = .fromEnvironment) { self.configuration = configuration }
    public func restore(_ tokens: SessionTokens) { self.tokens = tokens }
    public func clear() { tokens = nil }
    public func login(username: String, password: String, consumer: Bool = true) async throws -> LoginPayload {
        let path = consumer ? "/consumer/users/login" : "/auth/login"
        let data = try await request(path: path, method: "POST", body: ["username": username, "password": password], authenticated: false)
        guard let result = try decode(LoginPayload.self, from: data) else { throw ChronosAPIError.server("登录响应缺少令牌") }
        tokens = SessionTokens(accessToken: result.accessToken, refreshToken: result.refreshToken)
        return result
    }
    public func refresh() async throws {
        guard let refresh = tokens?.refreshToken else { throw ChronosAPIError.server("会话已过期，请重新登录") }
        let data = try await request(path: "/auth/refresh", method: "POST", body: ["refreshToken": refresh], authenticated: false)
        guard let result = try decode(SessionTokens.self, from: data) else { throw ChronosAPIError.server("刷新响应无效") }
        tokens = SessionTokens(accessToken: result.accessToken, refreshToken: refresh)
    }
    public func get<T: Decodable>(_ path: String, as type: T.Type) async throws -> T {
        let data = try await request(path: path, method: "GET", body: nil, authenticated: true)
        guard let value = try decode(T.self, from: data) else {
            throw ChronosAPIError.server("接口返回数据为空")
        }
        return value
    }
    private func request(path: String, method: String, body: [String: String]?, authenticated: Bool, retry: Bool = true) async throws -> Data {
        var urlRequest = URLRequest(url: configuration.baseURL.appendingPathComponent(path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))))
        urlRequest.httpMethod = method; urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if authenticated, let token = tokens?.accessToken { urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body { urlRequest.httpBody = try JSONSerialization.data(withJSONObject: body) }
        let (data, response) = try await URLSession.shared.data(for: urlRequest)
        guard let http = response as? HTTPURLResponse else { throw ChronosAPIError.invalidResponse }
        if http.statusCode == 401 && authenticated && retry { try await refresh(); return try await request(path: path, method: method, body: body, authenticated: true, retry: false) }
        guard (200..<300).contains(http.statusCode) else { throw ChronosAPIError.server((try? decoder.decode(ResultData<Empty>.self, from: data))?.msg ?? "请求失败（\(http.statusCode)）") }
        return data
    }
    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T? {
        let result = try decoder.decode(ResultData<T>.self, from: data)
        guard result.code == "200" || result.code == "201" else { throw ChronosAPIError.server(result.msg ?? "请求失败") }
        return result.data
    }
}
private struct Empty: Decodable {}

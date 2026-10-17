import Foundation

public actor ChronosSession {
    private let configuration: ChronosAPIConfiguration
    private var tokens: SessionTokens?
    private let decoder = JSONDecoder()
    private let tokenStore = TokenStore()

    public init(configuration: ChronosAPIConfiguration = .fromEnvironment) {
        self.configuration = configuration
    }

    public func restore(_ tokens: SessionTokens) {
        self.tokens = tokens
        persist(tokens)
    }

    public func restorePersisted() -> SessionTokens? {
        guard let saved = tokenStore.load() else {
            return nil
        }
        tokens = saved
        return saved
    }

    public func clear() {
        tokens = nil
        tokenStore.clear()
    }

    /// Supplies the short-lived access token to the trusted in-app portal.
    public func currentAccessToken() -> String? {
        tokens?.accessToken
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
            authenticated: false,
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
        tokenStore.save(tokens)
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

import Foundation

public actor ChronosSession {
    private let configuration: ChronosAPIConfiguration
    private var tokens: SessionTokens?
    private let decoder = JSONDecoder()
    private let tokenStore = TokenStore()
    private var servedFromCache = false
    private var cachedAt: Date?

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
        clearResponseCache()
    }

    /// Exposes the short-lived access token to native feature clients that need it.
    public func currentAccessToken() -> String? {
        tokens?.accessToken
    }

    public func lastResponseWasCached() -> Bool { servedFromCache }
    public func lastCachedAt() -> Date? { cachedAt }

    public func clearOfflineCache() {
        servedFromCache = false
        cachedAt = nil
        clearResponseCache()
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

    public func getJSON(_ path: String) async throws -> JSONValue {
        try await get(path, as: JSONValue.self)
    }

    public func sendJSON(
        _ path: String,
        method: String = "POST",
        body: JSONValue = .object([:])
    ) async throws -> JSONValue {
        let bodyData = try JSONEncoder().encode(body)
        let data = try await requestData(
            path: path,
            method: method,
            bodyData: bodyData,
            authenticated: true,
            retry: true
        )
        return try decode(JSONValue.self, from: data) ?? .null
    }

    /// Uploads a workflow or business attachment with the same authenticated
    /// multipart contract used by the web client.
    public func uploadFile(
        data: Data,
        name: String,
        mimeType: String,
        businessType: String,
        businessId: String? = nil
    ) async throws -> JSONValue {
        let boundary = "ChronosBoundary-\(UUID().uuidString)"
        var body = Data()
        func append(_ value: String) {
            body.append(Data(value.utf8))
        }
        append("--\(boundary)\r\n")
        append("Content-Disposition: form-data; name=\"file\"; filename=\"\(name.replacingOccurrences(of: "\"", with: ""))\"\r\n")
        append("Content-Type: \(mimeType)\r\n\r\n")
        body.append(data)
        append("\r\n--\(boundary)--\r\n")

        var components = URLComponents()
        components.path = "/files"
        components.queryItems = [URLQueryItem(name: "businessType", value: businessType)]
        if let businessId, !businessId.isEmpty {
            components.queryItems?.append(URLQueryItem(name: "businessId", value: businessId))
        }
        let response = try await requestData(
            path: components.string ?? "/files",
            method: "POST",
            bodyData: body,
            authenticated: true,
            retry: true,
            contentType: "multipart/form-data; boundary=\(boundary)"
        )
        return try decode(JSONValue.self, from: response) ?? .null
    }

    public func downloadFile(id: String) async throws -> Data {
        try await requestData(
            path: "/files/\(id)/content",
            method: "GET",
            bodyData: nil,
            authenticated: true,
            retry: true,
            contentType: nil
        )
    }

    /// Downloads a binary resource from an authenticated business endpoint.
    public func download(_ path: String) async throws -> Data {
        try await requestData(
            path: path,
            method: "GET",
            bodyData: nil,
            authenticated: true,
            retry: true,
            contentType: nil
        )
    }

    public func deleteFile(id: String) async throws {
        _ = try await requestData(
            path: "/files/\(id)",
            method: "DELETE",
            bodyData: nil,
            authenticated: true,
            retry: true,
            contentType: nil
        )
    }

    private func request(
        path: String,
        method: String,
        body: [String: String]?,
        authenticated: Bool,
        retry: Bool = true
    ) async throws -> Data {
        let bodyData = body.map { try? JSONSerialization.data(withJSONObject: $0) } ?? nil
        return try await requestData(
            path: path,
            method: method,
            bodyData: bodyData,
            authenticated: authenticated,
            retry: retry
        )
    }

    private func requestData(
        path: String,
        method: String,
        bodyData: Data?,
        authenticated: Bool,
        retry: Bool,
        contentType: String? = "application/json"
    ) async throws -> Data {
        var urlRequest = URLRequest(url: try makeURL(path))
        urlRequest.httpMethod = method
        if let contentType {
            urlRequest.setValue(contentType, forHTTPHeaderField: "Content-Type")
        }
        if authenticated, let accessToken = tokens?.accessToken {
            urlRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }
        urlRequest.httpBody = bodyData

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: urlRequest)
            servedFromCache = false
            cachedAt = nil
        } catch {
            if method == "GET", contentType == "application/json", let cached = cachedResponse(path) {
                servedFromCache = true
                return cached
            }
            throw error
        }
        guard let http = response as? HTTPURLResponse else {
            throw ChronosAPIError.invalidResponse
        }
        if http.statusCode == 401 && authenticated && retry {
            try await refresh()
            return try await requestData(
                path: path,
                method: method,
                bodyData: bodyData,
                authenticated: true,
                retry: false,
                contentType: contentType
            )
        }
        switch http.statusCode {
        case 200..<300:
            if method == "GET", contentType == "application/json" { cache(data, for: path) }
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

    private func cache(_ data: Data, for path: String) {
        guard cacheable(path) else { return }
        let url = cacheURL(path)
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    private func cachedResponse(_ path: String) -> Data? {
        guard cacheable(path) else { return nil }
        let url = cacheURL(path)
        guard let attributes = try? FileManager.default.attributesOfItem(atPath: url.path),
              let modified = attributes[.modificationDate] as? Date,
              Date().timeIntervalSince(modified) < 24 * 60 * 60 else { return nil }
        cachedAt = modified
        return try? Data(contentsOf: url)
    }

    private func cacheable(_ path: String) -> Bool {
        ["/portal/bootstrap", "/publications", "/portal/education/family/", "/education/portal/"]
            .contains { path.hasPrefix($0) }
    }

    private func cacheURL(_ path: String) -> URL {
        let encoded = Data(path.utf8).base64EncodedString()
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "+", with: "-")
        return FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("ChronosOffline", isDirectory: true)
            .appendingPathComponent(encoded + ".json")
    }

    private func clearResponseCache() {
        let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("ChronosOffline", isDirectory: true)
        try? FileManager.default.removeItem(at: directory)
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

import Foundation

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

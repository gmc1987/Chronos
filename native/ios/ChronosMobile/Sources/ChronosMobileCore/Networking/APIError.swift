import Foundation

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

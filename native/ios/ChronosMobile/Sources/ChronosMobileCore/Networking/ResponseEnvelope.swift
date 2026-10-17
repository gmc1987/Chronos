import Foundation

public struct ResultData<T: Decodable>: Decodable {
    public let code: String
    public let msg: String?
    public let data: T?
}

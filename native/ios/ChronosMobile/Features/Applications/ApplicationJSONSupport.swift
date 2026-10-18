import SwiftUI

func records(from value: JSONValue) -> [JSONValue] {
    if let rows = value.arrayValue { return rows }; guard let object = value.objectValue else { return [] }
    for key in ["content", "items", "records", "data"] { if let rows = object[key]?.arrayValue { return rows } }; return [value]
}
func displayFields(_ value: JSONValue) -> [(String, String)] {
    guard var object = value.objectValue else { return [("value", value.stringValue ?? "-")] }
    if let meeting = object["meeting"]?.objectValue { object.merge(meeting) { _, new in new } }
    let order = ["flowName", "title", "name", "purpose", "documentTitle", "fileName", "status", "nodeName", "initiator", "startTime", "endTime", "createTime", "description"] + object.keys.sorted()
    var result: [(String, String)] = []
    for key in order where !result.contains(where: { $0.0 == key }) { if let text = object[key]?.stringValue, !text.isEmpty { result.append((key, text)) } }
    return result.isEmpty ? [("value", "业务记录")] : result
}
func fieldLabel(_ key: String) -> String { ["status":"状态", "nodeName":"节点", "initiator":"发起人", "startTime":"开始", "endTime":"结束", "createTime":"创建时间", "description":"说明", "purpose":"事由"][key] ?? key }
func jsonString(_ values: [String: JSONValue]) -> String { guard let data = try? JSONEncoder().encode(values) else { return "{}" }; return String(data: data, encoding: .utf8) ?? "{}" }
func detailFields(_ value: JSONValue, prefix: String = "") -> [(String, String)] {
    switch value {
    case .object(let object):
        return object.keys.sorted().flatMap { key in detailFields(object[key] ?? .null, prefix: prefix.isEmpty ? fieldLabel(key) : "\(prefix) · \(fieldLabel(key))") }
    case .array(let array):
        if array.isEmpty { return [(prefix, "暂无")] }
        return array.enumerated().flatMap { detailFields($0.element, prefix: "\(prefix) \($0.offset + 1)") }
    case .null: return [(prefix, "—")]
    default: return [(prefix, value.stringValue ?? "—")]
    }
}

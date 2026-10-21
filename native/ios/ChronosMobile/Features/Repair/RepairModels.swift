import Foundation

struct RepairPage: Decodable { let content: [RepairOrder]; let totalElements: Int }

struct RepairOrder: Decodable, Identifiable {
    let id, orderNo, title, category, description, location, urgency: String
    let status, dispatchMode, reporterName: String
    let technicianName, completionNote: String?
    let createTime: String?
    let events: [RepairEvent]?
}

struct RepairEvent: Decodable, Identifiable {
    var id: String { "\(createTime ?? "")-\(eventType)" }
    let eventType, toStatus, operatorUsername: String
    let remark, createTime: String?
}

struct RepairMaterialPage: Decodable { let content: [RepairMaterial] }
struct RepairMaterial: Decodable, Identifiable {
    let id, materialName, unit, status: String
    let availableQuantity: Double
}

enum RepairLabels {
    static let status = ["PENDING_DISPATCH":"待派单", "DISPATCHED":"已派单", "ACCEPTED":"已接单",
        "IN_PROGRESS":"维修中", "WAITING_MATERIAL":"待物料", "COMPLETED":"已完工待确认",
        "CLOSED":"已关闭", "CANCELLED":"已取消"]
    static let category = ["ELECTRICAL":"电气", "WATER":"给排水", "NETWORK":"网络", "HVAC":"空调",
        "FURNITURE":"家具", "SECURITY":"安防", "OTHER":"其他"]
}

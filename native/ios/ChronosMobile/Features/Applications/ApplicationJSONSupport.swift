import Foundation
import SwiftUI

private let hiddenRecordFields: Set<String> = [
    "businessKey", "rowVersion", "variablesJson", "operations", "returnTargets"
]

private let recordLabels: [String: String] = [
    "flowName": "流程名称", "flowCode": "流程编码", "title": "标题", "name": "名称",
    "purpose": "事由", "documentTitle": "公文标题", "fileName": "文件名称",
    "status": "状态", "instanceStatus": "流程状态", "nodeName": "当前节点",
    "nodeKey": "节点编码", "currentNodeKey": "当前节点编码", "initiator": "发起人",
    "assignee": "处理人", "comment": "审批意见", "version": "版本", "category": "分类",
    "tags": "标签", "description": "说明", "summary": "摘要", "direction": "收发类型",
    "urgency": "紧急程度", "startTime": "开始时间", "endTime": "结束时间",
    "startAt": "开始时间", "endAt": "结束时间", "startDate": "开始日期",
    "endDate": "结束日期", "createTime": "创建时间", "lastUpdateTime": "更新时间",
    "dueAt": "办理期限", "completedAt": "完成时间", "finishedAt": "结束时间",
    "withdrawnAt": "撤回时间", "cancelledAt": "取消时间",
    "cancellationRequestedAt": "销假申请时间", "cancellationDecidedAt": "销假处理时间",
    "createBy": "创建人", "lastUpdateBy": "更新人", "approvedBy": "审批人",
    "withdrawnBy": "撤回人", "completedBy": "完成人", "cancelledBy": "取消人",
    "requestType": "申请类型", "resourceName": "使用资源", "destination": "目的地",
    "passengerCount": "乘车人数", "copyCount": "份数", "leaveType": "假别",
    "applicantType": "申请人类型", "requestedDays": "请假天数", "reason": "申请原因",
    "actualEndDate": "实际结束日期", "cancellationStatus": "销假状态",
    "cancellationReason": "销假原因", "cancellationComment": "销假意见",
    "balanceDeducted": "已扣减假期余额", "budgetProjectCode": "预算项目编码",
    "costCenterCode": "成本中心编码", "currency": "币种", "transportAmount": "交通费",
    "accommodationAmount": "住宿费", "mealAmount": "餐费", "otherAmount": "其他费用",
    "estimatedAmount": "预计总额", "financeStatus": "财务状态",
    "financeReference": "财务凭证", "cancelReason": "取消原因",
    "employeeName": "姓名", "employeeCode": "工号", "departmentName": "归属部门",
    "positionName": "职级", "workPhone": "电话", "workEmail": "邮箱",
    "taskKind": "任务类型", "slaStatus": "时限状态", "reminderCount": "催办次数",
    "escalationLevel": "升级级别", "requiresFormInput": "需要填写表单",
    "returnPolicy": "退回策略", "rejectPolicy": "拒绝策略", "claimable": "可认领",
    "claimed": "已认领", "approvalMode": "审批方式", "mainFormName": "申请表单"
]

private let dateTimeFields: Set<String> = [
    "createTime", "lastUpdateTime", "startTime", "endTime", "startAt", "endAt", "dueAt",
    "completedAt", "finishedAt", "withdrawnAt", "cancelledAt", "cancellationRequestedAt",
    "cancellationDecidedAt"
]

func records(from value: JSONValue) -> [JSONValue] {
    if let rows = value.arrayValue { return rows }
    guard let object = value.objectValue else { return [] }
    for key in ["content", "items", "records", "data"] {
        if let rows = object[key]?.arrayValue { return rows }
    }
    return [value]
}

/// Internal identifiers remain available to navigation and actions, but never appear in user cards.
func isHiddenRecordField(_ key: String) -> Bool {
    hiddenRecordFields.contains(key) || key == "id" || key.hasSuffix("Id") || key.hasSuffix("ID")
}

func fieldLabel(_ key: String) -> String {
    recordLabels[key] ?? "其他信息"
}

func displayValue(for key: String, raw text: String) -> String {
    if dateTimeFields.contains(key), text.count >= 19 {
        let prefix = String(text.prefix(19)).replacingOccurrences(of: "T", with: " ")
        if prefix.count == 19 { return prefix }
    }
    if ["status", "instanceStatus", "financeStatus", "cancellationStatus"].contains(key) {
        return [
            "PENDING": "待处理", "CLAIMABLE": "待认领", "APPROVED": "已通过",
            "REJECTED": "已拒绝", "RUNNING": "审批中", "COMPLETED": "已完成",
            "WITHDRAWN": "已撤回", "CANCELLED": "已取消", "TERMINATED": "已终止",
            "NONE": "无", "NOT_REQUIRED": "无需处理"
        ][text] ?? text
    }
    return text
}

func displayFields(_ value: JSONValue) -> [(String, String)] {
    guard var object = value.objectValue else { return [("value", value.stringValue ?? "-")] }
    if let meeting = object["meeting"]?.objectValue { object.merge(meeting) { _, new in new } }

    // 通讯录列表只显示姓名、工号、部门和职级；电话和邮箱放在详情页。
    let isContact = object["employeeName"] != nil && object["employeeCode"] != nil
    let preferred = isContact
        ? ["employeeName", "employeeCode", "departmentName", "positionName"]
        : ["flowName", "title", "name", "purpose", "documentTitle", "fileName", "status",
           "nodeName", "initiator", "assignee", "destination", "startDate", "startTime",
           "endDate", "endTime", "createTime", "description"]
    let order = preferred + object.keys.sorted()
    var result: [(String, String)] = []
    for key in order where !result.contains(where: { $0.0 == key }) {
        guard !isHiddenRecordField(key), recordLabels[key] != nil else { continue }
        if isContact && ["workPhone", "workEmail"].contains(key) { continue }
        if let text = object[key]?.stringValue, !text.isEmpty {
            result.append((key, displayValue(for: key, raw: text)))
        }
    }
    return result.isEmpty ? [("value", "业务记录")] : result
}

func jsonString(_ values: [String: JSONValue]) -> String {
    guard let data = try? JSONEncoder().encode(values) else { return "{}" }
    return String(data: data, encoding: .utf8) ?? "{}"
}

func detailFields(_ value: JSONValue, prefix: String = "") -> [(String, String)] {
    switch value {
    case .object(let object):
        return object.keys.sorted().flatMap { key -> [(String, String)] in
            guard !isHiddenRecordField(key), recordLabels[key] != nil else { return [] }
            let label = fieldLabel(key)
            return detailFields(object[key] ?? .null, prefix: prefix.isEmpty ? label : "\(prefix) · \(label)")
                .map { ($0.0, displayValue(for: key, raw: $0.1)) }
        }
    case .array(let array):
        if array.isEmpty { return [(prefix, "暂无")] }
        return array.enumerated().flatMap { detailFields($0.element, prefix: "\(prefix) \($0.offset + 1)") }
    case .null:
        return [(prefix, "—")]
    default:
        return [(prefix, value.stringValue ?? "—")]
    }
}

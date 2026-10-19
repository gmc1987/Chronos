import SwiftUI

struct WorkbenchItemDetailView: View {
    let title: String
    let item: PortalSummaryItem

    var body: some View {
        List {
            detail("标题", item.title ?? item.flowName)
            detail("摘要", item.summary)
            detail("当前节点", item.nodeName)
            detail("到期时间", item.dueAt)
            detail("SLA 状态", item.slaStatus)
        }
        .navigationTitle(title + "详情")
    }

    @ViewBuilder
    private func detail(_ label: String, _ value: String?) -> some View {
        if let value, !value.isEmpty { LabeledContent(label, value: value) }
    }
}

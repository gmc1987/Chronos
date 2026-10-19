import SwiftUI

struct SummaryContributionCard: View {
    let title: String
    let icon: String
    let contribution: PortalContribution?
    let emptyMessage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: title, icon: icon)
            if let contribution, contribution.available, let data = contribution.data {
                if let total = data.total {
                    HStack(alignment: .firstTextBaseline, spacing: 7) {
                        Text("\(total)").font(.system(size: 30, weight: .bold))
                        Text("项").foregroundStyle(ChronosPalette.secondary)
                        Spacer()
                        if let overdue = data.overdue, overdue > 0 {
                            Text("\(overdue) 项逾期")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.red)
                        }
                    }
                }
                let items = data.items ?? []
                if items.isEmpty {
                    Text(emptyMessage)
                        .font(.subheadline)
                        .foregroundStyle(ChronosPalette.secondary)
                        .frame(maxWidth: .infinity, minHeight: 54, alignment: .center)
                } else {
                    ForEach(items.prefix(3), id: \.stableID) { item in
                        NavigationLink {
                            if let instanceID = item.instanceId {
                                WorkflowInstanceView(instanceID: instanceID)
                            } else {
                                WorkbenchItemDetailView(title: title, item: item)
                            }
                        } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.title ?? item.flowName ?? "待处理事项")
                                    .font(.subheadline.weight(.semibold))
                                Text(item.summary ?? item.nodeName ?? "点击查看详情")
                                    .font(.caption)
                                    .foregroundStyle(ChronosPalette.secondary)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 8)
                            .overlay(alignment: .top) { Divider() }
                        }
                        .buttonStyle(.plain)
                    }
                }
            } else {
                ConstructionInlineView(message: contribution?.message ?? "移动端摘要建设中")
            }
        }
        .padding(16)
        .cardStyle()
    }
}

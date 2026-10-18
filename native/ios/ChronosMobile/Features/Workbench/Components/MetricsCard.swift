import SwiftUI

struct MetricsCard: View {
    let contribution: PortalContribution?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: "数据概览", icon: "chart.bar.xaxis")
            if let metrics = contribution?.data?.metrics, !metrics.isEmpty {
                LazyVGrid(columns: [.init(.flexible()), .init(.flexible())], spacing: 10) {
                    ForEach(metrics.prefix(6)) { metric in
                        VStack(spacing: 4) {
                            Text("\(metric.value)").font(.title2.bold())
                            Text(metric.label)
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(ChronosPalette.background, in: RoundedRectangle(cornerRadius: 12))
                    }
                }
            } else {
                ConstructionInlineView(message: "当前账号暂无授权指标")
            }
        }
        .padding(16)
        .cardStyle()
    }
}

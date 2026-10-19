import SwiftUI

struct MetricsCard: View {
    let contribution: PortalContribution?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: "数据概览", icon: "chart.bar.xaxis")
            if let metrics = contribution?.data?.metrics, !metrics.isEmpty {
                LazyVGrid(columns: [.init(.flexible()), .init(.flexible())], spacing: 10) {
                    ForEach(metrics.prefix(6)) { metric in
                        if let route = metric.route, !route.isEmpty {
                            NavigationLink {
                                NativeRecordsView(
                                    title: metric.label,
                                    path: route,
                                    empty: "暂无\(metric.label)数据"
                                )
                            } label: { metricTile(metric, drillable: true) }
                            .buttonStyle(.plain)
                            .accessibilityHint("打开\(metric.label)明细")
                        } else {
                            metricTile(metric, drillable: false)
                        }
                    }
                }
            } else {
                ConstructionInlineView(message: "当前账号暂无授权指标")
            }
        }
        .padding(16)
        .cardStyle()
    }

    private func metricTile(_ metric: PortalMetric, drillable: Bool) -> some View {
        VStack(spacing: 4) {
            Text("\(metric.value)").font(.title2.bold())
            HStack(spacing: 4) {
                Text(metric.label)
                if drillable { Image(systemName: "chevron.right").font(.caption2) }
            }
            .font(.caption)
            .foregroundStyle(ChronosPalette.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(12)
        .background(ChronosPalette.background, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(metric.label)，\(metric.value)")
    }
}

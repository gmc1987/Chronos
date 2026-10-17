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
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.title ?? item.flowName ?? "待处理事项")
                                .font(.subheadline.weight(.semibold))
                            Text(item.summary ?? item.nodeName ?? item.businessKey ?? "查看详情请前往 Web 门户")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 8)
                        .overlay(alignment: .top) { Divider() }
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

struct RecentMeetingsCard: View {
    @EnvironmentObject var model: AppModel
    @State var state: MeetingLoadState = .loading

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: "近期会议", icon: "person.3")
            switch state {
            case .loading:
                ProgressView("正在加载会议…").frame(maxWidth: .infinity, minHeight: 70)
            case .error(let message):
                InlineErrorView(message: message) { Task { await load() } }
            case .loaded(let meetings):
                if meetings.isEmpty {
                    Text("未来暂无需要参加的会议")
                        .foregroundStyle(ChronosPalette.secondary)
                        .frame(maxWidth: .infinity, minHeight: 54, alignment: .center)
                } else {
                    ForEach(meetings.prefix(3)) { value in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(value.meeting.title).font(.subheadline.weight(.semibold))
                            Text("\(displayDateTime(value.meeting.startTime)) · \(value.currentParticipant?.responseStatus ?? "待确认")")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 8)
                        .overlay(alignment: .top) { Divider() }
                    }
                }
            }
        }
        .padding(16)
        .cardStyle()
        .task { await load() }
    }

    func load() async {
        state = .loading
        do {
            let values = try await model.session.get(
                MeetingsAPI.list,
                as: [MeetingView].self
            )
            let upcoming = values
                .filter { $0.meeting.status != "CANCELLED" }
                .sorted { $0.meeting.startTime < $1.meeting.startTime }
            state = .loaded(upcoming)
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}

enum MeetingLoadState {
    case loading
    case loaded([MeetingView])
    case error(String)
}

struct CapabilityGroupCard: View {
    let title: String
    let icon: String
    let applications: [PortalApplication]
    let emptyMessage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            CardTitle(title: title, icon: icon)
            if applications.isEmpty {
                ConstructionInlineView(message: emptyMessage)
            } else {
                ForEach(applications.prefix(5)) { application in
                    NavigationLink {
                        ApplicationDestination(application: application)
                    } label: {
                        HStack {
                            Text(application.name).foregroundStyle(ChronosPalette.text)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(ChronosPalette.secondary)
                        }
                        .padding(.vertical, 5)
                    }
                }
            }
        }
        .padding(16)
        .cardStyle()
    }
}

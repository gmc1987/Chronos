import SwiftUI

struct RecentMeetingsCard: View {
    let contribution: PortalContribution?
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
                        NavigationLink {
                            MeetingDetailView(meeting: value)
                        } label: {
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
                        .buttonStyle(.plain)
                    }
                }
            }
        }
        .padding(16)
        .cardStyle()
        .task {
            if let values = contribution?.data?.meetings {
                state = .loaded(upcoming(values))
            } else {
                await load()
            }
        }
    }

    func load() async {
        state = .loading
        do {
            let values = try await model.session.get(
                MeetingsAPI.list,
                as: [MeetingView].self
            )
            state = .loaded(upcoming(values))
        } catch {
            state = .error(error.localizedDescription)
        }
    }

    private func upcoming(_ values: [MeetingView]) -> [MeetingView] {
        values.filter { $0.meeting.status != "CANCELLED" }
            .sorted { $0.meeting.startTime < $1.meeting.startTime }
    }
}

enum MeetingLoadState {
    case loading
    case loaded([MeetingView])
    case error(String)
}

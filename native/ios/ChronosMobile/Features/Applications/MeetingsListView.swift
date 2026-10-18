import SwiftUI

/// Native meeting list used by the application center. It deliberately reuses the
/// same actionable detail screen as the workbench so confirmation and check-in do
/// not disappear when users enter from a different navigation path.
struct MeetingsListView: View {
    @EnvironmentObject private var model: AppModel
    @State private var meetings: [MeetingView] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        Group {
            if loading {
                ProgressView("正在加载会议…")
            } else if let error {
                InlineErrorView(message: error) { Task { await load() } }
            } else if meetings.isEmpty {
                ContentUnavailableView("暂无会议", systemImage: "person.3")
            } else {
                List(meetings) { meeting in
                    NavigationLink {
                        MeetingDetailView(meeting: meeting)
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(meeting.meeting.title).font(.headline)
                            Text("\(displayDateTime(meeting.meeting.startTime)) 至 \(displayDateTime(meeting.meeting.endTime))")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(meeting.room?.roomName ?? meeting.meeting.meetingType ?? "会议信息")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .refreshable { await load() }
            }
        }
        .navigationTitle("我的会议")
        .task { await load() }
    }

    private func load() async {
        loading = meetings.isEmpty
        do {
            meetings = try await model.session.get(MeetingsAPI.list, as: [MeetingView].self)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        loading = false
    }
}

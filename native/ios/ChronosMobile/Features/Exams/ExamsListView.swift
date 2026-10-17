import SwiftUI

struct ExamsListView: View {
    @EnvironmentObject var model: AppModel
    @State var state: ListLoadState<ExamView> = .loading

    var body: some View {
        NavigationStack {
            LoadableList(
                state: state,
                emptyIcon: "doc.text",
                emptyText: "当前没有已发布考试安排",
                retry: { Task { await load() } }
            ) { exam in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(exam.subjectName).font(.headline)
                        Spacer()
                        Text(exam.examDate)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(ChronosPalette.green)
                    }
                    Text(exam.planName)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text("\(exam.startTime)-\(exam.endTime) · \(exam.classroomName ?? "考场待定") · 座位 \(exam.seatNo.map(String.init) ?? "待定")")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }
            .navigationTitle("考试")
            .task { await load() }
            .refreshable { await load() }
        }
    }

    func load() async {
        state = .loading
        do {
            state = .loaded(try await model.session.get(
                ExamsAPI.list,
                as: [ExamView].self
            ))
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}

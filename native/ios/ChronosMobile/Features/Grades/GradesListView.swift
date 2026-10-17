import SwiftUI

struct GradesListView: View {
    @EnvironmentObject var model: AppModel
    @State var state: ListLoadState<GradeView> = .loading

    var body: some View {
        NavigationStack {
            LoadableList(
                state: state,
                emptyIcon: "graduationcap",
                emptyText: "当前学期暂无已发布成绩",
                retry: { Task { await load() } }
            ) { grade in
                HStack {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(grade.courseName).font(.headline)
                        Text(grade.offeringName ?? grade.semesterCode ?? "课程成绩")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 3) {
                        Text(decimalText(grade.totalScore))
                            .font(.title3.bold())
                            .foregroundStyle(grade.passed == false ? .red : ChronosPalette.green)
                        Text(grade.gradeLevel ?? (grade.passed == false ? "未通过" : "已发布"))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .padding(.vertical, 4)
            }
            .navigationTitle("成绩")
            .task { await load() }
            .refreshable { await load() }
        }
    }

    func load() async {
        state = .loading
        do {
            state = .loaded(try await model.session.get(
                GradesAPI.list,
                as: [GradeView].self
            ))
        } catch {
            state = .error(error.localizedDescription)
        }
    }
}

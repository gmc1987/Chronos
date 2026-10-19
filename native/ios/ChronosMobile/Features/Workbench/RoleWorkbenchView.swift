import SwiftUI

struct RoleWorkbenchView: View {
    let bootstrap: PortalBootstrap
    let kind: WorkbenchKind
    @EnvironmentObject var model: AppModel

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 16) {
                    IdentityHeader(
                        eyebrow: title,
                        name: bootstrap.user.displayName ?? bootstrap.user.username,
                        detail: bootstrap.user.positionName ?? todayText,
                        icon: headerIcon
                    )
                    if kind == .teacher {
                        TimetableCard()
                        NavigationLink {
                            InvigilationView()
                        } label: {
                            Label("我的监考", systemImage: "person.badge.clock")
                                .font(.headline)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .cardStyle()
                        }
                        .buttonStyle(.plain)
                        NavigationLink {
                            TeachingSupervisionView()
                        } label: {
                            Label("课堂督导核验", systemImage: "qrcode.viewfinder")
                                .font(.headline)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .cardStyle()
                        }
                        .buttonStyle(.plain)
                    }
                    SummaryContributionCard(
                        title: "我的待办",
                        icon: "checklist",
                        contribution: bootstrap.contributions["WORKFLOW"],
                        emptyMessage: "当前没有待处理事项"
                    )
                    RecentMeetingsCard()
                    if bootstrap.user.permissions.contains("education:supervision:assignment:view") {
                        NavigationLink {
                            SupervisionTasksView()
                        } label: {
                            Label("督导任务办理", systemImage: "checkmark.seal")
                                .font(.headline)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .cardStyle()
                        }
                        .buttonStyle(.plain)
                    }
                    if kind == .management {
                        SummaryContributionCard(
                            title: "督办事项",
                            icon: "exclamationmark.bubble",
                            contribution: bootstrap.contributions["SUPERVISION"],
                            emptyMessage: "当前没有运行中的督办事项"
                        )
                        MetricsCard(contribution: bootstrap.contributions["DATA"])
                    } else {
                        CapabilityGroupCard(
                            title: "协同办公",
                            icon: "person.2.wave.2",
                            applications: collaborationApplications,
                            emptyMessage: "协同办公移动摘要建设中"
                        )
                        if kind == .teacher {
                            CapabilityGroupCard(
                                title: "教学中心",
                                icon: "books.vertical",
                                applications: teachingApplications,
                                emptyMessage: "教学能力将按服务端授权逐步开放"
                            )
                        } else {
                            SummaryContributionCard(
                                title: "公告摘要",
                                icon: "megaphone",
                                contribution: bootstrap.contributions["ANNOUNCEMENT"],
                                emptyMessage: "当前没有可见公告"
                            )
                        }
                    }
                }
                .padding()
            }
            .background(ChronosPalette.background)
            .navigationTitle("工作台")
            .refreshable { await model.reloadBootstrap() }
        }
    }

    var title: String {
        switch kind {
        case .teacher: return "教师工作台"
        case .staff: return "教职工工作台"
        case .management: return "管理工作台"
        case .student: return "学生工作台"
        case .parent: return "家长工作台"
        }
    }

    var headerIcon: String {
        switch kind {
        case .teacher: return "person.crop.rectangle.stack.fill"
        case .management: return "building.columns.fill"
        default: return "person.text.rectangle.fill"
        }
    }

    var collaborationApplications: [PortalApplication] {
        bootstrap.applications.filter {
            appMatches($0, ["请假", "出差", "用车", "用印", "公文", "文件", "会议", "workflow"])
        }
    }

    var teachingApplications: [PortalApplication] {
        bootstrap.applications.filter {
            appMatches($0, ["课件", "题库", "错题", "教学", "education", "course"])
        }
    }
}


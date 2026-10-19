import SwiftUI

struct UnsupportedApplicationView: View {
    let application: PortalApplication
    var body: some View {
        ContentUnavailableView(
            "暂未配置原生功能",
            systemImage: "app.badge.checkmark",
            description: Text("\(application.name) 尚未绑定 nativeFeatureCode，请联系管理员完成应用配置。")
        ).navigationTitle(application.name)
    }
}

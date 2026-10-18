import SwiftUI

struct NativeModuleList: View {
    let title: String, modules: [NativeModule]
    var body: some View {
        List(modules) { module in
            NavigationLink { NativeRecordsView(title: module.title, path: module.path, empty: "暂无\(module.title)数据") } label: {
                Label { VStack(alignment: .leading) { Text(module.title).font(.headline); Text(module.detail).font(.caption).foregroundStyle(.secondary) } }
                icon: { Image(systemName: module.icon).foregroundStyle(ChronosPalette.green) }
            }
        }.navigationTitle(title)
    }
}

import SwiftUI

struct NativeModuleList: View {
    let title: String, modules: [NativeModule]
    var body: some View {
        List(modules) { module in
            NavigationLink { NativeModuleDestinationView(module: module) } label: {
                Label { VStack(alignment: .leading) { Text(module.title).font(.headline); Text(module.detail).font(.caption).foregroundStyle(.secondary) } }
                icon: { Image(systemName: module.icon).foregroundStyle(ChronosPalette.green) }
            }
        }.navigationTitle(title)
    }
}

@ViewBuilder
private func NativeModuleDestinationView(module: NativeModule) -> some View {
    switch module.destination {
    case .records(let path, let empty):
        NativeRecordsView(title: module.title, path: path, empty: empty)
    case .leaves:
        LeaveRequestsView()
    case .businessTrips:
        BusinessTripsView()
    case .officeRequests:
        OfficeRequestsView()
    }
}

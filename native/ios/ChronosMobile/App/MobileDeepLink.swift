import Foundation

enum MobileDeepLink: Identifiable {
    case publication(String)
    case workflow(String)
    case application(String)

    var id: String {
        switch self {
        case .publication(let id): return "publication:\(id)"
        case .workflow(let id): return "workflow:\(id)"
        case .application(let id): return "application:\(id)"
        }
    }

    init?(url: URL) {
        let parts = ([url.host].compactMap { $0 } + url.pathComponents.filter { $0 != "/" })
        guard parts.count >= 2 else { return nil }
        switch parts[0].lowercased() {
        case "publication", "notice", "announcement": self = .publication(parts[1])
        case "workflow", "task": self = .workflow(parts[1])
        case "application", "app": self = .application(parts[1])
        default: return nil
        }
    }
}

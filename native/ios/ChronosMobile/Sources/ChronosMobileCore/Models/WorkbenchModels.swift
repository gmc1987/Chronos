import Foundation

public enum WorkbenchKind: String, Sendable {
    case student
    case teacher
    case staff
    case management
}

public enum WorkbenchResolver {
    public static func resolve(bootstrap: PortalBootstrap) -> WorkbenchKind {
        let profileTypes = Set(
            (bootstrap.contributions["DATA"]?.data?.profileTypes ?? [])
                .map { $0.uppercased() }
        )
        let roles = bootstrap.user.roles.map { $0.uppercased() }
        let permissions = Set(bootstrap.user.permissions.map { $0.lowercased() })

        let managementRoleMarkers = [
            "ADMIN", "SUPER", "LEADER", "PRINCIPAL", "PRESIDENT", "DIRECTOR",
            "MANAGER", "校领导", "管理员", "领导"
        ]
        let isManagementRole = roles.contains { role in
            managementRoleMarkers.contains { role.contains($0) }
        }
        let hasManagementPermission = permissions.contains("portal:manage")
            || permissions.contains("portal:admin:view")
            || permissions.contains { $0.hasPrefix("data:overview:") }

        if isManagementRole || hasManagementPermission {
            return .management
        }
        if profileTypes.contains("STUDENT") {
            return .student
        }
        if profileTypes.contains("TEACHER") {
            return .teacher
        }
        return .staff
    }
}

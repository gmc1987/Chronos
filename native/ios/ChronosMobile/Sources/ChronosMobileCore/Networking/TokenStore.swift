import Foundation
import Security

struct TokenStore {
    private let key = "chronos.mobile.session.tokens"
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> SessionTokens? {
        var data = KeychainTokenStore.load(account: key)
        // Migrate sessions written by older releases from UserDefaults once.
        if data == nil, let legacy = defaults.data(forKey: key) {
            if KeychainTokenStore.save(legacy, account: key) {
                defaults.removeObject(forKey: key)
                data = legacy
            }
        }
        guard let data else { return nil }
        return try? JSONDecoder().decode(SessionTokens.self, from: data)
    }

    func save(_ tokens: SessionTokens) {
        guard let data = try? JSONEncoder().encode(tokens) else {
            return
        }
        _ = KeychainTokenStore.save(data, account: key)
        defaults.removeObject(forKey: key)
    }

    func clear() {
        KeychainTokenStore.delete(account: key)
        defaults.removeObject(forKey: key)
    }
}

private enum KeychainTokenStore {
    private static let service = "com.gmc1987.chronos.mobile.session"

    static func load(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else { return nil }
        return result as? Data
    }

    @discardableResult
    static func save(_ data: Data, account: String) -> Bool {
        delete(account: account)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: data,
        ]
        return SecItemAdd(query as CFDictionary, nil) == errSecSuccess
    }

    static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

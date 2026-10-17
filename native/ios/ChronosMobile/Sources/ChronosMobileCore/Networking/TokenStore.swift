import Foundation

struct TokenStore {
    private let key = "chronos.mobile.session.tokens"
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> SessionTokens? {
        guard let data = defaults.data(forKey: key) else {
            return nil
        }
        return try? JSONDecoder().decode(SessionTokens.self, from: data)
    }

    func save(_ tokens: SessionTokens) {
        guard let data = try? JSONEncoder().encode(tokens) else {
            return
        }
        defaults.set(data, forKey: key)
    }

    func clear() {
        defaults.removeObject(forKey: key)
    }
}

import Foundation
import Shared

/// Swift implementation of the Kotlin-exported `KeyValueStore` protocol — the shared module
/// defines the cache *policy* (PersistedPriceCache), each platform supplies the storage.
final class UserDefaultsKeyValueStore: KeyValueStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func getString(key: String) -> String? {
        defaults.string(forKey: key)
    }

    func putString(key: String, value: String) {
        defaults.set(value, forKey: key)
    }
}

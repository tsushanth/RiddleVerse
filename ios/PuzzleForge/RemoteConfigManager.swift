//
//  RemoteConfigManager.swift
//  PuzzleForge
//

import Foundation
import FirebaseRemoteConfig

class RemoteConfigManager {
    static let shared = RemoteConfigManager()

    private let remoteConfig = RemoteConfig.remoteConfig()

    private init() {
        let settings = RemoteConfigSettings()
        settings.minimumFetchInterval = 3600 // 1 hour cache
        remoteConfig.configSettings = settings

        // Defaults — all feature flags off so review passes cleanly
        remoteConfig.setDefaults([
            "show_telegram_banner": false as NSObject
        ])
    }

    func fetchAndActivate(completion: (() -> Void)? = nil) {
        remoteConfig.fetchAndActivate { _, _ in
            completion?()
        }
    }

    var showTelegramBanner: Bool {
        remoteConfig.configValue(forKey: "show_telegram_banner").boolValue
    }
}

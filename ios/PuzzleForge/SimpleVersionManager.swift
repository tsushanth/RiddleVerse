//
//  AppVersionResponse.swift
//  RiddleVerse
//
//  Created by Sushanth Tiruvaipati on 10/17/25.
//
import SwiftUI

struct AppVersionResponse: Codable {
    let platform: String           // "ios" or "android"
    let latestVersion: String      // "1.5.0"
    let currentUserVersion: String // User's version from request
    let needsUpdate: Bool          // Server-calculated boolean
    let whatsNew: [String]         // Array of feature descriptions
    let appStoreUrl: String        // Direct link to App Store
    let isForced: Bool             // Whether update is mandatory
}

// MARK: - Simple Version Manager
class SimpleVersionManager {
    static let shared = SimpleVersionManager()
    private init() {}
    
    private let hasShownUpdateKey = "has_shown_update_prompt"
    
    /// Get current app version
    var currentVersion: String {
        return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }
    
    /// Check if user has already dismissed update prompt for current version
    func hasShownUpdateForCurrentVersion() -> Bool {
        let savedVersion = UserDefaults.standard.string(forKey: hasShownUpdateKey)
        return savedVersion == currentVersion
    }
    
    /// Mark update prompt as shown
    func markUpdateShown() {
        UserDefaults.standard.set(currentVersion, forKey: hasShownUpdateKey)
    }
    
    /// Check for updates from server
    func checkForUpdate(completion: @escaping (AppVersionResponse?) -> Void) {
        guard let url = URL(string: "https://puzzleverseai.com/api/check-version") else {
            completion(nil)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "platform": "ios",
            "currentVersion": currentVersion,
            "deviceInfo": [
                "model": UIDevice.current.model,
                "osVersion": UIDevice.current.systemVersion
            ]
        ]
        
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            guard let data = data,
                  error == nil,
                  let versionResponse = try? JSONDecoder().decode(AppVersionResponse.self, from: data) else {
                DispatchQueue.main.async {
                    completion(nil)
                }
                return
            }
            
            DispatchQueue.main.async {
                completion(versionResponse)
            }
        }.resume()
    }
}

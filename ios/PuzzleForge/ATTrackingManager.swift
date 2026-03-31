//
//  ATTrackingManager.swift
//  PuzzleForge
//

import AppTrackingTransparency
import AdSupport

class TrackingPermissionManager {
    static let shared = TrackingPermissionManager()
    
    private init() {}
    
    func requestTrackingPermission(completion: @escaping (Bool) -> Void) {
        // Only request on iOS 14+
        if #available(iOS 14, *) {
            ATTrackingManager.requestTrackingAuthorization { status in
                DispatchQueue.main.async {
                    switch status {
                    case .authorized:
                        print("✅ Tracking authorized")
                        completion(true)
                    case .denied, .restricted, .notDetermined:
                        print("❌ Tracking not authorized: \(status.rawValue)")
                        completion(false)
                    @unknown default:
                        completion(false)
                    }
                }
            }
        } else {
            // iOS 13 and below - no ATT needed
            completion(true)
        }
    }
    
    var trackingStatus: ATTrackingManager.AuthorizationStatus {
        if #available(iOS 14, *) {
            return ATTrackingManager.trackingAuthorizationStatus
        }
        return .authorized
    }
    
    var idfa: String {
        if #available(iOS 14, *) {
            if ATTrackingManager.trackingAuthorizationStatus == .authorized {
                return ASIdentifierManager.shared().advertisingIdentifier.uuidString
            }
        }
        return ASIdentifierManager.shared().advertisingIdentifier.uuidString
    }
}

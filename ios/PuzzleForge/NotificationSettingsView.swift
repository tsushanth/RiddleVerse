//
//  NotificationSettingsView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/25/25.
//


//
//  NotificationSettingsView.swift
//  PuzzleForge
//
//  Created by Assistant on 6/25/25.
//

import SwiftUI

struct NotificationSettingsView: View {
    @EnvironmentObject var notificationManager: NotificationManager
    @State private var showingAlert = false
    @State private var alertMessage = ""
    
    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Notification Permissions")) {
                    HStack {
                        Image(systemName: notificationManager.isNotificationPermissionGranted ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .foregroundColor(notificationManager.isNotificationPermissionGranted ? .green : .red)
                        
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Push Notifications")
                                .font(.headline)
                            Text(notificationManager.isNotificationPermissionGranted ? "Enabled" : "Disabled")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        if !notificationManager.isNotificationPermissionGranted {
                            Button("Enable") {
                                notificationManager.requestNotificationPermission()
                            }
                            .buttonStyle(.borderedProminent)
                            .font(.caption)
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                Section(header: Text("Daily Puzzle Notifications")) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: "bell.badge")
                                .foregroundColor(.blue)
                            Text("Daily Puzzle Alerts")
                                .font(.headline)
                        }
                        
                        Text("Get notified when your daily puzzles are ready")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        if notificationManager.isNotificationPermissionGranted {
                            Text("✅ You'll receive notifications for new daily puzzles")
                                .font(.caption)
                                .foregroundColor(.green)
                        } else {
                            Text("⚠️ Enable notifications above to receive daily puzzle alerts")
                                .font(.caption)
                                .foregroundColor(.orange)
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                Section(header: Text("Debug & Testing")) {
                    VStack(alignment: .leading, spacing: 12) {
                        if let token = notificationManager.fcmToken {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("FCM Token Status")
                                    .font(.headline)
                                Text("✅ Token registered")
                                    .font(.caption)
                                    .foregroundColor(.green)
                                Text("Token: \(String(token.prefix(20)))...")
                                    .font(.system(.caption, design: .monospaced))
                                    .foregroundColor(.secondary)
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("FCM Token Status")
                                    .font(.headline)
                                Text("⚠️ No token available")
                                    .font(.caption)
                                    .foregroundColor(.orange)
                            }
                        }
                        
                        Button("Test Notification") {
                            notificationManager.sendTestNotification()
                            alertMessage = "Test notification sent! Check your notifications."
                            showingAlert = true
                        }
                        .buttonStyle(.bordered)
                        .disabled(!notificationManager.isNotificationPermissionGranted)
                        
                        Button("Refresh FCM Token") {
                            notificationManager.getFCMToken()
                            alertMessage = "FCM token refresh requested"
                            showingAlert = true
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding(.vertical, 4)
                }
                
                Section(footer: Text("Notifications help you stay updated with new daily puzzles and challenges. You can disable them in iOS Settings at any time.")) {
                    Button("Open iOS Settings") {
                        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(settingsUrl)
                        }
                    }
                    .foregroundColor(.blue)
                }
            }
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.large)
        }
        .alert("Notification Test", isPresented: $showingAlert) {
            Button("OK") { }
        } message: {
            Text(alertMessage)
        }
        .onAppear {
            // Refresh notification status when view appears
            notificationManager.getFCMToken()
        }
    }
}

struct NotificationSettingsView_Previews: PreviewProvider {
    static var previews: some View {
        NotificationSettingsView()
            .environmentObject(NotificationManager.shared)
    }
}
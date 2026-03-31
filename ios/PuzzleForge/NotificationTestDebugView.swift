//
//  NotificationTestDebugView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/25/25.
//


//
//  NotificationTestDebugView.swift
//  PuzzleForge
//
//  Created for testing Firebase notifications
//

import SwiftUI
import FirebaseMessaging

struct NotificationTestDebugView: View {
    @EnvironmentObject var notificationManager: NotificationManager
    @State private var testResults: [String] = []
    @State private var testEmail = "test@example.com"
    @State private var isRunningTests = false
    
    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Current Status")) {
                    StatusRow(
                        title: "Notification Permission",
                        value: notificationManager.isNotificationPermissionGranted ? "✅ Granted" : "❌ Denied",
                        isGood: notificationManager.isNotificationPermissionGranted
                    )
                    
                    StatusRow(
                        title: "FCM Token",
                        value: notificationManager.fcmToken != nil ? "✅ Available" : "❌ Missing",
                        isGood: notificationManager.fcmToken != nil
                    )
                    
                    if let token = notificationManager.fcmToken {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Token Preview:")
                                .font(.caption)
                                .fontWeight(.semibold)
                            Text(String(token.prefix(50)) + "...")
                                .font(.system(.caption, design: .monospaced))
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    StatusRow(
                        title: "User Email",
                        value: getCurrentUserEmail() ?? "Not signed in",
                        isGood: getCurrentUserEmail() != nil
                    )
                }
                
                Section(header: Text("Quick Tests")) {
                    VStack(spacing: 12) {
                        Button("1. Request Permission") {
                            notificationManager.requestNotificationPermission()
                            addTestResult("🔔 Requested notification permission")
                        }
                        .buttonStyle(.bordered)
                        .disabled(notificationManager.isNotificationPermissionGranted)
                        
                        Button("2. Get FCM Token") {
                            notificationManager.getFCMToken()
                            addTestResult("🔑 Requested FCM token")
                        }
                        .buttonStyle(.bordered)
                        
                        Button("3. Register with Server") {
                            testServerRegistration()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(notificationManager.fcmToken == nil || getCurrentUserEmail() == nil)
                        
                        Button("4. Send Test Notification") {
                            testServerNotification()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(getCurrentUserEmail() == nil)
                        
                        Button("5. Schedule Local Test") {
                            scheduleLocalTestNotification()
                        }
                        .buttonStyle(.bordered)
                    }
                    .frame(maxWidth: .infinity)
                }
                
                Section(header: Text("Test User Email")) {
                    HStack {
                        TextField("Enter test email", text: $testEmail)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                        
                        Button("Use Current") {
                            if let email = getCurrentUserEmail() {
                                testEmail = email
                            }
                        }
                        .font(.caption)
                    }
                }
                
                Section(header: Text("Test Results")) {
                    if testResults.isEmpty {
                        Text("No tests run yet")
                            .foregroundColor(.secondary)
                            .italic()
                    } else {
                        ForEach(testResults.indices, id: \.self) { index in
                            Text(testResults[index])
                                .font(.caption)
                                .padding(.vertical, 2)
                        }
                    }
                    
                    if !testResults.isEmpty {
                        Button("Clear Results") {
                            testResults.removeAll()
                        }
                        .font(.caption)
                        .foregroundColor(.red)
                    }
                }
                
                Section(header: Text("Advanced Tests")) {
                    Button("Run Full Test Suite") {
                        runFullTestSuite()
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isRunningTests)
                    
                    if isRunningTests {
                        HStack {
                            ProgressView()
                                .scaleEffect(0.8)
                            Text("Running tests...")
                                .font(.caption)
                        }
                    }
                }
            }
            .navigationTitle("Notification Tests")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Refresh") {
                        notificationManager.getFCMToken()
                        addTestResult("🔄 Refreshed FCM token")
                    }
                }
            }
        }
    }
    
    // MARK: - Helper Methods
    private func addTestResult(_ message: String) {
        let timestamp = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
        testResults.append("[\(timestamp)] \(message)")
    }
    
    private func getCurrentUserEmail() -> String? {
        return UserDefaults.standard.string(forKey: "current_user_email")
    }
    
    private func testServerRegistration() {
        guard let userEmail = getCurrentUserEmail(),
              let fcmToken = notificationManager.fcmToken else {
            addTestResult("❌ Missing email or FCM token")
            return
        }
        
        addTestResult("📡 Testing server registration...")
        
        guard let url = URL(string: "https://puzzleverseai.com/register-fcm-token") else {
            addTestResult("❌ Invalid server URL")
            return
        }
        
        let payload = [
            "userEmail": userEmail,
            "fcmToken": fcmToken,
            "platform": "ios"
        ]
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            addTestResult("❌ Failed to encode payload: \(error)")
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    addTestResult("❌ Server registration failed: \(error.localizedDescription)")
                    return
                }
                
                if let httpResponse = response as? HTTPURLResponse {
                    if httpResponse.statusCode == 200 {
                        addTestResult("✅ Server registration successful!")
                    } else {
                        addTestResult("❌ Server registration failed with status \(httpResponse.statusCode)")
                    }
                }
            }
        }.resume()
    }
    
    private func testServerNotification() {
        addTestResult("📱 Sending test notification via server...")
        
        guard let url = URL(string: "https://puzzleverseai.com/test-notification") else {
            addTestResult("❌ Invalid test URL")
            return
        }
        
        let payload = [
            "userEmail": testEmail,
            "title": "🧪 iOS Test Notification",
            "body": "This is a test notification from RiddleVerse iOS app!"
        ]
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            addTestResult("❌ Failed to encode test payload: \(error)")
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    addTestResult("❌ Test notification failed: \(error.localizedDescription)")
                    return
                }
                
                if let httpResponse = response as? HTTPURLResponse {
                    addTestResult("📡 Test notification response: \(httpResponse.statusCode)")
                    
                    if let data = data,
                       let responseString = String(data: data, encoding: .utf8) {
                        addTestResult("📄 Response: \(responseString)")
                    }
                }
            }
        }.resume()
    }
    
    private func scheduleLocalTestNotification() {
        let content = UNMutableNotificationContent()
        content.title = "🧪 Local Test Notification"
        content.body = "This is a local test notification to verify iOS notification display works"
        content.sound = .default
        content.badge = 1
        
        content.userInfo = [
            "type": "test",
            "source": "local",
            "timestamp": Date().timeIntervalSince1970
        ]
        
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
        let request = UNNotificationRequest(identifier: "local_test", content: content, trigger: trigger)
        
        UNUserNotificationCenter.current().add(request) { error in
            DispatchQueue.main.async {
                if let error = error {
                    addTestResult("❌ Failed to schedule local notification: \(error)")
                } else {
                    addTestResult("✅ Local test notification scheduled for 3 seconds")
                }
            }
        }
    }
    
    private func runFullTestSuite() {
        isRunningTests = true
        addTestResult("🚀 Starting full test suite...")
        
        // Test 1: Check permissions
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if notificationManager.isNotificationPermissionGranted {
                addTestResult("✅ Test 1/5: Permissions granted")
            } else {
                addTestResult("❌ Test 1/5: Permissions not granted")
            }
            
            // Test 2: Check FCM token
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                if notificationManager.fcmToken != nil {
                    addTestResult("✅ Test 2/5: FCM token available")
                } else {
                    addTestResult("❌ Test 2/5: FCM token missing")
                }
                
                // Test 3: Check user authentication
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    if getCurrentUserEmail() != nil {
                        addTestResult("✅ Test 3/5: User authenticated")
                    } else {
                        addTestResult("❌ Test 3/5: User not authenticated")
                    }
                    
                    // Test 4: Server registration
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        testServerRegistration()
                        
                        // Test 5: Local notification
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                            scheduleLocalTestNotification()
                            
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                                addTestResult("🏁 Test suite completed!")
                                isRunningTests = false
                            }
                        }
                    }
                }
            }
        }
    }
}

struct StatusRow: View {
    let title: String
    let value: String
    let isGood: Bool
    
    var body: some View {
        HStack {
            Text(title)
                .fontWeight(.medium)
            Spacer()
            Text(value)
                .foregroundColor(isGood ? .green : .red)
                .fontWeight(.semibold)
        }
    }
}

struct NotificationTestDebugView_Previews: PreviewProvider {
    static var previews: some View {
        NotificationTestDebugView()
            .environmentObject(NotificationManager.shared)
    }
}
//
//  QAPuzzleViewModel.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/25/25.
//
import SwiftUI
import Firebase
import FirebaseAuth
import GoogleSignIn
import AuthenticationServices
import CryptoKit
import FirebaseFirestore
import UserNotifications

class QAPuzzleViewModel: ObservableObject {
    var currentNonce: String?
    @Published var isSignedIn = false
    @Published var userEmail = ""
    @Published var userId: String = "" // Ensure user ID is tracked
    @Published var currentPuzzle: Puzzle? // Make it optional
    @Published var selectedPuzzle: Puzzle?
    @Published var currentPuzzleSet: OuterPuzzleData?
    @Published var userAnswer: String = ""
    @Published var feedbackMessage: String = ""
    @Published var isCorrect: Bool = false
    @Published var showFeedback: Bool = false
    @Published var isLoading: Bool = false
    @Published var currentQuestionIndex: Int = 1
    @Published var totalQuestions: Int = 10
    @Published var timerValue: Int = 13
    @Published var allPuzzlesCompleted: Bool = false
    
    private var timer: Timer?
    
    init () {
        self.currentPuzzle = Puzzle.empty
        self.currentQuestionIndex = 0
        self.currentPuzzleSet = nil
        self.totalQuestions = 0
    }
    
    init(puzzle: Puzzle, questionIndex: Int = 1, totalQuestions: Int = 1, puzzleSet: OuterPuzzleData? = nil) {
            self.currentPuzzle = puzzle
            self.currentQuestionIndex = questionIndex
            self.currentPuzzleSet = puzzleSet
        self.totalQuestions = puzzleSet?.puzzleData.puzzles.count ?? totalQuestions
            startTimer()
    }
    
    deinit {
        stopTimer()
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            if self.timerValue > 0 {
                self.timerValue -= 1
            } else {
                // Time's up logic
                self.stopTimer()
            }
        }
    }
    
    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
    
    func checkAnswer(userAnswer: String, completion: @escaping (Bool) -> Void) {
        guard !userAnswer.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        
        self.userAnswer = userAnswer
        isLoading = true
        
        let url = URL(string: "https://puzzleverseai.com/api/puzzles/check-answer")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "question": currentPuzzle?.question,
            "expected_answer": currentPuzzle?.answer,
            "guessed_answer": userAnswer,
            "puzzleType": currentPuzzle?.puzzleType ?? "custom",
            "modelName": "gpt-4"
        ]
        
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request) { data, _, _ in
            DispatchQueue.main.async {
                self.isLoading = false
            }
            
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let correct = json["correct"] as? Bool else {
                return
            }
            
            DispatchQueue.main.async {
                self.isCorrect = correct
                self.feedbackMessage = correct ? "Correct!" : "Incorrect. Correct answer: \(self.currentPuzzle?.answer)"
                self.showFeedback = true
                completion(correct)
            }
        }.resume()
    }
    
    func checkAuthStatus() {
        if let user = Auth.auth().currentUser {
            isSignedIn = true
            userEmail = user.email ?? "Unknown"
            userId = user.uid
            UserDefaults.standard.set(userEmail, forKey: "current_user_email")
            NotificationManager.shared.onUserSignIn(userEmail: userEmail)
            print("✅ User signed in. User ID: \(userId)")
        } else {
            isSignedIn = false
            userEmail = ""
            userId = ""
            UserDefaults.standard.removeObject(forKey: "current_user_email")
            print("❌ No user signed in.")
        }
    }

    
    func loadNextPuzzle() {
        // Reset answer and feedback state
        userAnswer = ""
        isCorrect = false
        showFeedback = false
        if let puzzleSet = currentPuzzleSet {
                loadNextCustomPuzzle()
            } else {
                loadRandomPuzzle()
            }
    }
    
    func handleAppleSignIn(result: Result<ASAuthorization, Error>, completion: @escaping (Bool, String?, String?) -> Void) {
        switch result {
        case .success(let authorization):
            if let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential {
                guard let identityToken = appleIDCredential.identityToken,
                      let tokenString = String(data: identityToken, encoding: .utf8) else {
                    completion(false, "APPLE_TOKEN_NIL", nil)
                    return
                }

                guard let nonce = currentNonce else {
                    completion(false, "NONCE_NIL", nil)
                    return
                }

                let credential = OAuthProvider.credential(
                    withProviderID: "apple.com",
                    idToken: tokenString,
                    rawNonce: nonce
                )

                Auth.auth().signIn(with: credential) { authResult, error in
                    if let error = error {
                        DispatchQueue.main.async {
                            completion(false, "FIREBASE: \(error.localizedDescription)", nil)
                        }
                        return
                    }
                    DispatchQueue.main.async {
                        let userEmail = Auth.auth().currentUser?.email ?? "Unknown"
                        let userId = Auth.auth().currentUser?.uid ?? "Unknown"

                        self.isSignedIn = true
                        self.userEmail = userEmail
                        self.userId = userId

                        UserDefaults.standard.set(userEmail, forKey: "current_user_email")
                        NotificationManager.shared.onUserSignIn(userEmail: userEmail)
                        completion(true, userEmail, userId)
                    }
                }
            } else {
                completion(false, "CREDENTIAL_TYPE: \(type(of: authorization.credential))", nil)
            }
        case .failure(let error):
            completion(false, "APPLE_ERROR: \(error.localizedDescription)", nil)
        }
    }
    
    func updateScore(by points: Int) {
        let updatedScore = UserDefaults.standard.integer(forKey: "userScore") + points
        UserDefaults.standard.set(updatedScore, forKey: "userScore")

        let body: [String: Any] = [
            "userId": userId,
            "name": userEmail,
            "score": updatedScore
        ]

        var request = URLRequest(url: URL(string: "https://puzzleverseai.com/update-score")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request).resume()
    }


    func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashedData = SHA256.hash(data: inputData)
        return hashedData.compactMap { String(format: "%02x", $0) }.joined()
    }
    
    func signUpWithEmail(email: String, password: String, completion: @escaping (Bool, String?) -> Void) {
        Auth.auth().createUser(withEmail: email, password: password) { authResult, error in
            if let error = error {
                completion(false, error.localizedDescription)
            } else {
                self.isSignedIn = true
                self.userEmail = authResult?.user.email ?? "Unknown"
                UserDefaults.standard.set(self.userEmail, forKey: "current_user_email")
                NotificationManager.shared.onUserSignIn(userEmail: self.userEmail)
                completion(true, nil)
            }
        }
    }

    
    func loadRandomPuzzle() {
        guard let type = currentPuzzle?.puzzleType,
              let url = URL(string: "https://puzzleverseai.com/fetch-random-puzzle-ios/\(type)") else { return }
        
        isLoading = true
        
        URLSession.shared.dataTask(with: url) { data, _, _ in
            DispatchQueue.main.async { self.isLoading = false }
            
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let puzzleData = json["puzzleData"] as? [String: Any] else { return }
            
            let nextPuzzle = Puzzle(
                question: puzzleData["question"] as? String ?? "",
                answer: puzzleData["answer"] as? String ?? "",
                hint: puzzleData["hint"] as? String ?? "",
                options: [],
                format: puzzleData["puzzleType"] as? String ?? "qa",
                puzzleType: puzzleData["puzzleType"] as? String,
                puzzleId: puzzleData["puzzleId"] as? String ?? UUID().uuidString,
                id: UUID().uuidString,
                name: "Next",
                createdAt: Date().timeIntervalSince1970 * 1000,
                status: "ready",
                difficulty: puzzleData["difficulty"] as? String ?? "Easy"
            )
            
            DispatchQueue.main.async {
                self.currentPuzzle = nextPuzzle
                self.userAnswer = ""
                self.currentQuestionIndex += 1
                self.timerValue = 13 // Reset timer for new question
                self.startTimer()
                
                // Ensure we clear any residual puzzle set data
                self.currentPuzzleSet = nil
            }
        }.resume()
    }
    
    func loadNextCustomPuzzle() {
        print("🔄 Attempting to load next custom puzzle...")
        print("📊 Current state before update:")
        print("- Question Index: \(currentQuestionIndex)")
        // Use the actual count from the loaded set for totalQuestions
        print("- Total Questions (from set): \(currentPuzzleSet?.puzzleData.puzzles.count ?? 0)")
        print("- All Puzzles Completed Flag: \(allPuzzlesCompleted)")

        // 1. Ensure a puzzle set is loaded
        guard let puzzleSet = currentPuzzleSet else {
            print("❌ No puzzle set available. Marking as completed.")
            allPuzzlesCompleted = true
            // Optionally, reset currentPuzzle to nil if you want the UI to clear
            currentPuzzle = nil
            return
        }

        // 2. Increment the question index for the *next* puzzle
        // We increment it first, because `currentQuestionIndex` should represent
        // the index of the puzzle we *are about to load*.
        currentQuestionIndex += 1

        // 3. Check if the next index is within the bounds of the puzzles array
        // Remember: arrays are 0-indexed, so `count` is one greater than the last valid index.
        if currentQuestionIndex < puzzleSet.puzzleData.puzzles.count {
            print("✅ Loading puzzle at 0-based index \(currentQuestionIndex)")
            let apiPuzzle = puzzleSet.puzzleData.puzzles[currentQuestionIndex]

            print("🧩 Details of API puzzle to load:")
            print("- Question: \(apiPuzzle.question)")
            print("- Answer: \(apiPuzzle.answer)")
            print("- Options: \(apiPuzzle.options)")
            print("- Hint: \(apiPuzzle.hint ?? "N/A")") // Handle optional hint

            // 4. Convert the ApiPuzzle to your app's Puzzle model
            // Use the dedicated initializer that correctly maps properties and handles types.
            currentPuzzle = Puzzle(apiPuzzle: apiPuzzle, outerPuzzleData: puzzleSet)

            // 5. Update UI-related state (if this method is in a ViewModel)
            // Reset timer, start it, etc.
            timerValue = 30 // Assuming you want to reset to 30, not 13.
            startTimer()

            // 6. Log the new state for debugging
            print("🆕 New state after loading puzzle:")
            print("- New Question Index: \(currentQuestionIndex)")
            print("- Current Puzzle Question: \(currentPuzzle?.question ?? "N/A")")
            // Update totalQuestions if you have a separate @State property for it
            totalQuestions = puzzleSet.puzzleData.puzzles.count // Update total questions
            allPuzzlesCompleted = false // Ensure this is false if we're still playing
        } else {
            // 7. If the index is out of bounds, it means all puzzles are completed
            print("🏁 Reached end of puzzle set. No more puzzles to load.")
            allPuzzlesCompleted = true
            currentQuestionIndex = 0 // Reset index for a new round/set
            currentPuzzle = nil // Clear the current puzzle as the set is finished
            // Stop any active timers
            // stopTimer() // Assuming you have a stopTimer method
        }
    }
    
    func signInWithEmail(email: String, password: String, completion: @escaping (Bool, String?) -> Void) {
        Auth.auth().signIn(withEmail: email, password: password) { authResult, error in
            if let error = error {
                completion(false, error.localizedDescription)
            } else {
                self.isSignedIn = true
                self.userEmail = authResult?.user.email ?? "Unknown"
                UserDefaults.standard.set(self.userEmail, forKey: "current_user_email")
                NotificationManager.shared.onUserSignIn(userEmail: self.userEmail)
                completion(true, nil)
            }
        }
    }

    func signInWithGoogle(completion: @escaping (Bool, String?, String?) -> Void) {
        guard let rootViewController = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow })?.rootViewController else {
            print("❌ Error: No root view controller found")
            completion(false, nil, nil)
            return
        }

        GIDSignIn.sharedInstance.signIn(withPresenting: rootViewController) { signInResult, error in
            if let error = error {
                print("❌ Google Sign-In failed: \(error.localizedDescription)")
                completion(false, nil, nil)
                return
            }

            guard let user = signInResult?.user,
                  let idToken = user.idToken?.tokenString else {
                print("❌ Error: Missing Google auth token")
                completion(false, nil, nil)
                return
            }

            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: user.accessToken.tokenString)

            Auth.auth().signIn(with: credential) { authResult, error in
                if let error = error {
                    print("❌ Firebase Sign-In failed: \(error.localizedDescription)")
                    completion(false, nil, nil)
                    return
                }

                DispatchQueue.main.async {
                    let userEmail = Auth.auth().currentUser?.email ?? "Unknown"
                    let userId = Auth.auth().currentUser?.uid ?? "Unknown"
                    
                    self.isSignedIn = true
                    self.userEmail = userEmail
                    self.userId = userId
                    
                    // Notification integration
                    UserDefaults.standard.set(userEmail, forKey: "current_user_email")
                    NotificationManager.shared.onUserSignIn(userEmail: userEmail)
                    
                    self.objectWillChange.send()
                    print("✅ Google Sign-In Successful! User ID: \(userId)")
                    
                    completion(true, userEmail, userId)
                }
            }
        }
    }
    
    func handleSuccessfulGoogleSignIn(userEmail: String, userId: String) {
            // Store user credentials
            self.userEmail = userEmail
            self.userId = userId
            self.isSignedIn = true
            
            // Update stored email for notifications
            UserDefaults.standard.set(userEmail, forKey: "current_user_email")
            
            // Register FCM token with server
            NotificationManager.shared.onUserSignIn(userEmail: userEmail)
            
            // Track sign-in event with notification status
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("user_sign_in", parameters: [
                "method": "google",
                "user_id": userId,
                "has_notification_permission": NotificationManager.shared.isNotificationPermissionGranted
            ]))
            
            print("✅ Google sign-in successful with notification registration for: \(userEmail)")
        }
        
        /// Handle successful Apple sign-in with notification integration
        func handleSuccessfulAppleSignIn(userEmail: String, userId: String) {
            // Store user credentials
            self.userEmail = userEmail
            self.userId = userId
            self.isSignedIn = true
            
            // Update stored email for notifications
            UserDefaults.standard.set(userEmail, forKey: "current_user_email")
            
            // Register FCM token with server
            NotificationManager.shared.onUserSignIn(userEmail: userEmail)
            
            // Track sign-in event
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("user_sign_in", parameters: [
                "method": "apple",
                "user_id": userId,
                "has_notification_permission": NotificationManager.shared.isNotificationPermissionGranted
            ]))
            
            print("✅ Apple sign-in successful with notification registration for: \(userEmail)")
        }
        
        /// Handle successful email sign-in with notification integration
        func handleSuccessfulEmailSignIn(userEmail: String, userId: String) {
            // Store user credentials
            self.userEmail = userEmail
            self.userId = userId
            self.isSignedIn = true
            
            // Update stored email for notifications
            UserDefaults.standard.set(userEmail, forKey: "current_user_email")
            
            // Register FCM token with server
            NotificationManager.shared.onUserSignIn(userEmail: userEmail)
            
            // Track sign-in event
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("user_sign_in", parameters: [
                "method": "email",
                "user_id": userId,
                "has_notification_permission": NotificationManager.shared.isNotificationPermissionGranted
            ]))
            
            print("✅ Email sign-in successful with notification registration for: \(userEmail)")
        }
        
        /// Setup daily puzzle notifications for the user
        func setupDailyPuzzleNotifications() {
            guard !userEmail.isEmpty else {
                print("⚠️ Cannot setup daily puzzle notifications - user not signed in")
                return
            }
            
            // This would typically involve setting up user topics on your server
            // For now, we'll just ensure the FCM token is registered
            NotificationManager.shared.onUserSignIn(userEmail: userEmail)
            
            print("📅 Daily puzzle notifications setup for: \(userEmail)")
        }
        
        /// Request notification permission if needed
        func requestNotificationPermissionIfNeeded() {
            if !NotificationManager.shared.isNotificationPermissionGranted {
                NotificationManager.shared.requestNotificationPermission()
                
                // Track permission request
                AnalyticsManager.shared.trackSafely(AnalyticsEvent("notification_permission_requested", parameters: [
                    "trigger": "post_sign_in",
                    "user_signed_in": !self.userEmail.isEmpty
                ]))
            }
        }
    
    func generateNonce() -> String {
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")
        var result = ""
        for _ in 0..<32 {
            result.append(charset.randomElement()!)
        }
        return result
    }

    func signOut() {
        do {
            try Auth.auth().signOut()
            DispatchQueue.main.async {
                self.isSignedIn = false
                self.userEmail = ""
                self.userId = ""
                UserDefaults.standard.removeObject(forKey: "current_user_email")
                NotificationManager.shared.onUserSignOut()
            }
        } catch {
            print("❌ Sign-out failed: \(error.localizedDescription)")
        }
    }
}

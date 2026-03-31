//
//  QAPuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import AVFoundation
import SwiftUI

struct QAPuzzleView: View {
    @StateObject var viewModel: QAPuzzleViewModel
    @Environment(\.presentationMode) var presentationMode
    @State private var userAnswer: String = ""
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var isCorrect = false
    @State private var keyboardHeight: CGFloat = 0
    @State private var showHint = false
    @State private var showLeaderboard = false
    @State private var showShareDialog = false
    let puzzleId: String
    @State private var showConfetti = false
    @State private var animateCorrect = false
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    
    let leaderboardData: [LeaderboardEntry]?
    @State private var audioPlayer: AVAudioPlayer?
    
    var completionView: some View {
            VStack(spacing: 20) {
                Text("All Puzzles Completed!")
                    .font(.title)
                
                // Show leaderboard button if we have data
                if let leaderboard = leaderboardData, !leaderboard.isEmpty {
                    Button {
                        showLeaderboard = true
                    } label: {
                        Label("View Leaderboard", systemImage: "trophy.fill")
                            .padding()
                            .background(Color.purple)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                }
                
                Button("Back to Home") {
                    presentationMode.wrappedValue.dismiss()
                }
                .padding()
            }
            .sheet(isPresented: $showLeaderboard) {
                if let leaderboard = leaderboardData {
                    LeaderboardView(entries: leaderboard)
                        .presentationDetents([.medium, .large])
                }
            }
    }
    

    private func sharePuzzle(email: String, name: String, sender: String) {
        guard let url = URL(string: "https://puzzleverseai.com/share-riddle") else { return }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let payload: [String: Any] = [
            "puzzleId": puzzleId,
            "recipientEmail": email,
            "recipientName": name,
            "senderId": sender
        ]
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
            
            URLSession.shared.dataTask(with: request) { _, response, error in
                if let error = error {
                    print("❌ Failed to share puzzle:", error.localizedDescription)
                    return
                }
                
                if let httpResponse = response as? HTTPURLResponse {
                    if httpResponse.statusCode == 200 {
                        print("✅ Puzzle shared successfully")
                    } else {
                        print("❌ Share failed with status code:", httpResponse.statusCode)
                    }
                }
            }.resume()
        } catch {
            print("❌ Failed to encode share payload:", error.localizedDescription)
        }
    }

    var body: some View {
        // Top-level Group to ensure type consistency for the two main branches
        // (completionView vs. main puzzle view)
        Group {
            if viewModel.allPuzzlesCompleted {
                // This completionView should be a well-defined View that always returns a consistent type.
                // Ensure this is a `VStack`, `ZStack`, or similar, not just fragmented views.
                completionView
                    // Attach any full-screen overlays related to completion here if needed.
            } else {
                // Main puzzle playing view
                // Safely unwrap currentPuzzle here as many subviews depend on it.
                // If currentPuzzle can genuinely be nil while allPuzzlesCompleted is false,
                // you might want to show a loading indicator or an error.
                if let currentPuzzle = viewModel.currentPuzzle {
                    // Use a ZStack to layer content, especially for the feedback overlay
                    ZStack {
                        GeometryReader { geometry in
                            ScrollViewReader { scrollProxy in
                                ScrollView {
                                    VStack(spacing: 24) {
                                        HStack {
                                            Spacer()

                                            // Share Button
                                            Button {
                                                showShareDialog = true
                                            } label: {
                                                Image(systemName: "square.and.arrow.up")
                                                    .font(.system(size: 20))
                                                    .foregroundColor(.blue)
                                            }
                                            // Conditional padding based on leaderboard presence
                                            .padding(.trailing, leaderboardData?.isEmpty == false ? 8 : 0)

                                            // Leaderboard Button (only show if we have data)
                                            // Ensure leaderboardData is accessible and its type is correct (e.g., [LeaderboardEntry]?)
                                            if let leaderboard = leaderboardData, !leaderboard.isEmpty {
                                                Button {
                                                    showLeaderboard = true
                                                } label: {
                                                    Image(systemName: "trophy.fill")
                                                        .font(.system(size: 20))
                                                        .foregroundColor(.purple)
                                                }
                                                .padding(.trailing, 8)
                                            }
                                        }
                                        .padding(.top, 8) // Padding for the top row of buttons

                                        // Title + Timer
                                        VStack(spacing: 8) {
                                            // Display 1-based question number for user
                                            Text("Question \(viewModel.currentQuestionIndex + 1)/\(viewModel.totalQuestions)")
                                                .font(.headline)

                                            ZStack(alignment: .leading) {
                                                Capsule()
                                                    .fill(Color.purple.opacity(0.2))
                                                    .frame(height: 20)

                                                Capsule()
                                                    .fill(Color.purple)
                                                    // Adjust width calculation. If 20 is max timer, then width is (timerValue / 20) * fullWidth
                                                    // Assuming a max timer of 30, and you want it to fill a certain width (e.g., 300)
                                                    .frame(width: CGFloat(viewModel.timerValue) / 30.0 * 300, height: 20) // Use 30.0 for float division
                                                    .animation(.linear, value: viewModel.timerValue)

                                                HStack {
                                                    Spacer()
                                                    Label("\(viewModel.timerValue < 10 ? "0" : "")\(viewModel.timerValue)", systemImage: "timer")
                                                        .foregroundColor(.white)
                                                        .padding(.trailing, 10)
                                                }
                                            }
                                            .frame(height: 20)
                                            .padding(.horizontal)
                                        }

                                        // Question Card
                                        VStack(spacing: 6) {
                                            // You already have a question number display above.
                                            // This might be redundant or you might want to combine them.
                                            // Text("Question \(viewModel.currentQuestionIndex)/\(viewModel.totalQuestions)")
                                            //     .font(.subheadline)
                                            //     .foregroundColor(.white.opacity(0.8))

                                            Text(currentPuzzle.question) // Safely unwrapped `currentPuzzle`
                                                .font(.title3)
                                                .fontWeight(.semibold)
                                                .multilineTextAlignment(.center)
                                                .foregroundColor(.white)
                                        }
                                        .padding()
                                        .background(RoundedRectangle(cornerRadius: 20).fill(Color.purple))
                                        .padding(.horizontal)

                                        // Answer Input
                                        TextField("Type your answer here", text: $userAnswer)
                                            .disabled(viewModel.isLoading || showFeedback)
                                            .textFieldStyle(PlainTextFieldStyle())
                                            .padding()
                                            .background(RoundedRectangle(cornerRadius: 20).stroke(Color.purple, lineWidth: 2))
                                            .padding(.horizontal)
                                            .id("answerField")
                                            .onTapGesture {
                                                // Scroll to button when text field is tapped (keyboard likely appears)
                                                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                                    withAnimation {
                                                        scrollProxy.scrollTo("continueButton", anchor: .bottom)
                                                    }
                                                }
                                            }

                                        // Continue Button
                                        Button("Continue") {
                                            checkAnswer() // This should set `isCorrect`, `feedbackMessage`, and `showFeedback`
                                        }
                                        .disabled(viewModel.isLoading || showFeedback)
                                        .frame(maxWidth: .infinity)
                                        .padding()
                                        .background(Color.orange)
                                        .foregroundColor(.white)
                                        .cornerRadius(25)
                                        .padding(.horizontal)
                                        .id("continueButton")

                                        // Hint Button
                                        Button(action: {
                                            showHint = true
                                        }) {
                                            HStack {
                                                Image(systemName: "lightbulb.fill")
                                                Text("Hint")
                                            }
                                            .padding(.horizontal)
                                            .padding(.vertical, 8)
                                            .background(RoundedRectangle(cornerRadius: 20).stroke(Color.orange))
                                            .foregroundColor(.orange) // Set foreground color for the hint button text/icon
                                        }

                                        // Dynamic spacer based on keyboard state
                                        Color.clear
                                            .frame(height: keyboardHeight > 0 ? keyboardHeight + 20 : 20)
                                            .id("bottomSpacer")
                                    }
                                    .padding(.top, 20)
                                    .padding(.bottom, keyboardHeight > 0 ? keyboardHeight + 20 : 20)
                                    .frame(minHeight: geometry.size.height) // Ensure ScrollView takes full height
                                    .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { notification in
                                        if let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
                                            keyboardHeight = keyboardFrame.height
                                            // Scroll to button when keyboard appears
                                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                                                withAnimation {
                                                    scrollProxy.scrollTo("continueButton", anchor: .bottom)
                                                }
                                            }
                                        }
                                    }
                                    .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                                        keyboardHeight = 0
                                    }
                                }
                            } // End ScrollViewReader

                        } // End GeometryReader

                        // Confetti View as an overlay on the entire ZStack
                        if showConfetti {
                            ConfettiView()
                                .allowsHitTesting(false) // Allow taps to pass through to views below
                                .frame(maxWidth: .infinity, maxHeight: .infinity) // Make it fill the ZStack
                                .zIndex(1) // Ensure it's on top of other content
                        }

                        // MARK: - Feedback Overlay
                        // This overlay should be at the ZStack level to cover the entire puzzle view
                        if showFeedback {
                            Color.black.opacity(0.4)
                                .edgesIgnoringSafeArea(.all)
                                .onTapGesture {
                                    // Allow tapping anywhere to dismiss feedback and load next puzzle
                                    showFeedback = false
                                    viewModel.loadNextPuzzle() // This should transition to the next puzzle
                                    userAnswer = ""
                                    isCorrect = false // Reset feedback state
                                    feedbackMessage = ""
                                    showConfetti = false // Ensure confetti is reset
                                }

                            VStack {
                                Text(isCorrect ? "Correct! 🎉" : "Incorrect")
                                    .font(.title)
                                    .foregroundColor(.white)
                                    .padding()
                                    .background(isCorrect ? Color.green : Color.red)
                                    .cornerRadius(10)

                                if !isCorrect {
                                    Text("Correct answer: \(currentPuzzle.answer)") // Safely unwrapped `currentPuzzle`
                                        .foregroundColor(.white)
                                        .padding()
                                }
                            }
                            .transition(.scale) // Animate the feedback box itself
                            .zIndex(2) // Ensure it's on top of confetti and main content
                        }
                    } // End ZStack
                    // Attach Sheets and Alerts to the outermost View in this branch for consistency
                    .sheet(isPresented: $showShareDialog) {
                        // Pass current puzzle info if needed for sharing specific puzzle
                        SharePuzzleView(
                            isPresented: $showShareDialog,
                            puzzleId: currentPuzzle.puzzleId, // Safely unwrapped `currentPuzzle`
                            onShare: sharePuzzle
                        )
                    }
                    .sheet(isPresented: $showLeaderboard) {
                        if let leaderboard = leaderboardData { // Ensure leaderboardData is available
                            LeaderboardView(entries: leaderboard)
                                .presentationDetents([.medium, .large])
                        }
                    }
                    .sheet(isPresented: $showHint) {
                        HintView(hintText: currentPuzzle.hint) // Safely unwrapped `currentPuzzle`
                            .presentationDetents([.medium, .large])
                    }
                    // The .alert should ideally also be attached to the ZStack or higher level
                    // but if `showFeedback` is already controlling an overlay,
                    // an alert might be redundant or conflict.
                    // If you want an actual system alert, change your feedback logic to use it.
                    // For now, I'll remove the redundant alert as you have a custom overlay.
                    /*
                    .alert(isPresented: $showFeedback) {
                        print("🔄 Handling feedback for question \(viewModel.currentQuestionIndex)")
                        return Alert(
                            title: Text(isCorrect ? "Correct!" : "Incorrect"),
                            message: Text(feedbackMessage),
                            dismissButton: .default(Text("Next")) {
                                print("⏭ User tapped Next, loading next puzzle")
                                viewModel.loadNextPuzzle()
                                userAnswer = ""
                                // Reset other feedback states here as well
                            }
                        )
                    }
                    */
                } else {
                    // Fallback for when viewModel.currentPuzzle is nil but allPuzzlesCompleted is false
                    // (e.g., initial loading state or error)
                    ProgressView("Loading Puzzle...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        } // End Group
    }
    
    // Simplified Leaderboard View
    struct LeaderboardView: View {
        let entries: [LeaderboardEntry]
        
        var body: some View {
            NavigationView {
                List(entries) { entry in
                    HStack {
                        Text(entry.userId) // Or any other user identifier you have
                        Spacer()
                        Text("\(entry.score) pts")
                            .bold()
                        if let time = entry.timeTaken {
                            Text("(\(time)s)")
                                .foregroundColor(.gray)
                        }
                    }
                }
                .navigationTitle("Leaderboard")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
    }

    
    struct HintView: View {
        let hintText: String
        @Environment(\.dismiss) private var dismiss
        
        var body: some View {
            NavigationView {
                ScrollView {
                    Text(hintText)
                        .padding()
                }
                .navigationTitle("Hint")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Done") {
                            dismiss()
                        }
                    }
                }
            }
        }
    }
    
    private func checkAnswer() {
        guard !userAnswer.trimmingCharacters(in: .whitespaces).isEmpty else {
            print("⚠️ Answer is empty.")
            return
        }
        
        viewModel.checkAnswer(userAnswer: userAnswer) { correct in
            isCorrect = correct
            showFeedback = true
            
            if correct {
                animateCorrect = true
                showConfetti = true
                playSound(named: "correct")
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    showConfetti = false
                    animateCorrect = false
                }
            } else {
                playSound(named: "buzz")
            }
        }
    }
    
    private func playSound(named name: String) {
        if let soundURL = Bundle.main.url(forResource: name, withExtension: "mp3") {
            do {
                audioPlayer = try AVAudioPlayer(contentsOf: soundURL)
                audioPlayer?.play()
            } catch {
                print("❌ Failed to play \(name):", error)
            }
        }
    }
}

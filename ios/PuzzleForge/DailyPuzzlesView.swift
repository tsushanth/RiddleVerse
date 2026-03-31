//  DailyPuzzlesView.swift
//  PuzzleForge
//
//  Enhanced to match Android behavior - immediate closure and background generation
//

import SwiftUI

extension DailyPuzzlesView {
    private func isCompleted(_ puzzle: DailyPuzzle) -> Bool {
        let completedKey = "completed_daily_puzzle_\(puzzle.id.uuidString)"
        return UserDefaults.standard.bool(forKey: completedKey)
    }
    
    private func markAsCompleted(_ puzzle: DailyPuzzle, selectedAnswer: String) {
        let completedKey = "completed_daily_puzzle_\(puzzle.id.uuidString)"
        UserDefaults.standard.set(true, forKey: completedKey)
        
        let answerKey = "answer_daily_puzzle_\(puzzle.id.uuidString)"
        UserDefaults.standard.set(selectedAnswer, forKey: answerKey)
    }
    
    private func getStoredAnswer(_ puzzle: DailyPuzzle) -> String? {
        let answerKey = "answer_daily_puzzle_\(puzzle.id.uuidString)"
        return UserDefaults.standard.string(forKey: answerKey)
    }
}

struct DailyPuzzlesView: View {
    @State private var puzzles: [DailyPuzzle] = []
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var selectedTopics: Set<String> = []
    @State private var availableTopics: [TopicOption] = []
    @State private var showTopicSelection = false
    @State private var isSavingTopics = false
    @State private var showSuccessAlert = false
    
    // Navigation from notification
    @State private var fromNotification = false
    @State private var notificationTopics: String?
    
    private let serverURL = "https://puzzleverseai.com"
    
    var body: some View {
        NavigationView {
            VStack {
                if showTopicSelection {
                    TopicSelectionView(
                        availableTopics: availableTopics,
                        selectedTopics: $selectedTopics,
                        onTopicsSelected: { topics in
                            handleTopicSelection(topics: topics)
                        },
                        onCancel: {
                            showTopicSelection = false
                        }
                    )
                } else if isLoading {
                    LoadingView(message: "Loading daily puzzles...")
                } else if let error = errorMessage {
                    ErrorView(message: error) {
                        loadDailyPuzzles()
                    }
                } else if puzzles.isEmpty {
                    EmptyStateView(onSetupTopics: {
                        loadAvailableTopics()
                    })
                } else {
                    PuzzleListView(puzzles: puzzles)
                }
            }
            .navigationTitle("Daily Puzzles")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    if !puzzles.isEmpty {
                        Button("Topics") {
                            loadAvailableTopics()
                        }
                    }
                    
                    Button("Refresh") {
                        loadDailyPuzzles()
                    }
                }
            }
            .onAppear {
                loadDailyPuzzles()
            }
            .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("NavigateToDailyPuzzles"))) { notification in
                handleNotificationNavigation(notification: notification)
            }
            .alert("New Daily Puzzles!", isPresented: $fromNotification) {
                Button("Let's Go!") {
                    fromNotification = false
                }
                Button("Later") {
                    fromNotification = false
                }
            } message: {
                if let topics = notificationTopics {
                    Text("Your daily puzzles for \(topics) are ready!")
                } else {
                    Text("Your daily puzzles are ready!")
                }
            }
            .alert("Topics Saved!", isPresented: $showSuccessAlert) {
                Button("OK") {
                    showSuccessAlert = false
                    loadDailyPuzzles() // Reload puzzles after success
                }
            } message: {
                Text("🎉 Topics saved! Your daily puzzles are being generated.")
            }
        }
    }
    
    // MARK: - Data Loading
    private func loadDailyPuzzles() {
        guard let userEmail = getUserEmail() else {
            errorMessage = "Please sign in to view daily puzzles"
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        loadUserTopics(userEmail: userEmail) { topics in
            guard !topics.isEmpty else {
                DispatchQueue.main.async {
                    self.isLoading = false
                    self.loadAvailableTopics() // Show topic selection instead of error
                }
                return
            }
            
            let group = DispatchGroup()
            var allPuzzles: [DailyPuzzle] = []
            var hasError = false
            
            for topic in topics {
                group.enter()
                loadDailyPuzzleSet(userEmail: userEmail, topic: topic) { puzzleSet in
                    if let puzzleSet = puzzleSet {
                        allPuzzles.append(contentsOf: puzzleSet.puzzles.map { puzzle in
                            DailyPuzzle(
                                id: UUID(),
                                topic: puzzleSet.topic,
                                question: puzzle.question,
                                answer: puzzle.answer,
                                hint: puzzle.hint ?? "",
                                options: puzzle.options ?? [],
                                difficulty: puzzle.difficulty ?? "Medium",
                                generationDate: puzzleSet.generationDate
                            )
                        })
                    } else {
                        hasError = true
                    }
                    group.leave()
                }
            }
            
            group.notify(queue: .main) {
                self.isLoading = false
                if hasError && allPuzzles.isEmpty {
                    self.errorMessage = "Failed to load daily puzzles. Please try again."
                } else {
                    self.puzzles = allPuzzles.shuffled()
                    
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("daily_puzzles_loaded", parameters: [
                        "puzzle_count": allPuzzles.count,
                        "topic_count": topics.count
                    ]))
                }
            }
        }
    }
    
    private func loadAvailableTopics() {
        guard let url = URL(string: "\(serverURL)/available-topics") else { return }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            guard let data = data,
                  let response = try? JSONDecoder().decode(AvailableTopicsResponse.self, from: data) else {
                return
            }
            
            DispatchQueue.main.async {
                self.availableTopics = response.topics
                self.showTopicSelection = true
            }
        }.resume()
    }
    
    private func handleTopicSelection(topics: Set<String>) {
        guard let userEmail = getUserEmail() else { return }
        
        // IMMEDIATELY close the view and show loading in main view
        showTopicSelection = false
        isLoading = true
        
        // Save topics
        saveUserTopics(userEmail: userEmail, topics: Array(topics)) { success in
            DispatchQueue.main.async {
                self.isLoading = false
                
                if success {
                    // Show success alert
                    self.showSuccessAlert = true
                    
                    // Start background generation
                    self.generatePuzzlesInBackground(userEmail: userEmail)
                    
                    // Track analytics
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("topics_selected", parameters: [
                        "topic_count": topics.count,
                        "topics": Array(topics).joined(separator: ",")
                    ]))
                } else {
                    self.errorMessage = "Failed to save topics. Please try again."
                }
            }
        }
    }
    
    private func saveUserTopics(userEmail: String, topics: [String], completion: @escaping (Bool) -> Void) {
        guard let url = URL(string: "\(serverURL)/save-user-topics") else {
            completion(false)
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = [
            "email": userEmail,
            "topics": topics
        ] as [String: Any]
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        } catch {
            completion(false)
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            let success = error == nil && (response as? HTTPURLResponse)?.statusCode == 200
            completion(success)
        }.resume()
    }
    
    // MARK: - Background Puzzle Generation (Fire and Forget)
    private func generatePuzzlesInBackground(userEmail: String) {
        // This runs in background and doesn't block the UI
        DispatchQueue.global(qos: .background).async {
            guard let encodedEmail = userEmail.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                  let url = URL(string: "\(self.serverURL)/generate-user-puzzles-with-notifications?email=\(encodedEmail)") else {
                return
            }
            
            var request = URLRequest(url: url)
            request.httpMethod = "GET"
            request.timeoutInterval = 15 // Short timeout since it's background
            
            URLSession.shared.dataTask(with: request) { data, response, error in
                // Optional: Show a subtle notification when generation completes
                if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                    DispatchQueue.main.async {
                        // Only show if the app is still active
                        if UIApplication.shared.applicationState == .active {
                            self.showCompletionToast()
                        }
                    }
                }
                // Silently handle errors - user has already left the screen
            }.resume()
        }
    }
    
    private func showCompletionToast() {
        // You can implement a toast/banner notification here
        // For now, we'll use a simple print statement
        print("✨ Daily puzzles generated successfully!")
        
        // Optional: Post a notification that other parts of the app can listen to
        NotificationCenter.default.post(
            name: NSNotification.Name("DailyPuzzlesGenerated"),
            object: nil
        )
    }
    
    private func loadUserTopics(userEmail: String, completion: @escaping ([String]) -> Void) {
        guard let url = URL(string: "\(serverURL)/list-user-daily-topics?email=\(userEmail)") else {
            completion([])
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let topics = json["topics"] as? [String] else {
                completion([])
                return
            }
            
            completion(topics)
        }.resume()
    }
    
    private func loadDailyPuzzleSet(userEmail: String, topic: String, completion: @escaping (DailyPuzzleSet?) -> Void) {
        guard let encodedEmail = userEmail.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let encodedTopic = topic.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(serverURL)/get-daily-puzzle?email=\(encodedEmail)&topic=\(encodedTopic)") else {
            completion(nil)
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            guard let data = data else {
                completion(nil)
                return
            }
            
            do {
                let decoder = JSONDecoder()
                let response = try decoder.decode(DailyPuzzleResponse.self, from: data)
                completion(response.puzzleSet)
            } catch {
                print("❌ Error decoding daily puzzle set: \(error)")
                completion(nil)
            }
        }.resume()
    }
    
    // MARK: - Notification Handling
    private func handleNotificationNavigation(notification: Notification) {
        guard let userInfo = notification.userInfo else { return }
        
        notificationTopics = userInfo["topics"] as? String
        fromNotification = true
        
        loadDailyPuzzles()
        
        AnalyticsManager.shared.trackSafely(AnalyticsEvent("notification_navigation", parameters: [
            "destination": "daily_puzzles",
            "topics": notificationTopics ?? "unknown"
        ]))
    }
    
    // MARK: - Utility
    private func getUserEmail() -> String? {
        return UserDefaults.standard.string(forKey: "current_user_email")
    }
}

// MARK: - Topic Selection View (Simplified)
struct TopicSelectionView: View {
    let availableTopics: [TopicOption]
    @Binding var selectedTopics: Set<String>
    let onTopicsSelected: (Set<String>) -> Void
    let onCancel: () -> Void
    
    private let columns = [
        GridItem(.adaptive(minimum: 150), spacing: 16)
    ]
    
    var body: some View {
        VStack(spacing: 24) {
            // Header
            VStack(spacing: 8) {
                Text("Choose Your Daily Puzzle Topics")
                    .font(.title2)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)
                
                Text("Select topics you're interested in. We'll generate daily puzzles for each topic.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal)
            
            // Topic Grid
            ScrollView {
                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(availableTopics, id: \.name) { topic in
                        TopicCard(
                            topic: topic,
                            isSelected: selectedTopics.contains(topic.name)
                        ) {
                            if selectedTopics.contains(topic.name) {
                                selectedTopics.remove(topic.name)
                            } else {
                                selectedTopics.insert(topic.name)
                            }
                        }
                    }
                }
                .padding(.horizontal)
            }
            
            // Action Buttons
            VStack(spacing: 12) {
                Button(action: {
                    onTopicsSelected(selectedTopics)
                }) {
                    HStack {
                        Image(systemName: "sparkles")
                        Text("Save & Generate Puzzles (\(selectedTopics.count))")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(selectedTopics.isEmpty ? Color.gray : Color.blue)
                    .cornerRadius(12)
                }
                .disabled(selectedTopics.isEmpty)
                
                Button("Cancel") {
                    onCancel()
                }
                .font(.body)
                .foregroundColor(.secondary)
            }
            .padding(.horizontal)
            
            if selectedTopics.isEmpty {
                Text("💡 Select at least one topic to continue")
                    .font(.caption)
                    .foregroundColor(.blue)
                    .padding(.horizontal)
            }
        }
        .padding(.vertical)
    }
}

struct TopicCard: View {
    let topic: TopicOption
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 12) {
                Text(topic.emoji)
                    .font(.system(size: 32))
                
                VStack(spacing: 4) {
                    Text(topic.name)
                        .font(.headline)
                        .fontWeight(.semibold)
                        .multilineTextAlignment(.center)
                    
                    Text(topic.description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
            }
            .padding()
            .frame(maxWidth: .infinity)
            .frame(height: 120)
            .background(isSelected ? Color.blue.opacity(0.1) : Color.gray.opacity(0.05))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Color.blue : Color.clear, lineWidth: 2)
            )
            .cornerRadius(12)
        }
        .buttonStyle(PlainButtonStyle())
        .scaleEffect(isSelected ? 1.05 : 1.0)
        .animation(.easeInOut(duration: 0.2), value: isSelected)
    }
}

// MARK: - Supporting Views (Enhanced)
struct LoadingView: View {
    let message: String
    
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.2)
            
            Text(message)
                .font(.body)
                .foregroundColor(.secondary)
        }
        .padding()
    }
}

struct ErrorView: View {
    let message: String
    let retryAction: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundColor(.orange)
            
            Text("Oops!")
                .font(.title2)
                .fontWeight(.bold)
            
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
            
            Button("Try Again") {
                retryAction()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

struct EmptyStateView: View {
    let onSetupTopics: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "calendar.badge.plus")
                .font(.system(size: 64))
                .foregroundColor(.blue)
            
            VStack(spacing: 8) {
                Text("Welcome to Daily Puzzles!")
                    .font(.title2)
                    .fontWeight(.bold)
                
                Text("Choose your favorite topics and we'll generate personalized daily puzzles just for you.")
                    .multilineTextAlignment(.center)
                    .foregroundColor(.secondary)
            }
            
            Button(action: onSetupTopics) {
                HStack {
                    Image(systemName: "sparkles")
                    Text("Choose Topics")
                }
                .font(.headline)
                .foregroundColor(.white)
                .padding(.horizontal, 32)
                .padding(.vertical, 12)
                .background(Color.blue)
                .cornerRadius(25)
            }
        }
        .padding()
    }
}

// MARK: - Data Models (Only the ones not in Puzzle.swift)
struct TopicOption: Codable {
    let name: String
    let description: String
    let emoji: String
    let category: String?
}

struct AvailableTopicsResponse: Codable {
    let topics: [TopicOption]
}

struct DailyPuzzleSet: Codable {
    let topic: String
    let generationDate: String
    let generationTimestamp: String
    let puzzleCount: Int
    let puzzles: [PuzzleData]
}

struct PuzzleData: Codable {
    let question: String
    let answer: String
    let hint: String?
    let options: [String]?
    let difficulty: String?
}

// MARK: - Existing Views (PuzzleListView, PuzzleRowView, etc.)
struct PuzzleListView: View {
    let puzzles: [DailyPuzzle]
    
    var body: some View {
        List {
            ForEach(groupedPuzzles, id: \.key) { topic, topicPuzzles in
                Section(header: Text(topic).font(.headline)) {
                    ForEach(topicPuzzles) { puzzle in
                        PuzzleRowView(puzzle: puzzle)
                    }
                }
            }
        }
    }
    
    private var groupedPuzzles: [(key: String, value: [DailyPuzzle])] {
        Dictionary(grouping: puzzles, by: { $0.topic })
            .sorted { $0.key < $1.key }
    }
}

struct PuzzleRowView: View {
    let puzzle: DailyPuzzle
    @State private var showMultipleChoice = false
    @State private var showAnswer = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(puzzle.question)
                        .font(.body)
                        .fontWeight(.medium)
                    
                    Spacer()
                    
                    if isCompleted {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.system(size: 16))
                    }
                }
                
                HStack {
                    Text("Difficulty: \(puzzle.difficulty)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Spacer()
                    
                    Text("Topic: \(puzzle.topic)")
                        .font(.caption)
                        .foregroundColor(.blue)
                }
            }
            
            if !puzzle.hint.isEmpty {
                Text("💡 Hint: \(puzzle.hint)")
                    .font(.caption)
                    .foregroundColor(.blue)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(8)
            }
            
            if isCompleted {
                if let storedAnswer = getStoredAnswer() {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("✅ Completed")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.green)
                        
                        Text("Your answer: \(storedAnswer)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        Text("Correct answer: \(puzzle.answer)")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(.green)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.green.opacity(0.05))
                    .cornerRadius(8)
                }
            } else {
                if !puzzle.options.isEmpty {
                    Button("Solve Puzzle") {
                        showMultipleChoice = true
                    }
                    .buttonStyle(.borderedProminent)
                } else {
                    if !showAnswer {
                        Button("Show Answer") {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                showAnswer = true
                                markAsCompleted("")
                            }
                        }
                        .buttonStyle(.bordered)
                    } else {
                        Text("Answer: \(puzzle.answer)")
                            .font(.body)
                            .fontWeight(.bold)
                            .foregroundColor(.green)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Color.green.opacity(0.1))
                            .cornerRadius(8)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
            }
        }
        .padding(.vertical, 8)
        .fullScreenCover(isPresented: $showMultipleChoice) {
            MultipleChoicePuzzleView(
                puzzleId: puzzle.id.uuidString,
                question: puzzle.question,
                options: puzzle.options,
                correctAnswer: puzzle.answer,
                hint: puzzle.hint.isEmpty ? nil : puzzle.hint,
                timerSeconds: 60,
                questionNumber: 1,
                totalQuestions: 1,
                puzzleDifficulty: puzzle.difficulty,
                onOptionSelected: { selectedOption in
                    markAsCompleted(selectedOption)
                    
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("daily_puzzle_answered", parameters: [
                        "puzzle_id": puzzle.id.uuidString,
                        "topic": puzzle.topic,
                        "difficulty": puzzle.difficulty,
                        "selected_option": selectedOption,
                        "correct_answer": puzzle.answer,
                        "is_correct": selectedOption == puzzle.answer
                    ]))
                    
                    showMultipleChoice = false
                },
                onExit: {
                    showMultipleChoice = false
                }
            )
        }
    }
    
    private var isCompleted: Bool {
        let completedKey = "completed_daily_puzzle_\(puzzle.id.uuidString)"
        return UserDefaults.standard.bool(forKey: completedKey)
    }
    
    private func markAsCompleted(_ selectedAnswer: String) {
        let completedKey = "completed_daily_puzzle_\(puzzle.id.uuidString)"
        UserDefaults.standard.set(true, forKey: completedKey)
        
        let answerKey = "answer_daily_puzzle_\(puzzle.id.uuidString)"
        UserDefaults.standard.set(selectedAnswer, forKey: answerKey)
    }
    
    private func getStoredAnswer() -> String? {
        let answerKey = "answer_daily_puzzle_\(puzzle.id.uuidString)"
        return UserDefaults.standard.string(forKey: answerKey)
    }
}

struct DailyPuzzlesView_Previews: PreviewProvider {
    static var previews: some View {
        DailyPuzzlesView()
    }
}

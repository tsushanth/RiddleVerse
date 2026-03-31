import SwiftUI
import AVFoundation
import Combine

// MARK: - Data Models
struct SimpleMusicPuzzleData {
    let puzzleId: String
    let theme: String
    let description: String
    let questions: [SimpleMusicQuestion]
    let totalQuestions: Int
    let timeLimit: Int
}

struct SimpleMusicQuestion {
    let id: Int
    let audioUrl: String
    let answer: String
    let hint: String
    let metadata: SimpleMusicMetadata
    var isAnswered: Bool = false
}

struct SimpleMusicMetadata {
    let duration: Int
    let albumImageUrl: String
    let requestedArtist: String
    let requestedTitle: String
}

enum SimpleAudioState {
    case stopped, playing, paused, loading
}

// MARK: - Music Puzzle Parser Extension
extension Puzzle {
    var simpleMusicPuzzleData: SimpleMusicPuzzleData? {
        guard let questionData = question.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] else {
            print("🔴 MUSIC: Failed to parse question as JSON")
            return nil
        }
        
        let puzzleId = json["puzzleId"] as? String ?? self.puzzleId
        let theme = json["theme"] as? String ?? "Music Quiz"
        let description = json["description"] as? String ?? "Match songs to their titles"
        let totalQuestions = json["totalTracks"] as? Int ?? 8
        let timeLimit = json["timeLimit"] as? Int ?? 360
        
        guard let questionsArray = json["questions"] as? [[String: Any]] else {
            print("🔴 MUSIC: No questions array found")
            return nil
        }
        
        let questions = questionsArray.enumerated().compactMap { index, questionData -> SimpleMusicQuestion? in
            let id = questionData["id"] as? Int ?? (index + 1)
            let audioUrl = questionData["audioUrl"] as? String ?? ""
            let answer = questionData["answer"] as? String ?? ""
            let hint = questionData["hint"] as? String ?? ""
            
            let metadataDict = questionData["metadata"] as? [String: Any] ?? [:]
            let metadata = SimpleMusicMetadata(
                duration: metadataDict["duration"] as? Int ?? 30,
                albumImageUrl: metadataDict["albumImageUrl"] as? String ?? "",
                requestedArtist: metadataDict["requestedArtist"] as? String ?? "",
                requestedTitle: metadataDict["requestedTitle"] as? String ?? ""
            )
            
            return SimpleMusicQuestion(
                id: id,
                audioUrl: audioUrl,
                answer: answer,
                hint: hint,
                metadata: metadata
            )
        }
        
        return SimpleMusicPuzzleData(
            puzzleId: puzzleId,
            theme: theme,
            description: description,
            questions: questions,
            totalQuestions: totalQuestions,
            timeLimit: timeLimit
        )
    }
}

// MARK: - Audio Player Manager
class SimpleMusicAudioManager: NSObject, ObservableObject {
    @Published var currentPlayingId: Int?
    @Published var audioState: SimpleAudioState = .stopped
    
    private var audioPlayer: AVAudioPlayer?
    private var currentUrlTask: URLSessionDataTask?
    
    func playAudio(questionId: Int, audioUrl: String) {
        print("🎵 Attempting to play audio for question \(questionId): \(audioUrl)")
        
        // Stop current playback
        stopAudio()
        
        currentPlayingId = questionId
        audioState = .loading
        
        guard let url = URL(string: audioUrl) else {
            print("🔴 MUSIC: Invalid audio URL: \(audioUrl)")
            audioState = .stopped
            currentPlayingId = nil
            return
        }
        
        // Cancel any existing download
        currentUrlTask?.cancel()
        
        // Download and play audio
        currentUrlTask = URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                
                if let error = error {
                    print("🔴 MUSIC: Audio download error: \(error.localizedDescription)")
                    self.audioState = .stopped
                    self.currentPlayingId = nil
                    return
                }
                
                guard let data = data else {
                    print("🔴 MUSIC: No audio data received")
                    self.audioState = .stopped
                    self.currentPlayingId = nil
                    return
                }
                
                do {
                    self.audioPlayer = try AVAudioPlayer(data: data)
                    self.audioPlayer?.delegate = self
                    self.audioPlayer?.play()
                    self.audioState = .playing
                    print("✅ MUSIC: Audio started playing for question \(questionId)")
                } catch {
                    print("🔴 MUSIC: Audio player error: \(error.localizedDescription)")
                    self.audioState = .stopped
                    self.currentPlayingId = nil
                }
            }
        }
        
        currentUrlTask?.resume()
    }
    
    func pauseAudio() {
        audioPlayer?.pause()
        audioState = .paused
    }
    
    func resumeAudio() {
        audioPlayer?.play()
        audioState = .playing
    }
    
    func stopAudio() {
        currentUrlTask?.cancel()
        audioPlayer?.stop()
        audioPlayer = nil
        audioState = .stopped
        currentPlayingId = nil
    }
    
    func togglePlayPause(questionId: Int, audioUrl: String) {
        if currentPlayingId == questionId {
            switch audioState {
            case .playing:
                pauseAudio()
            case .paused:
                resumeAudio()
            case .stopped, .loading:
                playAudio(questionId: questionId, audioUrl: audioUrl)
            }
        } else {
            playAudio(questionId: questionId, audioUrl: audioUrl)
        }
    }
}

extension SimpleMusicAudioManager: AVAudioPlayerDelegate {
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async {
            self.audioState = .stopped
            self.currentPlayingId = nil
        }
    }
    
    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        DispatchQueue.main.async {
            self.audioState = .stopped
            self.currentPlayingId = nil
            if let error = error {
                print("🔴 MUSIC: Audio decode error: \(error.localizedDescription)")
            }
        }
    }
}

// MARK: - Main Music Puzzle View
struct SimpleMusicPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @StateObject private var audioManager = SimpleMusicAudioManager()
    @State private var puzzleData: SimpleMusicPuzzleData?
    @State private var questions: [SimpleMusicQuestion] = []
    @State private var availableAnswers: [String] = []
    @State private var correctAnswers = 0
    @State private var wrongAnswers = 0
    @State private var timeRemaining = 360
    @State private var gameCompleted = false
    
    @State private var showTutorial = false
    @State private var hasSeenTutorial = false
    // Selection state
    @State private var selectedQuestionId: Int?
    @State private var selectedAnswer: String?
    
    // Feedback state
    @State private var showFeedback: String?
    @State private var feedbackColor: Color = .green
    
    // Timer
    @State private var timer: Timer?
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if showTutorial {
                            MusicPuzzleTutorialView(
                                onTutorialComplete: {
                                    showTutorial = false
                                    hasSeenTutorial = true
                                    UserDefaults.standard.set(true, forKey: "music_puzzle_tutorial_completed")
                                    print("🎵 Music puzzle tutorial completed")
                                },
                                onTutorialSkipped: {
                                    showTutorial = false
                                    hasSeenTutorial = true
                                    UserDefaults.standard.set(true, forKey: "music_puzzle_tutorial_skipped")
                                    print("🎵 Music puzzle tutorial skipped")
                                },
                                onBack: {
                                    showTutorial = false
                                    onExit()
                                }
                            )
                            .transition(.opacity)
                            .zIndex(1000)
                        }
            if let puzzleData = puzzleData {
                VStack(spacing: 0) {
                    // Header
                    MusicPuzzleHeader(
                        difficulty: puzzle.difficulty ?? "Medium",
                        round: "\(questionIndex + 1) of \(totalQuestions)",
                        timeRemaining: timeRemaining,
                        correctAnswers: correctAnswers,
                        totalQuestions: puzzleData.totalQuestions,
                        theme: puzzleData.theme,
                        description: puzzleData.description,
                        onBack: {
                            audioManager.stopAudio()
                            onExit()
                        },
                        onShowTutorial: { // Add this
                            showTutorial = true
                        }
                    )
                    
                    // Main content
                    HStack(spacing: 16) {
                        // Left side - Music samples
                        MusicSamplesPanel(
                            questions: questions.filter { !$0.isAnswered },
                            selectedQuestionId: selectedQuestionId,
                            audioManager: audioManager,
                            onQuestionSelected: { questionId in
                                selectedQuestionId = selectedQuestionId == questionId ? nil : questionId
                            }
                        )
                        
                        // Right side - Answers
                        AnswersPanel(
                            answers: availableAnswers,
                            selectedAnswer: selectedAnswer,
                            onAnswerSelected: { answer in
                                selectedAnswer = selectedAnswer == answer ? nil : answer
                            }
                        )
                    }
                    .padding(.horizontal, 16)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    
                    // Bottom controls
                    MusicBottomControls(
                        selectedQuestionId: selectedQuestionId,
                        selectedAnswer: selectedAnswer,
                        questionsRemaining: questions.count { !$0.isAnswered },
                        onSubmit: submitAnswer,
                        onSkip: {
                            let score = calculateMusicScore(correctAnswers: correctAnswers, totalQuestions: puzzleData.totalQuestions, wrongAnswers: wrongAnswers, timeRemaining: timeRemaining)
                            audioManager.stopAudio()
                            onAnswerSubmitted(false)
                            onNextPuzzle()
                        }
                    )
                    
                    // Progress bar
                    ProgressView(value: Double(correctAnswers), total: Double(puzzleData.totalQuestions))
                        .tint(.green)
                        .scaleEffect(x: 1, y: 2, anchor: .center)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                }
                
                // Feedback overlay
                if let feedback = showFeedback {
                    MusicFeedbackOverlay(message: feedback, color: feedbackColor)
                        .transition(.asymmetric(
                            insertion: .move(edge: .bottom).combined(with: .opacity),
                            removal: .move(edge: .bottom).combined(with: .opacity)
                        ))
                }
            } else {
                // Loading state
                VStack(spacing: 20) {
                    ProgressView()
                        .scaleEffect(1.5)
                        .tint(.white)
                    
                    Text("Loading music puzzle...")
                        .foregroundColor(.white)
                        .font(.title3)
                }
            }
        }
        .onAppear {
            setupPuzzle()
            checkForTutorial()
            startTimer()
        }
        .onDisappear {
            audioManager.stopAudio()
            timer?.invalidate()
        }
    }
    
    private func checkForTutorial() {
            let hasCompletedTutorial = UserDefaults.standard.bool(forKey: "music_puzzle_tutorial_completed")
            let hasSkippedTutorial = UserDefaults.standard.bool(forKey: "music_puzzle_tutorial_skipped")
            
            if !hasCompletedTutorial && !hasSkippedTutorial && !hasSeenTutorial {
                // Show tutorial for first-time users
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    showTutorial = true
                }
            }
        }
    
    private func setupPuzzle() {
        guard let data = puzzle.simpleMusicPuzzleData else {
            print("🔴 MUSIC: Failed to parse music puzzle data")
            return
        }
        
        puzzleData = data
        questions = data.questions
        availableAnswers = data.questions.map { $0.answer }.shuffled()
        timeRemaining = data.timeLimit
        
        print("✅ MUSIC: Setup completed - \(data.questions.count) questions")
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 && !gameCompleted {
                timeRemaining -= 1
            } else if timeRemaining <= 0 && !gameCompleted {
                gameCompleted = true
                let score = calculateMusicScore(correctAnswers: correctAnswers, totalQuestions: puzzleData?.totalQuestions ?? 1, wrongAnswers: wrongAnswers, timeRemaining: timeRemaining)
                audioManager.stopAudio()
                onAnswerSubmitted(correctAnswers > 0)
                onNextPuzzle()
            }
        }
    }
    
    private func submitAnswer() {
        guard let questionId = selectedQuestionId,
              let answer = selectedAnswer,
              let questionIndex = questions.firstIndex(where: { $0.id == questionId }) else { return }
        
        let question = questions[questionIndex]
        let isCorrect = question.answer == answer
        
        if isCorrect {
            correctAnswers += 1
            showFeedback = "🎉 Correct! Great job!"
            feedbackColor = .green
            
            // Mark question as answered
            questions[questionIndex].isAnswered = true
            
            // Remove from available answers
            if let answerIndex = availableAnswers.firstIndex(of: answer) {
                availableAnswers.remove(at: answerIndex)
            }
            
            // Stop audio
            audioManager.stopAudio()
            
            // Check completion
            if correctAnswers >= puzzleData?.totalQuestions ?? 1 {
                gameCompleted = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    let score = calculateMusicScore(correctAnswers: correctAnswers, totalQuestions: puzzleData?.totalQuestions ?? 1, wrongAnswers: wrongAnswers, timeRemaining: timeRemaining)
                    onAnswerSubmitted(true)
                    onNextPuzzle()
                }
            }
        } else {
            wrongAnswers += 1
            showFeedback = "❌ Wrong! Try again"
            feedbackColor = .red
        }
        
        // Clear selections
        selectedQuestionId = nil
        selectedAnswer = nil
        
        // Auto-hide feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            showFeedback = nil
        }
        
        onAnswerSubmitted(isCorrect)
    }
}

struct MusicPuzzleTutorialLauncher: View {
    @State private var showTutorial = false
    
    var body: some View {
        Button("Music Match Tutorial") {
            showTutorial = true
        }
        .sheet(isPresented: $showTutorial) {
            MusicPuzzleTutorialView(
                onTutorialComplete: {
                    showTutorial = false
                },
                onTutorialSkipped: {
                    showTutorial = false
                },
                onBack: {
                    showTutorial = false
                }
            )
        }
    }
}

// MARK: - Header Component
struct MusicPuzzleHeader: View {
    let difficulty: String
    let round: String
    let timeRemaining: Int
    let correctAnswers: Int
    let totalQuestions: Int
    let theme: String
    let description: String
    let onBack: () -> Void
    let onShowTutorial: () -> Void // Add this

    
    var body: some View {
        VStack(spacing: 8) {
            // Top row
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.white)
                        .font(.title2)
                }
                
                Spacer()
                
                VStack(spacing: 2) {
                    Text(difficulty.uppercased())
                        .foregroundColor(.white)
                        .font(.caption)
                        .fontWeight(.bold)
                    Text("ROUND \(round)")
                        .foregroundColor(.white)
                        .font(.caption2)
                }
                
                Spacer()
                
                Button(action: onShowTutorial) {
                                    Image(systemName: "questionmark.circle")
                                        .foregroundColor(.white)
                                        .font(.title2)
                                }
                
                Text(formatTime(timeRemaining))
                    .foregroundColor(timeRemaining < 30 ? .red : .white)
                    .font(.headline)
                    .fontWeight(.bold)
            }
            
            // Title and description
            VStack(spacing: 4) {
                Text(theme)
                    .foregroundColor(.white)
                    .font(.title2)
                    .fontWeight(.bold)
                
                Text(description)
                    .foregroundColor(.gray)
                    .font(.caption)
            }
            
            // Score display
            HStack {
                HStack(spacing: 4) {
                    ForEach(0..<totalQuestions, id: \.self) { index in
                        Image(systemName: "star.fill")
                            .foregroundColor(index < correctAnswers ? .yellow : .gray)
                            .font(.caption)
                    }
                }
                
                Spacer()
                
                Text("\(correctAnswers)/\(totalQuestions)")
                    .foregroundColor(.white)
                    .font(.headline)
                    .fontWeight(.bold)
            }
        }
        .padding(16)
    }
}

// MARK: - Music Samples Panel
struct MusicSamplesPanel: View {
    let questions: [SimpleMusicQuestion]
    let selectedQuestionId: Int?
    @ObservedObject var audioManager: SimpleMusicAudioManager
    let onQuestionSelected: (Int) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("🎵 Music Samples")
                .foregroundColor(.white)
                .font(.headline)
                .fontWeight(.bold)
            
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(questions, id: \.id) { question in
                        MusicSampleItem(
                            question: question,
                            isSelected: selectedQuestionId == question.id,
                            isPlaying: audioManager.currentPlayingId == question.id && audioManager.audioState == .playing,
                            onTap: {
                                onQuestionSelected(question.id)
                            },
                            onPlayPause: {
                                audioManager.togglePlayPause(questionId: question.id, audioUrl: question.audioUrl)
                            }
                        )
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.gray.opacity(0.2))
        )
    }
}

// MARK: - Music Sample Item
struct MusicSampleItem: View {
    let question: SimpleMusicQuestion
    let isSelected: Bool
    let isPlaying: Bool
    let onTap: () -> Void
    let onPlayPause: () -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            // Play/Pause button
            Button(action: onPlayPause) {
                ZStack {
                    Circle()
                        .fill(isPlaying ? Color.red : Color.green)
                        .frame(width: 48, height: 48)
                    
                    Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        .foregroundColor(.white)
                        .font(.title3)
                }
            }
            
            // Question info
            VStack(alignment: .leading, spacing: 4) {
                Text("Song #\(question.id)")
                    .foregroundColor(.white)
                    .font(.subheadline)
                    .fontWeight(.bold)
                
                Text("\(question.metadata.duration)s preview")
                    .foregroundColor(.gray)
                    .font(.caption)
            }
            
            Spacer()
            
            // Selection indicator
            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.white)
                    .font(.title3)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(isSelected ? Color.blue.opacity(0.7) : Color.gray.opacity(0.3))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.white.opacity(isSelected ? 1.0 : 0.0), lineWidth: 2)
                )
        )
        .frame(height: 72) // Fixed height to prevent size changes
        .onTapGesture {
            onTap()
        }
    }
}

// MARK: - Answers Panel
struct AnswersPanel: View {
    let answers: [String]
    let selectedAnswer: String?
    let onAnswerSelected: (String) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("🎯 Song Titles")
                .foregroundColor(.white)
                .font(.headline)
                .fontWeight(.bold)
            
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(answers, id: \.self) { answer in
                        AnswerItem(
                            answer: answer,
                            isSelected: selectedAnswer == answer,
                            onTap: {
                                onAnswerSelected(answer)
                            }
                        )
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.gray.opacity(0.2))
        )
    }
}

// MARK: - Answer Item
struct AnswerItem: View {
    let answer: String
    let isSelected: Bool
    let onTap: () -> Void
    
    @State private var isExpanded = false
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(answer)
                    .foregroundColor(.white)
                    .font(.subheadline)
                    .fontWeight(isSelected ? .bold : .regular)
                    .multilineTextAlignment(.leading)
                    .lineLimit(isExpanded ? nil : 2)
                    .animation(.easeInOut(duration: 0.3), value: isExpanded)
                
                // Show "tap to expand" hint if text is truncated
                if !isExpanded && isTruncated(text: answer) {
                    Text("Tap to expand...")
                        .font(.caption2)
                        .foregroundColor(.gray)
                        .italic()
                }
            }
            
            Spacer()
            
            VStack(spacing: 8) {
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.white)
                        .font(.title3)
                }
                
                // Expand/collapse indicator
                if isTruncated(text: answer) {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundColor(.white.opacity(0.7))
                        .font(.caption)
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(isSelected ? Color.green.opacity(0.7) : Color.gray.opacity(0.3))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isSelected ? Color.white : Color.clear, lineWidth: 2)
                )
        )
        .onTapGesture {
            // First tap expands/collapses if text is long
            if isTruncated(text: answer) && !isSelected {
                withAnimation(.easeInOut(duration: 0.3)) {
                    isExpanded.toggle()
                }
            } else {
                // Select the answer
                onTap()
            }
        }
        .onLongPressGesture(minimumDuration: 0.1) {
            // Long press always selects
            onTap()
        }
    }
    
    private func isTruncated(text: String) -> Bool {
        // Simple heuristic: if text is longer than ~40 characters, it might be truncated
        return text.count > 40
    }
}

// MARK: - Bottom Controls
struct MusicBottomControls: View {
    let selectedQuestionId: Int?
    let selectedAnswer: String?
    let questionsRemaining: Int
    let onSubmit: () -> Void
    let onSkip: () -> Void
    
    private var canSubmit: Bool {
        selectedQuestionId != nil && selectedAnswer != nil
    }
    
    var body: some View {
        HStack(spacing: 16) {
            // Skip button
            Button(action: onSkip) {
                Text("Skip")
                    .foregroundColor(.white)
                    .font(.subheadline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.gray)
                    )
            }
            .frame(maxWidth: .infinity, maxHeight: 44)
            
            // Submit button
            Button(action: onSubmit) {
                Text(canSubmit ? "Submit Answer" : "Select Music & Song")
                    .foregroundColor(.white)
                    .font(.headline)
                    .fontWeight(.bold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(canSubmit ? Color.green : Color.gray)
                    )
            }
            .disabled(!canSubmit)
            .frame(maxWidth: .infinity, maxHeight: 44)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }
}

// MARK: - Music Feedback Overlay
struct MusicFeedbackOverlay: View {
    let message: String
    let color: Color
    
    var body: some View {
        VStack {
            Spacer()
            
            Text(message)
                .foregroundColor(.white)
                .font(.title2)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
                .padding(20)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(color.opacity(0.9))
                )
                .padding(.horizontal, 40)
            
            Spacer()
        }
        .background(Color.clear)
    }
}

// MARK: - Helper Functions
private func formatTime(_ seconds: Int) -> String {
    let minutes = seconds / 60
    let remainingSeconds = seconds % 60
    return String(format: "%d:%02d", minutes, remainingSeconds)
}

private func calculateMusicScore(correctAnswers: Int, totalQuestions: Int, wrongAnswers: Int, timeRemaining: Int) -> Int {
    let baseScore = Int((Double(correctAnswers) / Double(totalQuestions)) * 100)
    let wrongPenalty = wrongAnswers * 5
    let timeBonus = timeRemaining / 10
    return max(0, baseScore - wrongPenalty + timeBonus)
}

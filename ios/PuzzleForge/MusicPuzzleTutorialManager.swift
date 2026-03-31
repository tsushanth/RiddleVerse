//
//  MusicPuzzleTutorialManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/15/25.
//


import SwiftUI
import AVFoundation

// MARK: - Tutorial Manager
class MusicPuzzleTutorialManager: ObservableObject {
    func getTutorialSteps() -> [TutorialStep] {
        return [
            TutorialStep(
                title: "Welcome to Music Match! 🎵",
                description: "Learn how to match song previews to their correct titles. Listen carefully and use your musical knowledge!",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Understanding the Layout 🎧",
                description: "On the left are music samples you can play. On the right are song titles to choose from. Your goal is to match them correctly!",
                targetComponent: "panels",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Play a Music Sample 🎮",
                description: "First, tap the play button next to any music sample to hear a preview. Try playing a song now!",
                targetComponent: "music_samples",
                id: "play_audio" // Interactive
            ),
            TutorialStep(
                title: "Select the Music Sample 🎯",
                description: "Great! Now tap on the music sample itself (not just the play button) to select it. This highlights your choice.",
                targetComponent: "music_samples",
                id: "select_music" // Interactive
            ),
            TutorialStep(
                title: "Choose the Song Title 📝",
                description: "Now tap on the song title you think matches the music you just heard. Look for familiar titles or artists!",
                targetComponent: "song_titles",
                id: "select_title" // Interactive
            ),
            TutorialStep(
                title: "Submit Your Match ✅",
                description: "Perfect! With both a music sample and title selected, tap 'Submit Answer' to check if your match is correct.",
                targetComponent: "submit_button",
                id: "submit_answer" // Interactive
            ),
            TutorialStep(
                title: "Learning from Feedback 📊",
                description: "You'll get immediate feedback! Green means correct, red means try again. Correct matches disappear from the lists.",
                targetComponent: "feedback",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Try Another Match 🔄",
                description: "Select another music sample and try to match it. Each song has only one correct title - use the process of elimination!",
                targetComponent: "music_samples",
                id: "try_another" // Interactive
            ),
            TutorialStep(
                title: "Managing Audio Playback 🎚️",
                description: "You can pause, replay, or switch between songs. Only one song plays at a time, so feel free to compare different samples!",
                targetComponent: "music_samples",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Watch the Timer ⏰",
                description: "Keep an eye on the countdown timer at the top. Work efficiently but don't rush - accuracy is more important than speed!",
                targetComponent: "timer",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Track Your Progress 📈",
                description: "The stars at the top show your progress, and the progress bar at the bottom fills as you make correct matches.",
                targetComponent: "progress",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Use Strategy Tips 💡",
                description: "Start with songs you recognize immediately. Use genre clues, vocal styles, and instruments to help identify unfamiliar tracks.",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "You're Ready to Rock! 🎸",
                description: "Excellent! Now you know how to play Music Match. Listen carefully, trust your musical instincts, and have fun!",
                targetComponent: "grid",
                id: "" // Informational
            )
        ]
    }
}

// MARK: - Main Tutorial View
struct MusicPuzzleTutorialView: View {
    let onTutorialComplete: () -> Void
    let onTutorialSkipped: () -> Void
    let onBack: () -> Void
    
    @StateObject private var tutorialManager = MusicPuzzleTutorialManager()
    @StateObject private var audioManager = TutorialMusicAudioManager()
    @State private var currentStepIndex = 0
    @State private var tutorialState = TutorialState.active
    @State private var sampleMusicData = createSampleMusicData()
    
    private var steps: [TutorialStep] {
        tutorialManager.getTutorialSteps()
    }
    
    private var currentStep: TutorialStep? {
        guard currentStepIndex < steps.count else { return nil }
        return steps[currentStepIndex]
    }
    
    var body: some View {
        ZStack {
            // Tutorial version of the music screen
            TutorialMusicContent(
                currentStep: currentStep,
                sampleMusicData: sampleMusicData,
                audioManager: audioManager,
                onTutorialAction: handleTutorialAction,
                onBack: onBack
            )
            
            // Tutorial overlay
            if tutorialState == .active && currentStepIndex < steps.count {
                let step = steps[currentStepIndex]
                
                if step.id.isEmpty {
                    // Informational step
                    SmartTutorialOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                } else {
                    // Interactive step
                    MinimalInteractiveOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                }
            }
        }
        .onChange(of: tutorialState) { state in
            switch state {
            case .completed:
                audioManager.stopAudio()
                onTutorialComplete()
            case .skipped:
                audioManager.stopAudio()
                onTutorialSkipped()
            case .active:
                break
            }
        }
        .onDisappear {
            audioManager.stopAudio()
        }
    }
    
    private func advanceStep() {
        if currentStepIndex < steps.count - 1 {
            currentStepIndex += 1
        } else {
            tutorialState = .completed
        }
    }
    
    private func skipTutorial() {
        tutorialState = .skipped
    }
    
    private func handleTutorialAction(_ action: String) {
        let step = steps[safe: currentStepIndex]
        if let step = step, !step.id.isEmpty && step.id == action {
            advanceStep()
        }
    }
}

// MARK: - Tutorial Audio Manager
class TutorialMusicAudioManager: NSObject, ObservableObject {
    @Published var currentPlayingId: Int?
    @Published var audioState: SimpleAudioState = .stopped
    
    private var audioPlayer: AVAudioPlayer?
    
    func playDemoAudio(questionId: Int) {
        print("🎵 TUTORIAL: Playing demo audio for question \(questionId)")
        
        // Stop current playback
        stopAudio()
        
        currentPlayingId = questionId
        audioState = .loading
        
        // For tutorial, we'll use a built-in system sound or generate a demo tone
        // In a real implementation, you could use actual audio files
        playDemoTone(for: questionId)
    }
    
    private func playDemoTone(for questionId: Int) {
        // Generate a simple demo tone for tutorial purposes
        // In production, replace with actual audio URLs
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.audioState = .playing
            
            // Simulate audio playback duration
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                if self.currentPlayingId == questionId {
                    self.audioState = .stopped
                    self.currentPlayingId = nil
                }
            }
        }
        
        // Play system sound for demo
        AudioServicesPlaySystemSound(1016) // Low power sound
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
        audioPlayer?.stop()
        audioPlayer = nil
        audioState = .stopped
        currentPlayingId = nil
    }
    
    func togglePlayPause(questionId: Int) {
        if currentPlayingId == questionId {
            switch audioState {
            case .playing:
                pauseAudio()
            case .paused:
                resumeAudio()
            case .stopped, .loading:
                playDemoAudio(questionId: questionId)
            }
        } else {
            playDemoAudio(questionId: questionId)
        }
    }
}

// MARK: - Tutorial Music Content
struct TutorialMusicContent: View {
    let currentStep: TutorialStep?
    let sampleMusicData: TutorialMusicPuzzleData
    @ObservedObject var audioManager: TutorialMusicAudioManager
    let onTutorialAction: (String) -> Void
    let onBack: () -> Void
    
    @State private var questions: [TutorialMusicQuestion]
    @State private var availableAnswers: [String]
    @State private var correctAnswers = 0
    @State private var timeRemaining = 240
    @State private var selectedQuestionId: Int?
    @State private var selectedAnswer: String?
    @State private var showFeedback: String?
    @State private var feedbackColor: Color = .green
    
    init(currentStep: TutorialStep?, sampleMusicData: TutorialMusicPuzzleData, audioManager: TutorialMusicAudioManager, onTutorialAction: @escaping (String) -> Void, onBack: @escaping () -> Void) {
        self.currentStep = currentStep
        self.sampleMusicData = sampleMusicData
        self.audioManager = audioManager
        self.onTutorialAction = onTutorialAction
        self.onBack = onBack
        self._questions = State(initialValue: sampleMusicData.questions)
        self._availableAnswers = State(initialValue: sampleMusicData.questions.map { $0.answer })
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header
                tutorialMusicHeader
                
                // Main content
                HStack(spacing: 16) {
                    // Left side - Music samples
                    tutorialMusicSamplesPanel
                    
                    // Right side - Song titles
                    tutorialSongTitlesPanel
                }
                .padding(.horizontal, 16)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "panels"))
                
                // Bottom controls
                tutorialBottomControls
                
                // Progress bar
                ProgressView(value: Double(correctAnswers), total: Double(sampleMusicData.totalQuestions))
                    .tint(.green)
                    .scaleEffect(x: 1, y: 2, anchor: .center)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                    .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "progress"))
            }
            
            // Feedback overlay
            if let feedback = showFeedback {
                TutorialMusicFeedbackOverlay(message: feedback, color: feedbackColor)
                    .transition(.asymmetric(
                        insertion: .move(edge: .bottom).combined(with: .opacity),
                        removal: .move(edge: .bottom).combined(with: .opacity)
                    ))
                    .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "feedback"))
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            // Auto-hide any feedback
            if showFeedback != nil {
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                    showFeedback = nil
                }
            }
        }
    }
    
    // MARK: - UI Components
    private var tutorialMusicHeader: some View {
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
                    Text("TUTORIAL")
                        .foregroundColor(.white)
                        .font(.caption)
                        .fontWeight(.bold)
                    Text("MUSIC MATCH")
                        .foregroundColor(.white)
                        .font(.caption2)
                }
                
                Spacer()
                
                Text(formatTime(timeRemaining))
                    .foregroundColor(.white)
                    .font(.headline)
                    .fontWeight(.bold)
                    .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "timer"))
            }
            
            // Title and description
            VStack(spacing: 4) {
                Text(sampleMusicData.theme)
                    .foregroundColor(.white)
                    .font(.title2)
                    .fontWeight(.bold)
                
                Text(sampleMusicData.description)
                    .foregroundColor(.gray)
                    .font(.caption)
            }
            
            // Score display
            HStack {
                HStack(spacing: 4) {
                    ForEach(0..<sampleMusicData.totalQuestions, id: \.self) { index in
                        Image(systemName: "star.fill")
                            .foregroundColor(index < correctAnswers ? .yellow : .gray)
                            .font(.caption)
                    }
                }
                
                Spacer()
                
                Text("\(correctAnswers)/\(sampleMusicData.totalQuestions)")
                    .foregroundColor(.white)
                    .font(.headline)
                    .fontWeight(.bold)
            }
        }
        .padding(16)
    }
    
    private var tutorialMusicSamplesPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("🎵 Music Samples")
                .foregroundColor(.white)
                .font(.headline)
                .fontWeight(.bold)
            
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(questions.filter { !$0.isAnswered }, id: \.id) { question in
                        TutorialMusicSampleItem(
                            question: question,
                            isSelected: selectedQuestionId == question.id,
                            isPlaying: audioManager.currentPlayingId == question.id && audioManager.audioState == .playing,
                            onTap: {
                                selectedQuestionId = selectedQuestionId == question.id ? nil : question.id
                                onTutorialAction("select_music")
                            },
                            onPlayPause: {
                                audioManager.togglePlayPause(questionId: question.id)
                                onTutorialAction("play_audio")
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
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "music_samples"))
    }
    
    private var tutorialSongTitlesPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("🎯 Song Titles")
                .foregroundColor(.white)
                .font(.headline)
                .fontWeight(.bold)
            
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(availableAnswers, id: \.self) { answer in
                        TutorialAnswerItem(
                            answer: answer,
                            isSelected: selectedAnswer == answer,
                            onTap: {
                                selectedAnswer = selectedAnswer == answer ? nil : answer
                                onTutorialAction("select_title")
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
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "song_titles"))
    }
    
    private var tutorialBottomControls: some View {
        HStack(spacing: 16) {
            // Skip button
            Button(action: {
                onTutorialAction("skip_puzzle")
            }) {
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
            Button(action: {
                handleTutorialSubmission()
            }) {
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
            .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "submit_button"))
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }
    
    private var canSubmit: Bool {
        selectedQuestionId != nil && selectedAnswer != nil
    }
    
    // MARK: - Tutorial Logic
    private func handleTutorialSubmission() {
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
            
        } else {
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
        
        onTutorialAction("submit_answer")
        
        // Check for "try another" step
        if currentStep?.id == "try_another" && correctAnswers > 1 {
            onTutorialAction("try_another")
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
}

// MARK: - Tutorial Components
struct TutorialMusicSampleItem: View {
    let question: TutorialMusicQuestion
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
                
                if !question.hint.isEmpty {
                    Text(question.hint)
                        .foregroundColor(.blue)
                        .font(.caption2)
                        .italic()
                }
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
                        .stroke(isSelected ? Color.white : Color.clear, lineWidth: 2)
                )
        )
        .onTapGesture {
            onTap()
        }
    }
}

struct TutorialAnswerItem: View {
    let answer: String
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        HStack {
            Text(answer)
                .foregroundColor(.white)
                .font(.subheadline)
                .fontWeight(isSelected ? .bold : .regular)
                .multilineTextAlignment(.leading)
                .lineLimit(2)
            
            Spacer()
            
            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.white)
                    .font(.title3)
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
            onTap()
        }
    }
}

struct TutorialMusicFeedbackOverlay: View {
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

// MARK: - Tutorial Data Models
struct TutorialMusicPuzzleData {
    let puzzleId: String
    let theme: String
    let description: String
    let questions: [TutorialMusicQuestion]
    let totalQuestions: Int
    let timeLimit: Int
}

struct TutorialMusicQuestion {
    let id: Int
    let audioUrl: String
    let answer: String
    let hint: String
    let metadata: TutorialMusicMetadata
    var isAnswered: Bool = false
}

struct TutorialMusicMetadata {
    let duration: Int
    let albumImageUrl: String
    let requestedArtist: String
    let requestedTitle: String
}

// MARK: - Sample Data Creation
func createSampleMusicData() -> TutorialMusicPuzzleData {
    let questions = [
        TutorialMusicQuestion(
            id: 1,
            audioUrl: "demo_audio_1",
            answer: "Shape of You",
            hint: "Popular Ed Sheeran hit",
            metadata: TutorialMusicMetadata(
                duration: 30,
                albumImageUrl: "",
                requestedArtist: "Ed Sheeran",
                requestedTitle: "Shape of You"
            )
        ),
        TutorialMusicQuestion(
            id: 2,
            audioUrl: "demo_audio_2",
            answer: "Bohemian Rhapsody",
            hint: "Classic Queen rock opera",
            metadata: TutorialMusicMetadata(
                duration: 30,
                albumImageUrl: "",
                requestedArtist: "Queen",
                requestedTitle: "Bohemian Rhapsody"
            )
        ),
        TutorialMusicQuestion(
            id: 3,
            audioUrl: "demo_audio_3",
            answer: "Billie Jean",
            hint: "Michael Jackson classic",
            metadata: TutorialMusicMetadata(
                duration: 30,
                albumImageUrl: "",
                requestedArtist: "Michael Jackson",
                requestedTitle: "Billie Jean"
            )
        )
    ]
    
    return TutorialMusicPuzzleData(
        puzzleId: "tutorial-music-001",
        theme: "Music Match Tutorial",
        description: "Learn to match songs with their titles",
        questions: questions,
        totalQuestions: questions.count,
        timeLimit: 240
    )
}


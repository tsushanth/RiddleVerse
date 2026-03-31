//
//  RetentionSubject.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/12/25.
//


//
//  MemoryRetentionPuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on [Current Date]
//

import SwiftUI
import AVFoundation

class RetentionFactModel: ObservableObject, Identifiable {
    let id: String
    let text: String
    let correctSubject: String
    let showTiming: TimeInterval
    
    @Published var isVisible: Bool = false
    @Published var isAnswered: Bool = false
    @Published var wasCorrect: Bool = false
    
    init(id: String, text: String, correctSubject: String, showTiming: TimeInterval) {
        self.id = id
        self.text = text
        self.correctSubject = correctSubject
        self.showTiming = showTiming
    }
}

extension Puzzle {
    var memoryRetentionPuzzleData: MemoryRetentionPuzzleData? {
        guard puzzleType?.lowercased() == "memory_retention" || puzzleType?.lowercased() == "memoryretention",
              let data = question.data(using: .utf8) else {
            return nil
        }
        
        do {
            let memoryData = try JSONDecoder().decode(MemoryRetentionPuzzleData.self, from: data)
            return memoryData
        } catch {
            print("❌ Failed to decode memory retention data: \(error)")
            return nil
        }
    }
    
    var hasValidMemoryRetentionData: Bool {
        return memoryRetentionPuzzleData != nil
    }
}

// MARK: - Data Models
struct RetentionSubject {
    let id: String
    let name: String
    let description: String
    let color: Color
    
    init(id: String, name: String, description: String, color: Color? = nil) {
        self.id = id
        self.name = name
        self.description = description
        self.color = color ?? RetentionSubject.generateSubjectColor()
    }
    
    static func generateSubjectColor() -> Color {
        let colors: [Color] = [
            Color(red: 0.42, green: 0.45, blue: 1.0),    // Purple-blue
            Color(red: 0.61, green: 0.15, blue: 0.69),   // Purple
            Color(red: 0.13, green: 0.59, blue: 0.95),   // Blue
            Color(red: 0.0, green: 0.74, blue: 0.83),    // Cyan
            Color(red: 0.30, green: 0.69, blue: 0.31),   // Green
            Color(red: 0.55, green: 0.76, blue: 0.29),   // Light green
            Color(red: 1.0, green: 0.60, blue: 0.0),     // Orange
            Color(red: 1.0, green: 0.34, blue: 0.13),    // Deep orange
            Color(red: 0.91, green: 0.12, blue: 0.39),   // Pink
        ]
        return colors.randomElement() ?? .blue
    }
}

struct RetentionFact {
    let id: String
    let text: String
    let correctSubject: String
    let showTiming: TimeInterval // in seconds
    var isVisible: Bool = false
    var isAnswered: Bool = false
    var wasCorrect: Bool = false
}

struct FactDragState {
    let factId: String
    var offset: CGSize = .zero
    var isDragging: Bool = false
}

enum MemoryRetentionPhase {
    case audioIntro
    case listeningFacts
    case finalFeedback
}

// MARK: - Memory Retention Data
struct MemoryRetentionPuzzleData: Codable {
    let topic: String
    let essay: String
    let audioUrl: String?
    let estimatedDuration: TimeInterval  // This will now be in seconds
    let subjects: [SubjectData]
    let facts: [FactData]
    
    struct SubjectData: Codable {
        let id: String
        let name: String
        let description: String
    }
    
    struct FactData: Codable {
        let id: String
        let text: String
        let correctSubject: String
        let showTiming: TimeInterval  // This will now be in seconds
    }
}

// MARK: - Main Memory Retention View
struct MemoryRetentionPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (String, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    @State private var selectedFactId: String? = nil
    @State private var audioError: String? = nil
    @State private var showAudioError: Bool = false
    
    @State private var currentPhase: MemoryRetentionPhase = .audioIntro
    @State private var audioPlayer: AVPlayer?
    @State private var isAudioPlaying: Bool = false
    @State private var audioProgress: TimeInterval = 0
    @State private var audioDuration: TimeInterval = 0
    
    @State private var subjects: [RetentionSubject] = []
    @State private var facts: [RetentionFactModel] = []
    @State private var factDragStates: [String: FactDragState] = [:]
    @State private var draggedOverSubjectId: String? = nil
    
    @State private var correctAnswers: Int = 0
    @State private var totalAnswered: Int = 0
    
    @State private var timeObserver: Any?
    
    var body: some View {
        ZStack {
            // Dark gradient background
            LinearGradient(
                colors: [
                    Color(red: 0.10, green: 0.10, blue: 0.18),
                    Color(red: 0.09, green: 0.13, blue: 0.24),
                    Color(red: 0.06, green: 0.20, blue: 0.38)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            switch currentPhase {
            case .audioIntro:
                AudioIntroScreen(
                    onBegin: {
                            currentPhase = .listeningFacts
                            // Start audio here when user clicks Begin
                            if let puzzleData = puzzle.memoryRetentionPuzzleData,
                               let audioUrl = puzzleData.audioUrl, !audioUrl.isEmpty {
                                startAudio(url: audioUrl)
                            } else {
                                startFallbackTiming()
                            }
                        },
                    onBack: onExit
                )
                
            case .listeningFacts:
                MemoryRetentionGameScreen(
                    subjects: subjects,
                    facts: facts,
                    factDragStates: factDragStates,
                    audioProgress: audioProgress,
                    audioDuration: audioDuration,
                    isAudioPlaying: isAudioPlaying,
                    draggedOverSubjectId: draggedOverSubjectId,
                    onFactDrag: handleFactDrag,
                    onFactDrop: handleFactDrop,
                    onDragOver: { subjectId in
                        draggedOverSubjectId = subjectId
                    },
                    onBack: onExit,
                    selectedFactId: $selectedFactId,
                    onFactAssignment: handleFactAssignment
                )
                
            case .finalFeedback:
                EmptyView() // Feedback will be handled by parent
            }
        }
        .alert("Audio Error", isPresented: $showAudioError) {
            Button("Try Again") {
                if let puzzleData = puzzle.memoryRetentionPuzzleData,
                   let audioUrl = puzzleData.audioUrl {
                    startAudio(url: audioUrl)
                }
            }
            Button("Continue Without Audio") {
                startFallbackTiming()
            }
        } message: {
            Text(audioError ?? "Failed to load audio. Please try again or continue without audio.")
        }
        .onAppear {
            setupAudioSession()
            setupPuzzle()
        }
        .onDisappear {
            cleanupAudio()
        }
    }
    
    private func setupAudioSession() {
            do {
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
                try AVAudioSession.sharedInstance().setActive(true)
                print("✅ Audio session configured successfully")
            } catch {
                print("❌ Failed to setup audio session: \(error)")
            }
    }
    
    private func startAudio(url: String) {
            print("🎵 Attempting to start audio: \(url)")
            
            guard let audioURL = URL(string: url) else {
                print("❌ Invalid audio URL: \(url)")
                startFallbackTiming()
                return
            }
            
            // Test the URL accessibility
            let request = URLRequest(url: audioURL)
            URLSession.shared.dataTask(with: request) { data, response, error in
                if let httpResponse = response as? HTTPURLResponse {
                    print("🌐 Audio URL response: \(httpResponse.statusCode)")
                    if httpResponse.statusCode == 200 {
                        DispatchQueue.main.async {
                            self.setupAudioPlayer(url: audioURL)
                        }
                    } else {
                        print("❌ Audio URL not accessible: \(httpResponse.statusCode)")
                        DispatchQueue.main.async {
                            self.audioError = "Failed to load audio (Error \(httpResponse.statusCode))"
                            self.showAudioError = true
                        }
                    }
                } else if let error = error {
                    print("❌ Audio URL error: \(error)")
                    DispatchQueue.main.async {
                        self.startFallbackTiming()
                    }
                }
            }.resume()
        }
    
    private func handleFactAssignment(factId: String, subjectId: String) {
        guard let fact = facts.first(where: { $0.id == factId }),
              fact.isVisible && !fact.isAnswered else { return }

        let isCorrect = fact.correctSubject == subjectId

        if let index = facts.firstIndex(where: { $0.id == factId }) {
            facts[index].isAnswered = true
            facts[index].wasCorrect = isCorrect
        }

        if isCorrect {
            correctAnswers += 1
        }
        totalAnswered += 1
        selectedFactId = nil

        NotificationCenter.default.post(
            name: NSNotification.Name("FactDropped"),
            object: nil,
            userInfo: [
                "factId": factId,
                "subjectId": subjectId,
                "isCorrect": isCorrect
            ]
        )
    }

        
        private func setupAudioPlayer(url: URL) {
            let playerItem = AVPlayerItem(url: url)
            audioPlayer = AVPlayer(playerItem: playerItem)
            
            // Set up time observer
            let interval = CMTime(seconds: 0.1, preferredTimescale: 600)
            timeObserver = audioPlayer?.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
                audioProgress = time.seconds
                updateVisibleFacts()
            }
            
            // Set up completion observer
            NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime,
                object: playerItem,
                queue: .main
            ) { _ in
                audioCompleted()
            }
            
            // Wait for the asset to load
            playerItem.asset.loadValuesAsynchronously(forKeys: ["duration"]) {
                DispatchQueue.main.async {
                    let duration = playerItem.asset.duration.seconds
                    if !duration.isNaN && duration > 0 {
                        self.audioDuration = duration
                        print("🎵 Audio duration loaded: \(duration)s")
                    } else {
                        self.audioDuration = 60.0
                        print("⚠️ Using fallback duration")
                    }
                    
                    // Start playback
                    self.audioPlayer?.play()
                    self.isAudioPlaying = true
                    print("🎵 Audio playback started successfully")
                }
            }
        }
    
    // MARK: - Setup and Data Parsing
    private func setupPuzzle() {
        // The puzzle.question already contains the parsed JSON data
        guard let data = puzzle.question.data(using: .utf8),
              let puzzleData = try? JSONDecoder().decode(MemoryRetentionPuzzleData.self, from: data) else {
            print("❌ Failed to parse memory retention data")
            return
        }
        
        self.subjects = puzzleData.subjects.map { subjectData in
            RetentionSubject(
                id: subjectData.id,
                name: subjectData.name,
                description: subjectData.description
            )
        }
        
        self.facts = puzzleData.facts.map {
            RetentionFactModel(
                id: $0.id,
                text: $0.text,
                correctSubject: $0.correctSubject,
                showTiming: $0.showTiming
            )
        }
        
        // Initialize drag states
        self.factDragStates = facts.reduce(into: [:]) { result, fact in
            result[fact.id] = FactDragState(factId: fact.id)
        }
        
        // Start audio if URL is available
        if let audioUrl = puzzleData.audioUrl, !audioUrl.isEmpty {
            // Audio will be started when user presses Begin button
            print("🎵 Audio URL ready: \(audioUrl)")
        }
        
        print("✅ Memory retention puzzle setup complete")
        print("📊 Subjects: \(subjects.count), Facts: \(facts.count)")
    }
    
    private func parseMemoryRetentionData(from jsonString: String) -> MemoryRetentionPuzzleData? {
        guard let data = jsonString.data(using: .utf8),
              let puzzleData = try? JSONDecoder().decode(MemoryRetentionPuzzleData.self, from: data) else {
            print("❌ Failed to decode memory retention JSON")
            return nil
        }
        return puzzleData
    }
    

    private func startFallbackTiming() {
        isAudioPlaying = true
        let estimatedDuration: TimeInterval = 60 // Default 60 seconds
        audioDuration = estimatedDuration
        
        // Remove [weak self] since struct doesn't need it
        Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { timer in
            audioProgress += 0.1
            updateVisibleFacts()
            
            if audioProgress >= estimatedDuration {
                timer.invalidate()
                audioCompleted()
            }
        }
        
        print("🎵 Started fallback timing")
    }
    
    private func updateVisibleFacts() {
        for index in facts.indices {
            if !facts[index].isVisible && audioProgress >= facts[index].showTiming {
                facts[index].isVisible = true
                print("🎯 Showing fact: \(facts[index].text) at \(audioProgress)s")
            }
        }
    }
    
    private func audioCompleted() {
        isAudioPlaying = false
        
        // Show any remaining facts
        for index in facts.indices {
            facts[index].isVisible = true
        }
        
        // Wait a moment then finish
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            currentPhase = .finalFeedback
            calculateResults()
        }
        
        print("🎵 Audio completed")
    }
    
    private func cleanupAudio() {
        if let observer = timeObserver {
            audioPlayer?.removeTimeObserver(observer)
        }
        audioPlayer?.pause()
        audioPlayer = nil
        NotificationCenter.default.removeObserver(self, name: .AVPlayerItemDidPlayToEndTime, object: nil)
    }
    
    // MARK: - Game Logic
    private func handleFactDrag(factId: String, offset: CGSize) {
        factDragStates[factId] = FactDragState(
            factId: factId,
            offset: offset,
            isDragging: true
        )
    }
    
    private func handleFactDrop(factId: String, subjectId: String?) {
        guard let fact = facts.first(where: { $0.id == factId }),
              fact.isVisible && !fact.isAnswered,
              let subjectId = subjectId else {
            // Reset drag state
            factDragStates[factId] = FactDragState(factId: factId)
            draggedOverSubjectId = nil
            return
        }
        
        let isCorrect = fact.correctSubject == subjectId
        
        if isCorrect {
            correctAnswers += 1
        }
        totalAnswered += 1
        
        // Update fact state
        if let factIndex = facts.firstIndex(where: { $0.id == factId }) {
            facts[factIndex].isAnswered = true
            facts[factIndex].wasCorrect = isCorrect
        }
        
        NotificationCenter.default.post(
            name: NSNotification.Name("FactDropped"),
            object: nil,
            userInfo: [
                "factId": factId,
                "subjectId": subjectId,
                "isCorrect": isCorrect
            ]
        )
        
        // Reset drag state
        factDragStates[factId] = FactDragState(factId: factId)
        draggedOverSubjectId = nil
        
        print("🎯 Fact '\(fact.text)' dropped on \(subjectId): \(isCorrect ? "✅" : "❌")")
    }
    
    private func calculateResults() {
        let accuracy = totalAnswered > 0 ? (Double(correctAnswers) / Double(totalAnswered) * 100) : 0
        let isSuccess = accuracy >= 70 // 70% threshold for success
        
        let userAnswer = "\(correctAnswers)/\(totalAnswered) facts correct (\(Int(accuracy))%)"
        
        print("🏁 Final results: \(userAnswer)")
        
        onAnswerSubmitted(userAnswer, isSuccess)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            onNextPuzzle()
        }
    }
}

// MARK: - Audio Intro Screen
struct AudioIntroScreen: View {
    let onBegin: () -> Void
    let onBack: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            // Header with back button
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .font(.title2)
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                }
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            
            Spacer()
            
            VStack(spacing: 40) {
                VStack(spacing: 20) {
                    Text("MEMORY RETENTION")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                    
                    Text("REQUIRES AUDIO")
                        .font(.title3)
                        .fontWeight(.medium)
                        .foregroundColor(.white.opacity(0.6))
                }
                
                // Pulsing audio icon
                PulsingAudioIcon()
                
                Text("LISTEN TO THE ESSAY AND\nTAP FACT AND THE THEN THE SUBJECT IT BELONGS TO")
                    .font(.title3)
                    .fontWeight(.medium)
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            
            Spacer()
            
            Button(action: onBegin) {
                Text("Begin")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color(red: 0.0, green: 0.74, blue: 0.83))
                    .cornerRadius(28)
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 40)
        }
    }
}

struct PulsingAudioIcon: View {
    @State private var scale: CGFloat = 1.0
    
    var body: some View {
        ZStack {
            Circle()
                .fill(Color.white.opacity(0.1))
                .frame(width: 120, height: 120)
            
            Image(systemName: "speaker.wave.2.fill")
                .font(.system(size: 60))
                .foregroundColor(.white)
        }
        .scaleEffect(scale)
        .onAppear {
            withAnimation(
                Animation.easeInOut(duration: 1.0)
                    .repeatForever(autoreverses: true)
            ) {
                scale = 1.2
            }
        }
    }
}

// MARK: - Game Screen
struct MemoryRetentionGameScreen: View {
    let subjects: [RetentionSubject]
    let facts: [RetentionFactModel]
    let factDragStates: [String: FactDragState]
    let audioProgress: TimeInterval
    let audioDuration: TimeInterval
    let isAudioPlaying: Bool
    let draggedOverSubjectId: String?
    let onFactDrag: (String, CGSize) -> Void
    let onFactDrop: (String, String?) -> Void
    let onDragOver: (String?) -> Void
    let onBack: () -> Void
    @Binding var selectedFactId: String?
    let onFactAssignment: (String, String) -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Spacer()
                
                VStack(spacing: 4) {
                    Text("Memory Retention")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text("TAP FACT AND THE THEN THE SUBJECT IT BELONGS TO")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                }
                
                Spacer()
                
                // Placeholder for symmetry
                Color.clear
                    .frame(width: 44, height: 44)
            }
            .padding(.horizontal, 16)
            .padding(.top, 10)
            
            // Audio progress bar
            AudioProgressBar(
                progress: audioDuration > 0 ? audioProgress / audioDuration : 0,
                isPlaying: isAudioPlaying
            )
            .padding(.horizontal, 24)
            .padding(.top, 20)
            
            Spacer()
            
            // Main game area
            ZStack {
                // Subject zones
                VStack {
                    HStack(spacing: 8) {
                        ForEach(subjects, id: \.id) { subject in
                            SubjectZoneView(
                                subject: subject,
                                isDraggedOver: draggedOverSubjectId == subject.id,
                                onFactDropped: { factId in
                                    onFactDrop(factId, subject.id)
                                },
                                onSubjectTapped: { subjectId in
                                    if let factId = selectedFactId {
                                        onFactAssignment(factId, subjectId)  // Use the passed function
                                    }
                                },
                                selectedFactId: selectedFactId
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    
                    Spacer() // Push subjects to top, leave space below for facts
                }
                
                // Floating facts
                ForEach(facts.filter { $0.isVisible && !$0.isAnswered }, id: \.id) { fact in
                        let dragState = factDragStates[fact.id] ?? FactDragState(factId: fact.id)
                        
                        FloatingFactView(
                            fact: fact,
                            isSelected: selectedFactId == fact.id,
                            onSelect: {
                                selectedFactId = fact.id
                            },
                            dragState: dragState,
                            subjects: subjects,
                            onDrag: { offset in
                                onFactDrag(fact.id, offset)
                            },
                            onDrop: { droppedSubjectId in
                                onDragOver(nil)
                                onFactDrop(fact.id, droppedSubjectId)
                            },
                            onDragOver: { subjectId in
                                onDragOver(subjectId)
                            }
                        )
                    }
            }
            
            Spacer()
            
            // Status info
            HStack {
                HStack(spacing: 8) {
                    Circle()
                        .fill(isAudioPlaying ? Color(red: 0.0, green: 0.74, blue: 0.83) : Color.white.opacity(0.3))
                        .frame(width: 8, height: 8)
                    
                    Text(isAudioPlaying ? "Audio Playing..." : "Audio Complete")
                        .font(.subheadline)
                        .foregroundColor(isAudioPlaying ? Color(red: 0.0, green: 0.74, blue: 0.83) : Color.white.opacity(0.5))
                }
                
                Spacer()
                
                let answeredCount = facts.filter { $0.isAnswered }.count
                let totalVisible = facts.filter { $0.isVisible }.count
                Text("\(answeredCount)/\(totalVisible) facts")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.7))
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 24)
        }
        .onAppear {
            print("🧠 Memory retention game screen appeared")
        }
    }
}

// MARK: - Subject Zone View
struct SubjectZoneView: View {
    let subject: RetentionSubject
    let isDraggedOver: Bool
    let onFactDropped: (String) -> Void
    let onSubjectTapped: (String) -> Void
    @State private var isBlinking = false
    let selectedFactId: String?
    
    @State private var feedbackState: String? = nil
    
    var body: some View {
        VStack(spacing: 12) {
            Text(subject.name)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .lineLimit(2)
            
            Text(subject.description)
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
                .lineLimit(3)
            
            if let feedback = feedbackState {
                Text(feedback == "correct" ? "✓" : "✗")
                    .font(.title)
                    .foregroundColor(feedback == "correct" ? .green : .red)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 120)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(
                    feedbackState == "correct" ? Color.green.opacity(0.3) :
                    feedbackState == "incorrect" ? Color.red.opacity(0.3) :
                    isDraggedOver ? Color.yellow.opacity(0.3) :
                    subject.color.opacity(0.2)
                )
                .opacity(isBlinking ? 0.3 : 1.0)
                        .animation(
                            isBlinking ?
                            Animation.easeInOut(duration: 0.2).repeatCount(6, autoreverses: true) :
                            .default,
                            value: isBlinking
                        )
        )
        .scaleEffect(feedbackState == "incorrect" ? 0.95 : 1.0)
        .animation(.easeInOut(duration: 0.2), value: feedbackState)
        .onTapGesture {
            if selectedFactId != nil {
                onSubjectTapped(subject.id)  // Just pass subject.id
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("FactDropped"))) { notification in
            if let userInfo = notification.userInfo,
               let droppedSubjectId = userInfo["subjectId"] as? String,
               let isCorrect = userInfo["isCorrect"] as? Bool,
               droppedSubjectId == subject.id {
                
                feedbackState = isCorrect ? "correct" : "incorrect"
                
                if !isCorrect {
                    isBlinking = true
                }
                
                // Reset feedback after animation
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                    feedbackState = nil
                    isBlinking = false
                }
            }
        }
    }
}

// MARK: - Floating Fact View
struct FloatingFactView: View {
    @ObservedObject var fact: RetentionFactModel
    let isSelected: Bool
    let onSelect: () -> Void
    let dragState: FactDragState
    let subjects: [RetentionSubject]
    let onDrag: (CGSize) -> Void
    let onDrop: (String?) -> Void
    let onDragOver: (String?) -> Void
    @State private var shouldFadeOut: Bool = false
    @State private var bounceOffset: CGSize = .zero
    
    
    @State private var position: CGSize = CGSize(
        width: CGFloat.random(in: -100...100),
        height: CGFloat.random(in: 200...300) // Move facts lower to avoid subject zones
    )
    
    var body: some View {
        // Make fact cards same size as subject zones
        VStack(spacing: 8) {
            // Drag handle at top
            HStack(spacing: 2) {
                ForEach(0..<6, id: \.self) { _ in
                    Rectangle()
                        .fill(Color.gray.opacity(0.6))
                        .frame(width: 12, height: 2)
                }
            }
            .padding(.top, 4)
            
            Spacer()
            
            // Fact text - centered like subject text
            Text(fact.text)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
                .lineLimit(4)
                .fixedSize(horizontal: false, vertical: true)
            
            Spacer()
        }
        .onTapGesture {
            if !fact.isAnswered {
                onSelect()
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(isSelected ? Color.yellow : Color.clear, lineWidth: 3)
        )
        .opacity(fact.isAnswered ? 0 : 1)
        .animation(.easeOut(duration: 0.3), value: fact.isAnswered)
        .frame(width: 120)
        .frame(height: 120) // Same height as subject zones
        .padding(12)
        .onChange(of: fact.isAnswered) { answered in
            if answered {
                // Trigger bounce and fade
                withAnimation(.interpolatingSpring(stiffness: 80, damping: 6)) {
                    bounceOffset = CGSize(width: CGFloat.random(in: -60...60), height: -100)
                }
                withAnimation(.easeOut(duration: 0.4).delay(0.2)) {
                    shouldFadeOut = true
                }
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(dragState.isDragging ? 0.9 : 0.95))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(
                            dragState.isDragging ? Color.blue : Color.gray.opacity(0.3),
                            lineWidth: dragState.isDragging ? 2 : 1
                        )
                )
                .shadow(
                    color: .black.opacity(dragState.isDragging ? 0.3 : 0.1),
                    radius: dragState.isDragging ? 12 : 4,
                    x: 0,
                    y: dragState.isDragging ? 6 : 2
                )
        )
        .scaleEffect(dragState.isDragging ? 1.05 : 1.0)
        .offset(
            x: position.width + dragState.offset.width + bounceOffset.width,
            y: position.height + dragState.offset.height + bounceOffset.height
        )
        .gesture(
            DragGesture()
                .onChanged { value in
                    onDrag(value.translation)
                    
                    let currentPosition = CGPoint(
                        x: position.width + value.translation.width,
                        y: position.height + value.translation.height
                    )
                    
                    let droppedSubject = detectDropTarget(at: currentPosition)
                    onDragOver(droppedSubject?.id)
                }
                .onEnded { value in
                    let finalPosition = CGPoint(
                        x: position.width + value.translation.width,
                        y: position.height + value.translation.height
                    )
                    
                    let droppedSubject = detectDropTarget(at: finalPosition)
                    onDrop(droppedSubject?.id)
                    
                    // Animate back if not dropped on target
                    if droppedSubject == nil {
                        withAnimation(.spring()) {
                            position = CGSize(
                                width: CGFloat.random(in: -100...100),
                                height: CGFloat.random(in: 200...300)
                            )
                        }
                    }
                }
        )
        .animation(.spring(response: 0.3), value: dragState.isDragging)
    }
    
    private func detectDropTarget(at position: CGPoint) -> RetentionSubject? {
        // Improved collision detection for same-sized elements
        let subjectZoneHeight: CGFloat = 120
        let subjectZoneY: CGFloat = 150 // Approximate Y position of subject zones
        
        // Check if dropped in subject zone area
        if position.y >= subjectZoneY && position.y <= (subjectZoneY + subjectZoneHeight) {
            let screenWidth: CGFloat = 400 // Approximate screen width
            let zoneWidth = screenWidth / CGFloat(subjects.count)
            let subjectIndex = Int((position.x + screenWidth/2) / zoneWidth)
            
            if subjects.indices.contains(subjectIndex) {
                return subjects[subjectIndex]
            }
        }
        
        return nil
    }
}

// MARK: - Audio Progress Bar
struct AudioProgressBar: View {
    let progress: Double
    let isPlaying: Bool
    
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Rectangle()
                    .fill(Color.white.opacity(0.1))
                    .frame(height: 4)
                
                Rectangle()
                    .fill(isPlaying ? Color(red: 0.0, green: 0.74, blue: 0.83) : Color.white.opacity(0.3))
                    .frame(width: geometry.size.width * progress, height: 4)
            }
        }
        .frame(height: 4)
        .clipShape(Capsule())
    }
}

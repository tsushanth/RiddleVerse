import SwiftUI
import AVFoundation
import Combine

// MARK: - Data Models
struct MemoryStoryData {
    let storyCard: String
    let question: String
    let options: [String]
    let scenario: String
    let character: String
    let itemCount: Int
    let audioUrl: String?
    let correctItems: [String]
    
    static func parse(from puzzle: Puzzle) -> MemoryStoryData? {
        guard let questionData = puzzle.question.data(using: .utf8),
              let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] else {
            print("❌ MEMORY_STORY: Failed to parse question JSON")
            return nil
        }
        
        let storyCard = questionJson["storyCard"] as? String ?? ""
        let question = questionJson["question"] as? String ?? ""
        let scenario = questionJson["scenario"] as? String ?? "grocery"
        let character = questionJson["character"] as? String ?? "Someone"
        let itemCount = questionJson["itemCount"] as? Int ?? 3
        let audioUrl = questionJson["audioUrl"] as? String
        
        // Parse options array
        var options: [String] = []
        if let optionsArray = questionJson["options"] as? [Any] {
            for option in optionsArray {
                if let optionString = option as? String {
                    options.append(optionString)
                }
            }
        }
        
        // Parse correct items from answer field (separate JSON array)
        var correctItems: [String] = []
        if let answerData = puzzle.answer.data(using: .utf8),
           let correctItemsArray = try? JSONSerialization.jsonObject(with: answerData) as? [String] {
            correctItems = correctItemsArray
        }
        
        print("📊 MEMORY_STORY: Parsed data:")
        print("   Story: \(storyCard)")
        print("   Question: \(question)")
        print("   Options: \(options.count)")
        print("   Correct items: \(correctItems)")
        print("   Audio URL: \(audioUrl ?? "none")")
        
        return MemoryStoryData(
            storyCard: storyCard,
            question: question,
            options: options,
            scenario: scenario,
            character: character,
            itemCount: itemCount,
            audioUrl: audioUrl,
            correctItems: correctItems
        )
    }
}

enum MemoryStoryPhase {
    case audioIntro
    case listening
    case answering
    case feedback
}

// MARK: - Main View
struct MemoryStoryPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Set<String>, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var currentPhase: MemoryStoryPhase = .audioIntro
    @State private var selectedItems: Set<String> = []
    @State private var isAudioPlaying = false
    @State private var audioWaves: [Float] = Array(repeating: 0.1, count: 50)
    @State private var audioPlayer: AVAudioPlayer?
    @State private var waveTimer: Timer?
    @State private var showFeedback = false
    @State private var isCorrect = false
    @State private var userAnswer = ""
    @State private var correctAnswer = ""
    
    private var memoryData: MemoryStoryData? {
        MemoryStoryData.parse(from: puzzle)
    }
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [
                    Color(red: 0.10, green: 0.10, blue: 0.18),
                    Color(red: 0.14, green: 0.13, blue: 0.24),
                    Color(red: 0.06, green: 0.20, blue: 0.38)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            if let data = memoryData {
                switch currentPhase {
                case .audioIntro:
                    MemoryStoryAudioIntroScreen(
                        onBegin: {
                            withAnimation(.easeInOut(duration: 0.3)) {
                                currentPhase = .listening
                            }
                        },
                        onBack: {
                            // Ensure audio is cleaned up when going back
                            cleanupAudio()
                            onExit()
                        }
                    )
                    
                case .listening:
                    AudioListeningScreen(
                        data: data,
                        audioWaves: audioWaves,
                        isPlaying: isAudioPlaying,
                        questionIndex: questionIndex,
                        totalQuestions: totalQuestions,
                        onAudioStarted: {
                            print("🎵 MEMORY_STORY: onAudioStarted called")
                            startAudioPlayback(url: data.audioUrl)
                        },
                        onAudioCompleted: {
                            print("🎵 MEMORY_STORY: onAudioCompleted called")
                            withAnimation(.easeInOut(duration: 0.3)) {
                                currentPhase = .answering
                            }
                        },
                        onBack: {
                            // Stop audio when going back
                            cleanupAudio()
                            onExit()
                        }
                    )
                    
                case .answering:
                    AnswerSelectionScreen(
                        data: data,
                        selectedItems: selectedItems,
                        questionIndex: questionIndex,
                        totalQuestions: totalQuestions,
                        onItemToggle: { item in
                            if selectedItems.contains(item) {
                                selectedItems.remove(item)
                            } else {
                                selectedItems.insert(item)
                            }
                        },
                        onSubmit: {
                            submitAnswer(data: data)
                        },
                        onBack: {
                            // Ensure audio is cleaned up when going back
                            cleanupAudio()
                            onExit()
                        }
                    )
                    
                case .feedback:
                    EmptyView() // Handled by feedback overlay
                }
            } else {
                // Error state
                VStack(spacing: 20) {
                    Text("Error loading Memory Story puzzle")
                        .foregroundColor(.red)
                        .font(.title2)
                    
                    Button("Back") {
                        onExit()
                    }
                    .padding()
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
            }
            
            // Feedback overlay
            if showFeedback {
                MemoryStoryFeedbackOverlay(
                    isCorrect: isCorrect,
                    userAnswer: userAnswer,
                    correctAnswer: correctAnswer,
                    onContinue: {
                        showFeedback = false
                        if isCorrect {
                            onNextPuzzle()
                        } else {
                            // Reset for retry
                            selectedItems = []
                            currentPhase = .answering
                        }
                    }
                )
            }
        }
        .onAppear {
            print("🧠 MemoryStoryPuzzleView appeared")
            setupAudioSession()
        }
        .onDisappear {
            cleanupAudio()
        }
    }
    
    private func setupAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("❌ Failed to setup audio session: \(error)")
        }
    }
    
    private func startAudioPlayback(url: String?) {
        guard let urlString = url, !urlString.isEmpty,
              let audioURL = URL(string: urlString) else {
            print("⚠️ No audio URL provided, using fallback timing")
            simulateAudioPlayback()
            return
        }
        
        print("🔊 Starting audio playback: \(audioURL)")
        
        // Configure audio session first (like Memory Sequencing)
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            print("✅ Audio session configured successfully")
        } catch {
            print("❌ Failed to setup audio session: \(error)")
        }
        
        // Download and play audio
        URLSession.shared.dataTask(with: audioURL) { data, response, error in
            print("🎵 URLSession callback received")
            
            if let error = error {
                print("❌ Audio download failed: \(error)")
                DispatchQueue.main.async {
                    simulateAudioPlayback()
                }
                return
            }
            
            if let httpResponse = response as? HTTPURLResponse {
                print("🎵 HTTP Response status: \(httpResponse.statusCode)")
            }
            
            guard let data = data else {
                print("❌ No audio data received")
                DispatchQueue.main.async {
                    simulateAudioPlayback()
                }
                return
            }
            
            print("🎵 Audio data received, size: \(data.count) bytes")
            
            DispatchQueue.main.async {
                do {
                    print("🎵 Creating AVAudioPlayer with data")
                    audioPlayer = try AVAudioPlayer(data: data)
                    audioPlayer?.delegate = MemoryStoryAudioPlayerDelegate { [self] in
                        stopAudioPlayback()
                    }
                    
                    print("🎵 Starting audio playback")
                    let success = audioPlayer?.play() ?? false
                    print("🎵 Audio play() returned: \(success)")
                    
                    if success {
                        isAudioPlaying = true
                        startWaveAnimation()
                        print("✅ Audio playback started successfully")
                        print("🎵 Audio duration: \(audioPlayer?.duration ?? 0) seconds")
                        
                        // Fallback completion check
                        let duration = audioPlayer?.duration ?? 0.0
                        DispatchQueue.main.asyncAfter(deadline: .now() + duration + 0.5) {
                            if audioPlayer?.isPlaying == false {
                                print("⏱️ Audio fallback completion triggered")
                                stopAudioPlayback()
                            }
                        }
                    } else {
                        print("❌ Audio play() failed")
                        simulateAudioPlayback()
                    }
                } catch {
                    print("❌ Audio playback failed: \(error)")
                    simulateAudioPlayback()
                }
            }
        }.resume()
        
        print("🎵 URLSession.dataTask started")
    }
    
    private func simulateAudioPlayback() {
        isAudioPlaying = true
        startWaveAnimation()
        
        // Simulate 4 seconds of audio
        DispatchQueue.main.asyncAfter(deadline: .now() + 4.0) {
            stopAudioPlayback()
        }
    }
    
    private func stopAudioPlayback() {
        isAudioPlaying = false
        audioPlayer?.stop()
        audioPlayer = nil
        stopWaveAnimation()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            withAnimation(.easeInOut(duration: 0.3)) {
                currentPhase = .answering
            }
        }
    }
    
    private func startWaveAnimation() {
        waveTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
            withAnimation(.easeInOut(duration: 0.1)) {
                audioWaves = generateAnimatedWaves()
            }
        }
    }
    
    private func stopWaveAnimation() {
        waveTimer?.invalidate()
        waveTimer = nil
        withAnimation(.easeOut(duration: 0.5)) {
            audioWaves = Array(repeating: 0.1, count: 50)
        }
    }
    
    private func generateAnimatedWaves() -> [Float] {
        return (0..<50).map { _ in Float.random(in: 0.2...0.8) }
    }
    
    private func submitAnswer(data: MemoryStoryData) {
        let correctCount = selectedItems.intersection(Set(data.correctItems)).count
        let incorrectCount = selectedItems.count - correctCount
        let missedCount = data.correctItems.count - correctCount
        
        isCorrect = correctCount == data.correctItems.count && incorrectCount == 0
        
        userAnswer = selectedItems.sorted().joined(separator: ", ")
        correctAnswer = data.correctItems.sorted().joined(separator: ", ")
        
        print("📝 Memory story answer submitted:")
        print("   User answer: \(userAnswer)")
        print("   Correct answer: \(correctAnswer)")
        print("   Is correct: \(isCorrect)")
        
        onAnswerSubmitted(selectedItems, isCorrect)
        
        showFeedback = true
    }
    
    private func cleanupAudio() {
        audioPlayer?.stop()
        audioPlayer = nil
        waveTimer?.invalidate()
        waveTimer = nil
    }
}

// MARK: - Memory Story Audio Intro Screen
struct MemoryStoryAudioIntroScreen: View {
    let onBegin: () -> Void
    let onBack: () -> Void
    
    @State private var pulseScale: CGFloat = 1.0
    
    var body: some View {
        VStack(spacing: 40) {
            // Back button
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding()
                }
                Spacer()
            }
            
            Spacer()
            
            Text("THIS GAME REQUIRES AUDIO")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white.opacity(0.8))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            
            // Pulsing audio icon
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.1))
                    .frame(width: 120, height: 120)
                
                Image(systemName: "speaker.wave.2.fill")
                    .font(.system(size: 60))
                    .foregroundColor(.white)
            }
            .scaleEffect(pulseScale)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                    pulseScale = 1.2
                }
            }
            
            Text("MAKE SURE YOU CAN\nHEAR THE TONE CLEARLY")
                .font(.body)
                .fontWeight(.medium)
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
                .lineSpacing(4)
            
            Spacer()
            
            // Begin button
            Button(action: onBegin) {
                Text("Begin")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        LinearGradient(
                            colors: [Color(red: 0.0, green: 0.737, blue: 0.831), Color(red: 0.0, green: 0.6, blue: 0.7)],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(28)
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 40)
        }
    }
}

// MARK: - Audio Listening Screen
struct AudioListeningScreen: View {
    let data: MemoryStoryData
    let audioWaves: [Float]
    let isPlaying: Bool
    let questionIndex: Int
    let totalQuestions: Int
    let onAudioStarted: () -> Void
    let onAudioCompleted: () -> Void
    let onBack: () -> Void
    
    var titleText: String {
        if data.storyCard.localizedCaseInsensitiveContains("business") {
            return "BUSINESS TRAVEL"
        } else if data.storyCard.localizedCaseInsensitiveContains("pack") {
            return "PACKING ESSENTIALS"
        } else if data.storyCard.localizedCaseInsensitiveContains("grocery") || data.storyCard.localizedCaseInsensitiveContains("shop") {
            return "SHOPPING LIST"
        } else if data.storyCard.localizedCaseInsensitiveContains("recipe") || data.storyCard.localizedCaseInsensitiveContains("cook") {
            return "COOKING INGREDIENTS"
        } else {
            return "MEMORY CHALLENGE"
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Top bar with back button and progress
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding()
                }
                
                Spacer()
                
                VStack(alignment: .trailing) {
                    Text("Puzzle \(questionIndex + 1) of \(totalQuestions)")
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    // Progress bar
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            Rectangle()
                                .fill(Color.white.opacity(0.3))
                                .frame(height: 4)
                                .cornerRadius(2)
                            
                            Rectangle()
                                .fill(Color.white)
                                .frame(width: geometry.size.width * CGFloat(questionIndex + 1) / CGFloat(totalQuestions), height: 4)
                                .cornerRadius(2)
                        }
                    }
                    .frame(height: 4)
                    .frame(width: 120)
                }
                .padding(.trailing)
            }
            .padding(.top, 8)
            
            Spacer()
            
            // Title
            Text(titleText)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white.opacity(0.9))
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            Spacer().frame(height: 40)
            
            // Audio wave visualization
            MemoryStoryAudioWaveView(waves: audioWaves, isPlaying: isPlaying)
                .frame(height: 120)
                .padding(.horizontal, 40)
            
            Spacer().frame(height: 40)
            
            // Status text
            Text(isPlaying ? "LISTENING..." : "LISTEN CAREFULLY")
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(isPlaying ? Color(red: 0.0, green: 0.737, blue: 0.831) : .white.opacity(0.7))
                .multilineTextAlignment(.center)
            
            // Story card as backup
            if !data.storyCard.isEmpty {
                VStack {
                    Spacer().frame(height: 20)
                    
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.1))
                        .overlay(
                            Text(data.storyCard)
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.8))
                                .multilineTextAlignment(.center)
                                .padding(16)
                        )
                        .padding(.horizontal, 32)
                }
            }
            
            // Audio status
            if let audioUrl = data.audioUrl, !audioUrl.isEmpty {
                Spacer().frame(height: 16)
                
                HStack(spacing: 8) {
                    Image(systemName: "speaker.wave.2.fill")
                        .font(.caption)
                        .foregroundColor(isPlaying ? Color(red: 0.0, green: 0.737, blue: 0.831) : .white.opacity(0.5))
                    
                    Text(isPlaying ? "Playing audio..." : "Audio ready")
                        .font(.caption)
                        .foregroundColor(isPlaying ? Color(red: 0.0, green: 0.737, blue: 0.831) : .white.opacity(0.5))
                }
            }
            
            Spacer()
        }
        .onAppear {
            // Auto-start audio after a brief delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                onAudioStarted()
            }
        }
    }
}

// MARK: - Answer Selection Screen
struct AnswerSelectionScreen: View {
    let data: MemoryStoryData
    let selectedItems: Set<String>
    let questionIndex: Int
    let totalQuestions: Int
    let onItemToggle: (String) -> Void
    let onSubmit: () -> Void
    let onBack: () -> Void
    
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 2)
    
    var body: some View {
        VStack(spacing: 0) {
            // Top bar
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding()
                }
                
                Spacer()
                
                VStack(alignment: .trailing) {
                    Text("Puzzle \(questionIndex + 1) of \(totalQuestions)")
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    // Progress bar
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            Rectangle()
                                .fill(Color.white.opacity(0.3))
                                .frame(height: 4)
                                .cornerRadius(2)
                            
                            Rectangle()
                                .fill(Color.white)
                                .frame(width: geometry.size.width * CGFloat(questionIndex + 1) / CGFloat(totalQuestions), height: 4)
                                .cornerRadius(2)
                        }
                    }
                    .frame(height: 4)
                    .frame(width: 120)
                }
                .padding(.trailing)
            }
            .padding(.top, 8)
            
            Spacer().frame(height: 20)
            
            // Question
            Text(data.question)
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            
            Spacer().frame(height: 30)
            
            // Options grid
            ScrollView {
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(data.options, id: \.self) { option in
                        MemoryOptionCard(
                            option: option,
                            isSelected: selectedItems.contains(option),
                            onTap: { onItemToggle(option) }
                        )
                    }
                }
                .padding(.horizontal, 16)
            }
            
            Spacer().frame(height: 20)
            
            // Progress indicator
            Text("Selected: \(selectedItems.count) / \(data.correctItems.count) items")
                .font(.caption)
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
            
            Spacer().frame(height: 16)
            
            // Submit button
            Button(action: onSubmit) {
                Text("SUBMIT")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        Group {
                            if selectedItems.isEmpty {
                                Color.gray.opacity(0.3)
                            } else {
                                LinearGradient(
                                    colors: [Color(red: 0.0, green: 0.737, blue: 0.831), Color(red: 0.0, green: 0.6, blue: 0.7)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            }
                        }
                    )
                    .cornerRadius(28)
            }
            .disabled(selectedItems.isEmpty)
            .padding(.horizontal, 24)
            .padding(.bottom, 30)
        }
    }
}

// MARK: - Memory Option Card
struct MemoryOptionCard: View {
    let option: String
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack {
                Text(option)
                    .font(.subheadline)
                    .fontWeight(isSelected ? .bold : .medium)
                    .foregroundColor(isSelected ? Color(red: 0.0, green: 0.737, blue: 0.831) : .white)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 80)
            .padding(8)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isSelected ? Color(red: 0.0, green: 0.737, blue: 0.831).opacity(0.2) : Color.white.opacity(0.1))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(
                                isSelected ? Color(red: 0.0, green: 0.737, blue: 0.831) : Color.clear,
                                lineWidth: 2
                            )
                    )
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Memory Story Audio Wave View
struct MemoryStoryAudioWaveView: View {
    let waves: [Float]
    let isPlaying: Bool
    
    var body: some View {
        HStack(alignment: .center, spacing: 2) {
            ForEach(0..<waves.count, id: \.self) { index in
                RoundedRectangle(cornerRadius: 1)
                    .fill(isPlaying ? Color(red: 0.0, green: 0.737, blue: 0.831) : Color.white.opacity(0.3))
                    .frame(width: 3)
                    .frame(height: CGFloat(waves[index]) * 80 + 10)
                    .animation(.easeInOut(duration: 0.1), value: waves[index])
            }
        }
    }
}

// MARK: - Memory Story Feedback Overlay
struct MemoryStoryFeedbackOverlay: View {
    let isCorrect: Bool
    let userAnswer: String
    let correctAnswer: String
    let onContinue: () -> Void
    
    var body: some View {
        ZStack {
            Color.black.opacity(0.8)
                .ignoresSafeArea()
            
            VStack(spacing: 24) {
                // Icon and title
                VStack(spacing: 16) {
                    Image(systemName: isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(isCorrect ? .green : .red)
                    
                    Text(isCorrect ? "Correct!" : "Incorrect")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
                
                // Answer details
                if !isCorrect {
                    VStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Your answer:")
                                .font(.caption)
                                .foregroundColor(.gray)
                            Text(userAnswer.isEmpty ? "No items selected" : userAnswer)
                                .font(.subheadline)
                                .foregroundColor(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(Color.red.opacity(0.2))
                                .cornerRadius(8)
                        }
                        
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Correct answer:")
                                .font(.caption)
                                .foregroundColor(.gray)
                            Text(correctAnswer)
                                .font(.subheadline)
                                .foregroundColor(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(Color.green.opacity(0.2))
                                .cornerRadius(8)
                        }
                    }
                }
                
                // Continue button
                Button(action: onContinue) {
                    Text(isCorrect ? "Continue" : "Try Again")
                        .font(.headline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            LinearGradient(
                                colors: [Color(red: 0.0, green: 0.737, blue: 0.831), Color(red: 0.0, green: 0.6, blue: 0.7)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(25)
                }
            }
            .padding(32)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color(.systemBackground))
                    .shadow(radius: 20)
            )
            .padding(.horizontal, 40)
        }
    }
}

// MARK: - Memory Story Audio Player Delegate
class MemoryStoryAudioPlayerDelegate: NSObject, AVAudioPlayerDelegate {
    private let onFinished: () -> Void
    
    init(onFinished: @escaping () -> Void) {
        self.onFinished = onFinished
    }
    
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        onFinished()
    }
}

//
//  SequenceItem.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/12/25.
//


import SwiftUI
import AVFoundation
import Foundation

// MARK: - Data Models
struct SequenceItem: Identifiable, Codable {
    let id: String
    let text: String
    let partNumber: Int
    let correctOrder: Int
    var isCorrect: Bool = false
    var isIncorrect: Bool = false
}

enum SequencingPhase {
    case audioIntro
    case partOneAudio
    case partOneSequence
    case partOneFeedback
    case partTwoIntro
    case partTwoAudio
    case partTwoSequence
    case partTwoFeedback
    case finalSequence
    case finalFeedback
}

// MARK: - Main Memory Sequencing View
struct MemorySequencingPuzzleView: View {
    let puzzleData: String
    let correctAnswer: String
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: () -> Void
    let onBack: () -> Void
    
    @State private var currentPhase: SequencingPhase = .audioIntro
    @State private var currentItems: [SequenceItem] = []
    @State private var audioPlayer: AVAudioPlayer?
    @State private var isAudioPlaying = false
    @State private var audioWaves: [Float] = Array(repeating: 0.1, count: 50)
    @State private var draggedItemIndex: Int? = nil
    @State private var dragOffset: CGSize = .zero
    
    // Parsed data
    @State private var topic = ""
    @State private var description = ""
    @State private var allItems: [SequenceItem] = []
    @State private var partOneAudioUrl: String?
    @State private var partTwoAudioUrl: String?
    @State private var isValidData = false
    @State private var hasStartedAudio = false
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [
                    Color(red: 0.1, green: 0.1, blue: 0.18),
                    Color(red: 0.09, green: 0.13, blue: 0.24),
                    Color(red: 0.06, green: 0.2, blue: 0.38)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            if !isValidData {
                VStack {
                    ProgressView()
                    Text("Loading puzzle...")
                        .foregroundColor(.white)
                        .padding(.top)
                }
            } else {
                currentPhaseView
            }
        }
        .onAppear {
            parsePuzzleData()
        }
        .onDisappear {
            print("🧹 SEQUENCING: View disappearing, cleaning up audio")
            cleanupAudio()
        }
    }
    
    @ViewBuilder
    private var currentPhaseView: some View {
        switch currentPhase {
        case .audioIntro:
            AudioIntroView(
                onBegin: {
                    withAnimation {
                        currentPhase = .partOneAudio
                    }
                },
                onBack: {
                                cleanupAudio()
                                onBack()
                            }
            )
            
        case .partOneAudio:
            AudioListeningView(
                title: topic,
                subtitle: "PART ONE OF TWO",
                description: description,
                audioUrl: partOneAudioUrl,
                audioWaves: audioWaves,
                isPlaying: isAudioPlaying,
                onAudioCompleted: {
                            isAudioPlaying = false
                            // Set up part 1 items for sequencing (shuffled)
                            let part1Items = allItems.filter { $0.partNumber == 1 }.shuffled()
                            currentItems = part1Items
                            
                            print("🎵 Part 1 audio completed, moving to sequencing")
                            print("🔄 Part 1 items: \(part1Items.map { $0.text })")
                            
                            withAnimation {
                                currentPhase = .partOneSequence
                            }
                        },
                onCleanup: cleanupAudio
            )
            
        case .partOneSequence:
            SequencingView(
                title: topic,
                subtitle: "PART ONE - ARRANGE IN ORDER",
                items: $currentItems,
                onSubmit: {
                    let isCorrect = checkSequenceCorrectness(items: currentItems)
                    if !isCorrect {
                        currentItems = showFeedbackAndCorrectOrder(items: currentItems)
                    }
                    withAnimation {
                        currentPhase = .partOneFeedback
                    }
                },
                onBack: {
                                cleanupAudio() // ADD THIS
                                onBack()
                            }
            )
            
        case .partOneFeedback:
            FeedbackView(
                title: "PART ONE - CORRECT ORDER",
                items: currentItems,
                onContinue: {
                    withAnimation {
                        currentPhase = .partTwoIntro
                    }
                }
            )
            
        case .partTwoIntro:
            PartTwoIntroView(
                topic: topic,
                onContinue: {
                    withAnimation {
                        currentPhase = .partTwoAudio
                    }
                }
            )
            
        case .partTwoAudio:
            AudioListeningView(
                title: topic,
                subtitle: "PART TWO OF TWO",
                description: description,
                audioUrl: partTwoAudioUrl,
                audioWaves: audioWaves,
                isPlaying: isAudioPlaying,
                onAudioCompleted: {
                    isAudioPlaying = false
                    // Set up part 2 items for sequencing (shuffled)
                    let part2Items = allItems.filter { $0.partNumber == 2 }.shuffled()
                    currentItems = part2Items
                    withAnimation {
                        currentPhase = .partTwoSequence
                    }
                },
                onCleanup: cleanupAudio
            )
            
        case .partTwoSequence:
            SequencingView(
                title: topic,
                subtitle: "PART TWO - ARRANGE IN ORDER",
                items: $currentItems,
                onSubmit: {
                    let isCorrect = checkSequenceCorrectness(items: currentItems)
                    if !isCorrect {
                        currentItems = showFeedbackAndCorrectOrder(items: currentItems)
                    }
                    withAnimation {
                        currentPhase = .partTwoFeedback
                    }
                },
                onBack: {
                                cleanupAudio() // ADD THIS
                                onBack()
                            }
            )
            
        case .partTwoFeedback:
            FeedbackView(
                title: "PART TWO - CORRECT ORDER",
                items: currentItems,
                onContinue: {
                    // Set up final sequence with all items shuffled
                    currentItems = allItems.shuffled()
                    withAnimation {
                        currentPhase = .finalSequence
                    }
                }
            )
            
        case .finalSequence:
            SequencingView(
                title: topic,
                subtitle: "COMPLETE SEQUENCE",
                items: $currentItems,
                onSubmit: {
                    let isCorrect = checkSequenceCorrectness(items: currentItems)
                    
                    // Submit answer and show feedback
                    onSubmitAnswer(isCorrect)
                    
                    if isCorrect {
                        cleanupAudio()
                        fetchNextPuzzle()
                    } else {
                        currentItems = showFeedbackAndCorrectOrder(items: allItems)
                        withAnimation {
                            currentPhase = .finalFeedback
                        }
                    }
                },
                onBack: {
                                cleanupAudio() // ADD THIS
                                onBack()
                            }
            )
            
        case .finalFeedback:
            FeedbackView(
                title: "COMPLETE SEQUENCE - CORRECT ORDER",
                items: allItems.sorted { $0.correctOrder < $1.correctOrder },
                onContinue: {
                    fetchNextPuzzle()
                }
            )
        }
    }
    
    private func cleanupAudio() {
        print("🧹 SEQUENCING: Cleaning up audio")
        audioPlayer?.stop()
        audioPlayer?.delegate = nil
        audioPlayer = nil
        isAudioPlaying = false
        
        // Deactivate audio session
        do {
            try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            print("✅ SEQUENCING: Audio session deactivated")
        } catch {
            print("❌ SEQUENCING: Failed to deactivate audio session: \(error)")
        }
    }
    
    // MARK: - Data Parsing
    private func parsePuzzleData() {
        do {
            guard let data = puzzleData.data(using: .utf8),
                  let questionData = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                print("❌ Failed to parse sequencing puzzle data")
                return
            }
            
            topic = questionData["topic"] as? String ?? ""
            description = questionData["description"] as? String ?? ""
            
            // IMPORTANT: Extract the audio URLs
            partOneAudioUrl = questionData["partOneAudioUrl"] as? String
            partTwoAudioUrl = questionData["partTwoAudioUrl"] as? String
            
            print("🎵 SEQUENCING: Extracted audio URLs:")
            print("   Part 1: \(partOneAudioUrl ?? "None")")
            print("   Part 2: \(partTwoAudioUrl ?? "None")")
            
            // Parse items array
            if let itemsArray = questionData["items"] as? [[String: Any]] {
                allItems = itemsArray.compactMap { itemData in
                    guard let id = itemData["id"] as? String,
                          let name = itemData["name"] as? String,
                          let partNumber = itemData["partNumber"] as? Int,
                          let correctOrder = itemData["correctOrder"] as? Int else {
                        return nil
                    }
                    
                    return SequenceItem(
                        id: id,
                        text: name,
                        partNumber: partNumber,
                        correctOrder: correctOrder
                    )
                }
            }
            
            isValidData = !allItems.isEmpty
            
        } catch {
            print("❌ Failed to parse sequencing puzzle data: \(error)")
            isValidData = false
        }
    }
    
    // MARK: - Helper Functions
    private func checkSequenceCorrectness(items: [SequenceItem]) -> Bool {
        let partNumbers = Set(items.map { $0.partNumber })
        
        if partNumbers.count == 1 {
            // Single part - check relative order within the part
            let sortedItems = items.sorted { $0.correctOrder < $1.correctOrder }
            return items.elementsEqual(sortedItems) { $0.id == $1.id }
        } else {
            // Multiple parts - check global chronological order
            return items.enumerated().allSatisfy { index, item in
                item.correctOrder == index + 1
            }
        }
    }
    
    private func showFeedbackAndCorrectOrder(items: [SequenceItem]) -> [SequenceItem] {
        return items.sorted { $0.correctOrder < $1.correctOrder }.map { item in
            var updatedItem = item
            updatedItem.isCorrect = true
            updatedItem.isIncorrect = false
            return updatedItem
        }
    }
}

// MARK: - Audio Intro View
struct AudioIntroView: View {
    let onBegin: () -> Void
    let onBack: () -> Void
    
    @State private var scale: CGFloat = 1.0
    
    var body: some View {
        VStack(spacing: 30) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.white)
                        .font(.title2)
                }
                Spacer()
            }
            .padding()
            
            Spacer()
            
            Text("THIS GAME REQUIRES AUDIO")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white.opacity(0.8))
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            // Audio icon with pulsing animation
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.1))
                    .frame(width: 120, height: 120)
                
                Image(systemName: "speaker.wave.3")
                    .font(.system(size: 60))
                    .foregroundColor(.white)
            }
            .scaleEffect(scale)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                    scale = 1.2
                }
            }
            
            Text("LISTEN TO TWO PARTS AND\nSEQUENCE THE EVENTS")
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
                .lineSpacing(6)
            
            Spacer()
            
            Button(action: onBegin) {
                Text("Begin")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.cyan)
                    .cornerRadius(28)
            }
            .padding(.horizontal, 32)
        }
    }
}

// MARK: - Audio Listening View
struct AudioListeningView: View {
    let title: String
    let subtitle: String
    let description: String
    let audioUrl: String?
    let audioWaves: [Float]
    let isPlaying: Bool
    let onAudioCompleted: () -> Void
    let onCleanup: () -> Void
    
    @State private var audioDelegate: AudioPlayerDelegate? = nil
    @State private var audioPlayer: AVAudioPlayer?
    @State private var hasStartedAudio = false
    
    var body: some View {
        VStack(spacing: 40) {
            Spacer()
            
            Text(title)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white.opacity(0.9))
                .multilineTextAlignment(.center)
            
            Text(subtitle)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
            
            // Audio wave visualization
            AudioWaveView(waves: audioWaves, isPlaying: isPlaying)
                .frame(height: 120)
                .padding(.horizontal, 40)
            
            Text(isPlaying ? "LISTENING..." : "LISTEN CAREFULLY")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(isPlaying ? .cyan : .white.opacity(0.7))
                .multilineTextAlignment(.center)
            
            // Description card
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.1))
                .overlay(
                    Text(description)
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(16)
                )
                .padding(.horizontal, 32)
            
            Spacer()
        }
        .onDisappear {
                    print("🧹 SEQUENCING: AudioListeningView disappearing, calling cleanup")
                    onCleanup() // ADD THIS
                }
        .onAppear {
            print("🎵 AudioListeningView appeared")
            print("🎵 Audio URL provided: \(audioUrl ?? "nil")")
            print("🎵 Title: \(title)")
            print("🎵 Subtitle: \(subtitle)")
            print("👀 onAppear triggered for AudioListeningView")
            print("🎵 hasStartedAudio = \(hasStartedAudio)")
            if !hasStartedAudio {
                hasStartedAudio = true
                startAudio()
            }
        }
    }
    
    private func startAudio() {
        print("🎵 startAudio() called")
        print("🎵 audioUrl: \(audioUrl ?? "nil")")
        let delegate = AudioPlayerDelegate(onCompletion: onAudioCompleted)
        self.audioDelegate = delegate
        self.audioPlayer?.delegate = delegate
        
        // ADD THIS: Configure audio session first
        do {
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
                try AVAudioSession.sharedInstance().setActive(true)
                print("✅ Audio session configured successfully")
            } catch {
                print("❌ Failed to setup audio session: \(error)")
            }
            
            guard let audioUrlString = audioUrl,
                  let url = URL(string: audioUrlString) else {
                print("❌ Invalid audio URL, using fallback")
                DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
                    onAudioCompleted()
                }
                return
            }
            
            print("🎵 Starting URLSession.dataTask for: \(url)")
        
        // Load and play audio
        URLSession.shared.dataTask(with: url) { data, response, error in
            print("🎵 URLSession callback received")
            
            if let error = error {
                print("❌ URLSession error: \(error.localizedDescription)")
                DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
                    print("🎵 Error fallback audio completed")
                    onAudioCompleted()
                }
                return
            }
            
            if let httpResponse = response as? HTTPURLResponse {
                print("🎵 HTTP Response status: \(httpResponse.statusCode)")
            }
            
            guard let data = data else {
                print("❌ No data received from audio URL")
                DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
                    print("🎵 No data fallback audio completed")
                    onAudioCompleted()
                }
                return
            }
            
            print("🎵 Audio data received, size: \(data.count) bytes")
            
            print("🎵 Audio data received, size: \(data.count) bytes")
                    
                    DispatchQueue.main.async {
                        do {
                            print("🎵 Creating AVAudioPlayer with data")
                            
                            // Store the player in the state variable
                            self.audioPlayer = try AVAudioPlayer(data: data)
                            self.audioPlayer?.delegate = self.audioDelegate

                            print("🎵 AudioPlayerDelegate created")
                            print("🎵 Starting audio playback")
                            
                            let success = self.audioPlayer?.play() ?? false
                            print("🎵 Audio play() returned: \(success)")
                            
                            if success {
                                print("🎵 Audio duration: \(self.audioPlayer?.duration ?? 0) seconds")
                            } else {
                                print("❌ Audio play() failed")
                                DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
                                    onAudioCompleted()
                                }
                            }
                            let duration = self.audioPlayer?.duration ?? 0.0
                            DispatchQueue.main.asyncAfter(deadline: .now() + duration + 0.2) {
                                if self.audioPlayer?.isPlaying == false {
                                    print("⏱️ Short audio fallback triggered")
                                    onAudioCompleted()
                                }
                            }
                            
                        } catch {
                            print("❌ Failed to create AVAudioPlayer: \(error.localizedDescription)")
                            DispatchQueue.main.asyncAfter(deadline: .now() + 5.0) {
                                onAudioCompleted()
                            }
                        }
                    }
        }.resume()
        
        print("🎵 URLSession.dataTask started")
    }
}

class AudioPlayerDelegate: NSObject, AVAudioPlayerDelegate {
    let onCompletion: () -> Void
    
    init(onCompletion: @escaping () -> Void) {
        self.onCompletion = onCompletion
        super.init()
        print("🎵 AudioPlayerDelegate created")
    }
    
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        print("🎵 audioPlayerDidFinishPlaying called, successfully: \(flag)")
        onCompletion()
    }
    
    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        print("❌ audioPlayerDecodeErrorDidOccur: \(error?.localizedDescription ?? "unknown error")")
        onCompletion()
    }
}

// MARK: - Audio Wave View
struct AudioWaveView: View {
    let waves: [Float]
    let isPlaying: Bool
    
    var body: some View {
        HStack(spacing: 2) {
            ForEach(waves.indices, id: \.self) { index in
                Rectangle()
                    .fill(isPlaying ? Color.cyan : Color.white.opacity(0.3))
                    .frame(width: 3)
                    .frame(height: CGFloat(waves[index]) * 100)
                    .cornerRadius(1.5)
            }
        }
    }
}

// MARK: - Sequencing View
struct SequencingView: View {
    let title: String
    let subtitle: String
    @Binding var items: [SequenceItem]
    let onSubmit: () -> Void
    let onBack: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.white)
                        .font(.title2)
                }
                Spacer()
            }
            .padding()

            Text(title)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)

            Text(subtitle)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)

            Text("DRAG TO REORDER")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.white.opacity(0.5))

            // ✅ DRAGGABLE LIST WITH TEXT
            List {
                ForEach(items) { item in
                    HStack(spacing: 16) {
                        Image(systemName: "line.3.horizontal")
                            .foregroundColor(.white.opacity(0.6))

                        Text(item.text)
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.white)

                        Spacer()
                    }
                    .padding(.vertical, 12)
                    .listRowBackground(Color.white.opacity(0.1))
                    .cornerRadius(8)
                }
                .onMove(perform: moveItem)
            }
            .listStyle(PlainListStyle())
            .environment(\.editMode, .constant(.active))
            .frame(maxHeight: 400)
            .cornerRadius(12)

            Button(action: onSubmit) {
                Text("SUBMIT")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.cyan)
                    .cornerRadius(28)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 20)
        }
        .background(Color.clear)
    }

    private func moveItem(from source: IndexSet, to destination: Int) {
        items.move(fromOffsets: source, toOffset: destination)
    }
}

// MARK: - Draggable Sequence Item View
struct DraggableSequenceItemView: View {
    let item: SequenceItem
    let index: Int
    let onMove: (IndexSet, Int) -> Void
    
    var body: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(borderColor, lineWidth: item.isCorrect || item.isIncorrect ? 2 : 0)
            )
            .overlay(
                HStack {
                    Image(systemName: "line.3.horizontal")
                        .foregroundColor(.white.opacity(0.7))
                        .font(.system(size: 16))
                    
                    Text("\(index + 1).")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white.opacity(0.7))
                        .frame(width: 24, alignment: .leading)
                    
                    Text(item.text)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.leading)
                    
                    Spacer()
                }
                .padding(16)
            )
            .frame(height: 80)
    }
    
    private var backgroundColor: Color {
        if item.isCorrect {
            return Color.green.opacity(0.2)
        } else if item.isIncorrect {
            return Color.red.opacity(0.2)
        } else {
            return Color.white.opacity(0.1)
        }
    }
    
    private var borderColor: Color {
        if item.isCorrect {
            return Color.green
        } else if item.isIncorrect {
            return Color.red
        } else {
            return Color.clear
        }
    }
}

// MARK: - Feedback View
struct FeedbackView: View {
    let title: String
    let items: [SequenceItem]
    let onContinue: () -> Void
    
    var body: some View {
        VStack(spacing: 40) {
            Text(title)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(.top, 60)
            
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(items.indices, id: \.self) { index in
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.green.opacity(0.2))
                            .overlay(
                                HStack {
                                    Text("\(index + 1).")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.green)
                                        .frame(width: 24, alignment: .leading)
                                    
                                    Text(items[index].text)
                                        .font(.system(size: 16, weight: .medium))
                                        .foregroundColor(.white)
                                        .multilineTextAlignment(.leading)
                                    
                                    Spacer()
                                }
                                .padding(16)
                            )
                            .frame(height: 80)
                    }
                }
                .padding(.horizontal, 16)
            }
            
            Button(action: onContinue) {
                Text("CONTINUE")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.cyan)
                    .cornerRadius(28)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 20)
        }
    }
}

// MARK: - Part Two Intro View
struct PartTwoIntroView: View {
    let topic: String
    let onContinue: () -> Void
    
    @State private var rotationAngle: Double = 0
    
    var body: some View {
        VStack(spacing: 60) {
            Spacer()
            
            Text(topic.uppercased())
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.white.opacity(0.9))
                .multilineTextAlignment(.center)
            
            Text("PART TWO OF TWO")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
            
            // Animated decorative element
            ZStack {
                Circle()
                    .stroke(Color.cyan.opacity(0.3), lineWidth: 3)
                    .frame(width: 100, height: 100)
                
                Circle()
                    .fill(Color.cyan.opacity(0.5))
                    .frame(width: 50, height: 50)
            }
            .rotationEffect(.degrees(rotationAngle))
            .onAppear {
                withAnimation(.linear(duration: 3.0).repeatForever(autoreverses: false)) {
                    rotationAngle = 360
                }
            }
            
            Spacer()
            
            Button(action: onContinue) {
                Text("Continue to Part Two")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.cyan)
                    .cornerRadius(28)
            }
            .padding(.horizontal, 48)
        }
    }
}

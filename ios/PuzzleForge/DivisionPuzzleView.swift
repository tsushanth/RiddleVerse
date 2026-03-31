//
//  DivisionPuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/11/25.
//

import SwiftUI

struct ValueButton: Equatable {
    let value: Int
    var count: Int
    let maxCount: Int
    
    init(value: Int, count: Int = 0, maxCount: Int) {
        self.value = value
        self.count = count
        self.maxCount = maxCount
    }
    
    static func == (lhs: ValueButton, rhs: ValueButton) -> Bool {
        return lhs.value == rhs.value &&
               lhs.count == rhs.count &&
               lhs.maxCount == rhs.maxCount
    }
}

struct DivisionPuzzleView: View {
    let score: Int
    let hearts: Int
    let level: String // e.g., "0/7"
    let dividend: Int // e.g., 872
    let divisor: Int // e.g., 8
    let correctAnswer: Int // e.g., 109 (872 ÷ 8)
    let onSubmitAnswer: (Int) -> Void
    let fetchNextPuzzle: () -> Void
    @StateObject private var feedbackManager = UnifiedFeedbackManager()


    
    @State private var valueButtons = [
        ValueButton(value: 50, count: 0, maxCount: 5),
        ValueButton(value: 10, count: 0, maxCount: 20),
        ValueButton(value: 5, count: 0, maxCount: 10),
        ValueButton(value: 1, count: 0, maxCount: 50)
    ]
    @State private var currentTotal = 0
    @State private var isAnswered = false
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                Color(red: 0.96, green: 0.96, blue: 0.96)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Top bar
                    topBar
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                    
                    Spacer(minLength: 60)
                    
                    // Division problem
                    divisionProblem
                        .padding(.horizontal, 40)
                    
                    Spacer(minLength: 80)
                    
                    // Answer circle
                    answerCircle
                        .frame(height: 200)
                    
                    Spacer()
                    
                    // Value input section
                    valueInputSection
                        .padding(.horizontal, 20)
                        .padding(.bottom, 40)
                }
            }
        }
        .withUnifiedFeedback(feedbackManager)
        .trackPuzzleViewOnce(
                    puzzleId: "id",
                    puzzleType: "division",
                    difficulty: "Easy",
                    questionIndex: 0
                )
        .onAppear {
            updateCurrentTotal()
        }
    }
    
    // MARK: - Top Bar
    private var topBar: some View {
        HStack {
            HStack(spacing: 10) {
                Image(systemName: "pause.fill")
                    .foregroundColor(.gray)
                    .font(.title2)
                
                Text("\(score)")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 4) {
                HStack(spacing: 5) {
                    ForEach(0..<hearts, id: \.self) { _ in
                        Image(systemName: "heart.fill")
                            .foregroundColor(.gray)
                            .font(.title3)
                    }
                }
                
                Text(level)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.gray)
            }
        }
    }
    
    // MARK: - Division Problem
    private var divisionProblem: some View {
        VStack(spacing: 16) {
            Text("\(dividend)")
                .font(.system(size: 64, weight: .bold))
                .foregroundColor(.gray)
            
            Text("DIVIDED BY \(divisor)")
                .font(.system(size: 18, weight: .medium))
                .foregroundColor(.gray)
                .tracking(1)
        }
    }
    
    // MARK: - Answer Circle
    private var answerCircle: some View {
        VStack {
            Spacer()
            
            ZStack {
                // 3D Shadow effect
                Circle()
                    .fill(Color(red: 0.83, green: 0.69, blue: 0.22).opacity(0.3))
                    .frame(width: 180, height: 180)
                    .offset(x: 8, y: 8)
                
                // Main circle
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [
                                Color(red: 1.0, green: 0.84, blue: 0.0).opacity(feedbackManager.isShowingFeedback ? 0.5 : 1.0), // Dim when disabled
                                                Color(red: 0.83, green: 0.69, blue: 0.22).opacity(feedbackManager.isShowingFeedback ? 0.5 : 1.0)

                            ],
                            center: .center,
                            startRadius: 30,
                            endRadius: 90
                        )
                    )
                    .frame(width: 180, height: 180)
                    .shadow(color: .black.opacity(0.3), radius: 8, x: 0, y: 4)
                    .onTapGesture {
                        if !feedbackManager.isShowingFeedback { // Add this check
                                submitAnswer()
                            }
                    }
                
                VStack(spacing: 8) {
                    Text("\(currentTotal)")
                        .font(.system(size: 48, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("SUBMIT")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .tracking(1)
                }
            }
            
            Spacer()
        }
    }
    
    // MARK: - Value Input Section
    private var valueInputSection: some View {
        VStack(spacing: 20) {
            // Value buttons row
            HStack {
                ForEach(0..<valueButtons.count, id: \.self) { index in
                    ValueInputButtonView(
                        value: valueButtons[index].value,
                        count: valueButtons[index].count,
                        maxCount: valueButtons[index].maxCount,
                        onIncrement: {
                            incrementValue(at: index)
                        },
                        onDecrement: {
                            decrementValue(at: index)
                        },
                        isDisabled: feedbackManager.isShowingFeedback
                    )
                    
                    if index < valueButtons.count - 1 {
                        Spacer()
                    }
                }
            }
            
            // Value labels row
            HStack {
                ForEach(valueButtons, id: \.value) { button in
                    Text("\(button.value)")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.gray)
                        .frame(width: 60)
                    
                    if button.value != valueButtons.last?.value {
                        Spacer()
                    }
                }
            }
        }
    }
    
    
    // MARK: - Helper Methods
    private func updateCurrentTotal() {
        currentTotal = valueButtons.reduce(0) { total, button in
            total + (button.value * button.count)
        }
    }
    
    private func incrementValue(at index: Int) {
        guard !isAnswered, !feedbackManager.isShowingFeedback, valueButtons[index].count < valueButtons[index].maxCount else { return } // Add !feedbackManager.isShowingFeedback

        hapticFeedback()
        valueButtons[index].count += 1
        updateCurrentTotal() // Update total after changing count
    }
    
    private func decrementValue(at index: Int) {
        guard !isAnswered, !feedbackManager.isShowingFeedback, valueButtons[index].count > 0 else { return } // Add !feedbackManager.isShowingFeedback

        hapticFeedback()
        valueButtons[index].count -= 1
        updateCurrentTotal() // Update total after changing count
    }
    
    private func submitAnswer() {
        guard !isAnswered, !feedbackManager.isShowingFeedback else { return }

        hapticFeedback()
        isAnswered = true
        let isAnswerCorrect = currentTotal == correctAnswer
        
        onSubmitAnswer(currentTotal)
        feedbackManager.showFeedback(
            puzzleType: "division",
            isCorrect: isAnswerCorrect,
            userAnswer: "\(currentTotal)",
            correctAnswer: "\(correctAnswer)",
            timeSpent: 15.0,
            difficulty: "Medium",
            timeRemaining: 45,
            totalTime: 60,
            onComplete: {
                // Don't call fetchNextPuzzle here - let the timer handle it
            }
        )
        
        // Single timing control - only this one
        DispatchQueue.main.asyncAfter(deadline: .now() + (isAnswerCorrect ? 1.5 : 1.0)) {
            if isAnswerCorrect {
                fetchNextPuzzle()
            } else {
                resetState()
            }
        }
    }
    
    private func resetState() {
        isAnswered = false
        currentTotal = 0
        valueButtons = [
            ValueButton(value: 50, count: 0, maxCount: 5),
            ValueButton(value: 10, count: 0, maxCount: 20),
            ValueButton(value: 5, count: 0, maxCount: 10),
            ValueButton(value: 1, count: 0, maxCount: 50)
        ]
        updateCurrentTotal()
    }
    
    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
}

// MARK: - Value Input Button View
struct ValueInputButtonView: View {
    let value: Int
    let count: Int
    let maxCount: Int
    let onIncrement: () -> Void
    let onDecrement: () -> Void
    let isDisabled: Bool
    
    var body: some View {
        VStack(spacing: 8) {
            // Increment button (top)
            Button(action: onIncrement) {
                RoundedRectangle(cornerRadius: 8)
                    .fill(count < maxCount ? Color(red: 0.83, green: 0.69, blue: 0.22) : Color.gray.opacity(0.3))
                    .frame(width: 60, height: 30)
                    .overlay(
                        Text("+")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                    )
            }
            .disabled(count >= maxCount || isDisabled)
            
            // Count display
            RoundedRectangle(cornerRadius: 4)
                .stroke(Color(red: 0.83, green: 0.69, blue: 0.22), lineWidth: 2)
                .frame(width: 60, height: 40)
                .background(
                    RoundedRectangle(cornerRadius: 4)
                        .fill(count > 0 ? Color(red: 0.83, green: 0.69, blue: 0.22).opacity(0.2) : Color.clear)
                )
                .overlay(
                    Text("\(count)")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.gray)
                )
            
            // Decrement button (bottom)
            Button(action: onDecrement) {
                RoundedRectangle(cornerRadius: 8)
                    .fill(count > 0 ? Color(red: 0.83, green: 0.69, blue: 0.22) : Color.gray.opacity(0.3))
                    .frame(width: 60, height: 30)
                    .overlay(
                        Text("−")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                    )
            }
            .disabled(count <= 0 || isDisabled)
        }
    }
}

//
//  PercentagePuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/11/25.
//

import SwiftUI

struct PercentagePuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Int, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    
    @StateObject private var timer = PuzzleTimer(totalTime: 60)
    @State private var currentInput = ""
    @State private var isAnswered = false
    @State private var score = 0
    
    private var percentageData: PercentagePuzzleData? {
        puzzle.percentagePuzzleData
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                backgroundView
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Top bar
                    topBar
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                    
                    // Question display
                    questionSection
                        .padding(.horizontal, 20)
                        .padding(.vertical, 20)
                    
                    // Percentage calculation display
                    percentageDisplay
                        .padding(.horizontal, 40)
                        .padding(.bottom, 20)
                    
                    Spacer()
                    
                    // Calculator interface
                    calculatorGrid
                        .padding(.horizontal, 20)
                        .padding(.bottom, 30)
                }
                
            }
        }
        .withUnifiedFeedback(feedbackManager)
        .trackPuzzleViewOnce(
                    puzzleId: "id",
                    puzzleType: "percentage",
                    difficulty: "Easy",
                    questionIndex: 0
                )
        .onAppear {
            timer.start()
        }
        .onDisappear {
            timer.stop()
        }
        .onChange(of: timer.timeRemaining) { newTime in
            if newTime <= 0 && !isAnswered && !feedbackManager.isShowingFeedback {
                submitAnswer()
            }
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Background View
    private var backgroundView: some View {
        LinearGradient(
            colors: [
                Color(red: 0.55, green: 0.29, blue: 0.62),
                Color(red: 0.42, green: 0.23, blue: 0.48),
                Color(red: 0.29, green: 0.16, blue: 0.34)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }
    
    // MARK: - Top Bar
    private var topBar: some View {
        HStack {
            Button(action: onExit) {
                HStack(spacing: 10) {
                    Image(systemName: "xmark")
                        .foregroundColor(.white.opacity(0.7))
                        .font(.title2)
                    
                    Text("Exit")
                        .font(.title3)
                        .fontWeight(.medium)
                        .foregroundColor(.white.opacity(0.7))
                }
            }
            
            Spacer()
            
            // Question counter
            Text("\(questionIndex + 1)/\(totalQuestions)")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.white.opacity(0.8))
            
            Spacer()
            
            // Timer
            Text(timer.formattedTime)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(timer.timeRemaining <= 10 ? .red : .white)
                .animation(.easeInOut(duration: 0.5), value: timer.timeRemaining <= 10)
        }
    }
    
    // MARK: - Question Section
    private var questionSection: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.white.opacity(0.9))
            .frame(maxHeight: 80)
            .overlay(
                VStack(spacing: 8) {
                    if let data = percentageData {
                        Text("What is \(data.percentage)% of \(data.baseNumber)?")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Color(red: 0.17, green: 0.24, blue: 0.31))
                            .multilineTextAlignment(.center)
                    } else {
                        Text("CALCULATE THE PERCENTAGE")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(red: 0.17, green: 0.24, blue: 0.31))
                            .multilineTextAlignment(.center)
                    }
                    
                    if let data = percentageData, !data.hint.isEmpty {
                        Text("💡 \(data.hint)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color(red: 0.17, green: 0.24, blue: 0.31).opacity(0.7))
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            )
    }
    
    // MARK: - Percentage Display
    private var percentageDisplay: some View {
        HStack(spacing: 20) {
            if let data = percentageData {
                percentageCircle(for: data)
                ofText
                baseNumberText(for: data)
                equalsText
                answerText
            }
        }
    }
    
    private func percentageCircle(for data: PercentagePuzzleData) -> some View {
        ZStack {
            Circle()
                .fill(percentageGradient)
                .overlay(
                    Circle()
                        .stroke(Color.white.opacity(0.5), lineWidth: 2)
                )
                .frame(width: 100, height: 100)
            
            Text("\(data.percentage)%")
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.white)
        }
    }
    
    private var percentageGradient: RadialGradient {
        RadialGradient(
            colors: [
                Color(red: 0.91, green: 0.66, blue: 0.49).opacity(0.8),
                Color(red: 0.82, green: 0.41, blue: 0.12).opacity(0.6),
                Color(red: 0.8, green: 0.52, blue: 0.25).opacity(0.4)
            ],
            center: .center,
            startRadius: 20,
            endRadius: 60
        )
    }
    
    private var ofText: some View {
        Text("OF")
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(.white)
    }
    
    private func baseNumberText(for data: PercentagePuzzleData) -> some View {
        Text("\(data.baseNumber)")
            .font(.system(size: 36, weight: .bold))
            .foregroundColor(.white)
    }
    
    private var equalsText: some View {
        Text("=")
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(.white)
    }
    
    private var answerText: some View {
        Text(currentInput.isEmpty ? "?" : currentInput)
            .font(.system(size: 36, weight: .bold))
            .foregroundColor(currentInput.isEmpty ? .white.opacity(0.6) : .white)
            .frame(minWidth: 60, alignment: .leading)
    }
    
    // MARK: - Calculator Grid
    private var calculatorGrid: some View {
        VStack(spacing: 15) {
            // Number grid (1-9)
            ForEach(0..<3, id: \.self) { row in
                HStack(spacing: 15) {
                    ForEach(0..<3, id: \.self) { col in
                        let number = row * 3 + col + 1
                        PercentageCalculatorButton(
                            text: "\(number)",
                            action: {
                                if !isAnswered && currentInput.count < 8 {
                                    hapticFeedback()
                                    currentInput += "\(number)"
                                }
                            },
                            isDisabled: feedbackManager.isShowingFeedback
                        )
                    }
                }
            }
            
            // Bottom row (Clear, 0, Submit)
            HStack(spacing: 15) {
                PercentageCalculatorButton(
                    text: "CLEAR",
                    action: {
                        if !isAnswered {
                            hapticFeedback()
                            currentInput = ""
                        }
                    },
                    isSpecial: true,
                    isDisabled: feedbackManager.isShowingFeedback
                )
                
                PercentageCalculatorButton(
                    text: "0",
                    action: {
                        if !isAnswered && currentInput.count < 8 {
                            hapticFeedback()
                            currentInput += "0"
                        }
                    },
                    isDisabled: feedbackManager.isShowingFeedback
                )
                
                PercentageCalculatorButton(
                    text: "SUBMIT",
                    action: submitAnswer,
                    isSpecial: true,
                    isSubmit: true,
                    isDisabled: feedbackManager.isShowingFeedback || currentInput.isEmpty
                )
            }
        }
    }
    
    
    // MARK: - Helper Methods
    private func submitAnswer() {
        guard !isAnswered, !feedbackManager.isShowingFeedback, !currentInput.isEmpty, let answer = Int(currentInput) else { return }
        guard let data = percentageData else { return }
        
        hapticFeedback()
        timer.stop()
        isAnswered = true
        let isAnswerCorrect = answer == data.correctAnswer
        
        feedbackManager.showFeedback(
            puzzleType: "percentage",
            isCorrect: isAnswerCorrect,
            userAnswer: currentInput,
            correctAnswer: "\(data.correctAnswer)",
            timeSpent: Double(timer.totalTime - timer.timeRemaining),
            difficulty: puzzle.difficulty,
            timeRemaining: timer.timeRemaining,
            totalTime: timer.totalTime,
            onComplete: {
                onNextPuzzle()
            }
        )
        
        onAnswerSubmitted(answer, isAnswerCorrect)
    }
    
    private func resetState() {
        isAnswered = false
        currentInput = ""
        timer.reset()
        timer.start()
    }
    
    private func calculateScore() -> Int {
        guard let data = percentageData else { return 0 }
        return PuzzleScoring.calculateScore(
            for: "percentage",
            timeRemaining: timer.timeRemaining,
            totalTime: 60,
            difficulty: data.difficulty
        )
    }
    
    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
}

// MARK: - Calculator Button
struct PercentageCalculatorButton: View {
    let text: String
    let action: () -> Void
    var isSpecial: Bool = false
    var isSubmit: Bool = false
    var isDisabled: Bool = false
    
    var body: some View {
        Button(action: action) {
            buttonContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .aspectRatio(1, contentMode: .fit)
                .background(buttonBackground)
                .overlay(buttonBorder)
        }
        .disabled(isDisabled)
        .buttonStyle(PlainButtonStyle())
    }
    
    private var buttonContent: some View {
        Text(text)
            .font(buttonFont)
            .fontWeight(.bold)
            .foregroundColor(.white.opacity(isDisabled ? 0.5 : 1.0))
            .lineLimit(1)
            .minimumScaleFactor(0.6)
    }
    
    private var buttonFont: Font {
        isSpecial ? .system(size: 14, weight: .bold) : .title2
    }
    
    private var buttonBackground: some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(backgroundFill)
    }
    
    private var backgroundFill: Color {
            let baseOpacity: Double = isDisabled ? 0.3 : 1.0
            if isSubmit {
                return Color.green.opacity(0.3 * baseOpacity)
            } else if isSpecial {
                return Color.white.opacity(0.1 * baseOpacity)
            } else {
                return Color.clear
            }
        }
        
        private var borderColor: Color {
            let opacity: Double = isDisabled ? 0.3 : 1.0
            if isSubmit {
                return Color.green.opacity(opacity)
            } else if isSpecial {
                return Color.white.opacity(0.6 * opacity)
            } else {
                return Color.white.opacity(0.3 * opacity)
            }
        }
    
    private var buttonBorder: some View {
        RoundedRectangle(cornerRadius: 8)
            .stroke(borderColor, lineWidth: borderWidth)
    }
    
    private var borderWidth: CGFloat {
        isSubmit ? 2 : 1
    }
}

// MARK: - Preview
struct PercentagePuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let samplePuzzle = Puzzle(
            question: """
            {"percentage":10,"baseNumber":6700,"correctAnswer":670,"difficulty":"Easy","hint":"Multiply by 0.10 or divide by 10"}
            """,
            answer: "670",
            hint: "Multiply by 0.10 or divide by 10",
            options: [],
            format: "percentage",
            puzzleType: "percentage",
            puzzleId: "sample-percentage",
            id: "sample-percentage",
            name: "Percentage Puzzle",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Easy"
        )
        
        PercentagePuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { answer, isCorrect in
                print("Answer: \(answer), Correct: \(isCorrect)")
            },
            onNextPuzzle: {
                print("Next puzzle")
            },
            onExit: {
                print("Exit")
            }
        )
    }
}

//
//  EstimationPuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/11/25.
//

import SwiftUI

struct ChartDataPoint: Equatable {
    let value: Double
    let yPosition: Float // Position on the chart (0-1, where 0 is bottom, 1 is top)
    
    static func == (lhs: ChartDataPoint, rhs: ChartDataPoint) -> Bool {
        return lhs.value == rhs.value && lhs.yPosition == rhs.yPosition
    }
}

struct EstimationPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Double, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()


    
    @StateObject private var timer = PuzzleTimer(totalTime: 75)
    @State private var dragPosition: CGPoint?
    @State private var estimatedValue: Double?
    @State private var isAnswered = false
    @State private var score = 0
    
    // Chart dimensions and ranges
    private let chartHeight: CGFloat = 400
    private let chartWidth: CGFloat = 300
    private var minValue: Double {
        return 0.0  // Always start from 0 for estimation puzzles
    }
    
    private var maxValue: Double {
        return niceMaxValue
    }
    
    // Alternative approach: Create nice round scale values
    private var niceMaxValue: Double {
        guard let data = estimationData else { return 50.0 }
        
        let correctSum = data.correctSum
        let rawMax = correctSum * 1.3 // 30% padding above correct answer
        
        // Round up to nice values
        if rawMax <= 10 { return 10 }
        else if rawMax <= 20 { return 20 }
        else if rawMax <= 30 { return 30 }
        else if rawMax <= 50 { return 50 }
        else if rawMax <= 75 { return 75 }
        else if rawMax <= 100 { return 100 }
        else { return ceil(rawMax / 25) * 25 } // Round to nearest 25
    }
    
    private var yAxisLabels: [Double] {
        let max = maxValue
        let numberOfLabels = 4
        
        var labels: [Double] = []
        for i in 1...numberOfLabels {
            let value = max * Double(i) / Double(numberOfLabels)
            labels.append(value)
        }
        return labels.reversed() // Reverse so largest is at top
    }
    
    private var estimationData: EstimationPuzzleData? {
        puzzle.estimationPuzzleData
    }
    
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
                    
                    Spacer(minLength: 40)
                    
                    // Chart area
                    chartArea
                        .frame(height: chartHeight + 100)
                    
                    Spacer(minLength: 20)
                    
                    // Instructions
                    instructionsCard
                        .padding(.horizontal, 20)
                    
                    Spacer()
                    
                    // Progress bar
                    progressBar
                        .padding(.horizontal, 40)
                        .padding(.bottom, 30)
                }
            }
        }
        .withUnifiedFeedback(feedbackManager)
        .trackPuzzleViewOnce(
                    puzzleId: "id",
                    puzzleType: "estimation",
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
                // Time's up - auto submit with current estimate or 0
                let autoEstimate = estimatedValue ?? 0.0
                submitAnswer(estimate: autoEstimate)
            }
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Top Bar
    private var topBar: some View {
        EnhancedEstimationTopGameBar(
            level: progressionManager.currentLevel,
            streakInfo: progressionManager.streakInfo,
            timer: timer.formattedTime,
            questionIndex: questionIndex,
            totalQuestions: totalQuestions,
            lives: 4,
            onExit: onExit
        )
    }
    
    // MARK: - Chart Area
    private var chartArea: some View {
        VStack {
            Spacer()
            
            if let data = estimationData {
                InteractiveChartView(
                    dataPoints: data.dataPoints,
                    dragPosition: dragPosition,
                    estimatedValue: estimatedValue,
                    minValue: minValue,
                    maxValue: maxValue,
                    yAxisLabels: yAxisLabels,
                    isDisabled: feedbackManager.isShowingFeedback,
                    chartWidth: chartWidth,
                    chartHeight: chartHeight,
                    onDragChanged: { position in
                        if !isAnswered && !feedbackManager.isShowingFeedback {
                            hapticFeedback()
                            dragPosition = position
                        }
                    },
                    onDragEnded: { position in
                        if !isAnswered && !feedbackManager.isShowingFeedback {
                            let estimated = calculateEstimatedValue(dragY: position.y)
                            estimatedValue = estimated
                            submitAnswer(estimate: estimated)
                        }
                    }
                )
                .frame(width: chartWidth + 60, height: chartHeight + 60)
            } else {
                Text("Error loading estimation data")
                    .foregroundColor(.red)
            }
            
            Spacer()
        }
    }
    
    // MARK: - Instructions Card
    private var instructionsCard: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color(red: 0.91, green: 0.89, blue: 0.95))
            .frame(height: 60)
            .overlay(
                HStack(spacing: 8) {
                    Image(systemName: "chevron.up")
                        .foregroundColor(Color(red: 0.61, green: 0.15, blue: 0.69))
                        .font(.system(size: 16, weight: .medium))
                    
                    Text("drag finger to estimate sum")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(Color(red: 0.61, green: 0.15, blue: 0.69))
                    
                    Image(systemName: "chevron.down")
                        .foregroundColor(Color(red: 0.61, green: 0.15, blue: 0.69))
                        .font(.system(size: 16, weight: .medium))
                }
            )
    }
    
    // MARK: - Progress Bar
    private var progressBar: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(Color.gray.opacity(0.3))
            .frame(height: 4)
    }
    
    // MARK: - Helper Methods
    private func calculateEstimatedValue(dragY: CGFloat) -> Double {
        let padding: CGFloat = 30
        let chartRect = CGRect(x: padding, y: padding, width: chartWidth, height: chartHeight)
        let relativeY = (chartRect.maxY - dragY) / chartRect.height
        let clampedY = max(0, min(1, relativeY))
        return minValue + (maxValue - minValue) * Double(clampedY)
    }
    
    private func submitAnswer(estimate: Double) {
        guard !isAnswered, !feedbackManager.isShowingFeedback else { return }
        guard let data = estimationData else { return }
        
        hapticFeedback()
        timer.stop()
        isAnswered = true
        let isAnswerCorrect = abs(estimate - data.correctSum) <= data.tolerance
        estimatedValue = estimate
        
        onAnswerSubmitted(estimate, isAnswerCorrect)
        
        // Use the new centralized feedback system
        feedbackManager.showFeedback(
            puzzleType: "estimation",
            isCorrect: isAnswerCorrect,
            userAnswer: String(format: "%.1f", estimate),
            correctAnswer: String(format: "%.2f", data.correctSum),
            timeSpent: Double(timer.totalTime - timer.timeRemaining),
            difficulty: puzzle.difficulty,
            timeRemaining: timer.timeRemaining,
            totalTime: timer.totalTime,
            onComplete: {
                onNextPuzzle()
            }
        )
    }
    
    private func resetState() {
        isAnswered = false
        dragPosition = nil
        estimatedValue = nil
        timer.reset()
        timer.start()
    }
    
    private func calculateScore() -> Int {
        guard let data = estimationData else { return 0 }
        return PuzzleScoring.calculateScore(
            for: "estimation",
            timeRemaining: timer.timeRemaining,
            totalTime: 75,
            difficulty: data.difficulty
        )
    }
    
    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .light)
        impactFeedback.impactOccurred()
    }
}

struct EnhancedEstimationTopGameBar: View {
    let level: UserLevel
    let streakInfo: StreakInfo
    let timer: String
    let questionIndex: Int
    let totalQuestions: Int
    let lives: Int
    let onExit: () -> Void
    
    var body: some View {
        HStack {
            // Left side: Exit button and level
            HStack(spacing: 12) {
                Button(action: onExit) {
                    HStack(spacing: 8) {
                        Image(systemName: "xmark")
                            .foregroundColor(.white)
                            .font(.title2)
                        Text("Exit")
                            .font(.title3)
                            .fontWeight(.medium)
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.red.opacity(0.8))
                    )
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("Level \(level.level)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    LevelProgressBar(level: level)
                        .frame(width: 100)
                }
            }
            
            Spacer()
            
            // Center: Question counter
            Text("\(questionIndex + 1)/\(totalQuestions)")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.white)
            
            Spacer()
            
            // Right side: Timer and streak
            VStack(alignment: .trailing, spacing: 4) {
                Text(timer)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                if streakInfo.currentStreak > 0 {
                    StreakDisplay(streakInfo: streakInfo)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.7))
        )
    }
}

// MARK: - Interactive Chart View
struct InteractiveChartView: View {
    let dataPoints: [ChartDataPoint]
    let dragPosition: CGPoint?
    let estimatedValue: Double?
    let minValue: Double
    let maxValue: Double
    let yAxisLabels: [Double]
    let isDisabled: Bool
    let chartWidth: CGFloat
    let chartHeight: CGFloat
    let onDragChanged: (CGPoint) -> Void
    let onDragEnded: (CGPoint) -> Void
    
    var body: some View {
        Canvas { context, size in
            let leftPadding: CGFloat = 60  // Increased for labels
            let topPadding: CGFloat = 30
            let rightPadding: CGFloat = 100  // Increased for data values and drag indicator
            let bottomPadding: CGFloat = 30
            
            let chartRect = CGRect(
                x: leftPadding,
                y: topPadding,
                width: size.width - leftPadding - rightPadding,
                height: chartHeight
            )
            
            drawChart(
                context: context,
                dataPoints: dataPoints,
                dragPosition: dragPosition,
                estimatedValue: estimatedValue,
                chartRect: chartRect,
                minValue: minValue,
                maxValue: maxValue,
                yAxisLabels: yAxisLabels // Pass it to drawing function
            )
        }
        .gesture(
                    DragGesture()
                        .onChanged { value in
                            if !isDisabled { // Use the passed parameter
                                onDragChanged(value.location)
                            }
                        }
                        .onEnded { value in
                            if !isDisabled {// Use the passed parameter
                                onDragEnded(value.location)
                            }
                        }
                )
    }
}

// MARK: - Chart Drawing
func drawChart(
    context: GraphicsContext,
    dataPoints: [ChartDataPoint],
    dragPosition: CGPoint?,
    estimatedValue: Double?,
    chartRect: CGRect,
    minValue: Double,
    maxValue: Double,
    yAxisLabels: [Double]
) {
    // Draw Y-axis
    let yAxisPath = Path { path in
        path.move(to: CGPoint(x: chartRect.minX, y: chartRect.minY))
        path.addLine(to: CGPoint(x: chartRect.minX, y: chartRect.maxY))
    }
    context.stroke(yAxisPath, with: .color(Color(red: 0.61, green: 0.15, blue: 0.69)), lineWidth: 2)
    
    // Draw dotted vertical line
    let dotSpacing: CGFloat = 8
    var currentY = chartRect.minY
    while currentY < chartRect.maxY {
        let dotPath = Path { path in
            path.addEllipse(in: CGRect(x: chartRect.minX - 1, y: currentY - 1, width: 2, height: 2))
        }
        context.fill(dotPath, with: .color(Color(red: 0.61, green: 0.15, blue: 0.69)))
        currentY += dotSpacing
    }
    
    // Draw Y-axis labels and tick marks
    for label in yAxisLabels {
        let yPos = chartRect.maxY - CGFloat((label - minValue) / (maxValue - minValue)) * chartRect.height
        
        // Draw tick mark
        let tickRect = CGRect(
            x: chartRect.minX - 8,
            y: yPos - 2,
            width: 12,
            height: 4
        )
        context.fill(Path(tickRect), with: .color(Color(red: 0.61, green: 0.15, blue: 0.69)))
        
        // Draw label with appropriate formatting
        let labelText: Text
        if label < 1 {
            labelText = Text("$\(String(format: "%.2f", label))")
        } else if label.truncatingRemainder(dividingBy: 1) == 0 {
            labelText = Text("$\(Int(label))")
        } else {
            labelText = Text("$\(String(format: "%.1f", label))")
        }
        
        let formattedLabel = labelText
            .font(.system(size: 14))
            .foregroundColor(Color(red: 0.61, green: 0.15, blue: 0.69))
        
        context.draw(formattedLabel, at: CGPoint(x: chartRect.minX - 25, y: yPos))
    }
    
    // Draw data points and values
    for (index, point) in dataPoints.enumerated() {
        let xPos = chartRect.minX + chartRect.width * 0.7
        let yPos = chartRect.maxY - CGFloat(point.yPosition) * chartRect.height
        
        // Draw square marker
        let squareRect = CGRect(x: xPos - 4, y: yPos - 4, width: 8, height: 8)
        context.fill(Path(squareRect), with: .color(Color(red: 0.61, green: 0.15, blue: 0.69)))
        
        // Draw value text
        let valueText = Text(String(format: "%.2f", point.value))
            .font(.system(size: 24))
            .foregroundColor(Color(red: 0.4, green: 0.4, blue: 0.4))
        
        context.draw(valueText, at: CGPoint(x: xPos + 50, y: yPos))
    }
    
    // Draw drag indicator if dragging
    if let position = dragPosition,
       position.x >= chartRect.minX && position.x <= chartRect.maxX &&
       position.y >= chartRect.minY && position.y <= chartRect.maxY {
        
        // Draw horizontal line
        let dragLinePath = Path { path in
            path.move(to: CGPoint(x: chartRect.minX, y: position.y))
            path.addLine(to: CGPoint(x: chartRect.maxX, y: position.y))
        }
        context.stroke(dragLinePath, with: .color(.green), lineWidth: 2)
        
        // Calculate and draw estimated value
        let relativeY = (chartRect.maxY - position.y) / chartRect.height
        let estimated = minValue + (maxValue - minValue) * Double(relativeY)
        
        let estimateText = Text(String(format: "%.1f", estimated))
            .font(.system(size: 18, weight: .bold))
            .foregroundColor(.green)
        
        context.draw(estimateText, at: CGPoint(x: chartRect.maxX + 15, y: position.y))
    }
}

// MARK: - Preview
struct EstimationPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let samplePuzzle = Puzzle(
            question: """
            {"dataPoints":[{"value":5.33,"yPosition":0.75},{"value":3.20,"yPosition":0.55},{"value":2.95,"yPosition":0.50}],"correctSum":11.48,"tolerance":1.0,"difficulty":"Medium","hint":"Look at the chart values and estimate their sum"}
            """,
            answer: "11.48",
            hint: "Look at the chart values and estimate their sum",
            options: [],
            format: "estimation",
            puzzleType: "estimation",
            puzzleId: "sample-estimation",
            id: "sample-estimation",
            name: "Estimation Puzzle",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Medium"
        )
        
        EstimationPuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { estimate, isCorrect in
                print("Estimate: \(estimate), Correct: \(isCorrect)")
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

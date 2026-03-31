//
//  FeaturedPuzzleComponents.swift
//  PuzzleForge
//

import SwiftUI
import FirebaseAuth

// MARK: - Featured Puzzle Data Models
struct FeaturedPuzzle: Codable, Identifiable {
    let id = UUID()
    let puzzleType: String
    let playCount: Int
    let uniquePlayers: Int
    let successRatePercent: Double
    let avgScore: Double
    let recentPlays: Int
    let featuredReason: String
    
    enum CodingKeys: String, CodingKey {
        case puzzleType = "puzzle_type"
        case playCount = "play_count"
        case uniquePlayers = "unique_players"
        case successRatePercent = "success_rate_percent"
        case avgScore = "avg_score"
        case recentPlays = "recent_plays"
        case featuredReason = "featured_reason"
    }
    
    // Get display info for puzzle type
    var displayInfo: (title: String, subtitle: String, icon: String, color: Color) {
        switch puzzleType {
        case "connotationwords":
            return ("Word Connotations", "Sort by sentiment", "brain.head.profile", .purple)
        case "math":
            return ("Math", "Mathematical challenges", "function", .blue)
        case "mathtipping":
            return ("Tip Calculator", "Calculate tips", "dollarsign.circle.fill", .green)
        case "anagram":
            return ("Anagram", "Word scrambles", "textformat.abc", .orange)
        case "multipleChoice":
            return ("Multiple Choice", "Quick questions", "questionmark.circle.fill", .indigo)
        case "average":
            return ("Average", "Calculate averages", "chart.bar.fill", .pink)
        case "division":
            return ("Division", "Master division", "divide.circle.fill", .cyan)
        case "conversion":
            return ("Conversion", "Unit comparisons", "arrow.left.arrow.right", .teal)
        case "estimation":
            return ("Estimation", "Chart estimation", "chart.line.uptrend.xyaxis", .mint)
        case "discounts":
            return ("Discounts", "Price ordering", "tag.fill", .red)
        default:
            return (puzzleType.capitalized, "Test your skills", "puzzlepiece.fill", .gray)
        }
    }
    
    var statsText: String {
        return "\(playCount) plays • \(Int(successRatePercent))% success"
    }
}

// MARK: - Featured Service
class FeaturedPuzzlesService: ObservableObject {
    @Published var featuredPuzzles: [FeaturedPuzzle] = []
    @Published var isLoading: Bool = false
    @Published var lastUpdated: Date?
    
    func fetchFeaturedPuzzles() {
        // Don't fetch if we have recent data (less than 1 hour old)
        if let lastUpdated = lastUpdated,
           Date().timeIntervalSince(lastUpdated) < 3600 {
            print("📊 Using cached featured puzzles")
            return
        }
        
        guard let url = URL(string: "https://puzzleverseai.com/featured-ai-puzzles") else {
            print("❌ Invalid featured puzzles URL")
            return
        }
        
        isLoading = true
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                self.isLoading = false
                
                if let error = error {
                    print("❌ Failed to fetch featured puzzles: \(error.localizedDescription)")
                    self.loadMockData() // Fallback to mock data
                    return
                }
                
                guard let data = data else {
                    print("❌ No data received for featured puzzles")
                    self.loadMockData()
                    return
                }
                
                do {
                    let puzzles = try JSONDecoder().decode([FeaturedPuzzle].self, from: data)
                    self.featuredPuzzles = Array(puzzles.prefix(5)) // Limit to 5
                    self.lastUpdated = Date()
                    print("✅ Loaded \(puzzles.count) featured puzzles")
                } catch {
                    print("❌ Failed to decode featured puzzles: \(error)")
                    self.loadMockData()
                }
            }
        }.resume()
    }
    
    // Mock data based on actual query results
    private func loadMockData() {
        featuredPuzzles = [
            FeaturedPuzzle(
                puzzleType: "connotationwords",
                playCount: 167,
                uniquePlayers: 29,
                successRatePercent: 94.6,
                avgScore: 24.5,
                recentPlays: 21,
                featuredReason: "Most Popular"
            ),
            FeaturedPuzzle(
                puzzleType: "math",
                playCount: 133,
                uniquePlayers: 21,
                successRatePercent: 92.5,
                avgScore: 29.8,
                recentPlays: 12,
                featuredReason: "Most Popular"
            ),
            FeaturedPuzzle(
                puzzleType: "anagram",
                playCount: 32,
                uniquePlayers: 5,
                successRatePercent: 93.8,
                avgScore: 24.1,
                recentPlays: 31,
                featuredReason: "High Success Rate"
            ),
            FeaturedPuzzle(
                puzzleType: "mathtipping",
                playCount: 53,
                uniquePlayers: 13,
                successRatePercent: 77.4,
                avgScore: 19.9,
                recentPlays: 0,
                featuredReason: "Try This"
            ),
            FeaturedPuzzle(
                puzzleType: "multipleChoice",
                playCount: 40,
                uniquePlayers: 11,
                successRatePercent: 55.0,
                avgScore: 12.5,
                recentPlays: 12,
                featuredReason: "Recently Played"
            )
        ]
        lastUpdated = Date()
        print("📊 Loaded mock featured puzzles data")
    }
}

// MARK: - Enhanced Daily Puzzles Section
struct EnhancedDailyPuzzlesSection: View {
    let dailyTopics: [String]
    let onDailyQuizSelected: (String) -> Void
    let onGenerateFresh: () -> Void
    let isGenerating: Bool
    let lastGeneratedTime: Date?
    
    @State private var showingAllTopics = false
    
    private let dailyTopicColors: [Color] = [
        Color.blue, Color.green, Color.orange, Color.purple, Color.cyan, Color.pink
    ]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header with Generate Fresh button
            HStack {
                HStack(spacing: 12) {
                    // Calendar icon with background
                    ZStack {
                        Circle()
                            .fill(Color.orange.opacity(0.2))
                            .frame(width: 40, height: 40)
                        Text("📅")
                            .font(.title2)
                    }
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Daily Quizzes")
                            .font(.title3)
                            .fontWeight(.bold)
                            .foregroundColor(.orange)
                        
                        Text(isGenerating ? "Generating fresh puzzles..." : "Fresh puzzles every day")
                            .font(.caption)
                            .foregroundColor(.orange.opacity(0.8))
                    }
                }
                
                Spacer()
                
                HStack(spacing: 8) {
                    // Generate Fresh button
                    Button(action: onGenerateFresh) {
                        HStack(spacing: 4) {
                            if isGenerating {
                                ProgressView()
                                    .scaleEffect(0.7)
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                Text("Generating...")
                                    .font(.caption2)
                                    .foregroundColor(.white)
                            } else {
                                Image(systemName: "arrow.clockwise")
                                    .font(.caption)
                                Text("Generate Fresh")
                                    .font(.caption2)
                            }
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.orange)
                        .cornerRadius(12)
                    }
                    .disabled(isGenerating)
                    
                    // NEW badge
                    Text("NEW")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.red)
                        .cornerRadius(8)
                }
            }
            
            // Last generated time
            if let lastGenerated = lastGeneratedTime {
                Text("Last generated: \(timeAgoString(from: lastGenerated))")
                    .font(.caption2)
                    .foregroundColor(.gray)
            }
            
            // Content based on state
            if dailyTopics.isEmpty {
                EmptyDailyPuzzlesState(onGenerateFresh: onGenerateFresh)
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "hand.tap")
                            .font(.caption)
                            .foregroundColor(.orange)
                        
                        Text("Tap a topic to start:")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(.orange)
                        
                        Spacer()
                        
                        Text("\(dailyTopics.count) fresh topics")
                            .font(.caption2)
                            .foregroundColor(.gray)
                    }
                    
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(dailyTopics.enumerated()), id: \.offset) { index, topic in
                                DailyTopicCard(
                                    topic: topic,
                                    backgroundColor: dailyTopicColors[index % dailyTopicColors.count],
                                    onClick: { onDailyQuizSelected(topic) }
                                )
                            }
                        }
                        .padding(.horizontal, 4)
                    }
                }
            }
        }
        .padding()
        .background(Color.orange.opacity(0.1))
        .cornerRadius(16)
    }
    
    private func timeAgoString(from date: Date) -> String {
        let now = Date()
        let interval = now.timeIntervalSince(date)
        
        if interval < 60 {
            return "just now"
        } else if interval < 3600 {
            return "\(Int(interval / 60))m ago"
        } else if interval < 86400 {
            return "\(Int(interval / 3600))h ago"
        } else {
            return "\(Int(interval / 86400))d ago"
        }
    }
}

struct EmptyDailyPuzzlesState: View {
    let onGenerateFresh: () -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "calendar")
                .font(.system(size: 32))
                .foregroundColor(.orange)
            
            Text("No daily topics available yet")
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.primary)
            
            Text("Click 'Generate Fresh' to create personalized daily quizzes based on your preferences")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            Button("Generate My Puzzles") {
                onGenerateFresh()
            }
            .font(.subheadline)
            .fontWeight(.semibold)
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .background(Color.orange)
            .cornerRadius(20)
        }
        .padding(20)
    }
}

// MARK: - Daily Topic Card
struct DailyTopicCard: View {
    let topic: String
    let backgroundColor: Color
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            VStack(spacing: 8) {
                HStack {
                    Spacer()
                    Text("NEW")
                        .font(.system(size: 6))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 2)
                        .background(Color.red)
                        .cornerRadius(4)
                }
                
                Image(systemName: "calendar")
                    .font(.system(size: 20))
                    .foregroundColor(.white)
                    .frame(width: 36, height: 36)
                    .background(Color.white.opacity(0.2))
                    .clipShape(Circle())
                
                VStack(spacing: 2) {
                    Text(topic)
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                    
                    Text("Daily Quiz")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.9))
                }
            }
            .frame(width: 140, height: 100)
            .padding(10)
            .background(
                LinearGradient(
                    colors: [backgroundColor, backgroundColor.opacity(0.8)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .cornerRadius(12)
            .shadow(color: .black.opacity(0.1), radius: 3, x: 0, y: 2)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Challenge Groups with Progress Tracking
struct ChallengeGroupWithProgress {
    let title: String
    let subtitle: String
    let puzzleTypes: [String]
    let icon: String
    let backgroundColor: Color
    let difficulty: String
    
    // Computed properties for progress
    var completionProgress: Float {
        let completedTypes = getCompletedPuzzleTypes()
        let completedCount = puzzleTypes.filter { completedTypes.contains($0) }.count
        return Float(completedCount) / Float(puzzleTypes.count)
    }
    
    var isCompleted: Bool {
        return completionProgress >= 1.0
    }
    
    private func getCompletedPuzzleTypes() -> Set<String> {
        let recentTypes = UserDefaults.standard.getRecentPuzzleTypes()
        return Set(recentTypes)
    }
}

struct EnhancedChallengesSectionWithProgress: View {
    let onGroupSelected: (ChallengeGroupWithProgress) -> Void
    
    private let challengeGroups = [
        ChallengeGroupWithProgress(
            title: "Math Master",
            subtitle: "Numbers & Logic",
            puzzleTypes: ["math", "average", "division", "percentages"],
            icon: "function",
            backgroundColor: Color.blue,
            difficulty: "Mixed"
        ),
        ChallengeGroupWithProgress(
            title: "Memory Champion",
            subtitle: "Brain Training",
            puzzleTypes: ["memorystory", "memorysquares", "memoryprevioussingle", "memorypreviouspair"],
            icon: "brain.head.profile",
            backgroundColor: Color.purple,
            difficulty: "Progressive"
        ),
        ChallengeGroupWithProgress(
            title: "Word Wizard",
            subtitle: "Language & Vocabulary",
            puzzleTypes: ["anagram", "synonyms", "antonyms", "wordsearch"],
            icon: "textformat.abc",
            backgroundColor: Color.green,
            difficulty: "Adaptive"
        ),
        ChallengeGroupWithProgress(
            title: "Logic Explorer",
            subtitle: "Problem Solving",
            puzzleTypes: ["trivia", "discounts", "conversion", "purchasing"],
            icon: "lightbulb",
            backgroundColor: Color.orange,
            difficulty: "Challenge"
        )
    ]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("🏆 Your Challenges")
                .font(.title2)
                .fontWeight(.semibold)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(challengeGroups, id: \.title) { group in
                        ChallengeGroupCardWithProgress(
                            group: group,
                            onTap: {
                                // Pick a random puzzle from the group or show group view
                                onGroupSelected(group)
                            }
                        )
                    }
                }
                .padding(.horizontal)
            }
        }
    }
}

struct ChallengeGroupCardWithProgress: View {
    let group: ChallengeGroupWithProgress
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                // Header with progress - more compact
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(group.title)
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .lineLimit(1)
                        
                        Text(group.subtitle)
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.9))
                            .lineLimit(1)
                    }
                    
                    Spacer()
                    
                    // Smaller progress indicator
                    if group.completionProgress > 0 {
                        ZStack {
                            Circle()
                                .stroke(Color.white.opacity(0.3), lineWidth: 1.5)
                                .frame(width: 20, height: 20)
                            
                            Circle()
                                .trim(from: 0, to: CGFloat(group.completionProgress))
                                .stroke(Color.white, lineWidth: 1.5)
                                .frame(width: 20, height: 20)
                                .rotationEffect(.degrees(-90))
                            
                            Text("\(Int(group.completionProgress * 100))%")
                                .font(.system(size: 7))
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                        }
                    }
                }
                
                // Smaller icon
                Image(systemName: group.icon)
                    .font(.system(size: 32))
                    .foregroundColor(.white)
                    .frame(width: 60, height: 60)
                    .background(Color.white.opacity(0.2))
                    .clipShape(Circle())
                
                // Compact footer
                VStack(spacing: 2) {
                    Text("\(group.puzzleTypes.count) puzzles")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.8))
                    
                    Text(group.difficulty)
                        .font(.system(size: 10))
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 1)
                        .background(Color.white.opacity(0.2))
                        .cornerRadius(6)
                }
            }
            .frame(width: 180, height: 180)
            .padding(.horizontal, 16)
            .padding(.vertical, 20)
            .background(
                LinearGradient(
                    colors: [group.backgroundColor, group.backgroundColor.opacity(0.7)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .cornerRadius(20)
            .shadow(color: .black.opacity(0.15), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Enhanced Stats Section
struct FStatCard: View {
    let title: String
    let value: String
    let subtitle: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 8) {
            Text(value)
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(color)
            
            VStack(spacing: 2) {
                Text(title)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
                
                Text(subtitle)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(color.opacity(0.1))
        .cornerRadius(12)
    }
}

struct EnhancedQuickStatsSection: View {
    @State private var todayCount = 0
    @State private var weeklyCount = 0
    @State private var totalScore = 0
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("📊 Your Stats")
                .font(.title3)
                .fontWeight(.semibold)
            
            HStack(spacing: 12) {
                FStatCard(
                    title: "Today",
                    value: "\(todayCount)",
                    subtitle: "puzzles",
                    color: .blue
                )
                
                FStatCard(
                    title: "This Week",
                    value: "\(weeklyCount)",
                    subtitle: "completed",
                    color: .green
                )
                
                FStatCard(
                    title: "Total Score",
                    value: "\(totalScore)",
                    subtitle: "points",
                    color: .orange
                )
            }
        }
        .onAppear {
            loadStats()
        }
    }
    
    private func loadStats() {
        let today = Calendar.current.startOfDay(for: Date())
        let todayKey = "completed_puzzles_\(today.timeIntervalSince1970)"
        todayCount = UserDefaults.standard.integer(forKey: todayKey)
        
        weeklyCount = UserDefaults.standard.integer(forKey: "weekly_completed_count")
        totalScore = UserDefaults.standard.integer(forKey: "total_user_score")
    }
}

// MARK: - Additional Card Components
struct RecentPuzzleCard: View {
    let puzzleType: String
    let onTap: () -> Void
    
    private var displayInfo: (title: String, icon: String, color: Color) {
        switch puzzleType {
        case "connotationwords":
            return ("Words", "brain.head.profile", .purple)
        case "math":
            return ("Math", "function", .blue)
        case "mathtipping":
            return ("Tips", "dollarsign.circle.fill", .green)
        case "anagram":
            return ("Anagram", "textformat.abc", .orange)
        case "trivia":
            return ("Trivia", "questionmark.circle.fill", .indigo)
        case "average":
            return ("Average", "chart.bar.fill", .pink)
        case "division":
            return ("Division", "divide.circle.fill", .cyan)
        default:
            return (puzzleType.capitalized, "puzzlepiece.fill", .gray)
        }
    }
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                Image(systemName: displayInfo.icon)
                    .font(.title2)
                    .foregroundColor(displayInfo.color)
                
                Text(displayInfo.title)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
                    .lineLimit(1)
            }
            .frame(width: 80, height: 80)
            .background(Color(.systemGray6))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(displayInfo.color.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Shimmer Effect
extension View {
    func shimmer() -> some View {
        self.modifier(ShimmerModifier())
    }
}

struct ShimmerModifier: ViewModifier {
    @State private var isAnimating = false
    
    func body(content: Content) -> some View {
        content
            .overlay(
                Rectangle()
                    .fill(
                        LinearGradient(
                            colors: [.clear, .white.opacity(0.4), .clear],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .rotationEffect(.degrees(70))
                    .offset(x: isAnimating ? 200 : -200)
                    .animation(
                        .linear(duration: 1.5).repeatForever(autoreverses: false),
                        value: isAnimating
                    )
            )
            .onAppear {
                isAnimating = true
            }
            .clipped()
    }
}

import SwiftUI
import FirebaseAuth

// MARK: - Puzzle Group Models
struct PuzzleGroup: Identifiable {
    let id: String
    let name: String
    let description: String
    let puzzleTypes: [String]
    let difficulty: String
    let category: String
    let completedCount: Int
    let totalTypes: Int
    let isCompleted: Bool
    
    var completionPercentage: Float {
        guard totalTypes > 0 else { return 0 }
        return Float(completedCount) / Float(totalTypes)
    }
}

// MARK: - Group Completion Manager
class GroupCompletionManager {
    static let shared = GroupCompletionManager()
    
    private init() {}
    
    func getCompletedTypes(userId: String, groupId: String) -> Set<String> {
        let key = "group_completion_\(userId)_\(groupId)"
        let completed = UserDefaults.standard.array(forKey: key) as? [String] ?? []
        return Set(completed)
    }
    
    func markTypeCompleted(userId: String, groupId: String, puzzleType: String) {
        let key = "group_completion_\(userId)_\(groupId)"
        var completed = UserDefaults.standard.array(forKey: key) as? [String] ?? []
        if !completed.contains(puzzleType) {
            completed.append(puzzleType)
            UserDefaults.standard.set(completed, forKey: key)
        }
    }
    
    func getAllGroups(userId: String) -> [PuzzleGroup] {
            let hardcodedGroups = [
                PuzzleGroup(
                    id: "math_basics",
                    name: "Math Fundamentals",
                    description: "Master basic math operations",
                    puzzleTypes: ["math", "division", "average", "percentages", "subtraction"],
                    difficulty: "Easy to Medium",
                    category: "Math",
                    completedCount: getCompletedTypes(userId: userId, groupId: "math_basics").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "math_basics").count == 5
                ),
                PuzzleGroup(
                    id: "math_advanced",
                    name: "Math Mastery",
                    description: "Advanced mathematical challenges",
                    puzzleTypes: ["mathexpression", "mathcrossword", "mathcomparison", "mathtipping", "pinballdeflector"],
                    difficulty: "Medium to Hard",
                    category: "Math",
                    completedCount: getCompletedTypes(userId: userId, groupId: "math_advanced").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "math_advanced").count == 5
                ),
                PuzzleGroup(
                    id: "math_applied",
                    name: "Math in Real Life",
                    description: "Apply math to everyday situations",
                    puzzleTypes: ["discounts", "purchasing", "conversion", "mathestimation", "numbersum"],
                    difficulty: "Medium",
                    category: "Math",
                    completedCount: getCompletedTypes(userId: userId, groupId: "math_applied").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "math_applied").count == 5
                ),
                PuzzleGroup(
                    id: "memory_basics",
                    name: "Memory Training",
                    description: "Boost your memory skills",
                    puzzleTypes: ["memoryprevioussingle", "memorypreviouspair", "memorysquares", "memorystory", "memoryretention"],
                    difficulty: "Easy to Medium",
                    category: "Memory",
                    completedCount: getCompletedTypes(userId: userId, groupId: "memory_basics").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "memory_basics").count == 5
                ),
                PuzzleGroup(
                    id: "memory_advanced",
                    name: "Memory Mastery",
                    description: "Challenge your working memory",
                    puzzleTypes: ["imagequestion", "triangledotmemory", "dualtask", "contextswitch", "memorysequencing"],
                    difficulty: "Medium to Hard",
                    category: "Memory",
                    completedCount: getCompletedTypes(userId: userId, groupId: "memory_advanced").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "memory_advanced").count == 5
                ),
                PuzzleGroup(
                    id: "word_fundamentals",
                    name: "Word Fundamentals",
                    description: "Build your vocabulary",
                    puzzleTypes: ["anagram", "synonyms", "antonyms", "wordprefix", "connotationwords"],
                    difficulty: "Easy to Medium",
                    category: "Word",
                    completedCount: getCompletedTypes(userId: userId, groupId: "word_fundamentals").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "word_fundamentals").count == 5
                ),
                PuzzleGroup(
                    id: "word_advanced",
                    name: "Word Master",
                    description: "Advanced word challenges",
                    puzzleTypes: ["crossword", "wordsearch", "wordsnake", "crypto", "storyPuzzle"],
                    difficulty: "Medium to Hard",
                    category: "Word",
                    completedCount: getCompletedTypes(userId: userId, groupId: "word_advanced").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "word_advanced").count == 5
                ),
                PuzzleGroup(
                    id: "visual_perception",
                    name: "Visual Perception",
                    description: "Test your visual recognition",
                    puzzleTypes: ["colorshapematching", "imagevortex", "uniqueobject", "oddoneout", "colortextmatching"],
                    difficulty: "Medium",
                    category: "Visual",
                    completedCount: getCompletedTypes(userId: userId, groupId: "visual_perception").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "visual_perception").count == 5
                ),
                PuzzleGroup(
                    id: "visual_advanced",
                    name: "Visual Mastery",
                    description: "Advanced visual challenges",
                    puzzleTypes: ["symmetry", "progressiverevelation", "realorai", "find_object", "imagepuzzle"],
                    difficulty: "Hard",
                    category: "Visual",
                    completedCount: getCompletedTypes(userId: userId, groupId: "visual_advanced").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "visual_advanced").count == 5
                ),
                PuzzleGroup(
                    id: "logic_puzzles",
                    name: "Logic & Reasoning",
                    description: "Sharpen your logical thinking",
                    puzzleTypes: ["flowpuzzle", "trivia", "numbersequence", "symbolswipe", "musicidentification"],
                    difficulty: "Medium",
                    category: "Logic",
                    completedCount: getCompletedTypes(userId: userId, groupId: "logic_puzzles").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "logic_puzzles").count == 5
                ),
                PuzzleGroup(
                    id: "geography_explorer",
                    name: "Geography Explorer",
                    description: "Discover the world",
                    puzzleTypes: ["geography_cities", "geography_countries", "trivia", "imagequestion", "progressiverevelation"],
                    difficulty: "Medium",
                    category: "Logic",
                    completedCount: getCompletedTypes(userId: userId, groupId: "geography_explorer").count,
                    totalTypes: 5,
                    isCompleted: getCompletedTypes(userId: userId, groupId: "geography_explorer").count == 5
                )
            ]
            
            return hardcodedGroups
        }
}

// MARK: - Helper Functions
private func getCategoryDisplayInfo(for category: String) -> (title: String, subtitle: String) {
    switch category {
    case "math":
        return ("Math", "Mathematical Challenges")
    case "anagram":
        return ("Anagram", "Word Scrambles")
    case "antonyms", "antonym":
        return ("Antonyms", "Match Opposite Words")
    case "synonyms", "synonym":
        return ("Synonyms", "Group Similar Words")
    case "trivia":
        return ("Trivia", "General Knowledge")
    case "average":
        return ("Average", "Calculate Averages")
    case "division":
        return ("Division", "Master Division")
    case "mathestimation":
        return ("Estimation", "Chart Estimation")
    case "percentages":
        return ("Percentage", "Percentage Calculations")
    case "discounts":
        return ("Discounts", "Calculate Discounts")
    case "purchasing":
        return ("Purchasing", "Subscription Calculations")
    case "conversion":
        return ("Conversion", "Unit Comparisons")
    case "mathtipping":
        return ("Tip Calculation", "Calculate correct tip amounts")
    case "memoryprevioussingle", "memory_previous_single":
        return ("Memory Single", "Remember Previous Item")
    case "memorypreviouspair", "memory_previous_pair":
        return ("Memory Pair", "Remember Previous Pairs")
    case "memorysquares", "memory_squares":
        return ("Memory Squares", "Visual Pattern Memory")
    case "memorystory", "memory_story":
        return ("Memory Story", "Audio Memory Challenges")
    case "memoryretention", "memory_retention":
        return ("Memory Retention", "Audio & Categorization")
    case "wordprefix", "word_prefix":
        return ("Word Prefix", "Find words with prefix")
    case "crossword":
        return ("Crossword", "Word Puzzles")
    case "colorshapematching", "color_shape_matching":
        return ("Color Shape Match", "Match Colors & Shapes")
    case "imagevortex", "image_vortex":
        return ("Image Vortex", "Visual Memory Challenge")
    case "uniqueobject", "unique_object":
        return ("Unique Object", "Find the Different One")
    case "wordsearch", "word_search":
        return ("Word Search", "Find Hidden Words")
    case "crypto":
        return ("Crypto", "Decode Messages")
    default:
        return (category.capitalized, "Test your \(category.capitalized) skills")
    }
}

// MARK: - Puzzle Group Views
struct PuzzleGroupCard: View {
    let group: PuzzleGroup
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 12) {
                // Header with completion indicator
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(group.name)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.leading)
                            .lineLimit(1)
                        
                        Text(group.description)
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.8))
                            .lineLimit(1)
                    }
                    
                    Spacer()
                    
                    if group.isCompleted {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.system(size: 20))
                    } else {
                        Text("\(group.completedCount)/\(group.totalTypes)")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(0.2))
                            .cornerRadius(12)
                    }
                }
                
                // Progress bar
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Progress")
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.7))
                        Spacer()
                        Text("\(Int(group.completionPercentage * 100))%")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.white)
                    }
                    
                    ProgressView(value: group.completionPercentage)
                        .progressViewStyle(LinearProgressViewStyle(tint: .white))
                        .scaleEffect(x: 1, y: 0.8, anchor: .center)
                }
                
                // Footer
                HStack {
                    Text(group.difficulty)
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.8))
                    
                    Spacer()
                    
                    Text("\(group.totalTypes) puzzles")
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.8))
                }
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(getGroupColor(for: group.category))
                    .shadow(color: .black.opacity(0.15), radius: 4, x: 0, y: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
        .frame(width: 200, height: 140)
    }
    
    private func getGroupColor(for category: String) -> LinearGradient {
        switch category {
        case "Math":
            return LinearGradient(
                colors: [Color(red: 0.08, green: 0.40, blue: 0.75), Color(red: 0.26, green: 0.65, blue: 0.96)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "Memory":
            return LinearGradient(
                colors: [Color(red: 0.00, green: 0.51, blue: 0.56), Color(red: 0.15, green: 0.78, blue: 0.85)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "Word":
            return LinearGradient(
                colors: [Color(red: 0.42, green: 0.45, blue: 1.0), Color(red: 0.61, green: 0.35, blue: 0.71)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "Visual":
            return LinearGradient(
                colors: [Color(red: 0.91, green: 0.12, blue: 0.39), Color(red: 1.0, green: 0.34, blue: 0.13)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        default:
            return LinearGradient(
                colors: [Color.gray, Color.blue],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }
}

struct PuzzleGroupsSection: View {
    let groups: [PuzzleGroup]
    let onGroupSelected: (PuzzleGroup) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Puzzle Collections")
                    .font(.title2)
                    .fontWeight(.semibold)
                Spacer()
                Text("\(groups.count) collections")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(groups) { group in
                        PuzzleGroupCard(group: group) {
                            onGroupSelected(group)
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
    }
}

// MARK: - Puzzle Group Detail View
struct PuzzleGroupDetailView: View {
    let group: PuzzleGroup
    @State private var completedTypes: Set<String> = []
    @State private var currentGroupData: PuzzleGroup
    @Environment(\.dismiss) private var dismiss
    let onPuzzleSelected: (String, String) -> Void // puzzleType, groupId
    
    init(group: PuzzleGroup, onPuzzleSelected: @escaping (String, String) -> Void) {
        self.group = group
        self.onPuzzleSelected = onPuzzleSelected
        self._currentGroupData = State(initialValue: group)
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Progress Header
                GroupProgressHeader(group: currentGroupData)
                
                // Instructions Card
                InstructionsCard()
                
                // Puzzle Types List
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(Array(group.puzzleTypes.enumerated()), id: \.offset) { index, puzzleType in
                            let isCompleted = completedTypes.contains(puzzleType)
                            let isUnlocked = index == 0 || completedTypes.contains(group.puzzleTypes[max(0, index - 1)])
                            let isCurrent = isUnlocked && !isCompleted
                            
                            PuzzleTypeStepCard(
                                index: index,
                                puzzleType: puzzleType,
                                isCompleted: isCompleted,
                                isUnlocked: isUnlocked,
                                isCurrent: isCurrent,
                                isLast: index == group.puzzleTypes.count - 1,
                                onPuzzleSelected: {
                                    if isUnlocked {
                                        onPuzzleSelected(puzzleType, group.id)
                                    }
                                }
                            )
                        }
                    }
                    .padding()
                }
                
                if currentGroupData.isCompleted {
                    CompletionCelebration()
                }
            }
            .navigationTitle(group.name)
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .onAppear {
            refreshCompletionStatus()
        }
    }
    
    private func refreshCompletionStatus() {
        guard let userId = Auth.auth().currentUser?.email else { return }
        completedTypes = GroupCompletionManager.shared.getCompletedTypes(userId: userId, groupId: group.id)
        
        // Update group data
        currentGroupData = PuzzleGroup(
            id: group.id,
            name: group.name,
            description: group.description,
            puzzleTypes: group.puzzleTypes,
            difficulty: group.difficulty,
            category: group.category,
            completedCount: completedTypes.count,
            totalTypes: group.totalTypes,
            isCompleted: completedTypes.count == group.totalTypes
        )
    }
}

struct GroupProgressHeader: View {
    let group: PuzzleGroup
    
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Progress")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("\(group.completedCount)/\(group.totalTypes) puzzle types completed")
                        .font(.headline)
                        .fontWeight(.semibold)
                }
                
                Spacer()
                
                if group.isCompleted {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                        .font(.system(size: 32))
                } else {
                    Text("\(Int(group.completionPercentage * 100))%")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(.blue)
                }
            }
            
            ProgressView(value: group.completionPercentage)
                .progressViewStyle(LinearProgressViewStyle(tint: group.isCompleted ? .green : .blue))
            
            HStack {
                Text("Difficulty: \(group.difficulty)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Spacer()
                Text("Each type has 5 puzzles")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color(.systemGray6))
    }
}

struct InstructionsCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("How it works:")
                .font(.subheadline)
                .fontWeight(.semibold)
            
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("•")
                    Text("Each puzzle type contains 5 challenges")
                }
                HStack {
                    Text("•")
                    Text("Complete all 5 to unlock the next puzzle type")
                }
                HStack {
                    Text("•")
                    Text("Try different types to discover your favorites!")
                }
            }
            .font(.caption)
            .foregroundColor(.secondary)
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(12)
        .padding(.horizontal)
    }
}

struct PuzzleTypeStepCard: View {
    let index: Int
    let puzzleType: String
    let isCompleted: Bool
    let isUnlocked: Bool
    let isCurrent: Bool
    let isLast: Bool
    let onPuzzleSelected: () -> Void
    
    private var displayInfo: (title: String, subtitle: String) {
        return getCategoryDisplayInfo(for: puzzleType)
    }
    
    var body: some View {
        HStack(spacing: 16) {
            // Progress indicator
            VStack(spacing: 0) {
                // Circle
                ZStack {
                    Circle()
                        .fill(circleColor)
                        .frame(width: 24, height: 24)
                    
                    circleContent
                }
                
                // Connecting line
                if !isLast {
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 2, height: 20)
                }
            }
            
            // Content card
            Button(action: onPuzzleSelected) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(displayInfo.title)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(isUnlocked ? .primary : .gray)
                        
                        Text(statusText)
                            .font(.caption)
                            .foregroundColor(statusColor)
                    }
                    
                    Spacer()
                    
                    if isUnlocked {
                        Image(systemName: isCompleted ? "arrow.clockwise" : "play.fill")
                            .foregroundColor(isCompleted ? .blue : .white)
                            .font(.system(size: 16))
                            .frame(width: 32, height: 32)
                            .background(isCompleted ? Color.blue.opacity(0.2) : Color.blue)
                            .clipShape(Circle())
                    }
                }
                .padding()
                .background(cardBackground)
                .cornerRadius(12)
            }
            .disabled(!isUnlocked)
            .buttonStyle(PlainButtonStyle())
        }
    }
    
    private var circleColor: Color {
        if isCompleted { return .green }
        if isCurrent { return .blue }
        if isUnlocked { return .blue.opacity(0.3) }
        return .gray.opacity(0.3)
    }
    
    @ViewBuilder
    private var circleContent: some View {
        if isCompleted {
            Image(systemName: "checkmark")
                .foregroundColor(.white)
                .font(.system(size: 12, weight: .bold))
        } else if isCurrent {
            Circle()
                .fill(Color.white)
                .frame(width: 8, height: 8)
        } else if !isUnlocked {
            Image(systemName: "lock.fill")
                .foregroundColor(.gray)
                .font(.system(size: 10))
        }
    }
    
    private var statusText: String {
        if isCompleted { return "All 5 puzzles completed" }
        if isCurrent { return "Next to unlock" }
        if isUnlocked { return "5 puzzles available" }
        return "Complete previous to unlock"
    }
    
    private var statusColor: Color {
        if isCompleted { return .green }
        if isCurrent { return .blue }
        if isUnlocked { return .secondary }
        return .gray
    }
    
    private var cardBackground: Color {
        if isCompleted { return Color.green.opacity(0.1) }
        if isCurrent { return Color.blue.opacity(0.1) }
        if isUnlocked { return Color(.systemBackground) }
        return Color(.systemGray6)
    }
}

struct CompletionCelebration: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "trophy.fill")
                .foregroundColor(.yellow)
                .font(.system(size: 48))
            
            Text("Group Completed!")
                .font(.title2)
                .fontWeight(.bold)
            
            Text("Amazing! You've mastered all puzzle types in this group. Ready for the next challenge?")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            HStack(spacing: 16) {
                Text("🏆")
                Text("⭐")
                Text("🎯")
            }
            .font(.title)
        }
        .padding()
        .background(Color.green.opacity(0.1))
        .cornerRadius(16)
        .padding()
    }
}

//
//  Puzzle.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//


//
//  Puzzle.swift
//  RiddleRush
//
//  Created by Sushanth Tiruvaipati on 5/22/25.
//
import Foundation

// MARK: - SubtractionPuzzleData struct
struct SubtractionPuzzleData {
    let allProblems: [[Int]]
    let difficulty: String
    let hint: String?
    var currentProblemIndex: Int
    
    var number1: Int {
        guard currentProblemIndex < allProblems.count, allProblems[currentProblemIndex].count >= 2 else { return 0 }
        return allProblems[currentProblemIndex][0]
    }
    
    var number2: Int {
        guard currentProblemIndex < allProblems.count, allProblems[currentProblemIndex].count >= 2 else { return 0 }
        return allProblems[currentProblemIndex][1]
    }
    
    var correctAnswer: Int {
        guard currentProblemIndex < allProblems.count, allProblems[currentProblemIndex].count >= 3 else { return 0 }
        return allProblems[currentProblemIndex][2]
    }
    
    // Helper to get next problem if available
    var hasNextProblem: Bool {
        return currentProblemIndex + 1 < allProblems.count
    }
    
    func nextProblem() -> SubtractionPuzzleData? {
        guard hasNextProblem else { return nil }
        return SubtractionPuzzleData(
            allProblems: allProblems,
            difficulty: difficulty,
            hint: hint,
            currentProblemIndex: currentProblemIndex + 1
        )
    }
    
    var progressText: String {
        return "\(currentProblemIndex + 1)/\(allProblems.count)"
    }
}
// MARK: - PuzzleCategory Extensions
extension PuzzleCategory {
    /// Get category by puzzle type
    static func category(for puzzleType: String) -> PuzzleCategory? {
        return categories.first { $0.puzzleType == puzzleType }
    }
    
    /// Get display color for category
    var displayColor: String {
        switch puzzleType {
        case "math":
            return "blue"
        case "storyPuzzle":
            return "purple"
        case "anagram":
            return "green"
        case "trivia":
            return "orange"
        case "average":
            return "red"
        default:
            return "gray"
        }
    }
}

// Updated structs to match the actual JSON structure

struct ApiPuzzle: Codable, Identifiable {
    let puzzleId: String
    let userId: String
    let topic: String
    let format: String
    let question: String
    let difficulty: String
    let timestamp: String
    let answer: String
    let options: [String]
    let hint: String?
    var id: String { puzzleId }
}

struct InnerPuzzleData: Codable {
    let puzzles: [ApiPuzzle]
    let leaderboard: [LeaderboardEntry]?
}

// Keep your existing OuterPuzzleData unchanged
struct OuterPuzzleData: Codable {
    let userId: String
    let topic: String
    let format: String
    let numPuzzles: Int
    let createdAt: String
    let updatedAt: String
    let puzzleData: InnerPuzzleData
}

// Add this new wrapper struct
struct NestedPuzzleDataWrapper: Codable {
    let puzzleData: InnerPuzzleData
}

// Replace your CustomPuzzleSetResponse with this:
struct CustomPuzzleSetResponse: Codable {
    let userId: String
    let topic: String
    let format: String
    let numPuzzles: Int
    let createdAt: String
    let updatedAt: String
    let puzzleData: NestedPuzzleDataWrapper
    
    // Computed property to easily create OuterPuzzleData for compatibility
    var asOuterPuzzleData: OuterPuzzleData {
        return OuterPuzzleData(
            userId: userId,
            topic: topic,
            format: format,
            numPuzzles: numPuzzles,
            createdAt: createdAt,
            updatedAt: updatedAt,
            puzzleData: puzzleData.puzzleData
        )
    }
}

struct LeaderboardEntry: Codable, Identifiable {
    let userId: String
    let score: Int
    let timeTaken: Int?
    var id: String { userId }
}

struct Puzzle: Identifiable, Codable {
    let question: String
    let answer: String
    let hint: String
    let options: [String]
    let format: String
    let puzzleType: String?
    let puzzleId: String
    let id: String
    let name: String
    let createdAt: TimeInterval
    let status: String
    let difficulty: String
    
    static var empty: Puzzle {
        return Puzzle(
                question: "",
                answer: "",
                hint: "",
                options: [],
                format: "qa",
                puzzleType: nil,
                puzzleId: UUID().uuidString,
                id: UUID().uuidString,
                name: "Untitled",
                createdAt: Date().timeIntervalSince1970 * 1000,
                status: "pending",
                difficulty: "Easy"
            )
    }

    var creationTimeFormatted: String {
        let date = Date(timeIntervalSince1970: createdAt / 1000.0)
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
    
    init(
        question: String,
        answer: String,
        hint: String,
        options: [String],
        format: String,
        puzzleType: String? = nil,
        puzzleId: String,
        id: String,
        name: String,
        createdAt: TimeInterval,
        status: String,
        difficulty: String
    ) {
        self.question = question
        self.answer = answer
        self.hint = hint
        self.options = options
        self.format = format
        self.puzzleType = puzzleType
        self.puzzleId = puzzleId
        self.id = id
        self.name = name
        self.createdAt = createdAt
        self.status = status
        self.difficulty = difficulty
    }

    init(json: [String: Any]) {
        self.question = json["question"] as? String ?? ""
        self.answer = json["answer"] as? String ?? ""
        self.hint = json["hint"] as? String ?? ""
        self.format = json["format"] as? String ?? "qa"
        self.puzzleId = json["puzzleId"] as? String ?? "unknown"
        self.options = json["options"] as? [String] ?? []
        
        self.id = json["id"] as? String ?? UUID().uuidString
        self.name = json["name"] as? String ?? "Untitled"
        self.createdAt = json["createdAt"] as? TimeInterval ?? 0
        self.status = json["status"] as? String ?? "unknown"
        self.puzzleType = json["puzzleType"] as? String
        self.difficulty = json["difficulty"] as? String ?? "unknown"
    }
    
    init(apiPuzzle: ApiPuzzle, outerPuzzleData: OuterPuzzleData) {
            let dateFormatter = ISO8601DateFormatter()
            var timeInterval: TimeInterval = Date().timeIntervalSince1970 // Fallback to current time
            if let date = dateFormatter.date(from: apiPuzzle.timestamp) {
                timeInterval = date.timeIntervalSince1970
            }

            self.question = apiPuzzle.question
            self.answer = apiPuzzle.answer
            self.hint = apiPuzzle.hint ?? "" // Ensure hint is non-nil
            self.options = apiPuzzle.options
            self.format = apiPuzzle.format
            self.puzzleType = apiPuzzle.format.lowercased()
            self.puzzleId = apiPuzzle.puzzleId
            self.id = apiPuzzle.puzzleId
            self.name = apiPuzzle.topic // API 'topic' becomes your 'name'
            self.createdAt = timeInterval * 1000 // Convert to milliseconds if your TimeInterval is in milliseconds
            // IMPORTANT: Handling 'status'. If OuterPuzzleData doesn't have it, provide a default.
            self.status = "active" // Default status if not available from API
            self.difficulty = apiPuzzle.difficulty
        }

}

struct DailyPuzzleResponse: Codable {
    let puzzleSet: DailyPuzzleSet
    let success: Bool?
    let source: String?
    let puzzleCount: Int?
}

struct DailyPuzzle: Identifiable {
    let id: UUID
    let topic: String
    let question: String
    let answer: String
    let hint: String
    let options: [String]
    let difficulty: String
    let generationDate: String
}

struct FailedPuzzleResponse: Codable {
    let status: String
    let message: String
    let puzzleSet: FailedPuzzleSetInfo
    let action: String?        // Make optional - not in regenerating response
    let failedAt: String?      // Make optional - not in regenerating response
}

struct FailedPuzzleSetInfo: Codable {
    let name: String
    let creator: String
    let format: String
}

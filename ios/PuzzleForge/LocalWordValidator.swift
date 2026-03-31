//
//  LocalWordValidator.swift
//  PuzzleForge
//

import Foundation
import SQLite3

// MARK: - Word Validation Result
struct WordValidationResult {
    let valid: Bool
    let word: String?
    let points: Int
    let reason: String
    let source: String
    let isNewDiscovery: Bool
}

// MARK: - Local Word Validator
class LocalWordValidator {
    static let shared = LocalWordValidator()
    
    private var db: OpaquePointer?
    private var dictionaryWords: Set<String> = []
    private var isInitialized = false
    private let initializationQueue = DispatchQueue(label: "com.puzzleforge.validator", qos: .userInitiated)
    private var initializationTask: Task<Void, Never>?
    
    private init() {
        // Don't auto-initialize, let caller do it explicitly
    }
    
    // MARK: - Initialization
    func initialize() async {
        guard !isInitialized else { return }
        
        print("📚 Initializing LocalWordValidator...")
        
        // Try to load from SQLite first
        if await loadFromSQLite() {
            print("✅ Loaded words from SQLite database")
            isInitialized = true
            return
        }
        
        // Fallback to text file
        if await loadFromTextFile() {
            print("✅ Loaded words from dictionary.txt")
            isInitialized = true
            return
        }
        
        print("⚠️ Failed to load dictionary - will rely on server validation only")
    }
    
    // MARK: - Load from SQLite
    private func loadFromSQLite() async -> Bool {
        guard let dbPath = Bundle.main.path(forResource: "words", ofType: "db") else {
            print("❌ words.db not found in bundle")
            return false
        }
        
        var tempDB: OpaquePointer?
        guard sqlite3_open(dbPath, &tempDB) == SQLITE_OK else {
            print("❌ Failed to open SQLite database")
            return false
        }
        
        self.db = tempDB
        
        // Query all words from the database
        let query = "SELECT word FROM words"
        var statement: OpaquePointer?
        
        guard sqlite3_prepare_v2(db, query, -1, &statement, nil) == SQLITE_OK else {
            print("❌ Failed to prepare SQLite query")
            sqlite3_close(db)
            return false
        }
        
        var wordCount = 0
        var loadedWords = Set<String>() // Use local set first to avoid threading issues
        
        while sqlite3_step(statement) == SQLITE_ROW {
            if let cString = sqlite3_column_text(statement, 0) {
                // Words in DB are lowercase, store them uppercase for comparison
                let word = String(cString: cString).uppercased()
                loadedWords.insert(word)
                wordCount += 1
                
                // Log progress every 10000 words
                if wordCount % 10000 == 0 {
                    print("📊 Loaded \(wordCount) words...")
                }
            }
        }
        
        sqlite3_finalize(statement)
        
        // Now assign to the main set
        self.dictionaryWords = loadedWords
        
        print("📊 Loaded \(wordCount) words from SQLite database")
        print("📚 Sample words: \(Array(dictionaryWords.prefix(5)).joined(separator: ", "))")
        
        return wordCount > 0
    }
    
    // MARK: - Load from Text File
    private func loadFromTextFile() async -> Bool {
        guard let filePath = Bundle.main.path(forResource: "dictionary", ofType: "txt") else {
            print("❌ dictionary.txt not found in bundle")
            return false
        }
        
        do {
            let content = try String(contentsOfFile: filePath, encoding: .utf8)
            let words = content.components(separatedBy: .newlines)
            
            for word in words {
                let cleanWord = word.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                if !cleanWord.isEmpty && cleanWord.allSatisfy({ $0.isLetter }) {
                    dictionaryWords.insert(cleanWord)
                }
            }
            
            print("📊 Loaded \(dictionaryWords.count) words from dictionary.txt")
            return !dictionaryWords.isEmpty
        } catch {
            print("❌ Failed to read dictionary.txt: \(error)")
            return false
        }
    }
    
    // MARK: - Word Validation
    func validateWord(
        word: String,
        letterSet: String,
        usedWords: Set<String>,
        puzzleWords: [String: LetterSetWord]
    ) -> WordValidationResult {
        let normalizedWord = word.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
        
        // 1. Check minimum length
        guard normalizedWord.count >= 3 else {
            return WordValidationResult(
                valid: false,
                word: nil,
                points: 0,
                reason: "Word too short (minimum 3 letters)",
                source: "validation",
                isNewDiscovery: false
            )
        }
        
        // 2. Check if already used
        if usedWords.contains(normalizedWord) {
            return WordValidationResult(
                valid: false,
                word: nil,
                points: 0,
                reason: "Word already found",
                source: "validation",
                isNewDiscovery: false
            )
        }
        
        // 3. Check if letters are available in letter set
        guard canFormWord(normalizedWord, from: letterSet) else {
            return WordValidationResult(
                valid: false,
                word: nil,
                points: 0,
                reason: "Cannot form word from available letters",
                source: "validation",
                isNewDiscovery: false
            )
        }
        
        // 4. Check if word is in puzzle data (server-provided words)
        if let puzzleWord = puzzleWords[normalizedWord] {
            return WordValidationResult(
                valid: true,
                word: normalizedWord,
                points: puzzleWord.points,
                reason: "Valid word from puzzle data",
                source: "server",
                isNewDiscovery: false
            )
        }
        
        // 5. Check if word exists in local dictionary
        if dictionaryWords.contains(normalizedWord) {
            let points = calculatePoints(for: normalizedWord, letterSet: letterSet)
            return WordValidationResult(
                valid: true,
                word: normalizedWord,
                points: points,
                reason: "Valid word from local dictionary",
                source: "local_dictionary",
                isNewDiscovery: true
            )
        }
        
        // 6. Not found in any source
        return WordValidationResult(
            valid: false,
            word: nil,
            points: 0,
            reason: "Word not found in dictionary",
            source: "validation",
            isNewDiscovery: false
        )
    }
    
    // MARK: - Helper Functions
    private func canFormWord(_ word: String, from letterSet: String) -> Bool {
        var availableLetters = letterSet.uppercased().map { $0 }
        
        for char in word {
            guard let index = availableLetters.firstIndex(of: char) else {
                return false
            }
            availableLetters.remove(at: index)
        }
        
        return true
    }
    
    private func calculatePoints(for word: String, letterSet: String) -> Int {
        let basePoints = 10
        let length = word.count
        
        // Length multiplier
        let lengthMultiplier: Double = {
            switch length {
            case 3: return 1.0
            case 4: return 1.5
            case 5: return 2.0
            case 6: return 2.5
            case 7...8: return 3.0
            default: return 3.5
            }
        }()
        
        // Check if uses all letters (bonus)
        let usesAllLetters = Set(word.uppercased()).count == Set(letterSet.uppercased()).count
        let allLettersBonus = usesAllLetters ? 50 : 0
        
        return Int(Double(basePoints) * lengthMultiplier) + allLettersBonus
    }
    
    // MARK: - Get Hints
    func getHints(letterSet: String, count: Int = 5) -> [String] {
        let normalizedLetterSet = letterSet.uppercased()
        var hints: [String] = []
        
        // Find words that can be formed from the letter set
        let possibleWords = dictionaryWords.filter { word in
            word.count >= 3 && word.count <= normalizedLetterSet.count &&
            canFormWord(word, from: normalizedLetterSet)
        }
        
        // Sort by length (prefer shorter words for hints)
        let sortedWords = possibleWords.sorted { $0.count < $1.count }
        
        // Return first 'count' words
        hints = Array(sortedWords.prefix(count))
        
        return hints
    }
    
    // MARK: - Statistics
    func getDictionaryStats() -> (totalWords: Int, isInitialized: Bool) {
        return (dictionaryWords.count, isInitialized)
    }
    
    // MARK: - Check if word exists (simple check)
    func isValidWord(_ word: String) -> Bool {
        let normalized = word.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
        return dictionaryWords.contains(normalized)
    }
    
    deinit {
        if let db = db {
            sqlite3_close(db)
        }
    }
}

// MARK: - Helper Extension
extension String {
    func normalizedForWordSearch() -> String {
        return self.uppercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .filter { $0.isLetter }
    }
}

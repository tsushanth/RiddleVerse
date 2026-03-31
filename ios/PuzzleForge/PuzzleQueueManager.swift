//
//  PuzzleQueueManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/26/25.
//

import Foundation
import FirebaseAuth

class PuzzleQueueManager {
    static let shared = PuzzleQueueManager()
    
    // MARK: - Constants
    private let queueMinSize = 2
    private let queueTargetSize = 5
    private let queueMaxSize = 8
    
    // MARK: - Properties
    private var puzzleQueues: [String: [QueuedPuzzle]] = [:]
    private var queueStats: [String: QueueStats] = [:]
    private var currentlyFetching: Set<String> = []
    private let queue = DispatchQueue(label: "puzzle.queue.manager", attributes: .concurrent)
    
    private init() {
        loadQueuesFromDisk()
    }
    
    // MARK: - Public Methods
    
    /// Get next puzzle from queue with immediate fallback to direct fetch
    func getNextPuzzle(puzzleType: String, difficulty: String) async -> QueuedPuzzle? {
        let queueKey = "\(puzzleType)_\(difficulty.lowercased())"
        
        // First, try to get from queue
        let queuedPuzzle = await withCheckedContinuation { (continuation: CheckedContinuation<QueuedPuzzle?, Never>) in
            queue.async(flags: .barrier) {
                if var queue = self.puzzleQueues[queueKey], !queue.isEmpty {
                    let nextPuzzle = queue.removeFirst()
                    self.puzzleQueues[queueKey] = queue
                    
                    print("🔵 Serving puzzle from queue: \(nextPuzzle.puzzleId) (\(queue.count) remaining)")
                    
                    // Update stats
                    var stats = self.queueStats[queueKey] ?? QueueStats(queueKey: queueKey)
                    stats.totalServed += 1
                    stats.lastServedAt = Date()
                    self.queueStats[queueKey] = stats
                    
                    // Trigger async refill if needed (non-blocking)
                    if queue.count <= self.queueMinSize {
                        print("🔵 Queue low for \(queueKey) (\(queue.count) remaining), triggering background refill")
                        Task {
                            await self.refillQueueAsync(puzzleType: puzzleType, difficulty: difficulty)
                        }
                    }
                    
                    self.saveQueuesToDisk()
                    continuation.resume(returning: nextPuzzle)
                } else {
                    print("⚠️ Queue empty for \(queueKey), will use direct fetch")
                    continuation.resume(returning: nil)
                }
            }
        }
        
        // If we got a puzzle from queue, return it
        if let puzzle = queuedPuzzle {
            return puzzle
        }
        
        // If queue is empty, check if we need bulk fetch for session
        print("⚠️ Queue empty for \(queueKey), checking session needs")

        // Check if this might be for a multi-puzzle session by looking at recent calls
        // You can pass this info or check some context here
        let shouldFetchBulk = self.puzzleQueues[queueKey]?.count ?? 0 < 5 // or whatever logic determines session need

        if shouldFetchBulk {
            print("🔵 Fetching bulk puzzles for potential session")
            let bulkPuzzles = await fetchPuzzlesBatchForSession(puzzleType: puzzleType, difficulty: difficulty, count: 5)
            
            if !bulkPuzzles.isEmpty {
                // Store puzzles in queue and return first one
                await withCheckedContinuation { continuation in
                    queue.async(flags: .barrier) {
                        self.puzzleQueues[queueKey] = bulkPuzzles
                        self.saveQueuesToDisk()
                        continuation.resume()
                    }
                }
                
                print("✅ Fetched and stored \(bulkPuzzles.count) puzzles, returning first")
                return bulkPuzzles.first
            }
        }

        // Fallback to single fetch
        print("🔵 Using single direct fetch")
        let directPuzzle = await fetchPuzzleImmediately(puzzleType: puzzleType, difficulty: difficulty)

        // Still trigger background refill
        Task {
            await self.refillQueueAsync(puzzleType: puzzleType, difficulty: difficulty)
        }

        return directPuzzle
    }
    
    /// Initialize queues on app startup - completely async
    func initializeQueues(forYouPriorityPuzzles: Set<String>? = nil) async {
        print("🔵 Initializing puzzle queues for For You priorities")
        
        // Use provided priority puzzles or determine them dynamically
        let priorityPuzzles = forYouPriorityPuzzles ?? getForYouPriorityPuzzleTypes()
        
        print("🔵 Priority puzzle types for initialization: \(priorityPuzzles)")
        
        // Check which priority queues need initial filling
        let emptyQueues = await withCheckedContinuation { continuation in
            queue.async {
                let empty = Array(priorityPuzzles).filter { puzzleType in
                    let queueKey = "\(puzzleType)_medium"
                    let queue = self.puzzleQueues[queueKey]
                    return queue == nil || queue!.count < self.queueMinSize
                }
                continuation.resume(returning: empty)
            }
        }
        
        if !emptyQueues.isEmpty {
            print("🔵 Background filling \(emptyQueues.count) priority queues: \(emptyQueues)")
            
            // Fill empty priority queues in background using daily cache endpoint
            Task.detached(priority: .medium) {
                await self.fillQueuesUsingDailyCacheAsync(puzzleTypes: emptyQueues, difficulty: "medium")
            }
        } else {
            print("🔵 All priority queues already have sufficient puzzles")
        }
        
        print("🔵 Queue initialization complete (background filling continues)")
        logQueueStatus()
    }
    
    private func getForYouPriorityPuzzleTypes() -> Set<String> {
        var priorityPuzzles: Set<String> = []
        
        // 1. Recent puzzle types (top priority)
        let recentTypes = UserDefaults.standard.getRecentPuzzleTypes()
        priorityPuzzles.formUnion(recentTypes.prefix(4))
        
        // 2. Popular puzzle types for quick actions
        let popularTypes = ["math", "anagram", "trivia", "average", "memorystory"]
        priorityPuzzles.formUnion(popularTypes)
        
        // 3. Daily topics (if available from UserDefaults)
        if let savedDailyTopics = UserDefaults.standard.array(forKey: "daily_topics") as? [String] {
            priorityPuzzles.formUnion(savedDailyTopics)
        }
        
        // 4. Ensure we have at least 8 priority puzzles for good UX
        if priorityPuzzles.count < 8 {
            let fallbackTypes = ["wordprefix", "letterset", "imagepuzzle", "crossword", "wordsearch"]
            priorityPuzzles.formUnion(fallbackTypes.prefix(8 - priorityPuzzles.count))
        }
        
        // Limit to reasonable number to avoid overwhelming the server
        let finalPriorityPuzzles = Set(priorityPuzzles.prefix(12))
        
        print("🔵 Determined For You priority puzzles: \(finalPriorityPuzzles)")
        
        return finalPriorityPuzzles
    }
    
    func updatePriorityQueues(newPriorityPuzzles: Set<String>) async {
        print("🔵 Updating priority queues with new set: \(newPriorityPuzzles)")
        
        // Check which new priority queues need filling
        let emptyQueues = await withCheckedContinuation { continuation in
            queue.async {
                let empty = Array(newPriorityPuzzles).filter { puzzleType in
                    let queueKey = "\(puzzleType)_medium"
                    let queue = self.puzzleQueues[queueKey]
                    return queue == nil || queue!.count < self.queueMinSize
                }
                continuation.resume(returning: empty)
            }
        }
        
        if !emptyQueues.isEmpty {
            print("🔵 Filling \(emptyQueues.count) new priority queues: \(emptyQueues)")
            
            // Fill new priority queues in background using daily cache endpoint
            Task.detached(priority: .medium) {
                await self.fillQueuesUsingDailyCacheAsync(puzzleTypes: emptyQueues, difficulty: "medium")
            }
        }
    }

    // MARK: - Daily Cache Endpoint Methods

    private func fillQueuesUsingDailyCacheAsync(puzzleTypes: [String], difficulty: String) async {
        print("🔵 Filling queues using daily cache endpoint for: \(puzzleTypes)")
        
        // Process each puzzle type individually since the endpoint is per-type
        await withTaskGroup(of: Void.self) { group in
            for puzzleType in puzzleTypes {
                group.addTask {
                    await self.fetchFromDailyCacheAsync(puzzleType: puzzleType, difficulty: difficulty)
                }
            }
        }
        
        print("🔵 Completed filling queues using daily cache endpoint")
    }

    private func fetchFromDailyCacheAsync(puzzleType: String, difficulty: String) async {
        let queueKey = "\(puzzleType)_\(difficulty.lowercased())"
        
        // Check if already fetching for this queue
        let isAlreadyFetching = await withCheckedContinuation { continuation in
            queue.async {
                continuation.resume(returning: self.currentlyFetching.contains(queueKey))
            }
        }
        
        if isAlreadyFetching {
            print("🔵 Already fetching for \(queueKey), skipping duplicate request")
            return
        }
        
        // Mark as fetching
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                self.currentlyFetching.insert(queueKey)
                continuation.resume()
            }
        }
        
        do {
            let fetchedPuzzles = await fetchPuzzlesFromDailyCache(puzzleType: puzzleType, difficulty: difficulty)
            
            if !fetchedPuzzles.isEmpty {
                await withCheckedContinuation { continuation in
                    queue.async(flags: .barrier) {
                        var currentQueue = self.puzzleQueues[queueKey] ?? []
                        
                        // FIXED: Add deduplication here
                        let existingIds = Set(currentQueue.map { $0.puzzleId })
                        var uniqueAdded = 0
                        
                        // Add new puzzles to back of queue, checking for duplicates
                        for puzzle in fetchedPuzzles {
                            if !existingIds.contains(puzzle.puzzleId) && currentQueue.count < self.queueMaxSize {
                                currentQueue.append(puzzle)
                                uniqueAdded += 1
                            } else if existingIds.contains(puzzle.puzzleId) {
                                print("⚠️ Skipping duplicate puzzle from daily cache: \(puzzle.puzzleId)")
                            }
                        }
                        
                        self.puzzleQueues[queueKey] = currentQueue
                        
                        // Update stats
                        var stats = self.queueStats[queueKey] ?? QueueStats(queueKey: queueKey)
                        stats.totalFetched += uniqueAdded
                        stats.lastFetchedAt = Date()
                        self.queueStats[queueKey] = stats
                        
                        self.saveQueuesToDisk()
                        
                        print("🔵 Filled queue \(queueKey) with \(uniqueAdded) unique puzzles from daily cache (total: \(currentQueue.count))")
                        continuation.resume()
                    }
                }
            } else {
                print("⚠️ No puzzles fetched from daily cache for \(queueKey)")
            }
            
        } catch {
            print("🔴 Failed to fetch from daily cache for \(queueKey): \(error)")
        }
        
        // Remove from fetching set
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                self.currentlyFetching.remove(queueKey)
                continuation.resume()
            }
        }
    }
    
    func addPuzzleToQueue(_ puzzle: QueuedPuzzle, for puzzleType: String, difficulty: String) {
        let queueKey = "\(puzzleType)_\(difficulty.lowercased())"
        
        queue.async(flags: .barrier) {
            var currentQueue = self.puzzleQueues[queueKey] ?? []
            
            // Check for duplicates by puzzle ID
            let isDuplicate = currentQueue.contains { existingPuzzle in
                existingPuzzle.puzzleId == puzzle.puzzleId
            }
            
            if !isDuplicate && currentQueue.count < self.queueMaxSize {
                currentQueue.append(puzzle)
                self.puzzleQueues[queueKey] = currentQueue
                self.saveQueuesToDisk()
                print("✅ Added puzzle \(puzzle.puzzleId) to queue \(queueKey)")
            } else if isDuplicate {
                print("⚠️ Duplicate puzzle detected: \(puzzle.puzzleId), skipping")
            } else {
                print("⚠️ Queue full for \(queueKey), skipping puzzle \(puzzle.puzzleId)")
            }
        }
    }

    private func fetchPuzzlesFromDailyCache(puzzleType: String, difficulty: String) async -> [QueuedPuzzle] {
        let count = queueTargetSize // Fetch target number of puzzles
        let urlString = "https://puzzleverseai.com/api/daily-puzzle-cache/todays-puzzles/\(puzzleType)?difficulty=\(difficulty)&count=\(count)"
        
        guard let url = URL(string: urlString) else {
            print("🔴 Invalid URL for daily cache: \(urlString)")
            return []
        }
        
        do {
            print("🔵 Fetching from daily cache: \(urlString)")
            let (data, response) = try await URLSession.shared.data(from: url)
            
            if let httpResponse = response as? HTTPURLResponse {
                print("🔵 Daily cache HTTP Status: \(httpResponse.statusCode)")
                
                if httpResponse.statusCode == 200 {
                    if let responseString = String(data: data, encoding: .utf8) {
                        return processDailyCacheResponse(responseBody: responseString, puzzleType: puzzleType, difficulty: difficulty)
                    }
                } else {
                    print("🔴 Daily cache HTTP error \(httpResponse.statusCode)")
                    if let responseString = String(data: data, encoding: .utf8) {
                        print("🔴 Error response: \(responseString)")
                    }
                }
            }
        } catch {
            print("🔴 Daily cache network error: \(error.localizedDescription)")
        }
        
        return []
    }

    private func processDailyCacheResponse(responseBody: String, puzzleType: String, difficulty: String) -> [QueuedPuzzle] {
        var puzzles: [QueuedPuzzle] = []
        
        do {
            guard let jsonData = responseBody.data(using: .utf8),
                  let json = try JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
                  json["success"] as? Bool == true,
                  let puzzlesArray = json["puzzles"] as? [[String: Any]] else {
                print("🔴 Daily cache response indicated failure or missing puzzles array")
                return []
            }
            
            for puzzleData in puzzlesArray {
                do {
                    let queuedPuzzle = convertDailyCachePuzzleToQueuedPuzzle(
                        puzzleData: puzzleData,
                        puzzleType: puzzleType,
                        difficulty: difficulty
                    )
                    puzzles.append(queuedPuzzle)
                } catch {
                    print("🔴 Failed to parse daily cache puzzle: \(error)")
                }
            }
            
            print("🔵 Processed \(puzzles.count) puzzles from daily cache for \(puzzleType)")
            
        } catch {
            print("🔴 Failed to process daily cache response: \(error)")
        }
        
        return puzzles
    }

    private func convertDailyCachePuzzleToQueuedPuzzle(puzzleData: [String: Any], puzzleType: String, difficulty: String) -> QueuedPuzzle {
        let puzzleId = puzzleData["id"] as? String ?? UUID().uuidString
        let question = puzzleData["question"] as? String ?? ""
        let answer = puzzleData["answer"] as? String ?? ""
        
        // For memorystory and other complex types, the question field contains JSON
        // For simpler types, it might be plain text
        var hint = ""
        var options: [String] = []
        
        // Try to parse question as JSON for complex puzzle types
        if let questionData = question.data(using: .utf8),
           let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
            hint = questionJson["hint"] as? String ?? ""
            if let questionOptions = questionJson["options"] as? [String] {
                options = questionOptions
            }
        }
        
        // If options array is empty, check the top-level options field
        if options.isEmpty,
           let topLevelOptions = puzzleData["options"] as? [String] {
            options = topLevelOptions
        }
        
        return QueuedPuzzle(
            puzzleId: puzzleId,
            puzzleType: puzzleType,
            difficulty: difficulty,
            question: question,
            answer: answer,
            hint: hint,
            options: options,
            queuedAt: Date(),
            mediaUrls: extractMediaUrls(from: puzzleData)
        )
    }
    
    /// Get queue status for monitoring
    func getQueueStatus() -> [String: QueueInfo] {
        return queue.sync {
            var status: [String: QueueInfo] = [:]
            
            for (queueKey, puzzleQueue) in puzzleQueues {
                let stats = queueStats[queueKey]
                status[queueKey] = QueueInfo(
                    queueKey: queueKey,
                    currentSize: puzzleQueue.count,
                    isLow: puzzleQueue.count <= queueMinSize,
                    isFetching: currentlyFetching.contains(queueKey),
                    stats: stats
                )
            }
            
            return status
        }
    }
    
    /// Clear specific queue
    func clearQueue(puzzleType: String, difficulty: String) {
        queue.async(flags: .barrier) {
            let queueKey = "\(puzzleType)_\(difficulty.lowercased())"
            self.puzzleQueues[queueKey] = []
            self.saveQueuesToDisk()
            print("🔵 Cleared queue: \(queueKey)")
        }
    }
    
    /// Clear all queues
    func clearAllQueues() {
        queue.async(flags: .barrier) {
            self.puzzleQueues.removeAll()
            self.queueStats.removeAll()
            self.currentlyFetching.removeAll()
            
            if let userDefaults = UserDefaults.standard as UserDefaults? {
                userDefaults.removeObject(forKey: "puzzle_queue_data")
                userDefaults.removeObject(forKey: "puzzle_queue_stats")
            }
            
            print("🔵 Cleared all queues")
        }
    }
    
    private func refillQueueAsync(puzzleType: String, difficulty: String) async {
        let queueKey = "\(puzzleType)_\(difficulty.lowercased())"
        
        // Check if already fetching for this queue
        let isAlreadyFetching = await withCheckedContinuation { continuation in
            queue.async {
                continuation.resume(returning: self.currentlyFetching.contains(queueKey))
            }
        }
        
        if isAlreadyFetching {
            print("🔵 Already fetching for \(queueKey), skipping duplicate request")
            return
        }
        
        // Mark as fetching
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                self.currentlyFetching.insert(queueKey)
                continuation.resume()
            }
        }
        
        do {
            print("🔵 Starting background refill for \(queueKey)")
            
            let fetchedPuzzles = await fetchPuzzlesBatchAsync(puzzleTypes: [puzzleType], difficulty: difficulty)
            
            if !fetchedPuzzles.isEmpty {
                await withCheckedContinuation { continuation in
                    queue.async(flags: .barrier) {
                        var currentQueue = self.puzzleQueues[queueKey] ?? []
                        
                        // FIXED: Add deduplication here
                        let existingIds = Set(currentQueue.map { $0.puzzleId })
                        
                        // Add new puzzles to back of queue, checking for duplicates
                        for puzzle in fetchedPuzzles {
                            if !existingIds.contains(puzzle.puzzleId) && currentQueue.count < self.queueMaxSize {
                                currentQueue.append(puzzle)
                                print("✅ Added unique puzzle \(puzzle.puzzleId) to queue")
                            } else if existingIds.contains(puzzle.puzzleId) {
                                print("⚠️ Skipping duplicate puzzle: \(puzzle.puzzleId)")
                            }
                        }
                        
                        self.puzzleQueues[queueKey] = currentQueue
                        self.saveQueuesToDisk()
                        
                        let uniqueAdded = fetchedPuzzles.filter { !existingIds.contains($0.puzzleId) }.count
                        print("🔵 Refilled queue \(queueKey) with \(uniqueAdded) unique puzzles (total: \(currentQueue.count))")
                        continuation.resume()
                    }
                }
            } else {
                print("⚠️ No puzzles fetched for refill of \(queueKey)")
            }
            
        } catch {
            print("🔴 Failed to refill queue \(queueKey): \(error)")
        }
        
        // Remove from fetching set
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                self.currentlyFetching.remove(queueKey)
                continuation.resume()
            }
        }
    }
    
    private func batchFillQueuesAsync(puzzleTypes: [String], difficulty: String) async {
        let fetchedPuzzles = await fetchPuzzlesBatchAsync(puzzleTypes: puzzleTypes, difficulty: difficulty)
        
        await withCheckedContinuation { continuation in
            queue.async(flags: .barrier) {
                var totalUniqueAdded = 0
                
                for puzzle in fetchedPuzzles {
                    let queueKey = "\(puzzle.puzzleType)_\(difficulty.lowercased())"
                    var currentQueue = self.puzzleQueues[queueKey] ?? []
                    
                    // FIXED: Check for duplicates by puzzle ID
                    let isDuplicate = currentQueue.contains { $0.puzzleId == puzzle.puzzleId }
                    
                    if !isDuplicate && currentQueue.count < self.queueMaxSize {
                        currentQueue.append(puzzle)
                        self.puzzleQueues[queueKey] = currentQueue
                        totalUniqueAdded += 1
                        
                        // Update stats
                        var stats = self.queueStats[queueKey] ?? QueueStats(queueKey: queueKey)
                        stats.totalFetched += 1
                        stats.lastFetchedAt = Date()
                        self.queueStats[queueKey] = stats
                    } else if isDuplicate {
                        print("⚠️ Skipping duplicate puzzle in batch: \(puzzle.puzzleId)")
                    }
                }
                
                self.saveQueuesToDisk()
                print("🔵 Background batch filled queues with \(totalUniqueAdded) unique puzzles (skipped \(fetchedPuzzles.count - totalUniqueAdded) duplicates)")
                continuation.resume()
            }
        }
    }
    
    func removeDuplicatesFromAllQueues() {
        queue.async(flags: .barrier) {
            var totalRemoved = 0
            
            for (queueKey, puzzleQueue) in self.puzzleQueues {
                var seen = Set<String>()
                var uniquePuzzles: [QueuedPuzzle] = []
                
                for puzzle in puzzleQueue {
                    if !seen.contains(puzzle.puzzleId) {
                        seen.insert(puzzle.puzzleId)
                        uniquePuzzles.append(puzzle)
                    } else {
                        totalRemoved += 1
                        print("🧹 Removing duplicate: \(puzzle.puzzleId) from \(queueKey)")
                    }
                }
                
                self.puzzleQueues[queueKey] = uniquePuzzles
            }
            
            if totalRemoved > 0 {
                self.saveQueuesToDisk()
                print("🧹 Removed \(totalRemoved) duplicate puzzles from all queues")
            }
        }
    }
    
    private func fetchPuzzlesBatchAsync(puzzleTypes: [String], difficulty: String) async -> [QueuedPuzzle] {
        // Guest mode: use daily cache instead of authenticated batch endpoint
        if AuthHelper.isGuest {
            print("🔵 Guest mode: using daily cache for batch fill")
            if let puzzleType = puzzleTypes.first {
                return await fetchPuzzlesFromDailyCache(puzzleType: puzzleType, difficulty: difficulty)
            }
            return []
        }

        let userId = AuthHelper.userId
        let userEmail = AuthHelper.userEmail

        let requestData: [String: Any] = [
            "userId": userId,
            "puzzleTypes": puzzleTypes,
            "puzzlesPerType": queueTargetSize,
            "excludeCompleted": false,
            "difficulty": difficulty
        ]
        
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: requestData)
            
            var request = URLRequest(url: URL(string: "https://puzzleverseai.com/batch-fetch-puzzles")!)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = jsonData
            
            print("🔵 Background batch filling queues for: \(puzzleTypes)")
            
            let (data, response) = try await URLSession.shared.data(for: request)
            
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                if let responseString = String(data: data, encoding: .utf8) {
                    return processBatchQueueResponse(responseBody: responseString, difficulty: difficulty)
                }
            } else {
                print("🔴 Batch fill failed with response: \(response)")
            }
            
        } catch {
            print("🔴 Exception during batch fill: \(error)")
        }
        
        return []
    }
    
    private func fetchPuzzleImmediately(puzzleType: String, difficulty: String) async -> QueuedPuzzle? {
        let urlString: String
        if AuthHelper.isGuest {
            urlString = "https://puzzleverseai.com/fetch-random-puzzle-ios/\(puzzleType)"
            print("🔵 IMMEDIATE FETCH: Guest mode - using anonymous endpoint")
        } else {
            let userId = AuthHelper.userId
            let userEmail = AuthHelper.userEmail
            urlString = "https://puzzleverseai.com/fetch-next-puzzle-ios/\(puzzleType)?userId=\(userId)&email=\(userEmail)&difficulty=\(difficulty)"
        }
        print("🔵 IMMEDIATE FETCH: Attempting URL: \(urlString)")
        
        guard let url = URL(string: urlString) else {
            print("🔴 IMMEDIATE FETCH: Invalid URL: \(urlString)")
            return nil
        }
        
        do {
            print("🔵 IMMEDIATE FETCH: Making network request...")
            let (data, response) = try await URLSession.shared.data(from: url)
            
            if let httpResponse = response as? HTTPURLResponse {
                print("🔵 IMMEDIATE FETCH: HTTP Status: \(httpResponse.statusCode)")
                
                if httpResponse.statusCode == 200 {
                    if let responseString = String(data: data, encoding: .utf8) {
                        print("🔵 IMMEDIATE FETCH: Response: \(responseString)")
                        
                        if let jsonData = responseString.data(using: .utf8),
                           let json = try JSONSerialization.jsonObject(with: jsonData) as? [String: Any] {
                            
                            print("🔵 IMMEDIATE FETCH: JSON parsed successfully")
                            
                            if json["success"] as? Bool == true,
                               let puzzleData = json["puzzleData"] as? [String: Any] {
                                
                                print("🔵 IMMEDIATE FETCH: Success! Creating puzzle...")
                                return convertServerResponseToQueuedPuzzle(puzzleData: puzzleData, puzzleType: puzzleType, difficulty: difficulty)
                            } else {
                                print("🔴 IMMEDIATE FETCH: API returned success=false or missing puzzleData")
                                print("🔴 IMMEDIATE FETCH: JSON: \(json)")
                            }
                        } else {
                            print("🔴 IMMEDIATE FETCH: Failed to parse JSON from response")
                        }
                    } else {
                        print("🔴 IMMEDIATE FETCH: Failed to convert response to string")
                    }
                } else {
                    print("🔴 IMMEDIATE FETCH: HTTP error \(httpResponse.statusCode)")
                    if let responseString = String(data: data, encoding: .utf8) {
                        print("🔴 IMMEDIATE FETCH: Error response: \(responseString)")
                    }
                }
            }
        } catch {
            print("🔴 IMMEDIATE FETCH: Network error: \(error.localizedDescription)")
        }
        
        return nil
    }
    
    // MARK: - Existing helper methods (unchanged)
    
    private func processBatchQueueResponse(responseBody: String, difficulty: String) -> [QueuedPuzzle] {
        var puzzles: [QueuedPuzzle] = []
        
        do {
            guard let jsonData = responseBody.data(using: .utf8),
                  let json = try JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
                  json["success"] as? Bool == true,
                  let puzzleData = json["puzzleData"] as? [String: Any] else {
                print("🔴 Batch response indicated failure")
                return []
            }
            
            for (puzzleType, puzzlesValue) in puzzleData {
                if let puzzlesArray = puzzlesValue as? [[String: Any]] {
                    for puzzleObj in puzzlesArray {
                        do {
                            let queuedPuzzle = convertSinglePuzzleDataToQueuedPuzzle(puzzleData: puzzleObj, difficulty: difficulty)
                            puzzles.append(queuedPuzzle)
                        } catch {
                            print("🔴 Failed to parse puzzle for \(puzzleType): \(error)")
                        }
                    }
                    
                    print("🔵 Processed \(puzzlesArray.count) puzzles for \(puzzleType)")
                }
            }
            
        } catch {
            print("🔴 Failed to process batch queue response: \(error)")
        }
        
        return puzzles
    }
    
    private func convertServerResponseToQueuedPuzzle(puzzleData: [String: Any], puzzleType: String, difficulty: String) -> QueuedPuzzle {
        let options = puzzleData["options"] as? [String] ?? []
        
        return QueuedPuzzle(
            puzzleId: puzzleData["puzzleId"] as? String ?? "immediate_\(Date().timeIntervalSince1970)",
            puzzleType: puzzleType,
            difficulty: difficulty,
            question: puzzleData["question"] as? String ?? "",
            answer: puzzleData["answer"] as? String ?? "",
            hint: puzzleData["hint"] as? String ?? "",
            options: options,
            queuedAt: Date(),
            mediaUrls: extractMediaUrls(from: puzzleData)
        )
    }
    
    private func convertSinglePuzzleDataToQueuedPuzzle(puzzleData: [String: Any], difficulty: String) -> QueuedPuzzle {
        let options = puzzleData["options"] as? [String] ?? []
        
        return QueuedPuzzle(
            puzzleId: puzzleData["puzzleId"] as? String ?? UUID().uuidString,
            puzzleType: puzzleData["puzzleType"] as? String ?? "",
            difficulty: puzzleData["difficulty"] as? String ?? difficulty,
            question: puzzleData["question"] as? String ?? "",
            answer: puzzleData["answer"] as? String ?? "",
            hint: puzzleData["hint"] as? String ?? "",
            options: options,
            queuedAt: Date(),
            mediaUrls: extractMediaUrls(from: puzzleData)
        )
    }
    
    private func extractMediaUrls(from puzzleData: [String: Any]) -> [String] {
        var urls: [String] = []
        let urlPattern = #"https://[^\s\)"']+\.(jpg|jpeg|png|gif|webp|mp3|wav|ogg|m4a)(\?[^\s\)"']*)?"#
        let regex = try? NSRegularExpression(pattern: urlPattern, options: .caseInsensitive)
        
        let textFields = [
            puzzleData["question"] as? String ?? "",
            puzzleData["hint"] as? String ?? "",
            puzzleData["answer"] as? String ?? ""
        ]
        
        for text in textFields {
            let range = NSRange(location: 0, length: text.utf16.count)
            let matches = regex?.matches(in: text, options: [], range: range) ?? []
            
            for match in matches {
                if let range = Range(match.range, in: text) {
                    urls.append(String(text[range]))
                }
            }
        }
        
        // Extract from options
        if let options = puzzleData["options"] as? [String] {
            for option in options {
                let range = NSRange(location: 0, length: option.utf16.count)
                let matches = regex?.matches(in: option, options: [], range: range) ?? []
                
                for match in matches {
                    if let range = Range(match.range, in: option) {
                        urls.append(String(option[range]))
                    }
                }
            }
        }
        
        return Array(Set(urls)) // Remove duplicates
    }
    
    private func getServerSupportedPuzzleTypes() -> [String] {
        return [
            "anagram", "antonyms", "synonyms", "crossword", "wordsearch", "wordsnake",
            "wordprefix", "letterset", "memorysequencing", "memoryretention", "memorystory",
            "imagepuzzle", "imagequestion", "imagematch", "find_differences", "find_object",
            "waldopuzzle", "uniqueobject", "snakewordsearch", "musicidentification",
            "crypto", "sentencetransitions", "connotationwords"
        ]
    }
    
    private func loadQueuesFromDisk() {
        if let queueData = UserDefaults.standard.data(forKey: "puzzle_queue_data"),
           let decoded = try? JSONDecoder().decode([String: [QueuedPuzzle]].self, from: queueData) {
            puzzleQueues = decoded
            print("🔵 Loaded \(puzzleQueues.count) queues from disk")
        }
        
        if let statsData = UserDefaults.standard.data(forKey: "puzzle_queue_stats"),
           let decoded = try? JSONDecoder().decode([String: QueueStats].self, from: statsData) {
            queueStats = decoded
        }
    }
    
    private func saveQueuesToDisk() {
        do {
            let queueData = try JSONEncoder().encode(puzzleQueues)
            UserDefaults.standard.set(queueData, forKey: "puzzle_queue_data")
            
            let statsData = try JSONEncoder().encode(queueStats)
            UserDefaults.standard.set(statsData, forKey: "puzzle_queue_stats")
            
            UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "puzzle_queue_last_saved")
        } catch {
            print("🔴 Failed to save queues to disk: \(error)")
        }
    }
    
    private func logQueueStatus() {
        print("🔵 Current Queue Status:")
        for (queueKey, puzzleQueue) in puzzleQueues {
            let stats = queueStats[queueKey]
            let isFetching = currentlyFetching.contains(queueKey)
            
            let status = isFetching ? "FETCHING" :
                        puzzleQueue.count <= queueMinSize ? "LOW" :
                        puzzleQueue.count >= queueTargetSize ? "GOOD" : "OK"
            
            print("🔵   \(queueKey):")
            print("🔵     Size: \(puzzleQueue.count)")
            print("🔵     Status: \(status)")
            print("🔵     Total Served: \(stats?.totalServed ?? 0)")
            print("🔵     Total Fetched: \(stats?.totalFetched ?? 0)")
        }
    }
    
    func getBulkPuzzlesForSession(puzzleType: String, difficulty: String, count: Int) async -> [QueuedPuzzle] {
        let queueKey = "\(puzzleType)_\(difficulty.lowercased())"
        
        // First, try to get puzzles from existing queue
        let queuedPuzzles = await withCheckedContinuation { (continuation: CheckedContinuation<[QueuedPuzzle], Never>) in
            queue.async(flags: .barrier) {
                if var queue = self.puzzleQueues[queueKey], queue.count >= count {
                    // Take the required number of puzzles
                    let sessionPuzzles = Array(queue.prefix(count))
                    queue.removeFirst(count)
                    self.puzzleQueues[queueKey] = queue
                    
                    print("🎯 BULK: Serving \(count) puzzles from queue (\(queue.count) remaining)")
                    
                    // Update stats
                    var stats = self.queueStats[queueKey] ?? QueueStats(queueKey: queueKey)
                    stats.totalServed += count
                    stats.lastServedAt = Date()
                    self.queueStats[queueKey] = stats
                    
                    // Trigger refill if needed
                    if queue.count <= self.queueMinSize {
                        Task {
                            await self.refillQueueAsync(puzzleType: puzzleType, difficulty: difficulty)
                        }
                    }
                    
                    self.saveQueuesToDisk()
                    continuation.resume(returning: sessionPuzzles)
                } else {
                    print("⚠️ Queue insufficient for bulk session \(queueKey) (need \(count), have \(self.puzzleQueues[queueKey]?.count ?? 0))")
                    continuation.resume(returning: [])
                }
            }
        }
        
        // If we got enough puzzles from queue, return them
        if queuedPuzzles.count >= count {
            return queuedPuzzles
        }
        
        // Queue insufficient - fetch directly using modified batch method
        print("🔵 Fetching \(count) puzzles directly for session")
        return await fetchPuzzlesBatchForSession(puzzleType: puzzleType, difficulty: difficulty, count: count)
    }
    
    private func fetchPuzzlesBatchForSession(puzzleType: String, difficulty: String, count: Int) async -> [QueuedPuzzle] {
        // Guest mode: use daily cache instead of authenticated batch endpoint
        if AuthHelper.isGuest {
            print("🔵 Guest mode: using daily cache for session fetch")
            return await fetchPuzzlesFromDailyCache(puzzleType: puzzleType, difficulty: difficulty)
        }

        let userId = AuthHelper.userId

        let requestData: [String: Any] = [
            "userId": userId,
            "puzzleTypes": [puzzleType],
            "puzzlesPerType": count, // Use the session count here
            "excludeCompleted": false,
            "difficulty": difficulty
        ]
        
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: requestData)
            
            var request = URLRequest(url: URL(string: "https://puzzleverseai.com/batch-fetch-puzzles")!)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = jsonData
            
            print("🔵 Session batch fetching \(count) puzzles for: \(puzzleType)")
            
            let (data, response) = try await URLSession.shared.data(for: request)
            
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                if let responseString = String(data: data, encoding: .utf8) {
                    return processBatchQueueResponse(responseBody: responseString, difficulty: difficulty)
                }
            } else {
                print("🔴 Session batch fetch failed with response: \(response)")
            }
            
        } catch {
            print("🔴 Exception during session batch fetch: \(error)")
        }
        
        return []
    }
}

// MARK: - Data Models (unchanged)

struct QueuedPuzzle: Codable {
    let puzzleId: String
    let puzzleType: String
    let difficulty: String
    let question: String
    let answer: String
    let hint: String
    let options: [String]
    let queuedAt: Date
    let mediaUrls: [String]
    
    func toPuzzle() -> Puzzle {
        return Puzzle(
            question: question,
            answer: answer,
            hint: hint,
            options: options,
            format: puzzleType,
            puzzleType: puzzleType,
            puzzleId: puzzleId,
            id: UUID().uuidString,
            name: puzzleType,
            createdAt: queuedAt.timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: difficulty
        )
    }
}

struct QueueStats: Codable {
    let queueKey: String
    var totalFetched: Int = 0
    var totalServed: Int = 0
    var lastFetchedAt: Date = Date()
    var lastServedAt: Date = Date()
}

struct QueueInfo {
    let queueKey: String
    let currentSize: Int
    let isLow: Bool
    let isFetching: Bool
    let stats: QueueStats?
}

// MARK: - Array Extension for Chunking

extension Array {
    func chunked(into size: Int) -> [[Element]] {
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0..<Swift.min($0 + size, count)])
        }
    }
}

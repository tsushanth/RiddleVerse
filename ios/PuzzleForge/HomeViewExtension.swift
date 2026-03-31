//
//  HomeViewExtension.swift
//  RiddleVerse
//
//  Created by Sushanth Tiruvaipati on 10/17/25.
//
import SwiftUI
import FirebaseAuth

extension HomeView {
    
     func fetchPuzzle(for category: String, for difficulty: String) {
        print("🔵 fetchPuzzle called - category: \(category), difficulty: \(difficulty)")
        print("🔵 Current session state - completed: \(completedPuzzleCount), target: \(targetPuzzleCount)")
        print("🔵 selectedPuzzleSet is nil: \(selectedPuzzleSet == nil)")
        print("🔵 isPuzzleLoading: \(isPuzzleLoading)")
        print("🔵 isInActiveSession: \(isInActiveSession)")

        // Check client-side limits before fetching
        if !subscriptionManager.canFetchPuzzle(type: category) {
            if let limitInfo = subscriptionManager.getLimitInfo(for: category) {
                self.currentLimitInfo = limitInfo
                self.showLimitDialog = true
            }
            return
        }

        // Set session flag for new sessions (when starting fresh)
        if completedPuzzleCount == 0 && targetPuzzleCount > 1 {
            isInActiveSession = true
            print("🚀 Starting new session - set isInActiveSession = true")
        }

        // Don't clear navigation states when continuing a session
        if isInActiveSession || completedPuzzleCount == 0 {
            selectedPuzzleSet = nil
            //navigationState = .none
            currentQuestionIndex = 0
        }

        // Always set loading state
        isPuzzleLoading = true
        errorMessage = nil
        
        // Check if this is a local memory puzzle
        if isLocalMemoryPuzzle(category) {
            fetchLocalMemoryPuzzle(for: category, difficulty: difficulty)
            return
        }
        
        // For all other puzzles, use the existing network fetch
        fetchNetworkPuzzle(for: category, for: difficulty)
    }

    func resetPuzzleSession() {
        print("🔄 Resetting puzzle session")
        selectedPuzzle = nil
        selectedPuzzleSet = nil
        navigationState = .none
        currentQuestionIndex = 0
        currentSubtractionProblemIndex = 0
        isPuzzleLoading = false
        isPuzzleLoading = false
        completedPuzzleCount = 0
        sessionStats = []
        showCompletionScreen = false
        isInActiveSession = false  // Clear session flag
    }
    
    private func isLocalMemoryPuzzle(_ category: String) -> Bool {
        return category == "memoryprevioussingle" ||
               category == "memory_previous_single" ||
               category == "memorypreviouspair" ||
               category == "memory_previous_pair" ||
               category == "colorshapematching" ||
               category == "color_shape_matching" ||
               category == "imagevortex" ||
               category == "image_vortex" ||
               category == "mathcomparison" ||
               category == "math_comparison" ||
               category == "numbersequence" ||
               category == "number_sequence" ||
               category == "numbersum" ||
               category == "number_sum" ||
               category == "symbolswipe" ||
               category == "symbol_swipe" ||
               category == "uniqueobject" ||
               category == "unique_object" ||
               category == "contextswitch" ||
               category == "mathcrossword" ||
               category == "dualtask" ||
               category == "colortextmatching" ||
               category == "geography_cities" ||
               category == "geography_countries" ||
               category == "geographycities" ||
               category == "geographycountries" ||
               category == "conversion" ||
               category == "discounts" ||
               category == "discount" ||
               category == "mathtipping" ||
               category == "math_tipping" ||
               category == "mathestimation" ||
               category == "math_estimation" ||
               category == "purchasing" ||
               category == "division" ||
               category == "average" ||
               category == "percentages" ||
               category == "percentage" ||
               category == "pinballdeflector" ||
               category == "mathexpression" ||
               category == "triangledotmemory" ||
               category == "symmetry"
    }
    
    private func fetchLocalMemoryPuzzle(for category: String, difficulty: String) {
        selectedPuzzle = nil
        selectedPuzzleSet = nil
        if !isInActiveSession { navigationState = .none }   // ← only reset when not in-session
        isPuzzleLoading = true
        errorMessage = nil
        currentQuestionIndex = 0
        
        print("🧠 LOCAL: Generating \(category) puzzle with difficulty: \(difficulty)")
        
        // Simulate brief loading
        DispatchQueue.main.asyncAfter(deadline: .now()) {
            let (questionData, answerData) = self.generateLocalMemoryData(for: category, difficulty: difficulty)
            
            guard !questionData.isEmpty && !answerData.isEmpty else {
                self.errorMessage = "Failed to generate local puzzle data"
                self.isPuzzleLoading = false
                return
            }
            
            let puzzle = Puzzle(
                question: questionData,
                answer: answerData,
                hint: "Focus and remember the sequence!",
                options: [], // Memory puzzles don't use multiple choice options
                format: "memory",
                puzzleType: category,
                puzzleId: "local-\(category)-\(UUID().uuidString)",
                id: UUID().uuidString,
                name: category.capitalized,
                createdAt: Date().timeIntervalSince1970 * 1000,
                status: "ready",
                difficulty: difficulty
            )
            
            self.selectedPuzzle = puzzle
            
            // Set appropriate navigation state
            if category.contains("single") {
                self.navigationState = .memoryPreviousSingle
            } else if category.contains("pair") {
                self.navigationState = .memoryPreviousPair
            } else if category == "uniqueobject" || category == "unique_object" {
                self.navigationState = .uniqueObject
            } else if category.contains("mathexpression") {
                self.navigationState = .mathExpression
            } else if category == "triangledotmemory" || category == "triangle_dot_memory" {
                self.navigationState = .triangleDotMemory
            } else if category.contains("symmetry") {
                self.navigationState = .symmetry
            } else if category.contains("colorshape") {
                self.navigationState = .colorshapematching
            } else if category.contains("imagevortex") || category.contains("image_vortex") {
                self.navigationState = .imagevortex
            } else if category == "contextswitch" {
                self.navigationState = .contextswitch
            } else if category == "mathcrossword" || category == "math_crossword" {  // ADD THIS
                self.navigationState = .mathCrossword  // Route to math crossword
            } else if category == "dualtask" || category == "dual_task" {  // NEW: Add dual task navigation
                self.navigationState = .dualTask
            } else if category == "colortextmatching" || category == "color_text_matching" {  // NEW: Add color text matching navigation
                self.navigationState = .colorTextMatching
            } else if category == "discounts" || category == "discount" {
                self.navigationState = .discounts
            } else if category == "mathtipping" || category == "math_tipping" {
                self.navigationState = .tipBubble
            } else if category == "mathestimation" || category == "math_estimation" {
                self.navigationState = .estimation
            } else if category == "purchasing" {
                self.navigationState = .purchasing
            } else if category == "division" {
                self.navigationState = .division
            } else if category == "average" {
                self.navigationState = .average
            } else if category == "percentages" || category == "percentage" {
                self.navigationState = .percentage
            } else {
                self.navigationState = self.determineNavigationType(for: category, options: puzzle.options)
            }
            
            self.isPuzzleLoading = false
            self.keepCurrentViewWhileFetchingNext = false
            print("✅ LOCAL: Generated \(category) puzzle successfully")
        }
    }
    
    private func generateLocalMemoryData(for category: String, difficulty: String) -> (String, String) {
        switch category {
        case "memoryprevioussingle", "memory_previous_single":
            return LocalMemoryPuzzleGenerator.generateMemoryPreviousSingle(difficulty: difficulty)
        case "memorypreviouspair", "memory_previous_pair":
            return LocalMemoryPuzzleGenerator.generateMemoryPreviousPair(difficulty: difficulty)
        case "colorshapematching", "color_shape_matching":
            return LocalMemoryPuzzleGenerator.generateColorShapeMatching(difficulty: difficulty)
        case "mathexpression":
            return LocalMemoryPuzzleGenerator.generateMathExpression(difficulty: difficulty)
        case "imagevortex", "image_vortex":
            return LocalMemoryPuzzleGenerator.generateImageVortex(difficulty: difficulty)
        case "triangledotmemory":
            return LocalMemoryPuzzleGenerator.generateTriangleDotMemory(difficulty: difficulty)
        case "numbersequence":
            return LocalMemoryPuzzleGenerator.generateNumberSequence(difficulty: difficulty)
        case "symmetry":
            return LocalMemoryPuzzleGenerator.generateSymmetry(difficulty: difficulty)
        case "numbersum":
            return LocalMemoryPuzzleGenerator.generateNumberSum(difficulty: difficulty)
        case "symbolswipe":
            return LocalMemoryPuzzleGenerator.generateSymbolSwipe(difficulty: difficulty)
        case "uniqueobject":
            return LocalMemoryPuzzleGenerator.generateUniqueObject(difficulty: difficulty)
        case "mathcomparison":
            return LocalMemoryPuzzleGenerator.generateMathComparison(difficulty: difficulty)
        case "contextswitch":
            return LocalMemoryPuzzleGenerator.generateContextSwitch(difficulty: difficulty)
        case "mathcrossword", "math_crossword":
            return MathCrosswordGenerator.generatePuzzle(difficulty: difficulty)
        case "dualtask", "dual_task":
            return LocalMemoryPuzzleGenerator.generateDualTask(difficulty: difficulty)
        case "colortextmatching", "color_text_matching":
            return LocalMemoryPuzzleGenerator.generateColorTextMatching(difficulty: difficulty)
        case "geography_cities", "geographycities":
            return LocalMemoryPuzzleGenerator.generateCitiesPuzzle(difficulty: difficulty)
        case "geography_countries", "geographycountries":
            return LocalMemoryPuzzleGenerator.generateCountriesPuzzle(difficulty: difficulty)
        case "conversion":
            return LocalMemoryPuzzleGenerator.generateConversion(difficulty: difficulty)
        case "discounts", "discount":
            return LocalMemoryPuzzleGenerator.generateDiscounts(difficulty: difficulty)
        case "mathtipping", "math_tipping":
                return LocalMemoryPuzzleGenerator.generateMathTipping(difficulty: difficulty)
        case "mathestimation", "math_estimation":
            return LocalMemoryPuzzleGenerator.generateMathEstimation(difficulty: difficulty)
        case "purchasing":
            return LocalMemoryPuzzleGenerator.generatePurchasing(difficulty: difficulty)
        case "division":
            return LocalMemoryPuzzleGenerator.generateDivision(difficulty: difficulty)
        case "average":
            return LocalMemoryPuzzleGenerator.generateAverage(difficulty: difficulty)
        case "pinballdeflector":
            return LocalMemoryPuzzleGenerator.generatePinballDeflector(difficulty: difficulty)
        case "percentages", "percentage":
            return LocalMemoryPuzzleGenerator.generatePercentages(difficulty: difficulty)
        default:
            print("❌ Unknown local memory puzzle type: \(category)")
            return ("", "")
        }
    }
    
    // Rename your existing fetchPuzzle method to this:
    // Replace your fetchNetworkPuzzle method in HomeView.swift with this cleaned version:

    private func fetchNetworkPuzzle(for category: String, for difficulty: String) {
        DispatchQueue.main.async {
            self.isPuzzleLoading = true
            self.errorMessage = nil
            self.currentQuestionIndex = 0
        }
        
        print("🔵 FETCH: User: \(AuthHelper.userId) (guest: \(AuthHelper.isGuest))")
        print("Fetching puzzle for category: \(category), difficulty: \(difficulty)")

        Task {
            do {
                // Use the optimized queue manager - it will return immediately from queue or direct fetch
                if let queuedPuzzle = await PuzzleQueueManager.shared.getNextPuzzle(puzzleType: category, difficulty: difficulty) {
                    print("Got puzzle (queue or direct): \(queuedPuzzle.puzzleId)")
                    
                    // Convert QueuedPuzzle to your Puzzle model and apply transformations
                    let finalPuzzle = await processQueuedPuzzle(queuedPuzzle, category: category, difficulty: difficulty)
                    
                    await MainActor.run {
                        self.selectedPuzzle = finalPuzzle
                        self.isPuzzleLoading = false
                        
                        let finalNavState = self.determineNavigationType(for: category, options: finalPuzzle.options)
                        
                        // Force clear and reset navigation state
                        self.navigationState = .none
                        
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                            self.navigationState = finalNavState
                            print("🔵 Navigation state set to: \(finalNavState)")
                        }
                        
                        self.keepCurrentViewWhileFetchingNext = false
                    }
                    
                } else {
                    // This should rarely happen now, but keep as fallback
                    print("Both queue and direct fetch failed, showing error")
                    await MainActor.run {
                        self.isPuzzleLoading = false
                        self.errorMessage = "Unable to load puzzle. Please try again."
                    }
                }
                
            } catch {
                print("Error fetching puzzle: \(error)")
                await MainActor.run {
                    self.isPuzzleLoading = false
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func processQueuedPuzzle(_ queuedPuzzle: QueuedPuzzle, category: String, difficulty: String) async -> Puzzle {
        var finalQuestion = queuedPuzzle.question
        var finalAnswer = queuedPuzzle.answer
        var finalHint = queuedPuzzle.hint
        var finalOptions = queuedPuzzle.options
        
        switch category.lowercased() {
        case "antonym", "antonyms":
            // Antonyms: All data is JSON in question field
            if let questionData = queuedPuzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                finalQuestion = questionJson["question"] as? String ?? queuedPuzzle.question
                finalAnswer = questionJson["answer"] as? String ?? queuedPuzzle.answer
                finalHint = questionJson["hint"] as? String ?? queuedPuzzle.hint
                if let options = questionJson["options"] as? [String] {
                    finalOptions = options
                }
            }
            
        case "imagequestion", "image_question":
                print("🔵 PROCESS: Image question - passing raw JSON to transformation")
                // For image question, pass the raw question data through
                // The transformation will happen in applyPuzzleTransformations
                finalQuestion = queuedPuzzle.question
                finalAnswer = queuedPuzzle.answer
        
        case "wordprefix", "word_prefix":
            // Wordprefix: prefix in question, JSON data in answer
            finalQuestion = queuedPuzzle.answer  // JSON goes to question for the view
            finalAnswer = queuedPuzzle.question  // prefix stays as answer
            
        case "synonym", "synonyms":
            // Similar to antonyms - JSON in question
            if let questionData = queuedPuzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                finalQuestion = questionJson["question"] as? String ?? queuedPuzzle.question
                finalAnswer = questionJson["answer"] as? String ?? queuedPuzzle.answer
                finalHint = questionJson["hint"] as? String ?? queuedPuzzle.hint
                if let options = questionJson["options"] as? [String] {
                    finalOptions = options
                }
            }
            
        case "memorystory", "memory_story":
            // Memory story: JSON in question
            if let questionData = queuedPuzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                finalQuestion = queuedPuzzle.question // Keep as JSON for the view
                finalAnswer = queuedPuzzle.answer
            }
            
        case "memoryretention", "memory_retention":
            // Memory retention: JSON in question
            finalQuestion = queuedPuzzle.question // Keep as JSON
            finalAnswer = queuedPuzzle.answer
            
        case "memorysequencing", "memory_sequencing":
            // Memory sequencing: JSON in question
            finalQuestion = queuedPuzzle.question // Keep as JSON
            finalAnswer = queuedPuzzle.answer
            
        case "crypto", "cryptogram":
            // Crypto might need special handling
            finalQuestion = queuedPuzzle.question
            finalAnswer = queuedPuzzle.answer
            
        case "wordsearch", "word_search":
            // Word search: JSON in question
            finalQuestion = queuedPuzzle.question // Keep as JSON
            finalAnswer = queuedPuzzle.answer
            
        case "crossword":
            // Crossword: JSON in question
            finalQuestion = queuedPuzzle.question // Keep as JSON
            finalAnswer = queuedPuzzle.answer
            
        case "anagram":
            // Anagram might have options
            finalQuestion = queuedPuzzle.question
            finalAnswer = queuedPuzzle.answer
            finalOptions = queuedPuzzle.options
            
        default:
            // For other puzzles, try to parse JSON if question contains it
            if let questionData = queuedPuzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                
                if let actualQuestion = questionJson["question"] as? String {
                    finalQuestion = actualQuestion
                }
                if let actualAnswer = questionJson["answer"] as? String {
                    finalAnswer = actualAnswer
                }
                if let actualHint = questionJson["hint"] as? String {
                    finalHint = actualHint
                }
                if let actualOptions = questionJson["options"] as? [String] {
                    finalOptions = actualOptions
                }
            }
        }
        
        let parsedPuzzle = Puzzle(
            question: finalQuestion,
            answer: finalAnswer,
            hint: finalHint,
            options: finalOptions,
            format: queuedPuzzle.puzzleType,
            puzzleType: category,
            puzzleId: queuedPuzzle.puzzleId,
            id: UUID().uuidString,
            name: category,
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: difficulty
        )
        
        return await applyPuzzleTransformations(
            puzzle: parsedPuzzle,
            category: category,
            difficulty: difficulty
        )
    }

    private func fetchPuzzleDirectFromNetwork(category: String, difficulty: String) async {
        guard let url = fetchPuzzleURL(for: category, for: difficulty) else {
            await MainActor.run {
                self.isPuzzleLoading = false
                self.errorMessage = "Failed to create puzzle URL"
            }
            return
        }
        
        print("Fetching from URL: \(url)")
        
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            
            if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 200 {
                print("Network error: HTTP \(httpResponse.statusCode)")
                await MainActor.run {
                    self.isPuzzleLoading = false
                    self.errorMessage = "Network error: HTTP \(httpResponse.statusCode)"
                }
                return
            }
            
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                await MainActor.run {
                    self.isPuzzleLoading = false
                    self.errorMessage = "Failed to parse response"
                }
                return
            }

            // Check if this is a "generation in progress" response
            // Check if this is a "generation in progress" response
            if let success = json["success"] as? Bool, !success {
                let message = json["message"] as? String ?? "Puzzles are being generated. Please try again in a moment!"
                let regenerationAllowed = json["regenerationAllowed"] as? Bool ?? true
                
                // Check if this is a limit reached scenario
                if !regenerationAllowed {
                    if let remainingGenerations = json["remainingGenerations"] as? [String: Any],
                       let daily = remainingGenerations["daily"] as? Int,
                       let monthly = remainingGenerations["monthly"] as? Int {
                        
                        await MainActor.run {
                            self.isPuzzleLoading = false
                            
                            // Create limit info from the response
                            let limitInfo = LimitInfo(
                                reason: category,
                                dailyUsed: max(0, self.subscriptionManager.currentTier.dailyLimit - daily),
                                dailyLimit: self.subscriptionManager.currentTier.dailyLimit,
                                monthlyUsed: max(0, self.subscriptionManager.currentTier.monthlyLimit - monthly),
                                monthlyLimit: self.subscriptionManager.currentTier.monthlyLimit,
                                resetTime: self.getNextResetTime(),
                                upgradeUrl: "", // Empty string or your upgrade URL
                                message: message
                            )
                            
                            self.currentLimitInfo = limitInfo
                            self.showLimitDialog = true
                        }
                        return
                    }
                }
                
                // Regular generation in progress message
                await MainActor.run {
                    self.isPuzzleLoading = false
                    self.errorMessage = message
                }
                return
            }

            // If successful, continue with puzzle data parsing
            guard let puzzleData = json["puzzleData"] as? [String: Any] else {
                await MainActor.run {
                    self.isPuzzleLoading = false
                    self.errorMessage = "No puzzle data found in response"
                }
                return
            }
            
            // Extract puzzle data
            let optionsArray = puzzleData["options"] as? [String] ?? []
            let questionString = puzzleData["question"] as? String ?? ""
            let answerString = puzzleData["answer"] as? String ?? ""
            let hintString = puzzleData["hint"] as? String ?? ""
            let formatString = puzzleData["puzzleType"] as? String ?? "qa"
            let difficultyString = puzzleData["difficulty"] as? String ?? difficulty
            
            // Create initial puzzle
            let puzzle = Puzzle(
                question: questionString,
                answer: answerString,
                hint: hintString,
                options: optionsArray,
                format: formatString,
                puzzleType: category,
                puzzleId: puzzleData["puzzleId"] as? String ?? UUID().uuidString,
                id: UUID().uuidString,
                name: category,
                createdAt: Date().timeIntervalSince1970 * 1000,
                status: "ready",
                difficulty: difficultyString
            )
            
            // Apply transformations
            let finalPuzzle = await applyPuzzleTransformations(puzzle: puzzle, category: category, difficulty: difficulty)
            
            await MainActor.run {
                self.selectedPuzzle = finalPuzzle
                self.isPuzzleLoading = false

                // Record the fetch for limit tracking
                self.subscriptionManager.recordPuzzleFetch(for: category)

                let finalNavState = self.determineNavigationType(for: category, options: finalPuzzle.options)
                if self.navigationState == .none {
                    self.navigationState = finalNavState
                }
                self.keepCurrentViewWhileFetchingNext = false
                print("Direct network puzzle loaded with navigation state: \(finalNavState)")
            }
            
        } catch {
            print("Network error: \(error.localizedDescription)")
            await MainActor.run {
                self.isPuzzleLoading = false
                self.errorMessage = error.localizedDescription
            }
        }
    }
    
    private func getNextResetTime() -> String {
        let calendar = Calendar.current
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date())!
        let nextMidnight = calendar.startOfDay(for: tomorrow)
        
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return "Daily limits reset at \(formatter.string(from: nextMidnight))"
    }

    private func applyPuzzleTransformations(puzzle: Puzzle, category: String, difficulty: String) async -> Puzzle {
        var finalQuestionString = puzzle.question
        var finalAnswerString = puzzle.answer
        let optionsArray = puzzle.options
        
        if category == "percentages" && puzzle.answer.isEmpty {
            if let questionData = puzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
               let answer = questionJson["answer"] as? Int {
                finalAnswerString = String(answer)
                print("Extracted answer from question JSON: \(finalAnswerString)")
            }
        }
        
        if category == "imagequestion" || category == "image_question" {
            print("IMAGE_QUESTION: Processing image question puzzle")
            print("IMAGE_QUESTION: Original question: \(puzzle.question)")
            
            if let questionData = puzzle.question.data(using: .utf8),
               var questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                
                // Convert timeLimit from milliseconds to seconds
                if let timeLimit = questionJson["timeLimit"] as? Int {
                    let timeLimitInSeconds = timeLimit / 1000
                    questionJson["timeLimit"] = timeLimitInSeconds
                    print("IMAGE_QUESTION: Converted timeLimit from \(timeLimit)ms to \(timeLimitInSeconds)s")
                }
                
                // Add difficulty if missing
                if questionJson["difficulty"] == nil {
                    questionJson["difficulty"] = difficulty
                    print("IMAGE_QUESTION: Added difficulty: \(difficulty)")
                }
                
                // Verify image URL is valid
                if let imageUrl = questionJson["imageUrl"] as? String {
                    if imageUrl.isEmpty || !imageUrl.hasPrefix("http") || imageUrl.contains("PLACEHOLDER") {
                        print("IMAGE_QUESTION: ⚠️ Invalid image URL detected: \(imageUrl)")
                    } else {
                        print("IMAGE_QUESTION: ✅ Valid image URL: \(imageUrl)")
                    }
                }
                
                // Verify questions array
                if let questions = questionJson["questions"] as? [[String: Any]] {
                    print("IMAGE_QUESTION: Found \(questions.count) questions")
                } else {
                    print("IMAGE_QUESTION: ⚠️ No questions array found")
                }
                
                // Re-serialize the modified JSON with proper error handling
                do {
                    let updatedData = try JSONSerialization.data(withJSONObject: questionJson, options: [])
                    if let updatedString = String(data: updatedData, encoding: .utf8) {
                        finalQuestionString = updatedString
                        print("IMAGE_QUESTION: Successfully updated puzzle data")
                        print("IMAGE_QUESTION: Updated JSON: \(updatedString)")
                    } else {
                        print("IMAGE_QUESTION: ⚠️ Failed to convert data to string")
                        // Fallback to original
                        finalQuestionString = puzzle.question
                    }
                } catch {
                    print("IMAGE_QUESTION: ⚠️ Failed to re-serialize puzzle data: \(error)")
                    // Fallback to original
                    finalQuestionString = puzzle.question
                }
            } else {
                print("IMAGE_QUESTION: ⚠️ Failed to parse question JSON")
                // Fallback to original
                finalQuestionString = puzzle.question
            }
        }
        
        if category == "wordsearch" || category == "word_search" {
            print("WORDSEARCH: Processing word search puzzle")
            print("WORDSEARCH: Original question: \(puzzle.question)")
            print("WORDSEARCH: Original answer: \(puzzle.answer)")
            
            if let questionData = puzzle.question.data(using: .utf8) {
                do {
                    let outerJson = try JSONSerialization.jsonObject(with: questionData) as? [String: Any]
                    
                    let innerQuestionString: String
                    if let nestedQuestion = outerJson?["question"] as? String {
                        innerQuestionString = nestedQuestion
                        print("WORDSEARCH: Found double-nested JSON format")
                    } else {
                        innerQuestionString = puzzle.question
                        print("WORDSEARCH: Found single JSON format")
                    }
                    
                    if let innerData = innerQuestionString.data(using: .utf8),
                       let wordSearchJson = try JSONSerialization.jsonObject(with: innerData) as? [String: Any] {
                        
                        if let matrixArray = wordSearchJson["matrix"] as? [[Any]],
                           let wordsArray = wordSearchJson["words"] as? [[String: Any]],
                           let width = wordSearchJson["width"] as? Int,
                           let height = wordSearchJson["height"] as? Int {
                            
                            let instructions = wordSearchJson["instructions"] as? String ?? "Find all hidden words"
                            
                            print("WORDSEARCH: Successfully parsed word search data")
                            print("WORDSEARCH: Grid size: \(width)x\(height)")
                            print("WORDSEARCH: Word count: \(wordsArray.count)")
                            print("WORDSEARCH: Instructions: \(instructions)")
                            
                            let words = wordsArray.compactMap { wordDict -> String? in
                                return wordDict["word"] as? String
                            }
                            print("WORDSEARCH: Words: \(words)")
                            
                            let wordCount = words.count
                            let baseTimePerWord: Int
                            
                            switch difficulty.lowercased() {
                            case "easy":
                                baseTimePerWord = 30
                            case "medium":
                                baseTimePerWord = 25
                            case "hard":
                                baseTimePerWord = 20
                            default:
                                baseTimePerWord = 25
                            }
                            
                            let calculatedTime = wordCount * baseTimePerWord + 60
                            let timeLimit = max(calculatedTime, 120)
                            
                            print("WORDSEARCH: Calculated time limit: \(timeLimit)s (\(timeLimit/60):\(String(format: "%02d", timeLimit%60)))")
                            
                            finalQuestionString = innerQuestionString
                            finalAnswerString = puzzle.answer
                            
                            print("WORDSEARCH: Successfully processed word search data for view")
                        } else {
                            print("WORDSEARCH: Missing required fields in word search JSON")
                            print("WORDSEARCH: Available keys: \(wordSearchJson.keys)")
                        }
                    } else {
                        print("WORDSEARCH: Failed to parse inner word search JSON")
                    }
                    
                } catch {
                    print("WORDSEARCH: JSON parsing error: \(error)")
                }
            } else {
                print("WORDSEARCH: Failed to convert question to data")
            }
        }
        
        if category == "crypto" {
            print("CRYPTO: Processing crypto puzzle")
            print("CRYPTO: Original question: \(puzzle.question)")
            print("CRYPTO: Original answer: \(puzzle.answer)")
            
            // Check if the question is already JSON (from daily cache endpoint)
            if let questionData = puzzle.question.data(using: .utf8),
               let existingJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
               existingJson["originalText"] != nil {
                
                print("CRYPTO: Question is already JSON format, using as-is")
                finalQuestionString = puzzle.question
                finalAnswerString = puzzle.answer
                
            } else {
                // Question is plain text, need to generate crypto data
                print("CRYPTO: Question is plain text, generating crypto puzzle data")
                
                let cleanText = puzzle.question.uppercased().filter { $0.isLetter || $0.isWhitespace }

                if !cleanText.isEmpty {
                    let cryptoData = generateCryptoPuzzleData(text: cleanText, difficulty: difficulty, answer: puzzle.answer)
                    
                    if let jsonData = try? JSONSerialization.data(withJSONObject: cryptoData),
                       let jsonString = String(data: jsonData, encoding: .utf8) {
                        
                        finalQuestionString = jsonString
                        finalAnswerString = puzzle.answer
                        
                        print("CRYPTO: Successfully generated crypto puzzle JSON")
                        print("CRYPTO: Unique letters: \((cryptoData["numberMapping"] as? [String: Int])?.count ?? 0)")
                        print("CRYPTO: Revealed letters: \((cryptoData["revealedLetters"] as? [String])?.count ?? 0)")
                        print("CRYPTO: Time limit: \(cryptoData["timeLimit"] as? Int ?? 0)s")
                        
                    } else {
                        print("CRYPTO: Failed to serialize crypto puzzle data")
                        // Fallback to original text
                        finalQuestionString = puzzle.question
                        finalAnswerString = puzzle.answer
                    }
                } else {
                    print("CRYPTO: Empty or invalid text for crypto puzzle")
                    finalQuestionString = puzzle.question
                    finalAnswerString = puzzle.answer
                }
            }
        }
        
        if category == "pinballdeflector" || category == "pinball_deflector" {
            print("PINBALL: Processing pinball deflector puzzle")
            print("PINBALL: Original question: \(puzzle.question)")
            
            if let questionData = puzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                
                if let matrixArray = questionJson["matrix"] as? [[Int]],
                   let matrixSize = questionJson["matrixSize"] as? Int,
                   let startPosArray = questionJson["startPosition"] as? [Int],
                   let startDirObj = questionJson["startDirection"] as? [String: Any],
                   let memoryTime = questionJson["memoryTime"] as? Int {
                    
                    var deflectorCount = 0
                    for i in 0..<matrixSize {
                        let row = matrixArray[i]
                        for j in 0..<matrixSize {
                            let value = row[j]
                            if value == 1 || value == 2 {
                                deflectorCount += 1
                            }
                        }
                    }
                    
                    let startPosition = startPosArray
                    let startDirection = startDirObj["name"] as? String ?? "RIGHT"
                    
                    print("PINBALL: Successfully processed pinball deflector data")
                    print("PINBALL: Matrix size: \(matrixSize)×\(matrixSize)")
                    print("PINBALL: Start position: \(startPosition)")
                    print("PINBALL: Start direction: \(startDirection)")
                    print("PINBALL: Deflectors: \(deflectorCount)")
                    print("PINBALL: Memory time: \(memoryTime)ms")
                } else {
                    print("PINBALL: Failed to parse pinball deflector data structure")
                }
            } else {
                print("PINBALL: Failed to parse pinball deflector JSON")
            }
            
            finalQuestionString = puzzle.question
            finalAnswerString = puzzle.answer
        }
        
        if category == "memorystory" || category == "memory_story" {
            print("MEMORY_STORY: Processing memory story puzzle")
            print("MEMORY_STORY: Original question: \(puzzle.question)")
            print("MEMORY_STORY: Original answer: \(puzzle.answer)")
            
            if let questionData = puzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                
                let storyCard = questionJson["storyCard"] as? String ?? ""
                let question = questionJson["question"] as? String ?? ""
                let scenario = questionJson["scenario"] as? String ?? "grocery"
                let character = questionJson["character"] as? String ?? "Someone"
                let itemCount = questionJson["itemCount"] as? Int ?? 3
                
                // FIX: Extract audio URL properly from the JSON
                let audioUrl = questionJson["audioUrl"] as? String
                
                // Debug: Print what we found
                print("MEMORY_STORY: Extracted audio URL: \(audioUrl ?? "nil")")
                print("MEMORY_STORY: Story card: \(storyCard)")
                print("MEMORY_STORY: Question: \(question)")
                
                if let optionsArray = questionJson["options"] as? [Any] {
                    var parsedOptions: [String] = []
                    for option in optionsArray {
                        if let optionString = option as? String {
                            parsedOptions.append(optionString)
                        }
                    }
                    print("MEMORY_STORY: Parsed \(parsedOptions.count) options")
                }
                
                if let answerData = puzzle.answer.data(using: .utf8),
                   let correctItemsArray = try? JSONSerialization.jsonObject(with: answerData) as? [String] {
                    print("MEMORY_STORY: Correct items: \(correctItemsArray)")
                }
                
                print("MEMORY_STORY: Successfully processed memory story data")
                print("MEMORY_STORY: Final audio URL: \(audioUrl ?? "none")")
            } else {
                print("MEMORY_STORY: Failed to parse question JSON")
            }
            
            finalQuestionString = puzzle.question
            finalAnswerString = puzzle.answer
        }
        
        if category == "synonyms" || category == "synonym" {
            print("SYNONYM: Processing synonym puzzle")
            print("SYNONYM: Original question: \(puzzle.question)")
            print("SYNONYM: Original answer: \(puzzle.answer)")
            
            if let questionData = puzzle.question.data(using: .utf8) {
                do {
                    if let json = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                        print("SYNONYM: Found JSON object format")
                        
                        let actualJson: [String: Any]
                        if let nestedQuestion = json["question"] as? String,
                           let nestedData = nestedQuestion.data(using: .utf8),
                           let nestedJson = try JSONSerialization.jsonObject(with: nestedData) as? [String: Any] {
                            print("SYNONYM: Found double-nested JSON format")
                            actualJson = nestedJson
                        } else {
                            print("SYNONYM: Using direct JSON format")
                            actualJson = json
                        }
                        
                        if let wordSetsArray = actualJson["wordSets"] as? [[String]] {
                            print("SYNONYM: Successfully parsed wordSets array")
                            print("SYNONYM: Found \(wordSetsArray.count) synonym sets")
                            wordSetsArray.enumerated().forEach { index, set in
                                print("SYNONYM: Set \(index): \(set)")
                            }
                            
                            if wordSetsArray.count == 3 {
                                print("SYNONYM: Valid synonym puzzle with 3 sets")
                            } else {
                                print("SYNONYM: Warning - expected 3 sets, got \(wordSetsArray.count)")
                            }
                        } else {
                            print("SYNONYM: No wordSets array found in JSON")
                        }
                        
                    } else if let jsonArray = try JSONSerialization.jsonObject(with: questionData) as? [[String]] {
                        print("SYNONYM: Found direct array format")
                        print("SYNONYM: Found \(jsonArray.count) synonym sets")
                        jsonArray.enumerated().forEach { index, set in
                            print("SYNONYM: Set \(index): \(set)")
                        }
                        
                        if jsonArray.count == 3 {
                            print("SYNONYM: Valid synonym puzzle with 3 sets")
                        } else {
                            print("SYNONYM: Warning - expected 3 sets, got \(jsonArray.count)")
                        }
                    } else {
                        print("SYNONYM: Unknown JSON format in question")
                    }
                    
                } catch {
                    print("SYNONYM: JSON parsing error: \(error)")
                }
            } else {
                print("SYNONYM: Failed to convert question to data")
            }
            
            print("SYNONYM: Successfully processed synonym data")
        }
        
        if category == "wordprefix" || category == "word_prefix" {
            print("WORDPREFIX: Processing word prefix puzzle")
            print("WORDPREFIX: Original question: \(puzzle.question)")
            print("WORDPREFIX: Original answer: \(puzzle.answer)")
            
            if let questionData = puzzle.question.data(using: .utf8) {
                if let outerJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                    if let extractedPrefix = outerJson["question"] as? String,
                       let answerJsonString = outerJson["answer"] as? String {
                        print("WORDPREFIX: Found nested format with prefix: \(extractedPrefix)")
                        finalQuestionString = answerJsonString
                        finalAnswerString = puzzle.answer
                    } else if outerJson["timeLimit"] != nil {
                        print("WORDPREFIX: Found direct format")
                        if let allWordsArray = outerJson["allWords"] as? [[String: Any]],
                           !allWordsArray.isEmpty,
                           let firstWordData = allWordsArray.first,
                           let firstWord = firstWordData["word"] as? String {
                            let extractedPrefix = String(firstWord.prefix(3)).lowercased()
                            print("WORDPREFIX: Extracted prefix from first word: \(extractedPrefix)")
                        }
                        finalQuestionString = puzzle.question
                        finalAnswerString = puzzle.answer
                    }
                }
            }
            
            print("WORDPREFIX: Successfully processed word prefix data")
        }
        
        if category == "memory_retention" || category == "memoryretention" {
            print("MEMORY: Processing memory retention puzzle")
            print("MEMORY: Original question: \(puzzle.question)")
            print("MEMORY: Original answer: \(puzzle.answer)")
            
            if let questionData = puzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                
                if var factsArray = questionJson["facts"] as? [[String: Any]] {
                    for i in 0..<factsArray.count {
                        if let showTiming = factsArray[i]["showTiming"] as? Int {
                            factsArray[i]["showTiming"] = Double(showTiming) / 1000.0
                        }
                    }
                    
                    var updatedJson = questionJson
                    if let estimatedDuration = updatedJson["estimatedDuration"] as? Int {
                        updatedJson["estimatedDuration"] = Double(estimatedDuration) / 1000.0
                    }
                    updatedJson["facts"] = factsArray
                    
                    if let updatedData = try? JSONSerialization.data(withJSONObject: updatedJson),
                       let updatedString = String(data: updatedData, encoding: .utf8) {
                        finalQuestionString = updatedString
                    }
                }
                
                print("MEMORY: Successfully processed and converted timing data")
            }
        }
        
        if category == "memory_sequencing" || category == "memorysequencing" {
            print("SEQUENCING: Processing memory sequencing puzzle")
            print("SEQUENCING: Original question: \(puzzle.question)")
            print("SEQUENCING: Original answer: \(puzzle.answer)")
            
            if let questionData = puzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                
                let puzzleId = puzzle.puzzleId
                
                var updatedJson = questionJson
                
                let partOneAudioScript = questionJson["partOneAudioScript"] as? String ?? ""
                let partTwoAudioScript = questionJson["partTwoAudioScript"] as? String ?? ""
                
                if !partOneAudioScript.isEmpty {
                    let partOneAudioUrl = "https://uujjodxicvifmiwlimob.supabase.co/storage/v1/object/public/puzzle-audio/\(puzzleId)_part1.mp3"
                    updatedJson["partOneAudioUrl"] = partOneAudioUrl
                    print("SEQUENCING: Part 1 Audio URL: \(partOneAudioUrl)")
                }
                
                if !partTwoAudioScript.isEmpty {
                    let partTwoAudioUrl = "https://uujjodxicvifmiwlimob.supabase.co/storage/v1/object/public/puzzle-audio/\(puzzleId)_part2.mp3"
                    updatedJson["partTwoAudioUrl"] = partTwoAudioUrl
                    print("SEQUENCING: Part 2 Audio URL: \(partTwoAudioUrl)")
                }
                
                if let updatedData = try? JSONSerialization.data(withJSONObject: updatedJson),
                   let updatedString = String(data: updatedData, encoding: .utf8) {
                    finalQuestionString = updatedString
                    print("SEQUENCING: Updated question with audio URLs")
                }
                
                print("SEQUENCING: Successfully processed sequencing data")
            }
        }
        
        if category == "average" {
            print("AVERAGE: Processing average puzzle")
            print("AVERAGE: Original question: \(puzzle.question)")
            print("AVERAGE: Original answer: \(puzzle.answer)")
            
            if let questionData = puzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                
                if let average = questionJson["average"] as? Double {
                    finalAnswerString = String(format: "%.1f", average)
                    print("AVERAGE: Extracted average from question JSON: \(finalAnswerString)")
                } else if let average = questionJson["average"] as? Int {
                    finalAnswerString = String(average)
                    print("AVERAGE: Extracted integer average from question JSON: \(finalAnswerString)")
                } else {
                    print("AVERAGE: No 'average' key found in question JSON")
                    if let numbers = questionJson["numbers"] as? [Int] {
                        let sum = numbers.reduce(0, +)
                        let calculatedAverage = Double(sum) / Double(numbers.count)
                        finalAnswerString = String(format: "%.1f", calculatedAverage)
                        print("AVERAGE: Calculated average from numbers: \(finalAnswerString)")
                    }
                }
            } else {
                print("AVERAGE: Failed to parse question as JSON")
                if !puzzle.answer.isEmpty && puzzle.answer != "0" {
                    print("AVERAGE: Using provided answer: \(puzzle.answer)")
                    finalAnswerString = puzzle.answer
                }
            }
            
            print("AVERAGE: Final answer: \(finalAnswerString)")
        }
        
        if category == "mathestimation" {
            if let questionData = puzzle.question.data(using: .utf8),
               let questionArray = try? JSONSerialization.jsonObject(with: questionData) as? [Double],
               let correctAnswer = Double(puzzle.answer) {
                
                var dataPoints: [[String: Any]] = []
                for (index, value) in questionArray.enumerated() {
                    dataPoints.append([
                        "value": value,
                        "yPosition": 0.2 + (Double(index) * 0.15),
                        "index": index
                    ])
                }
                
                let estimationData: [String: Any] = [
                    "values": questionArray,
                    "dataPoints": dataPoints,
                    "correctSum": correctAnswer,
                    "tolerance": max(correctAnswer * 0.1, 1.0),
                    "difficulty": difficulty,
                    "hint": "Add up all the values shown on the chart"
                ]
                
                if let convertedData = try? JSONSerialization.data(withJSONObject: estimationData),
                   let convertedString = String(data: convertedData, encoding: .utf8) {
                    finalQuestionString = convertedString
                }
            }
        }
        
        if category == "subtraction" {
            print("SUBTRACTION: Processing subtraction puzzle")
            print("SUBTRACTION: Original question: \(puzzle.question)")
            print("SUBTRACTION: Original answer: \(puzzle.answer)")
            
            if let questionData = puzzle.question.data(using: .utf8),
               let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
               let problems = questionJson["problems"] as? [[Int]],
               !problems.isEmpty,
               problems[0].count >= 3 {
                
                finalAnswerString = String(problems[0][2])
                print("SUBTRACTION: Extracted answer from first problem: \(finalAnswerString)")
                print("SUBTRACTION: First problem: \(problems[0][0]) - \(problems[0][1]) = \(problems[0][2])")
                
                let calculatedAnswer = problems[0][0] - problems[0][1]
                if calculatedAnswer != problems[0][2] {
                    print("SUBTRACTION: Math doesn't match! \(problems[0][0]) - \(problems[0][1]) = \(calculatedAnswer), but answer says \(problems[0][2])")
                }
            } else {
                print("SUBTRACTION: Failed to parse problems array from question")
                if puzzle.answer.isEmpty {
                    print("SUBTRACTION: No answer provided either, using 0 as fallback")
                    finalAnswerString = "0"
                }
            }
            
            print("SUBTRACTION: Final answer: \(finalAnswerString)")
        }
        
        if category == "anagram" {
            if !optionsArray.isEmpty {
                let anagramData: [String: Any] = [
                    "sentence": puzzle.question,
                    "correctWord": puzzle.answer,
                    "options": optionsArray
                ]
                
                if let convertedData = try? JSONSerialization.data(withJSONObject: anagramData),
                   let convertedString = String(data: convertedData, encoding: .utf8) {
                    finalQuestionString = convertedString
                    print("Converted anagram format: \(finalQuestionString)")
                }
            }
        }
        
        if category == "division" && puzzle.answer.isEmpty {
            print("Division puzzle detected with empty answer, attempting extraction...")
            if let questionData = puzzle.question.data(using: .utf8) {
                print("Question data created successfully")
                if let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                    print("Question JSON parsed: \(questionJson.keys)")
                    if let problems = questionJson["problems"] as? [[Int]] {
                        print("Problems array found with \(problems.count) problems")
                        if !problems.isEmpty && problems[0].count >= 3 {
                            finalAnswerString = String(problems[0][2])
                            print("Extracted division answer from question JSON: \(finalAnswerString)")
                            print("First problem: \(problems[0]) -> Answer: \(problems[0][2])")
                        } else {
                            print("Problems array is empty or first problem doesn't have 3 elements")
                        }
                    } else {
                        print("No 'problems' key found in question JSON")
                    }
                } else {
                    print("Failed to parse question as JSON")
                }
            } else {
                print("Failed to create question data")
            }
        }
        
        print("Question: \(puzzle.question)")
        print("Answer: \(finalAnswerString)")
        print("Format: \(puzzle.format)")
        print("Options count: \(optionsArray.count)")
        
        let finalPuzzle = Puzzle(
            question: finalQuestionString,
            answer: finalAnswerString,
            hint: puzzle.hint,
            options: optionsArray,
            format: puzzle.format,
            puzzleType: category,
            puzzleId: puzzle.puzzleId,
            id: puzzle.id,
            name: category,
            createdAt: puzzle.createdAt,
            status: "ready",
            difficulty: difficulty
        )
        
        // For purchasing puzzles, verify the data parsing
        if category == "purchasing" {
            if let purchasingData = finalPuzzle.purchasingPuzzleData {
                print("Successfully parsed purchasing data:")
                print("  - Payment: \(purchasingData.payment)")
                print("  - Frequency: \(purchasingData.frequency)")
                print("  - Purpose: \(purchasingData.purpose)")
                print("  - Yearly Total: \(purchasingData.yearlyTotal)")
            } else {
                print("Failed to parse purchasing data from question: \(finalQuestionString)")
            }
        }
        
        return finalPuzzle
    }
}

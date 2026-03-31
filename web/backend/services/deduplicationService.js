// services/deduplicationService.js - ENHANCED WITH WORD SEARCH SUPPORT

import { supabase } from '../config/database.js';
import { loadModel, generateEmbedding, adjustEmbeddingSize } from './puzzleService.js';
import cosineSimilarity from 'cosine-similarity';

const randomizedPuzzleTypes = [
    'pinballdeflector',
    'mathtipping',
    'mathestimation',  
    'memorysquares',
    'memorypreviouspair',
    'wordprefix', 
];

/**
 * Centralized deduplication service for all puzzle types
 * ENHANCED: Now includes word search duplicate detection
 */
export class DeduplicationService {
    constructor() {
        this.embeddingModel = null;
        this.debugMode = false;
        this.cache = new Map();
        this.cacheExpiry = 5 * 60 * 1000; // 5 minutes
        
        // Enhanced thresholds for different puzzle types
        this.thresholds = {
            // Text-based puzzles
            'anagram': { structural: 1.0, semantic: 0.8 },
            'antonyms': { structural: 0.8, semantic: 0.7 },
            'synonyms': { structural: 0.8, semantic: 0.7 },
            'crypto': { structural: 0.9, semantic: 0.7 },
            
            // Word search puzzles - NEW
            'wordsearch': { 
                exactMatch: 1.0,      // Exact word list match
                highOverlap: 0.7,     // 70% word overlap
                semantic: 0.6         // Semantic similarity threshold
            },
            
            // Math puzzles
            'mathtipping': { structural: 0.95, semantic: 0.6 },
            'percentages': { structural: 0.9, semantic: 0.6 },
            'mathestimation': { structural: 0.8, semantic: 0.6 },
            
            // Default
            'default': { structural: 0.8, semantic: 0.7 }
        };
    }

    setDebugMode(enabled) {
        this.debugMode = enabled;
        this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'}`);
    }

    debugLog(message, type = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString();
            const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '🔍';
            console.log(`${emoji} [${timestamp}] DEDUP: ${message}`);
        }
    }

    async initializeEmbeddingModel() {
        if (!this.embeddingModel) {
            this.debugLog('Loading embedding model...');
            this.embeddingModel = await loadModel();
            this.debugLog('Embedding model loaded successfully', 'success');
        }
        return this.embeddingModel;
    }

    /**
     * ENHANCED: Main deduplication check with word search support
     */
    
    async isDuplicate(puzzleType, puzzleObj, existingPuzzles = null) {
        // Add null/undefined check at the very beginning
        if (!puzzleObj) {
            this.debugLog('puzzleObj is null or undefined, treating as unique', 'warning');
            return false;
        }

        // Handle both old string format and new object format for backward compatibility
        const question = typeof puzzleObj === 'string' ? puzzleObj : puzzleObj?.question;
        const answer = typeof puzzleObj === 'object' ? puzzleObj.answer : null;
        const normalizedType = puzzleType.toLowerCase();

        // Add additional validation for question
        if (!question && typeof puzzleObj !== 'string') {
            this.debugLog('No question found in puzzleObj and not a string, treating as unique', 'warning');
            return false;
        }

        if (puzzleType.toLowerCase() === 'memorystory') {
            try {
                const question = typeof puzzleObj === 'string' ? puzzleObj : puzzleObj.question;
                const recentStories = await this.getRecentMemoryStories(10);
                if (recentStories.length === 0) return false;
                
                const newStoryData = JSON.parse(question);
                return await this.validateMemoryStoryUniqueness(newStoryData, recentStories);
            } catch (error) {
                this.debugLog(`Memory story duplicate check failed: ${error.message}`, 'error');
                return false;
            }
        }
        
        this.debugLog(`🔍 Starting duplicate check for ${normalizedType}: ${question?.substring(0, 50)}...`);

        if (randomizedPuzzleTypes.includes(normalizedType)) {
            console.log(`⭐️ Skipping duplicate check for randomized puzzle type: ${puzzleType}`);
            return {
                summary: {
                    totalProcessed: 0,
                    duplicatesFound: 0,
                    message: `Skipped - ${puzzleType} uses randomization`
                }
            };
        }
        
        try {
            // Get existing puzzles if not provided
            if (!existingPuzzles) {
                existingPuzzles = await this.getExistingPuzzles(puzzleType);
            }

            if (!existingPuzzles || existingPuzzles.length === 0) {
                this.debugLog('No existing puzzles found, treating as unique', 'success');
                return false;
            }

            this.debugLog(`📊 Checking against ${existingPuzzles.length} existing puzzles`);

            // ✅ NEW: Special handling for word search puzzles
            if (normalizedType.includes('wordsearch') || normalizedType.includes('word_search')) {
                return await this.checkWordSearchDuplicate(question, existingPuzzles);
            }

            // Determine deduplication strategy based on puzzle type
            const strategy = this.getDeduplicationStrategy(puzzleType);
            this.debugLog(`Using ${strategy} strategy for ${puzzleType}`);

            switch (strategy) {
                case 'structural':
                    // For structural, still use question-based comparison
                    const questionsSet = Array.isArray(existingPuzzles) 
                        ? new Set(existingPuzzles.map(p => typeof p === 'string' ? p : p.question)) 
                        : existingPuzzles;
                    return await this.checkStructuralDuplicate(puzzleType, question, questionsSet);
                
                case 'semantic':
                    // For semantic, still use question-based comparison  
                    const questionsList = Array.isArray(existingPuzzles)
                        ? existingPuzzles.map(p => typeof p === 'string' ? p : p.question)
                        : Array.from(existingPuzzles);
                    return await this.checkSemanticDuplicate(puzzleType, question, new Set(questionsList));
                
                case 'word-based':
                    // For word-based puzzles (anagrams, etc.), use full puzzle object comparison
                    return await this.checkWordBasedDuplicate(puzzleType, puzzleObj, existingPuzzles);
                
                case 'hybrid':
                    // Try structural first (faster), then semantic if no structural match
                    const questionsSetHybrid = Array.isArray(existingPuzzles) 
                        ? new Set(existingPuzzles.map(p => typeof p === 'string' ? p : p.question)) 
                        : existingPuzzles;
                    const structuralDupe = await this.checkStructuralDuplicate(puzzleType, question, questionsSetHybrid);
                    if (structuralDupe) return true;
                    
                    const questionsListHybrid = Array.isArray(existingPuzzles)
                        ? existingPuzzles.map(p => typeof p === 'string' ? p : p.question)
                        : Array.from(existingPuzzles);
                    return await this.checkSemanticDuplicate(puzzleType, question, new Set(questionsListHybrid));
                
                default:
                    this.debugLog(`Unknown strategy: ${strategy}, defaulting to semantic`, 'warning');
                    const questionsListDefault = Array.isArray(existingPuzzles)
                        ? existingPuzzles.map(p => typeof p === 'string' ? p : p.question)
                        : Array.from(existingPuzzles);
                    return await this.checkSemanticDuplicate(puzzleType, question, new Set(questionsListDefault));
            }

        } catch (error) {
            this.debugLog(`Error in duplicate check: ${error.message}`, 'error');
            // Return false on error to allow puzzle generation to continue
            return false;
        }
    }

    async getRecentMemoryStories(limit = 10) {
        try {
            const { data: recentPuzzles, error } = await supabase
                .from('puzzles')
                .select('question, answer, timestamp')
                .ilike('type', 'memorystory')
                .not('question', 'is', null)
                .order('timestamp', { ascending: false })
                .limit(limit);
                
            return recentPuzzles || [];
        } catch (error) {
            this.debugLog(`Error fetching recent memory stories: ${error.message}`, 'error');
            return [];
        }
    }
    
    async validateMemoryStoryUniqueness(newStory, recentStories) {
        if (!recentStories || recentStories.length === 0) return false;
        
        const newScenario = newStory.scenario || 'unknown';
        const newCharacter = newStory.character || 'unknown';
        const newItems = new Set((newStory.correctItems || []).map(item => 
            item.toLowerCase().replace(/[^\w\s]/g, '').trim()
        ));
        
        for (let i = 0; i < Math.min(recentStories.length, 5); i++) {
            try {
                const recentQuestion = JSON.parse(recentStories[i].question);
                
                const recentScenario = recentQuestion.scenario || 'unknown';
                const recentCharacter = recentQuestion.character || 'unknown';
                const recentItems = new Set((recentQuestion.correctItems || []).map(item => 
                    item.toLowerCase().replace(/[^\w\s]/g, '').trim()
                ));
                
                // Check exact scenario match
                if (newScenario.toLowerCase() === recentScenario.toLowerCase()) {
                    this.debugLog(`Duplicate scenario detected: "${newScenario}"`, 'warning');
                    return true;
                }
                
                // Check same character + item overlap
                if (newCharacter.toLowerCase() === recentCharacter.toLowerCase()) {
                    const commonItems = [...newItems].filter(item => recentItems.has(item));
                    const overlapPercentage = commonItems.length / Math.max(newItems.size, recentItems.size);
                    
                    if (overlapPercentage > 0.4) {
                        this.debugLog(`Same character with high item overlap: ${Math.round(overlapPercentage * 100)}%`, 'warning');
                        return true;
                    }
                }
                
            } catch (parseError) {
                continue;
            }
        }
        
        return false;
    }

    /**
     * ✅ NEW: Specialized word search duplicate detection
     */
    async checkWordSearchDuplicate(newQuestion, existingPuzzles) {
        this.debugLog(`🔍 Checking word search duplicates`);

        try {
            // Parse the new question to extract words
            let newWordList = [];
            try {
                const newQuestionData = JSON.parse(newQuestion);
                newWordList = newQuestionData.words || [];
            } catch (parseError) {
                this.debugLog(`⚠️ Could not parse new question as JSON, treating as text`);
                return false;
            }

            if (newWordList.length === 0) {
                this.debugLog(`⚠️ No words found in new question`);
                return false;
            }

            // Create word signature for the new puzzle
            const newWordSignature = this.createWordSearchSignature(newWordList);
            this.debugLog(`📝 New word signature: ${newWordSignature.substring(0, 50)}...`);

            // Check against existing word searches
            for (let i = 0; i < existingPuzzles.length; i++) {
                const existingPuzzle = existingPuzzles[i];
                const existingQuestion = typeof existingPuzzle === 'string' ? existingPuzzle : existingPuzzle.question;
                
                try {
                    let existingWordList = [];
                    
                    // Parse existing question
                    const existingQuestionData = JSON.parse(existingQuestion);
                    existingWordList = existingQuestionData.words || [];

                    if (existingWordList.length === 0) continue;

                    // Create signature for existing puzzle
                    const existingSignature = this.createWordSearchSignature(existingWordList);

                    // Check for exact match
                    if (newWordSignature === existingSignature) {
                        this.debugLog(`❌ Exact word search duplicate found at index ${i}`);
                        return true;
                    }

                    // Check for high overlap
                    const overlapResult = this.calculateWordOverlap(newWordList, existingWordList);
                    const threshold = this.thresholds.wordsearch?.highOverlap || 0.7;

                    if (overlapResult.percentage >= threshold) {
                        this.debugLog(`❌ High overlap word search duplicate: ${Math.round(overlapResult.percentage * 100)}% overlap (${overlapResult.common.length}/${newWordList.length} words)`);
                        return true;
                    }

                    // Log low overlap for debugging
                    if (overlapResult.percentage > 0.3) {
                        this.debugLog(`📊 Moderate overlap detected: ${Math.round(overlapResult.percentage * 100)}% (${overlapResult.common.length} words)`);
                    }

                } catch (parseError) {
                    // Skip malformed entries
                    continue;
                }
            }

            this.debugLog(`✅ No word search duplicates found`);
            return false;

        } catch (error) {
            this.debugLog(`❌ Word search duplicate check error: ${error.message}`, 'error');
            return false;
        }
    }

    /**
     * ✅ NEW: Create normalized signature for word search
     */
    createWordSearchSignature(wordList) {
        // Extract just the words and normalize them
        const words = wordList.map(w => {
            if (typeof w === 'object' && w.word) {
                return w.word.toUpperCase().trim();
            } else if (typeof w === 'string') {
                return w.toUpperCase().trim();
            }
            return '';
        }).filter(w => w.length > 0);

        // Sort alphabetically and join
        return words.sort().join('|');
    }

    /**
     * ✅ NEW: Calculate word overlap between two word lists
     */
    calculateWordOverlap(wordList1, wordList2) {
        // Normalize word lists
        const words1 = this.normalizeWordList(wordList1);
        const words2 = this.normalizeWordList(wordList2);

        // Find common words
        const set1 = new Set(words1);
        const set2 = new Set(words2);
        const commonWords = [...set1].filter(word => set2.has(word));

        // Calculate overlap percentage
        const maxLength = Math.max(words1.length, words2.length);
        const percentage = maxLength > 0 ? commonWords.length / maxLength : 0;

        return {
            common: commonWords,
            percentage: percentage,
            commonCount: commonWords.length,
            total1: words1.length,
            total2: words2.length
        };
    }

    /**
     * ✅ NEW: Normalize word list for comparison
     */
    normalizeWordList(wordList) {
        return wordList.map(w => {
            if (typeof w === 'object' && w.word) {
                return w.word.toUpperCase().trim();
            } else if (typeof w === 'string') {
                return w.toUpperCase().trim();
            }
            return '';
        }).filter(w => w.length > 0);
    }

    /**
     * Get existing puzzles with both question and answer fields
     */
    async getExistingPuzzles(puzzleType) {
        this.debugLog(`Fetching existing puzzles for ${puzzleType}...`);
        
        // Check cache first
        const cacheKey = `puzzles_${puzzleType}`;
        const cached = this.cache.get(cacheKey);
        
        if (cached && Date.now() - cached.timestamp < this.cacheExpiry) {
            this.debugLog(`📋 Using cached puzzles for ${puzzleType} (${cached.puzzles.length} items)`);
            return cached.puzzles;
        }
        
        try {
            const timeoutPromise = new Promise((_, reject) => 
                setTimeout(() => reject(new Error('Query timeout')), 10000)
            );
            
            const { data: puzzles, error } = await Promise.race([
                supabase
                    .from('puzzles')
                    .select('question, answer')
                    .ilike('type', puzzleType)
                    .not('question', 'is', null)
                    .order('timestamp', { ascending: false })
                    .limit(100),
                timeoutPromise
            ]);

            if (error) {
                this.debugLog(`Error fetching puzzles: ${error.message}`, 'error');
                return [];
            }

            // Return full puzzle objects instead of just questions
            const puzzleObjects = puzzles.map(puzzle => ({
                question: puzzle.question,
                answer: puzzle.answer
            })).filter(p => p.question);

            // Cache the results
            this.cache.set(cacheKey, {
                puzzles: puzzleObjects,
                timestamp: Date.now()
            });

            this.debugLog(`Loaded ${puzzleObjects.length} existing puzzles`);
            return puzzleObjects;

        } catch (error) {
            this.debugLog(`Error processing puzzles: ${error.message}`, 'error');
            return [];
        }
    }

    /**
     * ENHANCED: Updated strategy determination to include word-based types and word search
     */
    getDeduplicationStrategy(puzzleType) {
        const structuralTypes = [
            'mathestimation', 'mathtipping', 'percentages', 'division',
            'average', 'subtraction', 'purchasing', 'discounts', 'conversion'
        ];

        const semanticTypes = [
            'crossword', 'wordprefix',
            'memorystory', 'memorysequencing', 'memoryretention'
        ];

        // Word-based types that need special handling
        const wordBasedTypes = [
            'anagram', 'synonyms', 'antonyms'
        ];

        // ✅ NEW: Word search types
        const wordSearchTypes = [
            'wordsearch', 'word_search'
        ];

        const hybridTypes = [
            'connotationwords', 'wordassociation', 'multiplechoice'
        ];

        const normalizedType = puzzleType.toLowerCase();

        if (structuralTypes.includes(normalizedType)) {
            return 'structural';
        } else if (wordSearchTypes.includes(normalizedType)) {
            return 'word-search'; // ✅ NEW strategy
        } else if (wordBasedTypes.includes(normalizedType)) {
            return 'word-based';
        } else if (semanticTypes.includes(normalizedType)) {
            return 'semantic';
        } else if (hybridTypes.includes(normalizedType)) {
            return 'hybrid';
        } else {
            return 'semantic'; // Default to semantic for unknown types
        }
    }

    /**
     * Existing structural duplicate check (unchanged)
     */
    async checkStructuralDuplicate(puzzleType, question, existingQuestions) {
        try {
            const newPuzzleData = JSON.parse(question);
            const normalizedType = puzzleType.toLowerCase();

            this.debugLog(`Checking structural duplicate for ${normalizedType}`);

            for (const existingQuestion of existingQuestions) {
                try {
                    const existingData = JSON.parse(existingQuestion);
                    
                    let isDuplicate = false;
                    
                    switch (normalizedType) {
                        case 'mathtipping':
                            isDuplicate = this.isMathTippingDuplicate(newPuzzleData, existingData);
                            break;
                        case 'mathestimation':
                            isDuplicate = this.isMathEstimationDuplicate(newPuzzleData, existingData);
                            break;
                        case 'percentages':
                            isDuplicate = this.isPercentageDuplicate(newPuzzleData, existingData);
                            break;
                        case 'discounts':
                            isDuplicate = this.isDiscountDuplicate(newPuzzleData, existingData);
                            break;
                        case 'conversion':
                            isDuplicate = this.isConversionDuplicate(newPuzzleData, existingData);
                            break;
                        case 'purchasing':
                            isDuplicate = this.isPurchasingDuplicate(newPuzzleData, existingData);
                            break;
                        case 'division':
                            isDuplicate = this.isDivisionDuplicate(newPuzzleData, existingData);
                            break;
                        case 'average':
                            isDuplicate = this.isAverageDuplicate(newPuzzleData, existingData);
                            break;
                        case 'subtraction':
                            isDuplicate = this.isSubtractionDuplicate(newPuzzleData, existingData);
                            break;
                        default:
                            isDuplicate = this.isGenericJSONDuplicate(newPuzzleData, existingData);
                    }

                    if (isDuplicate) {
                        this.debugLog(`Structural duplicate found for ${normalizedType}`, 'warning');
                        return true;
                    }
                } catch (parseError) {
                    // Skip invalid JSON entries
                    continue;
                }
            }

            this.debugLog(`No structural duplicates found for ${normalizedType}`, 'success');
            return false;

        } catch (parseError) {
            this.debugLog(`Question is not valid JSON, skipping structural check`, 'warning');
            return false;
        }
    }

    /**
     * Existing semantic duplicate check (unchanged)
     */
    async checkSemanticDuplicate(puzzleType, question, existingQuestions, threshold = 0.75) {
        this.debugLog(`Checking semantic duplicate with threshold ${threshold}`);

        // Skip if too many existing questions to prevent timeout
        if (existingQuestions.size > 1000) {
            this.debugLog('Too many existing questions, skipping semantic check', 'warning');
            return false;
        }

        await this.initializeEmbeddingModel();

        const newEmbedding = adjustEmbeddingSize(await generateEmbedding(question));
        let maxSimilarity = 0;
        let checkedCount = 0;

        for (const existing of existingQuestions) {
            const existingEmbedding = adjustEmbeddingSize(await generateEmbedding(existing));
            const similarity = cosineSimilarity(newEmbedding, existingEmbedding);

            checkedCount++;
            maxSimilarity = Math.max(maxSimilarity, similarity);

            if (similarity > threshold) {
                this.debugLog(`Semantic duplicate found! Similarity: ${similarity.toFixed(3)}`, 'warning');
                return true;
            }

            // Early termination for performance
            if (checkedCount >= 100) {
                this.debugLog('Stopping semantic check after 100 comparisons', 'warning');
                break;
            }
        }

        this.debugLog(`No semantic duplicates found. Max similarity: ${maxSimilarity.toFixed(3)}`, 'success');
        return false;
    }

    /**
     * Check for word-based duplicates (anagrams, antonyms, synonyms)
     */
    async checkWordBasedDuplicate(puzzleType, puzzleObj, existingPuzzles) {
        const normalizedType = puzzleType.toLowerCase();
        
        this.debugLog(`Checking word-based duplicate for ${normalizedType}`);

        for (const existingPuzzle of existingPuzzles) {
            try {
                let isDuplicate = false;

                switch (normalizedType) {
                    case 'anagram':
                        isDuplicate = this.isAnagramDuplicate(puzzleObj, existingPuzzle);
                        break;
                    case 'antonyms':
                        isDuplicate = this.isAntonymDuplicate(puzzleObj, existingPuzzle);
                        break;
                    case 'synonyms':
                        isDuplicate = this.isSynonymDuplicate(puzzleObj, existingPuzzle);
                        break;
                    case 'crossword':
                        isDuplicate = this.isCrosswordDuplicate(puzzleObj, existingPuzzle);
                        break;
                }

                if (isDuplicate) {
                    this.debugLog(`Word-based duplicate found for ${normalizedType}`, 'warning');
                    return true;
                }
            } catch (error) {
                this.debugLog(`Error checking word-based duplicate: ${error.message}`, 'error');
                continue;
            }
        }

        this.debugLog(`No word-based duplicates found for ${normalizedType}`, 'success');
        return false;
    }

    // ===================
    // STRUCTURAL CHECKERS (keeping existing implementations)
    // ===================

    isMathTippingDuplicate(newPuzzle, existingPuzzle) {
        const newBill = parseFloat(newPuzzle.billAmount);
        const existingBill = parseFloat(existingPuzzle.billAmount);
        const newTip = parseFloat(newPuzzle.tipPercentage);
        const existingTip = parseFloat(existingPuzzle.tipPercentage);

        const billAmountMatch = Math.abs(newBill - existingBill) < 0.01;
        const tipPercentageMatch = Math.abs(newTip - existingTip) < 0.01;

        if (billAmountMatch && tipPercentageMatch) {
            return true;
        }

        // Check for near-duplicates
        const billDifference = Math.abs(newBill - existingBill);
        const tipDifference = Math.abs(newTip - existingTip);

        // Bills within $0.50 AND same tip percentage
        if (billDifference <= 0.50 && tipDifference < 0.1) {
            return true;
        }

        // Same bill amount AND tip percentages within 1%
        if (billDifference < 0.01 && tipDifference <= 1.0) {
            return true;
        }

        return false;
    }

    isMathEstimationDuplicate(newPuzzle, existingPuzzle) {
        if (!Array.isArray(newPuzzle) || !Array.isArray(existingPuzzle)) {
            return false;
        }

        if (newPuzzle.length !== existingPuzzle.length) {
            return false;
        }

        const newSorted = [...newPuzzle].map(n => parseFloat(n)).sort((a, b) => a - b);
        const existingSorted = [...existingPuzzle].map(n => parseFloat(n)).sort((a, b) => a - b);

        // Check for identical arrays
        const isIdentical = newSorted.every((num, index) => 
            Math.abs(num - existingSorted[index]) < 0.01
        );

        if (isIdentical) {
            return true;
        }

        // Check for "essentially same" puzzles (80% similar numbers)
        const similarityCount = newSorted.filter((num, index) => 
            Math.abs(num - existingSorted[index]) < 1.0
        ).length;

        return similarityCount / newSorted.length >= 0.8;
    }

    isPercentageDuplicate(newPuzzle, existingPuzzle) {
        const totalMatch = Math.abs(parseFloat(newPuzzle.total) - parseFloat(existingPuzzle.total)) < 0.01;
        const percentageMatch = Math.abs(parseFloat(newPuzzle.percentage) - parseFloat(existingPuzzle.percentage)) < 0.01;

        if (totalMatch && percentageMatch) {
            return true;
        }

        // Check for common percentage/total combinations
        const commonPercentages = [10, 15, 20, 25, 50, 75];
        const isCommonPercentage = commonPercentages.includes(parseFloat(newPuzzle.percentage));

        if (isCommonPercentage && percentageMatch) {
            const totalDifference = Math.abs(parseFloat(newPuzzle.total) - parseFloat(existingPuzzle.total));
            return totalDifference <= 10;
        }

        return false;
    }

    isDiscountDuplicate(newPuzzle, existingPuzzle) {
        if (!Array.isArray(newPuzzle) || !Array.isArray(existingPuzzle)) {
            return false;
        }

        if (newPuzzle.length !== existingPuzzle.length) {
            return false;
        }

        // Check for similar items
        const similarItems = newPuzzle.filter(newItem => 
            existingPuzzle.some(existingItem => 
                newItem.name === existingItem.name &&
                Math.abs(newItem.originalPrice - existingItem.originalPrice) < 0.01 &&
                newItem.discountPercentage === existingItem.discountPercentage
            )
        );

        return similarItems.length > 0;
    }

    isConversionDuplicate(newPuzzle, existingPuzzle) {
        return (
            // Same values, same units (exact match)
            (newPuzzle.value1 === existingPuzzle.value1 && 
             newPuzzle.unit1 === existingPuzzle.unit1 &&
             newPuzzle.value2 === existingPuzzle.value2 && 
             newPuzzle.unit2 === existingPuzzle.unit2) ||
            
            // Reversed values and units (A vs B becomes B vs A)
            (newPuzzle.value1 === existingPuzzle.value2 && 
             newPuzzle.unit1 === existingPuzzle.unit2 &&
             newPuzzle.value2 === existingPuzzle.value1 && 
             newPuzzle.unit2 === existingPuzzle.unit1)
        );
    }

    isPurchasingDuplicate(newPuzzle, existingPuzzle) {
        const paymentMatch = Math.abs(parseFloat(newPuzzle.payment) - parseFloat(existingPuzzle.payment)) < 0.01;
        const frequencyMatch = (newPuzzle.frequency || '').toLowerCase() === (existingPuzzle.frequency || '').toLowerCase();

        return paymentMatch && frequencyMatch;
    }

    isDivisionDuplicate(newPuzzle, existingPuzzle) {
        if (!Array.isArray(newPuzzle.problems) || !Array.isArray(existingPuzzle.problems)) {
            return false;
        }

        if (newPuzzle.problems.length !== existingPuzzle.problems.length) {
            return false;
        }

        const newProblemsSet = new Set(newPuzzle.problems.map(p => `${p[0]}÷${p[1]}`));
        const existingProblemsSet = new Set(existingPuzzle.problems.map(p => `${p[0]}÷${p[1]}`));

        const intersection = [...newProblemsSet].filter(x => existingProblemsSet.has(x));
        return intersection.length / newProblemsSet.size >= 0.7;
    }

    isAverageDuplicate(newPuzzle, existingPuzzle) {
        if (!Array.isArray(newPuzzle.numbers) || !Array.isArray(existingPuzzle.numbers)) {
            return false;
        }

        if (newPuzzle.numbers.length !== existingPuzzle.numbers.length) {
            return false;
        }

        const newSorted = [...newPuzzle.numbers].map(n => parseFloat(n)).sort((a, b) => a - b);
        const existingSorted = [...existingPuzzle.numbers].map(n => parseFloat(n)).sort((a, b) => a - b);

        return newSorted.every((num, index) => 
            Math.abs(num - existingSorted[index]) < 0.01
        );
    }

    isSubtractionDuplicate(newPuzzle, existingPuzzle) {
        if (!Array.isArray(newPuzzle.problems) || !Array.isArray(existingPuzzle.problems)) {
            return false;
        }

        if (newPuzzle.problems.length !== existingPuzzle.problems.length) {
            return false;
        }

        const newProblemsSet = new Set(newPuzzle.problems.map(p => `${p[0]}-${p[1]}`));
        const existingProblemsSet = new Set(existingPuzzle.problems.map(p => `${p[0]}-${p[1]}`));

        const intersection = [...newProblemsSet].filter(x => existingProblemsSet.has(x));
        return intersection.length / newProblemsSet.size >= 0.7;
    }

    isGenericJSONDuplicate(newPuzzle, existingPuzzle) {
        // Deep comparison of JSON objects
        if (JSON.stringify(newPuzzle) === JSON.stringify(existingPuzzle)) {
            return true;
        }

        // Fuzzy comparison for similar structures
        const newKeys = Object.keys(newPuzzle).sort();
        const existingKeys = Object.keys(existingPuzzle).sort();

        if (JSON.stringify(newKeys) === JSON.stringify(existingKeys)) {
            let similarFields = 0;
            for (const key of newKeys) {
                if (newPuzzle[key] === existingPuzzle[key]) {
                    similarFields++;
                }
            }

            return similarFields / newKeys.length >= 0.8;
        }

        return false;
    }

    isAnagramDuplicate(puzzleObj, existingPuzzle) {
        try {
            // Handle different input formats
            let newAnswer, newQuestion;
            let existingAnswer, existingQuestion;

            // Extract data from new puzzle
            if (typeof puzzleObj === 'string') {
                // Check if it's JSON or plain text
                if (puzzleObj.trim().startsWith('{') || puzzleObj.trim().startsWith('[')) {
                    try {
                        const parsed = JSON.parse(puzzleObj);
                        newAnswer = parsed.answer;
                        newQuestion = parsed.question;
                    } catch (parseError) {
                        this.debugLog(`Failed to parse new puzzle as JSON, treating as plain answer: ${puzzleObj.substring(0, 50)}...`);
                        // Treat as plain answer string
                        newAnswer = puzzleObj.trim();
                        newQuestion = null;
                    }
                } else {
                    // Plain text - treat as answer
                    newAnswer = puzzleObj.trim();
                    newQuestion = null;
                }
            } else if (typeof puzzleObj === 'object' && puzzleObj !== null) {
                // New object format
                newAnswer = puzzleObj.answer;
                newQuestion = puzzleObj.question;
            } else {
                this.debugLog(`Invalid puzzleObj format: ${typeof puzzleObj}`, 'error');
                return false;
            }

            // Extract data from existing puzzle
            if (typeof existingPuzzle === 'string') {
                // Check if it's JSON or plain text
                if (existingPuzzle.trim().startsWith('{') || existingPuzzle.trim().startsWith('[')) {
                    try {
                        const parsed = JSON.parse(existingPuzzle);
                        existingAnswer = parsed.answer;
                        existingQuestion = parsed.question;
                    } catch (parseError) {
                        this.debugLog(`Failed to parse existing puzzle as JSON, treating as plain answer: ${existingPuzzle.substring(0, 50)}...`);
                        // Treat as plain answer string
                        existingAnswer = existingPuzzle.trim();
                        existingQuestion = null;
                    }
                } else {
                    // Plain text - treat as answer
                    existingAnswer = existingPuzzle.trim();
                    existingQuestion = null;
                }
            } else if (typeof existingPuzzle === 'object' && existingPuzzle !== null) {
                // New object format
                existingAnswer = existingPuzzle.answer;
                existingQuestion = existingPuzzle.question;
            } else {
                this.debugLog(`Invalid existingPuzzle format: ${typeof existingPuzzle}`, 'error');
                return false;
            }

            this.debugLog(`ANAGRAM COMPARISON:`);
            this.debugLog(`New: answer="${newAnswer}", question="${newQuestion}"`);
            this.debugLog(`Existing: answer="${existingAnswer}", question="${existingQuestion}"`);

            // PRIMARY CHECK: Compare answer fields (original words)
            if (newAnswer && existingAnswer) {
                const normalizedNew = newAnswer.toLowerCase().trim();
                const normalizedExisting = existingAnswer.toLowerCase().trim();
                
                if (normalizedNew === normalizedExisting) {
                    this.debugLog(`ANAGRAM DUPLICATE: Same answer word "${newAnswer}"`, 'warning');
                    return true;
                }
            }

            // SECONDARY CHECK: Compare scrambled versions (questions) if both exist
            if (newQuestion && existingQuestion) {
                const normalizedNewQuestion = newQuestion.toLowerCase().trim();
                const normalizedExistingQuestion = existingQuestion.toLowerCase().trim();
                
                if (normalizedNewQuestion === normalizedExistingQuestion) {
                    this.debugLog(`ANAGRAM DUPLICATE: Same scrambled version "${newQuestion}"`, 'warning');
                    return true;
                }
            }

            // FALLBACK CHECK: If we only have answers, treat the inputs as questions vs answers
            if (!newQuestion && !existingQuestion && newAnswer && existingAnswer) {
                // This might be a case where we're comparing a scrambled word (input) against an answer
                // We can't reliably determine duplicates in this case without additional context
                this.debugLog(`ANAGRAM COMPARISON: Both inputs appear to be plain text, cannot determine duplicate reliably`);
            }

            this.debugLog(`ANAGRAM NOT DUPLICATE: Different words/scrambles`);
            return false;

        } catch (error) {
            this.debugLog(`ANAGRAM ERROR: ${error.message}`, 'error');
            // Log additional debug info to help diagnose the issue
            this.debugLog(`PuzzleObj type: ${typeof puzzleObj}, value: ${JSON.stringify(puzzleObj).substring(0, 100)}...`);
            this.debugLog(`ExistingPuzzle type: ${typeof existingPuzzle}, value: ${JSON.stringify(existingPuzzle).substring(0, 100)}...`);
            return false;
        }
    }

    /**
     * Antonym duplicate checker - handles puzzle objects
     */
    isAntonymDuplicate(puzzleObj, existingPuzzle) {
        try {
            // Extract the actual pairs data
            let inputPairs, existingPairs;
            
            // For new puzzle - handle both formats
            inputPairs = this.extractAntonymPairs(puzzleObj);
            existingPairs = this.extractAntonymPairs(existingPuzzle);
    
            // FIX: Validate that we have arrays before calling normalizeWordPairs
            if (!Array.isArray(inputPairs)) {
                this.debugLog(`Input pairs is not an array: ${typeof inputPairs}`, 'error');
                return false;
            }
    
            if (!Array.isArray(existingPairs)) {
                this.debugLog(`Existing pairs is not an array: ${typeof existingPairs}`, 'error');
                return false;
            }
    
            // Additional validation: ensure each pair is an array
            if (!inputPairs.every(pair => Array.isArray(pair))) {
                this.debugLog(`Input contains non-array pairs`, 'error');
                return false;
            }
    
            if (!existingPairs.every(pair => Array.isArray(pair))) {
                this.debugLog(`Existing contains non-array pairs`, 'error');
                return false;
            }
    
            const normalizedInputPairs = this.normalizeWordPairs(inputPairs);
            const normalizedExisting = this.normalizeWordPairs(existingPairs);
    
            // Count shared pairs
            let sharedPairs = 0;
    
            for (const inputPair of normalizedInputPairs) {
                for (const existingPair of normalizedExisting) {
                    if (inputPair[0] === existingPair[0] && inputPair[1] === existingPair[1]) {
                        sharedPairs++;
                        break;
                    }
                }
            }
    
            // Mark as duplicate if ANY pair matches
            const hasSharedPair = sharedPairs > 0;
    
            if (hasSharedPair) {
                this.debugLog(`Antonym duplicate found - shared ${sharedPairs} pairs`, 'warning');
            }
    
            return hasSharedPair;
    
        } catch (error) {
            this.debugLog(`Antonym duplicate check error: ${error.message}`, 'error');
            return false;
        }
    }    

    /**
     * Synonym duplicate checker - handles puzzle objects
     */
    isSynonymDuplicate(puzzleObj, existingPuzzle) {
        try {
            // Handle different input formats
            let inputGroups, existingGroups;
            
            // Extract question data from new puzzle
            if (typeof puzzleObj === 'string') {
                inputGroups = JSON.parse(puzzleObj);
            } else if (puzzleObj.question) {
                if (typeof puzzleObj.question === 'string') {
                    inputGroups = JSON.parse(puzzleObj.question);
                } else {
                    inputGroups = puzzleObj.question;
                }
            } else {
                inputGroups = puzzleObj;
            }
            
            // Extract question data from existing puzzle
            if (typeof existingPuzzle === 'string') {
                existingGroups = JSON.parse(existingPuzzle);
            } else if (existingPuzzle.question) {
                if (typeof existingPuzzle.question === 'string') {
                    existingGroups = JSON.parse(existingPuzzle.question);
                } else {
                    existingGroups = existingPuzzle.question;
                }
            } else {
                existingGroups = existingPuzzle;
            }

            // Normalize word groups (sort words within each group for consistent comparison)
            const normalizedInput = this.normalizeWordGroups(inputGroups);
            const normalizedExisting = this.normalizeWordGroups(existingGroups);

            // Check if ANY synonym group appears in both questions
            for (const inputGroup of normalizedInput) {
                for (const existingGroup of normalizedExisting) {
                    // Compare groups as sorted arrays
                    if (this.areGroupsEqual([inputGroup], [existingGroup])) {
                        this.debugLog(`Synonym duplicate found - shared group: [${inputGroup.join(', ')}]`, 'warning');
                        return true;
                    }
                }
            }

            this.debugLog('No shared synonym groups found', 'success');
            return false;

        } catch (error) {
            this.debugLog(`Synonym duplicate check error: ${error.message}`, 'error');
            return false;
        }
    }

    /**
     * Crossword duplicate checker - handles puzzle objects
     */
    isCrosswordDuplicate(puzzleObj, existingPuzzle) {
        try {
            // Handle different input formats
            let inputWords, existingWords;
            
            // Extract answer data from new puzzle
            if (typeof puzzleObj === 'string') {
                const parsed = JSON.parse(puzzleObj);
                inputWords = parsed.answer || parsed;
            } else {
                inputWords = puzzleObj.answer || puzzleObj.question;
            }
            
            // Extract answer data from existing puzzle
            if (typeof existingPuzzle === 'string') {
                const parsed = JSON.parse(existingPuzzle);
                existingWords = parsed.answer || parsed;
            } else {
                existingWords = existingPuzzle.answer || existingPuzzle.question;
            }

            if (!Array.isArray(inputWords) || !Array.isArray(existingWords)) {
                return false;
            }

            // Check for significant overlap (70% of words match)
            const matchCount = inputWords.filter(word => 
                existingWords.includes(word)
            ).length;

            const overlapThreshold = Math.ceil(inputWords.length * 0.7);
            return matchCount >= overlapThreshold;
        } catch (error) {
            return false;
        }
    }

    // =================
    // HELPER METHODS
    // =================

    normalizeWordPairs(pairs) {
        // Add validation
        if (!Array.isArray(pairs)) {
            throw new Error(`Expected array of pairs, got ${typeof pairs}`);
        }
    
        return pairs.map((pair, index) => {
            if (!Array.isArray(pair)) {
                throw new Error(`Pair at index ${index} is not an array: ${typeof pair}`);
            }
            if (pair.length !== 2) {
                throw new Error(`Pair at index ${index} does not have exactly 2 elements: ${pair.length}`);
            }
            
            // Normalize and sort the pair
            return [...pair].map(word => 
                typeof word === 'string' ? word.toLowerCase().trim() : String(word).toLowerCase().trim()
            ).sort();
        }).sort((a, b) => a[0].localeCompare(b[0]));
    }

    normalizeWordGroups(groups) {
        return groups.map(group => 
            [...group].sort()
        ).sort((a, b) => a[0].localeCompare(b[0]));
    }

    arePairsEqual(pairs1, pairs2) {
        if (pairs1.length !== pairs2.length) return false;
        
        for (let i = 0; i < pairs1.length; i++) {
            if (pairs1[i].length !== pairs2[i].length) return false;
            for (let j = 0; j < pairs1[i].length; j++) {
                if (pairs1[i][j] !== pairs2[i][j]) return false;
            }
        }
        return true;
    }

    areGroupsEqual(groups1, groups2) {
        if (groups1.length !== groups2.length) return false;
        
        for (let i = 0; i < groups1.length; i++) {
            if (groups1[i].length !== groups2[i].length) return false;
            for (let j = 0; j < groups1[i].length; j++) {
                if (groups1[i][j] !== groups2[i][j]) return false;
            }
        }
        return true;
    }

    /**
     * Clear cache for specific puzzle type or all
     */
    clearCache(puzzleType = null) {
        if (puzzleType) {
            this.cache.delete(`puzzles_${puzzleType}`);
            this.debugLog(`🗑️ Cleared cache for ${puzzleType}`);
        } else {
            this.cache.clear();
            this.debugLog(`🗑️ Cleared all cache`);
        }
    }

    /**
     * Batch deduplication for multiple puzzles
     */
    async batchDuplicateCheck(puzzleType, puzzles) {
        this.debugLog(`Starting batch duplicate check for ${puzzles.length} ${puzzleType} puzzles`);

        const existingPuzzles = await this.getExistingPuzzles(puzzleType);
        const results = [];

        for (let i = 0; i < puzzles.length; i++) {
            const puzzle = puzzles[i];
            const isDupe = await this.isDuplicate(puzzleType, puzzle, existingPuzzles);
            
            results.push({
                index: i,
                puzzle: puzzle,
                isDuplicate: isDupe
            });

            // Add non-duplicate puzzles to existing set for next iterations
            if (!isDupe) {
                existingPuzzles.push(puzzle);
            }
        }

        const duplicateCount = results.filter(r => r.isDuplicate).length;
        this.debugLog(`Batch check complete: ${duplicateCount}/${puzzles.length} duplicates found`);

        return results;
    }

    /**
     * Get deduplication statistics for a puzzle type
     */
    async getDeduplicationStats(puzzleType) {
        const existingPuzzles = await this.getExistingPuzzles(puzzleType);
        const strategy = this.getDeduplicationStrategy(puzzleType);

        return {
            puzzleType,
            strategy,
            totalPuzzles: existingPuzzles.length,
            timestamp: new Date().toISOString(),
            thresholds: this.thresholds[puzzleType.toLowerCase()] || this.thresholds.default
        };
    }

    /**
     * Clean up duplicates in database (for maintenance)
     */
    async cleanupDuplicates(puzzleType, dryRun = true) {
        this.debugLog(`${dryRun ? 'Analyzing' : 'Cleaning'} duplicates for ${puzzleType}`);

        const existingPuzzles = await this.getExistingPuzzles(puzzleType);
        const duplicates = [];
        const seen = [];

        for (let i = 0; i < existingPuzzles.length; i++) {
            const puzzle = existingPuzzles[i];
            const isDupe = await this.isDuplicate(puzzleType, puzzle, seen);
            
            if (isDupe) {
                duplicates.push({ index: i, puzzle });
            } else {
                seen.push(puzzle);
            }
        }

        this.debugLog(`Found ${duplicates.length} duplicates out of ${existingPuzzles.length} total`);

        if (!dryRun && duplicates.length > 0) {
            // Implementation for actual cleanup would go here
            this.debugLog('Actual cleanup not implemented yet - this is a placeholder');
        }

        return {
            totalPuzzles: existingPuzzles.length,
            duplicatesFound: duplicates.length,
            duplicates: duplicates.slice(0, 10), // Return first 10 for inspection
            cleanupPerformed: !dryRun
        };
    }
}

// Export singleton instance
export const deduplicationService = new DeduplicationService();
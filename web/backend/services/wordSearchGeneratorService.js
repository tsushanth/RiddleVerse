// wordSearchGeneratorService.js - FIXED VERSION

import { supabase } from '../config/database.js';
import { callAI } from '../utils/aiClient.js';
import crypto from 'crypto';
import {USAGE_CATEGORIES} from '../utils/usageTracker.js';
import { unifiedWordService } from './unifedWordProcurementService.js';

export class WordSearchGeneratorService {
    constructor() {
        this.debugMode = true;
        this.maxTimeMs = 30000;
        this.maxAttempts = 1000;
        
        // Initialize unified word service session
        this.sessionId = null;
        
        this.directions = [
            { dx: 1, dy: 0, name: 'horizontal' },      // right
            { dx: -1, dy: 0, name: 'horizontal-rev' }, // left
            { dx: 0, dy: 1, name: 'vertical' },        // down
            { dx: 0, dy: -1, name: 'vertical-rev' },   // up
            { dx: 1, dy: 1, name: 'diagonal' },        // down-right
            { dx: -1, dy: -1, name: 'diagonal-rev' },  // up-left
            { dx: 1, dy: -1, name: 'anti-diagonal' },  // up-right
            { dx: -1, dy: 1, name: 'anti-diagonal-rev' } // down-left
        ];
    }

    debugLog(message, type = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString();
            const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '🔍';
            console.log(`${emoji} [${timestamp}] WordSearch: ${message}`);
        }
    }

    // ===== FIX 1: AI-POWERED TOPIC WORD GENERATION FOR CUSTOM PUZZLES =====
    
    /**
     * Generate topic-specific words using AI for custom puzzles
     */
    async generateTopicWordsWithAI(topic, difficulty, count = 12) {
        this.debugLog(`🤖 Generating ${count} words for topic "${topic}" using AI`);
        
        const prompt = `Generate exactly ${count} words related to "${topic}" for a word search puzzle.

REQUIREMENTS:
- All words MUST be directly related to "${topic}"
- Word length: 3-8 letters (good for word search)
- Common, recognizable words that people associate with ${topic}
- No proper nouns, abbreviations, or hyphenated words
- Each word needs a clear, short hint

DIFFICULTY: ${difficulty}
- Easy: Simple, common words (3-5 letters)
- Medium: Mix of lengths (3-7 letters) 
- Hard: Longer, more challenging words (4-8 letters)

FORMAT (return ONLY valid JSON):
{
  "words": [
    {"word": "PIZZA", "hint": "Italian dish with cheese"},
    {"word": "BREAD", "hint": "Baked staple food"}
  ]
}

Generate exactly ${count} words for "${topic}":`;

        try {
            const aiResponse = await callAI(prompt, null, 2, {
                category: USAGE_CATEGORIES.CUSTOM_PUZZLE_GENERATION,
                puzzleType: 'word_search',
                difficulty: difficulty
            });

            const wordData = JSON.parse(aiResponse);
            
            if (!wordData.words || !Array.isArray(wordData.words)) {
                throw new Error('Invalid AI response format');
            }

            // Filter and validate words
            const validWords = wordData.words
                .filter(w => w.word && w.hint && w.word.length >= 3 && w.word.length <= 8)
                .map(w => ({
                    word: w.word.toUpperCase().trim(),
                    hint: w.hint.trim()
                }))
                .filter((w, index, self) => {
                    // Remove duplicates and ensure only letters
                    return /^[A-Z]+$/.test(w.word) && 
                           self.findIndex(other => other.word === w.word) === index;
                })
                .slice(0, count);

            if (validWords.length < 5) {
                throw new Error(`Insufficient valid words generated: ${validWords.length}`);
            }

            this.debugLog(`✅ Generated ${validWords.length} topic-specific words using AI`);
            return validWords;

        } catch (error) {
            this.debugLog(`❌ AI word generation failed: ${error.message}`, 'error');
            
            // Fallback to unified service if AI fails
            this.debugLog(`🔄 Falling back to unified service...`);
            try {
                const result = await unifiedWordService.getWords({
                    count: count,
                    difficulty: difficulty,
                    topic: topic,
                    puzzleType: 'word_search',
                    allowAI: true,
                    requireTopicMatch: true
                });
                
                if (result.success && result.words.length >= 5) {
                    return result.words.map(w => ({
                        word: w.word,
                        hint: w.hint
                    }));
                }
            } catch (fallbackError) {
                this.debugLog(`❌ Fallback also failed: ${fallbackError.message}`, 'error');
            }
            
            // Final fallback to hardcoded words
            return this.getFallbackWordsForTopic(topic, difficulty);
        }
    }

    // ===== FIX 2: IMPROVED DEDUPLICATION FOR WORD SEARCH =====
    
    /**
     * Create a normalized signature for word search deduplication
     */
    createWordSearchSignature(wordList) {
        // Sort words alphabetically and create a signature
        const sortedWords = wordList.map(w => w.word || w).sort();
        return sortedWords.join('|').toLowerCase();
    }

    /**
     * Check if word search is duplicate based on word overlap
     */
    async isDuplicateWordSearch(newWords, existingWordSearches) {
        const newSignature = this.createWordSearchSignature(newWords);
        
        for (const existing of existingWordSearches) {
            try {
                let existingWordList = [];
                
                // Parse existing question to extract words
                if (typeof existing === 'string') {
                    const questionData = JSON.parse(existing);
                    existingWordList = questionData.words || [];
                } else if (existing.words) {
                    existingWordList = existing.words;
                }
                
                const existingSignature = this.createWordSearchSignature(existingWordList);
                
                // Check for exact match
                if (newSignature === existingSignature) {
                    this.debugLog(`❌ Exact word search duplicate found`);
                    return true;
                }
                
                // Check for high overlap (70% or more words in common)
                const newWordSet = new Set(newWords.map(w => (w.word || w).toLowerCase()));
                const existingWordSet = new Set(existingWordList.map(w => (w.word || w).toLowerCase()));
                
                const intersection = new Set([...newWordSet].filter(word => existingWordSet.has(word)));
                const overlapPercentage = intersection.size / Math.max(newWordSet.size, 1);
                
                if (overlapPercentage >= 0.7) {
                    this.debugLog(`❌ High overlap word search duplicate: ${Math.round(overlapPercentage * 100)}% words match`);
                    return true;
                }
                
            } catch (error) {
                continue; // Skip malformed entries
            }
        }
        
        return false;
    }

    /**
     * Enhanced word search generation with proper deduplication
     */
    async generateWordSearchWithDeduplication(wordList, difficulty = 'medium', gridSize = 15, topic = null) {
        this.debugLog(`🔍 Generating word search with deduplication check...`);
        
        try {
            // Check for duplicates before generation
            const { data: existingWordSearches } = await supabase
                .from('puzzles')
                .select('question')
                .ilike('type', 'wordsearch')
                .order('timestamp', { ascending: false })
                .limit(100); // Check last 100 word searches
            
            const existingQuestions = existingWordSearches?.map(p => p.question) || [];
            
            // Check if this word combination would be a duplicate
            const isDuplicate = await this.isDuplicateWordSearch(wordList, existingQuestions);
            
            if (isDuplicate) {
                this.debugLog(`❌ Word search would be duplicate, regenerating words...`);
                
                // Try to generate different words for the same topic
                if (topic) {
                    this.debugLog(`🔄 Regenerating words for topic: ${topic}`);
                    const newWords = await this.generateTopicWordsWithAI(topic, difficulty, wordList.length);
                    const newIsDuplicate = await this.isDuplicateWordSearch(newWords, existingQuestions);
                    
                    if (!newIsDuplicate) {
                        wordList = newWords;
                        this.debugLog(`✅ Generated unique word set after regeneration`);
                    } else {
                        this.debugLog(`⚠️ Still duplicate after regeneration, proceeding anyway`);
                    }
                } else {
                    // For general puzzles, shuffle and modify the word list
                    wordList = this.modifyWordListToAvoidDuplicate(wordList);
                }
            }
            
            // Generate the actual word search puzzle
            const wordSearch = await this.generateWordSearch(wordList, difficulty, gridSize);
            
            if (!wordSearch) {
                throw new Error('Failed to generate word search puzzle');
            }
            
            this.debugLog(`✅ Generated unique word search with ${wordSearch.words.length} words`);
            return wordSearch;
            
        } catch (error) {
            this.debugLog(`❌ Word search generation with deduplication failed: ${error.message}`, 'error');
            throw error;
        }
    }

    /**
     * Modify word list to avoid duplicates
     */
    modifyWordListToAvoidDuplicate(wordList) {
        this.debugLog(`🔄 Modifying word list to avoid duplicates...`);
        
        // Replace 1-2 words with similar alternatives
        const modifiedList = [...wordList];
        const replaceCount = Math.min(2, Math.floor(wordList.length * 0.3));
        
        for (let i = 0; i < replaceCount; i++) {
            const randomIndex = Math.floor(Math.random() * modifiedList.length);
            const alternatives = this.getWordAlternatives(modifiedList[randomIndex].word);
            
            if (alternatives.length > 0) {
                const newWord = alternatives[Math.floor(Math.random() * alternatives.length)];
                modifiedList[randomIndex] = {
                    word: newWord,
                    hint: this.generateGenericHint(newWord)
                };
            }
        }
        
        return modifiedList;
    }

    /**
     * Get alternative words for deduplication
     */
    getWordAlternatives(word) {
        const alternatives = {
            'HOUSE': ['HOME', 'CABIN', 'VILLA'],
            'CAR': ['AUTO', 'BIKE', 'TAXI'],
            'TREE': ['PLANT', 'BUSH', 'LEAF'],
            'BOOK': ['PAGE', 'TEXT', 'NOVEL'],
            'WATER': ['LAKE', 'RIVER', 'OCEAN'],
            'FIRE': ['FLAME', 'HEAT', 'SPARK'],
            'LIGHT': ['LAMP', 'BULB', 'GLOW'],
            'MUSIC': ['SONG', 'TUNE', 'BEAT'],
            'HAPPY': ['JOY', 'GLAD', 'SMILE'],
            'FAST': ['QUICK', 'SPEED', 'RUSH']
        };
        
        return alternatives[word.toUpperCase()] || [];
    }

    // ===== UPDATED CUSTOM WORD SEARCH GENERATION =====
    
    /**
     * FIXED: Custom word search generation using AI for topic words
     */
    async generateCustomWordSearch(topic, numPuzzles, userId, puzzleId, difficulty = 'medium') {
        console.log(`🔍 Starting FIXED custom word search generation for topic="${topic}"`);
        const startTime = Date.now();
        
        try {
            const generatedPuzzles = [];
            const maxAttemptsPerPuzzle = 3;

            for (let puzzleIndex = 0; puzzleIndex < numPuzzles; puzzleIndex++) {
                console.log(`🔍 Generating word search ${puzzleIndex + 1}/${numPuzzles}...`);
                
                let puzzleGenerated = false;
                let attempts = 0;

                while (!puzzleGenerated && attempts < maxAttemptsPerPuzzle) {
                    attempts++;
                    
                    try {
                        console.log(`🎯 Attempt ${attempts}/${maxAttemptsPerPuzzle} for puzzle ${puzzleIndex + 1}`);
                        
                        // ✅ FIX: Use AI to generate topic-specific words
                        const wordList = await this.generateTopicWordsWithAI(topic, difficulty, 12);
                        
                        if (!wordList || wordList.length < 5) {
                            console.warn(`⚠️ Insufficient words generated (${wordList?.length || 0}), retrying...`);
                            continue;
                        }

                        // Determine grid size
                        const gridSize = this.determineOptimalGridSize(wordList, difficulty);
                        
                        // ✅ FIX: Use enhanced generation with deduplication
                        const wordSearch = await this.generateWordSearchWithDeduplication(
                            wordList, 
                            difficulty, 
                            gridSize, 
                            topic
                        );
                        
                        if (!wordSearch || wordSearch.words.length < 5) {
                            console.warn(`⚠️ Word search generation failed, retrying...`);
                            continue;
                        }

                        // Format for storage
                        const formattedPuzzle = this.formatForCustomStorage(wordSearch, topic, difficulty);
                        
                        // Store in database
                        await supabase
                            .from('puzzles')
                            .insert({
                                parentSetId: puzzleId,
                                puzzleid: crypto.randomUUID(),
                                source: userId,
                                type: 'wordsearch',
                                status: 'completed',
                                difficulty: difficulty.toLowerCase(),
                                question: formattedPuzzle.question,
                                answer: formattedPuzzle.answer,
                                options: [],
                                hint: formattedPuzzle.hint,
                                timestamp: new Date().toISOString()
                            });
                        
                        generatedPuzzles.push(wordSearch);
                        puzzleGenerated = true;
                        
                        console.log(`✅ Word search ${puzzleIndex + 1} generated successfully with ${wordSearch.words.length} words`);
                        
                    } catch (error) {
                        console.error(`❌ Error in word search generation attempt ${attempts}:`, error.message);
                        
                        if (attempts >= maxAttemptsPerPuzzle) {
                            console.warn(`⚠️ Failed to generate word search ${puzzleIndex + 1} after ${attempts} attempts`);
                        }
                    }
                }

                // Small delay between puzzles
                if (puzzleIndex < numPuzzles - 1) {
                    await new Promise(resolve => setTimeout(resolve, 1000));
                }
            }

            // Final status update
            const finalStatus = generatedPuzzles.length > 0 ? 'completed' : 'failed';
            
            await Promise.all([
                supabase
                    .from('puzzle_sets')
                    .update({
                        status: finalStatus,
                        puzzle_count: generatedPuzzles.length,
                        updated_at: new Date().toISOString()
                    })
                    .eq('id', puzzleId),
                
                supabase
                    .from('puzzles')
                    .update({
                        status: finalStatus,
                        timestamp: new Date().toISOString()
                    })
                    .eq('parentSetId', puzzleId)
                    .is('puzzleid', null)
            ]);

            const elapsedTime = (Date.now() - startTime) / 1000;
            console.log(`⏱️ Word search generation completed in ${elapsedTime.toFixed(1)}s`);

            if (generatedPuzzles.length > 0) {
                console.log(`🎉 Generated ${generatedPuzzles.length}/${numPuzzles} topic-specific word search puzzles`);
                return { puzzleId };
            } else {
                console.error(`❌ No word search puzzles could be generated`);
                return { error: "Failed to generate any word search puzzles" };
            }

        } catch (error) {
            console.error(`💥 Fatal error in word search generation:`, error);
            
            // Mark as failed
            await Promise.allSettled([
                supabase
                    .from('puzzle_sets')
                    .update({
                        status: 'failed',
                        updated_at: new Date().toISOString()
                    })
                    .eq('id', puzzleId),
                
                supabase
                    .from('puzzles')
                    .update({
                        status: 'failed',
                        timestamp: new Date().toISOString()
                    })
                    .eq('parentSetId', puzzleId)
                    .is('puzzleid', null)
            ]);
            
            return { error: error.message };
        }
    }

    // ===== UPDATED GENERAL WORD SEARCH GENERATION =====
    
    /**
     * FIXED: Standard word search generation with proper deduplication
     */
    async generateStandardWordSearch(difficulty = 'medium', gridSize = 15) {
        this.debugLog(`Starting FIXED standard word search generation (${difficulty})`);
        
        try {
            // Start a new session for this generation
            this.startGenerationSession();
            
            // Generate words using unified word service
            const result = await unifiedWordService.getWords({
                count: this.getWordCountForDifficulty(difficulty),
                difficulty: difficulty,
                topic: null,
                puzzleType: 'word_search',
                allowAI: true,
                requireTopicMatch: false
            });
            
            if (!result.success || !result.words || result.words.length < 5) {
                throw new Error('Insufficient words from unified service');
            }
            
            const wordList = result.words.map(w => ({
                word: w.word,
                hint: w.hint
            }));
            
            // ✅ FIX: Use enhanced generation with deduplication
            const wordSearch = await this.generateWordSearchWithDeduplication(
                wordList, 
                difficulty, 
                gridSize, 
                null // no specific topic
            );
            
            if (!wordSearch) {
                throw new Error('Failed to generate word search puzzle');
            }
            
            // Format for storage
            const formattedPuzzle = this.formatForStandardStorage(wordSearch, difficulty);
            
            this.debugLog(`Successfully generated standard word search with ${wordSearch.words.length} words`, 'success');
            
            return {
                success: true,
                puzzleData: formattedPuzzle,
                sessionStats: this.getSessionStats()
            };
            
        } catch (error) {
            this.debugLog(`Standard word search generation failed: ${error.message}`, 'error');
            return {
                success: false,
                message: error.message,
                sessionStats: this.getSessionStats()
            };
        }
    }

    // ===== UTILITY METHODS =====
    
    determineOptimalGridSize(wordList, difficulty) {
        const maxWordLength = Math.max(...wordList.map(w => w.word.length));
        const wordCount = wordList.length;
        
        let baseSize;
        switch (difficulty.toLowerCase()) {
            case 'easy':
                baseSize = Math.max(10, maxWordLength + 2);
                break;
            case 'medium':
                baseSize = Math.max(12, maxWordLength + 3);
                break;
            case 'hard':
                baseSize = Math.max(15, maxWordLength + 4);
                break;
            default:
                baseSize = 12;
        }

        // Adjust for word count
        if (wordCount > 12) baseSize += 2;
        if (wordCount > 16) baseSize += 2;
        
        // Cap at reasonable sizes
        return Math.min(Math.max(baseSize, 10), 20);
    }

    getWordCountForDifficulty(difficulty) {
        switch (difficulty.toLowerCase()) {
            case 'easy': return 8;
            case 'medium': return 12;
            case 'hard': return 16;
            default: return 12;
        }
    }

    generateGenericHint(word) {
        const length = word.length;
        const firstLetter = word.charAt(0).toUpperCase();
        return `${length} letter word starting with ${firstLetter}`;
    }

    startGenerationSession() {
        this.sessionId = unifiedWordService.startSession('word_search');
        this.debugLog(`Started new word search session: ${this.sessionId}`);
    }

    getSessionStats() {
        return {
            localSessionId: this.sessionId,
            unifiedServiceStats: unifiedWordService.getSessionStats()
        };
    }

    // ... [Keep all existing word search generation logic like generateWordSearch, 
    //      findValidPlacement, placeWordOnGrid, etc. - these don't need changes]

    async generateWordSearch(wordList, difficulty = 'medium', gridSize = 15) {
        const startTime = Date.now();
        this.debugLog(`Starting word search generation with ${wordList.length} words on ${gridSize}x${gridSize} grid`);

        // Sort words by length (longest first) for better placement
        const sortedWords = [...wordList].sort((a, b) => b.word.length - a.word.length);
        
        let bestSolution = null;
        let attempts = 0;

        while (attempts < this.maxAttempts && !this.isTimedOut(startTime)) {
            attempts++;
            
            const grid = this.createEmptyGrid(gridSize);
            const placedWords = [];
            let success = true;

            for (const wordData of sortedWords) {
                const word = wordData.word.toUpperCase();
                const placement = this.findValidPlacement(grid, word, difficulty, gridSize);
                
                if (placement) {
                    this.placeWordOnGrid(grid, word, placement);
                    placedWords.push({
                        word: word,
                        hint: wordData.hint,
                        startX: placement.x,
                        startY: placement.y,
                        endX: placement.x + placement.dx * (word.length - 1),
                        endY: placement.y + placement.dy * (word.length - 1),
                        direction: placement.direction,
                        length: word.length
                    });
                } else {
                    success = false;
                    break;
                }
            }

            if (success && placedWords.length > 0) {
                const solution = {
                    grid: this.copyGrid(grid),
                    words: placedWords,
                    placedCount: placedWords.length,
                    totalWords: wordList.length
                };

                if (!bestSolution || placedWords.length > bestSolution.placedCount) {
                    bestSolution = solution;
                    
                    // If we placed all words, we have a perfect solution
                    if (placedWords.length === wordList.length) {
                        this.debugLog(`Perfect solution found after ${attempts} attempts`);
                        break;
                    }
                }
            }

            if (attempts % 100 === 0) {
                this.debugLog(`Attempt ${attempts}, best so far: ${bestSolution?.placedCount || 0}/${wordList.length} words`);
            }
        }

        if (bestSolution) {
            // Fill empty spaces with random letters
            this.fillEmptySpaces(bestSolution.grid);
            
            return {
                matrix: bestSolution.grid,
                words: bestSolution.words,
                width: gridSize,
                height: gridSize,
                placedWords: bestSolution.placedCount,
                totalWords: wordList.length,
                attempts: attempts
            };
        }

        this.debugLog("No solution found within time/attempt limits.");
        return null;
    }

    isTimedOut(startTime) {
        return Date.now() - startTime > this.maxTimeMs;
    }

    findValidPlacement(grid, word, difficulty, gridSize) {
        const possiblePlacements = [];
        const directions = this.getDirectionsForDifficulty(difficulty);

        // Try all positions and directions
        for (let y = 0; y < gridSize; y++) {
            for (let x = 0; x < gridSize; x++) {
                for (const dir of directions) {
                    if (this.canPlaceWord(grid, word, x, y, dir.dx, dir.dy, gridSize)) {
                        possiblePlacements.push({
                            x, y, 
                            dx: dir.dx, 
                            dy: dir.dy, 
                            direction: dir.name
                        });
                    }
                }
            }
        }

        // Return random placement to add variety
        if (possiblePlacements.length > 0) {
            const randomIndex = Math.floor(Math.random() * possiblePlacements.length);
            return possiblePlacements[randomIndex];
        }

        return null;
    }

    getDirectionsForDifficulty(difficulty) {
        switch (difficulty.toLowerCase()) {
            case 'easy':
                return this.directions.filter(d => 
                    d.name === 'horizontal' || d.name === 'vertical'
                );
            case 'medium':
                return this.directions.filter(d => 
                    !d.name.includes('rev')
                );
            case 'hard':
                return this.directions;
            default:
                return this.directions.filter(d => !d.name.includes('rev'));
        }
    }

    canPlaceWord(grid, word, x, y, dx, dy, gridSize) {
        const endX = x + dx * (word.length - 1);
        const endY = y + dy * (word.length - 1);
        
        if (endX < 0 || endX >= gridSize || endY < 0 || endY >= gridSize) {
            return false;
        }

        for (let i = 0; i < word.length; i++) {
            const cx = x + dx * i;
            const cy = y + dy * i;
            const cell = grid[cy][cx];
            
            if (cell !== '_' && cell !== word[i]) {
                return false;
            }
        }

        return true;
    }

    placeWordOnGrid(grid, word, placement) {
        const { x, y, dx, dy } = placement;
        
        for (let i = 0; i < word.length; i++) {
            const cx = x + dx * i;
            const cy = y + dy * i;
            grid[cy][cx] = word[i];
        }
    }

    fillEmptySpaces(grid) {
        const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
        
        for (let y = 0; y < grid.length; y++) {
            for (let x = 0; x < grid[y].length; x++) {
                if (grid[y][x] === '_') {
                    const randomIndex = Math.floor(Math.random() * letters.length);
                    grid[y][x] = letters[randomIndex];
                }
            }
        }
    }

    createEmptyGrid(size) {
        return Array(size).fill(null).map(() => Array(size).fill('_'));
    }

    copyGrid(grid) {
        return grid.map(row => [...row]);
    }

    formatForStandardStorage(wordSearch, difficulty) {
        return {
            question: JSON.stringify({
                matrix: wordSearch.matrix,
                words: wordSearch.words.map(w => ({
                    word: w.word,
                    hint: w.hint,
                    direction: w.direction,
                    length: w.length
                })),
                width: wordSearch.width,
                height: wordSearch.height,
                instructions: "Find all the hidden words in the grid"
            }),
            answer: JSON.stringify(wordSearch.words.map(w => ({
                word: w.word,
                startX: w.startX,
                startY: w.startY,
                endX: w.endX,
                endY: w.endY,
                direction: w.direction
            }))),
            hint: `Find ${wordSearch.words.length} hidden words in the grid`,
            difficulty: difficulty.toLowerCase(),
            options: [],
            metadata: {
                wordCount: wordSearch.words.length,
                gridSize: `${wordSearch.width}x${wordSearch.height}`,
                placedWords: wordSearch.placedWords,
                totalWords: wordSearch.totalWords,
                puzzleType: 'word_search',
                wordSource: 'ai_topic_generated'
            }
        };
    }

    formatForCustomStorage(wordSearch, topic, difficulty) {
        return {
            question: JSON.stringify({
                matrix: wordSearch.matrix,
                words: wordSearch.words.map(w => ({
                    word: w.word,
                    hint: w.hint,
                    direction: w.direction,
                    length: w.length
                })),
                width: wordSearch.width,
                height: wordSearch.height,
                topic: topic,
                instructions: `Find all words related to ${topic}`
            }),
            answer: JSON.stringify(wordSearch.words.map(w => ({
                word: w.word,
                startX: w.startX,
                startY: w.startY,
                endX: w.endX,
                endY: w.endY,
                direction: w.direction
            }))),
            hint: `Find ${wordSearch.words.length} words related to ${topic}`,
            difficulty: difficulty.toLowerCase(),
            options: [],
            metadata: {
                wordCount: wordSearch.words.length,
                gridSize: `${wordSearch.width}x${wordSearch.height}`,
                placedWords: wordSearch.placedWords,
                totalWords: wordSearch.totalWords,
                topic: topic,
                puzzleType: 'word_search',
                wordSource: 'ai_topic_generated'
            }
        };
    }

    // Fallback word generation methods (keep existing)
    getFallbackWordsForTopic(topic, difficulty) {
        const topicLower = topic.toLowerCase();
        let words = [];
        
        if (topicLower.includes('animal')) {
            words = [
                { word: 'CAT', hint: 'Furry pet that meows' },
                { word: 'DOG', hint: 'Loyal four-legged friend' },
                { word: 'BIRD', hint: 'Flying creature with feathers' },
                { word: 'FISH', hint: 'Aquatic vertebrate' },
                { word: 'TIGER', hint: 'Striped big cat' },
                { word: 'ELEPHANT', hint: 'Large mammal with trunk' },
                { word: 'PENGUIN', hint: 'Antarctic bird' },
                { word: 'DOLPHIN', hint: 'Intelligent marine mammal' }
            ];
        } else if (topicLower.includes('food')) {
            words = [
                { word: 'PIZZA', hint: 'Italian dish with cheese' },
                { word: 'APPLE', hint: 'Red or green fruit' },
                { word: 'BREAD', hint: 'Baked staple food' },
                { word: 'CHEESE', hint: 'Dairy product' },
                { word: 'PASTA', hint: 'Italian noodle dish' },
                { word: 'BANANA', hint: 'Yellow curved fruit' },
                { word: 'ORANGE', hint: 'Citrus fruit' },
                { word: 'TOMATO', hint: 'Red cooking ingredient' }
            ];
        } else {
            words = [
                { word: 'HOUSE', hint: 'Place where people live' },
                { word: 'TREE', hint: 'Tall plant with leaves' },
                { word: 'BOOK', hint: 'Reading material' },
                { word: 'WATER', hint: 'Clear liquid' },
                { word: 'LIGHT', hint: 'Bright illumination' },
                { word: 'MUSIC', hint: 'Sounds in harmony' },
                { word: 'FRIEND', hint: 'Close companion' },
                { word: 'HAPPY', hint: 'Feeling of joy' }
            ];
        }
        
        return words.slice(0, this.getWordCountForDifficulty(difficulty));
    }

    setDebugMode(enabled) {
        this.debugMode = enabled;
        this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'}`);
    }
}

// Export singleton instance
export const wordSearchService = new WordSearchGeneratorService();
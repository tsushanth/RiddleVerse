// services/antonymGeneratorService.js - Fixed DataMuse Enhanced Version

import { supabase } from '../config/database.js';
import { callAI } from '../utils/aiClient.js';
import { USAGE_CATEGORIES } from '../utils/usageTracker.js';
import { deduplicationService } from './deduplicationService.js';
import fetch from 'node-fetch';

/**
 * DataMuse Enhanced Antonym Generator Service - FIXED VERSION
 * Addresses issues found in testing: undefined scores, problematic words, thresholds
 */
export class DataMuseEnhancedAntonymService {
    constructor() {
        this.debugMode = false;
        this.datamuseBaseUrl = 'https://api.datamuse.com/words';
        this.requestDelay = 150;
        
        this.thresholds = {
            minCrosswordScore: 8.0,
            batchSize: 12,
            maxRetries: 3,
            minConfidence: 0.7,
            minWordLength: 3,
            datamuseMinScore: 500  // FIXED: Lowered from 1000 to catch more valid pairs
        };

        // FIXED: Blacklist problematic antonym patterns
        this.problematicWords = [
            'nonrigid', 'nonvolatile', 'nonvolatilizable', 'meanspirited', 
            'ungenerous', 'impalpable', 'univocal', 'licking'
        ];

        this.datamuseCache = new Map();
        this.cacheExpiry = 24 * 60 * 60 * 1000;
    }

    async generateAntonymPuzzle(difficulty = 'medium', pairCount = 6) {
        this.debugLog(`🎯 DataMuse-enhanced generation: ${difficulty} difficulty, ${pairCount} pairs`);
        
        try {
            let antonymPairs = [];
            
            // Step 1: DataMuse discovery (only for easy difficulty based on test results)
            if (difficulty.toLowerCase() === 'easy') {
                antonymPairs = await this.generateDataMuseFirstAntonyms(difficulty, pairCount);
                this.debugLog(`DataMuse discovery found ${antonymPairs.length} pairs for easy difficulty`);
            }
            
            // Step 2: AI generation with DataMuse validation for remaining pairs
            if (antonymPairs.length < pairCount) {
                const needed = pairCount - antonymPairs.length;
                this.debugLog(`Need ${needed} more pairs, using AI + validation`);
                const aiPairs = await this.generateAIWithDataMuseValidation(difficulty, needed);
                antonymPairs = antonymPairs.concat(aiPairs);
            }
            
            // Step 3: Pure AI fallback if still insufficient
            if (antonymPairs.length < pairCount) {
                const needed = pairCount - antonymPairs.length;
                this.debugLog(`Still need ${needed} pairs, using pure AI fallback`);
                const fallbackPairs = await this.generatePureAIFallback(difficulty, needed);
                antonymPairs = antonymPairs.concat(fallbackPairs);
            }
            
            const validatedPairs = antonymPairs.slice(0, pairCount);
            const puzzleData = this.formatForPuzzleSystem(validatedPairs, difficulty);
            
            this.debugLog(`✅ Generated ${validatedPairs.length} total pairs`);
            return {
                success: true,
                puzzleData
            };

        } catch (error) {
            this.debugLog(`❌ Generation failed: ${error.message}`, 'error');
            return {
                success: false,
                error: error.message
            };
        }
    }

    async generateDataMuseFirstAntonyms(difficulty, pairCount) {
        this.debugLog(`🔍 DataMuse discovery for ${pairCount} pairs`);
        
        const seedWords = await this.getSeedWordsForDifficulty(difficulty, pairCount * 4);
        const discoveredPairs = [];
        
        for (const word of seedWords) {
            if (discoveredPairs.length >= pairCount) break;
            
            try {
                const antonyms = await this.getDataMuseAntonyms(word.word);
                
                if (antonyms.length > 0) {
                    const bestAntonym = antonyms[0];
                    
                    // FIXED: Handle undefined scores properly
                    const score = bestAntonym.score || 0;
                    
                    if (score >= this.thresholds.datamuseMinScore && 
                        !this.isProblematicWord(bestAntonym.word)) {
                        
                        const pair = {
                            word1: word.word,
                            word2: bestAntonym.word,
                            confidence: this.scoreToConfidence(score),
                            source: 'datamuse_discovery',
                            category: 'verified',
                            datamuseScore: score
                        };
                        
                        if (this.validatePairQuality(pair, difficulty)) {
                            discoveredPairs.push(pair);
                            this.debugLog(`✓ Found: ${pair.word1} ↔ ${pair.word2} (score: ${score})`);
                        }
                    }
                }
                
                await this.delay(this.requestDelay);
                
            } catch (error) {
                this.debugLog(`⚠️ DataMuse error for ${word.word}: ${error.message}`, 'warning');
            }
        }
        
        return discoveredPairs;
    }

    async generateAIWithDataMuseValidation(difficulty, pairCount) {
        this.debugLog(`🤖 AI + DataMuse validation for ${pairCount} pairs`);
        
        // FIXED: Better prompting with cleaner examples
        const difficultyExamples = {
            easy: "happy/sad, big/small, hot/cold, fast/slow, good/bad, old/new",
            medium: "complex/simple, ancient/modern, victory/defeat, increase/decrease, unite/divide",
            hard: "abundant/scarce, optimistic/pessimistic, generous/stingy, coherent/chaotic",
            expert: "ambiguous/clear, orthodox/unconventional, tangible/intangible, ephemeral/permanent"
        };

        const prompt = `Generate ${pairCount * 3} clear antonym pairs for ${difficulty} difficulty word puzzles.

Examples for ${difficulty}: ${difficultyExamples[difficulty] || difficultyExamples.medium}

Requirements:
- Clear, obvious opposites only
- Common English words
- 3-10 letters each
- NO compound words with prefixes like "non-", "un-" unless very common (like "unhappy")
- Perfect for word puzzle games

Return JSON array: [{"word1": "happy", "word2": "sad", "confidence": 0.9}]`;

        try {
            const response = await callAI(prompt, 'gpt-3.5-turbo', 3, {
                category: USAGE_CATEGORIES.WORD_GENERATION,
                puzzleType: 'antonyms',
                difficulty: difficulty
            });
            
            const aiPairs = JSON.parse(response);
            const validatedPairs = [];
            
            for (const pair of aiPairs) {
                if (validatedPairs.length >= pairCount) break;
                
                // FIXED: Skip problematic words before validation
                if (this.isProblematicWord(pair.word1) || this.isProblematicWord(pair.word2)) {
                    continue;
                }
                
                const isValidated = await this.validatePairWithDataMuse(pair.word1, pair.word2);
                
                if (isValidated.isValid) {
                    validatedPairs.push({
                        word1: pair.word1,
                        word2: pair.word2,
                        confidence: Math.max(pair.confidence || 0.8, isValidated.confidence),
                        source: 'ai_datamuse_validated',
                        category: 'verified',
                        datamuseScore: isValidated.score
                    });
                    
                    this.debugLog(`✓ Validated: ${pair.word1} ↔ ${pair.word2} (score: ${isValidated.score})`);
                } else {
                    // FIXED: For medium+ difficulty, accept some AI pairs without DataMuse validation
                    // if they pass basic quality checks
                    if (difficulty !== 'easy' && this.validateBasicPairQuality(pair, difficulty)) {
                        validatedPairs.push({
                            word1: pair.word1,
                            word2: pair.word2,
                            confidence: pair.confidence || 0.75,
                            source: 'ai_basic_validated',
                            category: 'unverified',
                            datamuseScore: 0
                        });
                        
                        this.debugLog(`✓ Basic validation: ${pair.word1} ↔ ${pair.word2} (AI only)`);
                    }
                }
                
                await this.delay(this.requestDelay);
            }
            
            return validatedPairs;
            
        } catch (error) {
            this.debugLog(`⚠️ AI + validation failed: ${error.message}`, 'warning');
            return [];
        }
    }

    // FIXED: New method for pure AI fallback with better validation
    async generatePureAIFallback(difficulty, pairCount) {
        this.debugLog(`🎲 Pure AI fallback for ${pairCount} pairs`);
        
        const prompt = `Create ${pairCount} perfect antonym pairs for ${difficulty} difficulty.

Requirements:
- ONLY classic, clear opposites
- Common words everyone knows
- NO technical terms or compound words
- Examples: happy/sad, big/small, hot/cold

JSON: [{"word1": "word", "word2": "opposite", "confidence": 0.8}]`;

        try {
            const response = await callAI(prompt, 'gpt-3.5-turbo', 2, {
                category: USAGE_CATEGORIES.WORD_GENERATION,
                puzzleType: 'antonyms',
                difficulty: difficulty
            });
            
            const results = JSON.parse(response);
            return results
                .filter(pair => this.validateBasicPairQuality(pair, difficulty))
                .slice(0, pairCount)
                .map(pair => ({
                    word1: pair.word1,
                    word2: pair.word2,
                    confidence: pair.confidence || 0.75,
                    source: 'pure_ai_fallback',
                    category: 'basic',
                    datamuseScore: 0
                }));
                
        } catch (error) {
            this.debugLog(`❌ Pure AI fallback failed: ${error.message}`, 'error');
            return [];
        }
    }

    // FIXED: Check for problematic words
    isProblematicWord(word) {
        const normalizedWord = word.toLowerCase();
        
        // Check blacklist
        if (this.problematicWords.includes(normalizedWord)) {
            return true;
        }
        
        // Check for problematic patterns
        const problematicPatterns = [
            /^non[a-z]+$/,      // non- prefixes (except common ones)
            /^un[a-z]{8,}$/,    // long un- prefixes
            /ing$/,             // -ing words often problematic
            /tion$/             // -tion words often technical
        ];
        
        return problematicPatterns.some(pattern => pattern.test(normalizedWord));
    }

    // FIXED: Basic validation without DataMuse requirement
    validateBasicPairQuality(pair, difficulty) {
        if (!pair.word1 || !pair.word2 || pair.word1 === pair.word2) {
            return false;
        }

        if (!/^[a-zA-Z]+$/.test(pair.word1) || !/^[a-zA-Z]+$/.test(pair.word2)) {
            return false;
        }

        if (pair.word1.length < 3 || pair.word2.length < 3 || 
            pair.word1.length > 10 || pair.word2.length > 10) {
            return false;
        }

        if (this.isProblematicWord(pair.word1) || this.isProblematicWord(pair.word2)) {
            return false;
        }

        return true;
    }

    async getDataMuseAntonyms(word) {
        const cacheKey = `antonyms_${word.toLowerCase()}`;
        
        if (this.datamuseCache.has(cacheKey)) {
            const cached = this.datamuseCache.get(cacheKey);
            if (Date.now() - cached.timestamp < this.cacheExpiry) {
                return cached.data;
            }
        }
        
        try {
            const url = `${this.datamuseBaseUrl}?rel_ant=${encodeURIComponent(word)}&max=10`;
            const response = await fetch(url);
            
            if (!response.ok) {
                throw new Error(`DataMuse API error: ${response.status}`);
            }
            
            const data = await response.json();
            
            // FIXED: Filter out problematic antonyms before caching
            const filteredData = data.filter(item => 
                !this.isProblematicWord(item.word) && 
                item.score !== undefined && 
                item.score > 0
            );
            
            this.datamuseCache.set(cacheKey, {
                data: filteredData,
                timestamp: Date.now()
            });
            
            return filteredData;
            
        } catch (error) {
            this.debugLog(`❌ DataMuse API error for ${word}: ${error.message}`, 'error');
            return [];
        }
    }

    async validatePairWithDataMuse(word1, word2) {
        try {
            const antonyms1 = await this.getDataMuseAntonyms(word1);
            const antonyms2 = await this.getDataMuseAntonyms(word2);
            
            const word1ToWord2 = antonyms1.find(ant => 
                ant.word.toLowerCase() === word2.toLowerCase()
            );
            
            const word2ToWord1 = antonyms2.find(ant => 
                ant.word.toLowerCase() === word1.toLowerCase()
            );
            
            if (word1ToWord2 || word2ToWord1) {
                // FIXED: Handle undefined scores properly
                const score = Math.max(
                    word1ToWord2?.score || 0,
                    word2ToWord1?.score || 0
                );
                
                return {
                    isValid: score >= this.thresholds.datamuseMinScore,
                    score,
                    confidence: this.scoreToConfidence(score),
                    bidirectional: !!(word1ToWord2 && word2ToWord1)
                };
            }
            
            return { isValid: false, score: 0, confidence: 0 };
            
        } catch (error) {
            this.debugLog(`❌ DataMuse validation error: ${error.message}`, 'error');
            return { isValid: false, score: 0, confidence: 0 };
        }
    }

    scoreToConfidence(score) {
        // FIXED: Handle undefined/null scores
        if (!score || score === 0) return 0.60;
        
        if (score >= 50000) return 0.95;
        if (score >= 20000) return 0.90;
        if (score >= 10000) return 0.85;
        if (score >= 5000) return 0.80;
        if (score >= 1000) return 0.70;
        if (score >= 500) return 0.65;
        return 0.60;
    }

    async getSeedWordsForDifficulty(difficulty, limit) {
        const scoreThresholds = {
            easy: 6.0,
            medium: 8.0,
            hard: 12.0,
            expert: 15.0
        };

        const minScore = scoreThresholds[difficulty.toLowerCase()] || 8.0;
        
        const { data: words, error } = await supabase
            .from('common_words')
            .select('word, part_of_speech, crossword_score')
            .eq('has_definition', true)
            .gte('crossword_score', minScore)
            .in('part_of_speech', ['adj', 'n', 'v'])
            .gte('word', 'length', 3)
            .lte('word', 'length', 8)
            .order('crossword_score', { ascending: false })
            .limit(limit);

        if (error) {
            this.debugLog(`⚠️ Database error: ${error.message}`, 'warning');
            return [];
        }

        return words || [];
    }

    validatePairQuality(pair, difficulty) {
        return this.validateBasicPairQuality(pair, difficulty) && 
               pair.confidence >= this.thresholds.minConfidence;
    }

    formatForPuzzleSystem(antonymPairs, difficulty) {
        const sortedPairs = antonymPairs.sort((a, b) => (b.confidence || 0) - (a.confidence || 0));
        
        const pairs = sortedPairs.map(pair => ({
            word1: pair.word1,
            word2: pair.word2
        }));
        
        return {
            pairs,
            pairCount: pairs.length,
            instruction: "Drag each word to its opposite meaning",
            hint: `Find the opposites among these ${pairs.length * 2} words`,
            source: 'datamuse_enhanced_generator',
            metadata: {
                averageConfidence: this.calculateAverageConfidence(sortedPairs),
                averageDataMuseScore: this.calculateAverageScore(sortedPairs),
                dataMuseValidated: sortedPairs.filter(p => p.source.includes('datamuse')).length,
                generatedAt: new Date().toISOString(),
                qualityMetrics: this.calculateQualityMetrics(sortedPairs)
            }
        };
    }

    calculateQualityMetrics(pairs) {
        return {
            totalPairs: pairs.length,
            dataMuseDiscovered: pairs.filter(p => p.source === 'datamuse_discovery').length,
            dataMuseValidated: pairs.filter(p => p.source === 'ai_datamuse_validated').length,
            aiBasicValidated: pairs.filter(p => p.source === 'ai_basic_validated').length,
            pureAiFallback: pairs.filter(p => p.source === 'pure_ai_fallback').length,
            highConfidence: pairs.filter(p => p.confidence >= 0.9).length,
            averageWordLength: pairs.reduce((sum, p) => sum + p.word1.length + p.word2.length, 0) / (pairs.length * 2)
        };
    }

    calculateAverageConfidence(pairs) {
        if (pairs.length === 0) return 0;
        const sum = pairs.reduce((acc, pair) => acc + (pair.confidence || 0), 0);
        return Math.round((sum / pairs.length) * 100) / 100;
    }

    calculateAverageScore(pairs) {
        const scoredPairs = pairs.filter(p => p.datamuseScore && p.datamuseScore > 0);
        if (scoredPairs.length === 0) return 0;
        const sum = scoredPairs.reduce((acc, pair) => acc + pair.datamuseScore, 0);
        return Math.round(sum / scoredPairs.length);
    }

    async delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    debugLog(message, type = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString();
            const emoji = type === 'error' ? '❌' : type === 'warning' ? '⚠️' : type === 'success' ? '✅' : '🔍';
            console.log(`${emoji} [${timestamp}] DATAMUSE_ANTONYM: ${message}`);
        }
    }

    setDebugMode(enabled) {
        this.debugMode = enabled;
        this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'}`);
    }

    async healthCheck() {
        try {
            const testAntonyms = await this.getDataMuseAntonyms('happy');
            const hasAntonyms = testAntonyms.length > 0;
            
            const testValidation = await this.validatePairWithDataMuse('happy', 'sad');
            
            return {
                status: 'healthy',
                datamuseApi: hasAntonyms ? 'connected' : 'limited',
                validation: testValidation.isValid ? 'working' : 'needs_check',
                cacheSize: this.datamuseCache.size,
                problematicWordsFiltered: this.problematicWords.length,
                timestamp: new Date().toISOString()
            };

        } catch (error) {
            return {
                status: 'unhealthy',
                error: error.message,
                timestamp: new Date().toISOString()
            };
        }
    }
}

// Export singleton instance
export const dataMuseAntonymService = new DataMuseEnhancedAntonymService();

// Enhanced wrapper - PRESERVED API
export class EnhancedAntonymGenerator {
    constructor() {
        this.service = dataMuseAntonymService;
    }

    async generateAntonymPuzzle(difficulty = 'medium') {
        try {
            console.log(`🎯 DataMuse-enhanced antonym generation for ${difficulty} difficulty`);
            
            const pairCounts = {
                easy: 4,
                medium: 6,
                hard: 8,
                expert: 10
            };
            
            const pairCount = pairCounts[difficulty.toLowerCase()] || 6;
            const result = await this.service.generateAntonymPuzzle(difficulty, pairCount);
            
            if (!result.success) {
                throw new Error(result.error);
            }

            const puzzleData = {
                pairs: result.puzzleData.pairs,
                pairCount: result.puzzleData.pairCount,
                instruction: result.puzzleData.instruction,
                hint: result.puzzleData.hint,
                difficulty: difficulty,
                source: 'datamuse_enhanced_generator',
                metadata: result.puzzleData.metadata
            };

            console.log(`✅ Generated ${puzzleData.pairs.length} quality-validated pairs`);
            
            return {
                success: true,
                puzzleData
            };

        } catch (error) {
            console.error(`❌ Enhanced generation failed: ${error.message}`);
            return {
                success: false,
                message: error.message
            };
        }
    }

    setDebugMode(enabled) {
        this.service.setDebugMode(enabled);
    }

    async healthCheck() {
        return await this.service.healthCheck();
    }
}

export const enhancedAntonymGenerator = new EnhancedAntonymGenerator();
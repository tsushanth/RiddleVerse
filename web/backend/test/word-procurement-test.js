#!/usr/bin/env node

// Standalone test for Unified Word Procurement Service
// Tests word quality, deduplication, and all procurement sources

import { unifiedWordService } from './unifedWordProcurementService.js';

/**
 * Comprehensive word quality testing suite
 */
class unifiedWordServiceTester {
    constructor() {
        this.results = {
            totalTests: 0,
            passedTests: 0,
            failedTests: 0,
            testResults: []
        };
        
        // Quality criteria for different aspects
        this.qualityCriteria = {
            wordLength: {
                easy: { min: 3, max: 6 },
                medium: { min: 4, max: 8 },
                hard: { min: 5, max: 12 }
            },
            hintQuality: {
                minLength: 10,
                maxLength: 80,
                shouldNotContain: ['undefined', 'null', 'find this word']
            },

    topicRelevance: {
        animals: [
            'cat', 'dog', 'bird', 'fish', 'lion', 'tiger', 'bear', 'wolf', 'fox', 'deer',
            'horse', 'cow', 'pig', 'sheep', 'goat', 'rabbit', 'mouse', 'rat', 'elephant',
            'giraffe', 'zebra', 'hippo', 'rhino', 'monkey', 'ape', 'gorilla', 'kangaroo',
            'koala', 'panda', 'whale', 'dolphin', 'seal', 'penguin', 'eagle', 'hawk',
            'owl', 'snake', 'lizard', 'turtle', 'frog', 'shark', 'octopus', 'crab',
            'butterfly', 'bee', 'ant', 'spider', 'squirrel', 'raccoon', 'skunk',
            'hedgehog', 'porcupine', 'beaver', 'otter', 'bat', 'mole', 'shrew',
            'lemur', 'orangutan', 'chimpanzee', 'baboon', 'bison', 'buffalo'
        ],
        food: [
            'pizza', 'pasta', 'bread', 'cheese', 'butter', 'milk', 'egg', 'meat',
            'beef', 'pork', 'chicken', 'turkey', 'fish', 'salmon', 'tuna', 'rice',
            'wheat', 'corn', 'potato', 'tomato', 'onion', 'garlic', 'carrot', 'apple',
            'banana', 'orange', 'grape', 'strawberry', 'lemon', 'lime', 'soup', 'salad',
            'sandwich', 'burger', 'taco', 'cake', 'cookie', 'chocolate', 'sugar', 'salt',
            'pepper', 'spice', 'herb', 'oil', 'vinegar', 'sauce', 'ketchup', 'mustard',
            'honey', 'syrup', 'jam', 'jelly', 'yogurt', 'cream', 'cereal', 'oats',
            'quinoa', 'beans', 'lentils', 'nuts', 'almond', 'walnut', 'cashew',
            'broccoli', 'spinach', 'lettuce', 'cabbage', 'cauliflower', 'celery',
            'cucumber', 'mushroom', 'avocado', 'coconut', 'pineapple', 'coffee', 'tea'
        ],
        sports: [
            'soccer', 'football', 'basketball', 'baseball', 'tennis', 'golf', 'hockey',
            'volleyball', 'swimming', 'running', 'cycling', 'boxing', 'wrestling',
            'skiing', 'skating', 'surfing', 'climbing', 'hiking', 'fishing', 'hunting',
            'racing', 'marathon', 'sprint', 'jump', 'throw', 'catch', 'kick', 'hit',
            'serve', 'spike', 'dunk', 'goal', 'score', 'win', 'lose', 'team', 'player',
            'coach', 'referee', 'game', 'match', 'tournament', 'championship', 'league',
            'season', 'training', 'practice', 'exercise', 'fitness', 'workout', 'gym'
        ],
        science: [
            'atom', 'molecule', 'element', 'compound', 'energy', 'force', 'gravity',
            'magnetism', 'electricity', 'light', 'sound', 'wave', 'radiation', 'nuclear',
            'quantum', 'physics', 'chemistry', 'biology', 'geology', 'astronomy',
            'mathematics', 'equation', 'formula', 'theory', 'law', 'hypothesis',
            'experiment', 'research', 'study', 'analysis', 'data', 'measurement',
            'computer', 'software', 'hardware', 'program', 'algorithm', 'database',
            'network', 'internet', 'robot', 'artificial', 'intelligence', 'machine',
            'cell', 'dna', 'rna', 'protein', 'gene', 'chromosome', 'organism',
            'bacteria', 'virus', 'tissue', 'organ', 'system', 'evolution', 'species',
            'hydrogen', 'oxygen', 'carbon', 'nitrogen', 'sodium', 'calcium', 'iron',
            'planet', 'star', 'galaxy', 'universe', 'solar', 'moon', 'earth', 'mars',
            'nucleus', 'lab', 'test', 'matter'
        ]
    }
        };
    }

    /**
     * Run all tests
     */
    async runAllTests() {
        console.log('🧪 UNIFIED WORD PROCUREMENT SERVICE - QUALITY TESTS');
        console.log('═'.repeat(80));
        
        // Test 1: Database Health and Connectivity
        await this.testDatabaseHealth();
        
        // Test 2: Basic Word Procurement
        await this.testBasicWordProcurement();
        
        // Test 3: Difficulty Level Validation
        await this.testDifficultyLevels();
        
        // Test 4: Topic-Specific Word Quality
        await this.testTopicWordQuality();
        
        // Test 5: Session Deduplication
        await this.testSessionDeduplication();
        
        // Test 6: Fallback System Quality
        await this.testFallbackSystems();
        
        // Test 7: Word Source Distribution
        await this.testWordSourceDistribution();
        
        // Test 8: Hint Quality Assessment
        await this.testHintQuality();
        
        // Test 9: Edge Cases and Error Handling
        await this.testEdgeCases();
        
        // Test 10: Performance and Scalability
        await this.testPerformance();
        
        // Final Results
        this.displayFinalResults();
    }

    /**
     * Test 1: Database Health and Connectivity
     */
    async testDatabaseHealth() {
        console.log('\n🔍 TEST 1: Database Health and Connectivity');
        console.log('─'.repeat(50));
        
        try {
            const healthCheck = await unifiedWordService.healthCheck();
            
            const isHealthy = healthCheck.status === 'healthy';
            const hasDatabase = healthCheck.database === 'connected';
            
            console.log('Health Status:', healthCheck.status);
            console.log('Database Status:', healthCheck.database);
            console.log('Session ID:', healthCheck.sessionId);
            
            this.recordTest('Database Health Check', isHealthy && hasDatabase, {
                status: healthCheck.status,
                database: healthCheck.database,
                details: healthCheck
            });
            
        } catch (error) {
            this.recordTest('Database Health Check', false, { error: error.message });
        }
    }

    /**
     * Test 2: Basic Word Procurement
     */
    async testBasicWordProcurement() {
        console.log('\n🔍 TEST 2: Basic Word Procurement');
        console.log('─'.repeat(50));
        
        const testCases = [
            { count: 5, difficulty: 'easy' },
            { count: 10, difficulty: 'medium' },
            { count: 15, difficulty: 'hard' }
        ];
        
        for (const testCase of testCases) {
            try {
                console.log(`\nTesting: ${testCase.count} ${testCase.difficulty} words`);
                
                const result = await unifiedWordService.getWords(testCase);
                
                const success = result.success;
                const correctCount = result.words && result.words.length >= Math.min(testCase.count, 5);
                const hasHints = result.words && result.words.every(w => w.hint && w.hint.length > 0);
                const hasWords = result.words && result.words.every(w => w.word && w.word.length > 0);
                
                console.log(`  Success: ${success}`);
                console.log(`  Words returned: ${result.words?.length || 0}/${testCase.count}`);
                console.log(`  Source: ${result.source}`);
                console.log(`  Sample words: ${result.words?.slice(0, 3).map(w => w.word).join(', ')}`);
                
                const testPassed = success && correctCount && hasHints && hasWords;
                
                this.recordTest(`Basic Procurement (${testCase.difficulty})`, testPassed, {
                    requested: testCase.count,
                    received: result.words?.length || 0,
                    source: result.source,
                    hasValidWords: hasWords,
                    hasValidHints: hasHints
                });
                
            } catch (error) {
                this.recordTest(`Basic Procurement (${testCase.difficulty})`, false, { error: error.message });
            }
        }
    }

    /**
     * Test 3: Difficulty Level Validation
     */
    async testDifficultyLevels() {
        console.log('\n🔍 TEST 3: Difficulty Level Validation');
        console.log('─'.repeat(50));
        
        const difficulties = ['easy', 'medium', 'hard'];
        
        for (const difficulty of difficulties) {
            try {
                console.log(`\nTesting ${difficulty.toUpperCase()} difficulty validation:`);
                
                const result = await unifiedWordService.getWords({
                    count: 10,
                    difficulty: difficulty
                });
                
                if (result.success && result.words) {
                    const criteria = this.qualityCriteria.wordLength[difficulty];
                    const validLengths = result.words.filter(w => 
                        w.word.length >= criteria.min && w.word.length <= criteria.max
                    );
                    
                    const lengthCompliance = validLengths.length / result.words.length;
                    const avgLength = result.words.reduce((sum, w) => sum + w.word.length, 0) / result.words.length;
                    
                    console.log(`  Words tested: ${result.words.length}`);
                    console.log(`  Length compliance: ${(lengthCompliance * 100).toFixed(1)}%`);
                    console.log(`  Average length: ${avgLength.toFixed(1)} chars`);
                    console.log(`  Length range: ${Math.min(...result.words.map(w => w.word.length))} - ${Math.max(...result.words.map(w => w.word.length))}`);
                    console.log(`  Sample words: ${result.words.slice(0, 5).map(w => `${w.word}(${w.word.length})`).join(', ')}`);
                    
                    const testPassed = lengthCompliance >= 0.8; // 80% compliance threshold
                    
                    this.recordTest(`Difficulty Validation (${difficulty})`, testPassed, {
                        lengthCompliance: lengthCompliance,
                        averageLength: avgLength,
                        expectedRange: criteria,
                        sampleWords: result.words.slice(0, 5).map(w => w.word)
                    });
                } else {
                    this.recordTest(`Difficulty Validation (${difficulty})`, false, { error: 'No words returned' });
                }
                
            } catch (error) {
                this.recordTest(`Difficulty Validation (${difficulty})`, false, { error: error.message });
            }
        }
    }

    /**
     * Test 4: Topic-Specific Word Quality
     */
    async testTopicWordQuality() {
        console.log('\n🔍 TEST 4: Topic-Specific Word Quality');
        console.log('─'.repeat(50));
        
        const topics = ['animals', 'food', 'sports', 'science'];
        
        for (const topic of topics) {
            try {
                console.log(`\nTesting ${topic.toUpperCase()} topic quality:`);
                
                const result = await unifiedWordService.getWords({
                    count: 8,
                    difficulty: 'medium',
                    topic: topic,
                    puzzleType: 'word_search'
                });
                
                if (result.success && result.words) {
                    const relevantWords = this.assessTopicRelevance(result.words, topic);
                    const relevanceScore = relevantWords.length / result.words.length;
                    
                    console.log(`  Words received: ${result.words.length}`);
                    console.log(`  Source: ${result.source}`);
                    console.log(`  Topic relevance: ${(relevanceScore * 100).toFixed(1)}%`);
                    console.log(`  Relevant words: ${relevantWords.join(', ')}`);
                    console.log(`  All words: ${result.words.map(w => w.word).join(', ')}`);
                    
                    // Check hint quality for topic words
                    const topicHints = result.words.filter(w => this.isTopicRelevant(w.word, topic));
                    const hintQuality = this.assessHintQuality(topicHints);
                    
                    console.log(`  Hint quality score: ${(hintQuality * 100).toFixed(1)}%`);
                    
                    const testPassed = relevanceScore >= 0.5 && hintQuality >= 0.8; // 50% relevance, 80% hint quality
                    
                    this.recordTest(`Topic Quality (${topic})`, testPassed, {
                        relevanceScore: relevanceScore,
                        hintQuality: hintQuality,
                        source: result.source,
                        relevantWords: relevantWords,
                        allWords: result.words.map(w => w.word)
                    });
                } else {
                    this.recordTest(`Topic Quality (${topic})`, false, { error: 'No words returned' });
                }
                
            } catch (error) {
                this.recordTest(`Topic Quality (${topic})`, false, { error: error.message });
            }
        }
    }

    /**
     * Test 5: Session Deduplication
     */
    async testSessionDeduplication() {
        console.log('\n🔍 TEST 5: Session Deduplication');
        console.log('─'.repeat(50));
        
        try {
            // Start a new session
            const sessionId = unifiedWordService.startSession('dedup_test');
            console.log(`Started test session: ${sessionId}`);
            
            // Make multiple requests
            const requests = [
                { count: 5, difficulty: 'easy' },
                { count: 5, difficulty: 'easy' },
                { count: 5, difficulty: 'easy' }
            ];
            
            const allWords = new Set();
            const duplicatesFound = [];
            let totalWordsReceived = 0;
            
            for (let i = 0; i < requests.length; i++) {
                console.log(`\nRequest ${i + 1}:`);
                
                const result = await unifiedWordService.getWords(requests[i]);
                
                if (result.success && result.words) {
                    totalWordsReceived += result.words.length;
                    
                    console.log(`  Words: ${result.words.map(w => w.word).join(', ')}`);
                    console.log(`  Session duplicates removed: ${result.stats.sessionDuplicates}`);
                    
                    // Check for duplicates within this response
                    const responseWords = result.words.map(w => w.word);
                    const responseSet = new Set(responseWords);
                    
                    if (responseSet.size !== responseWords.length) {
                        console.log('  ⚠️  Internal duplicates found in response!');
                    }
                    
                    // Check for duplicates across requests
                    for (const word of responseWords) {
                        if (allWords.has(word)) {
                            duplicatesFound.push(word);
                        } else {
                            allWords.add(word);
                        }
                    }
                }
            }
            
            const sessionStats = unifiedWordService.getSessionStats();
            
            console.log(`\nSession Summary:`);
            console.log(`  Total words received: ${totalWordsReceived}`);
            console.log(`  Unique words: ${allWords.size}`);
            console.log(`  Cross-request duplicates: ${duplicatesFound.length}`);
            console.log(`  Session words used: ${sessionStats.wordsUsed}`);
            console.log(`  Deduplication effective: ${duplicatesFound.length === 0 ? 'YES' : 'NO'}`);
            
            const testPassed = duplicatesFound.length === 0 && allWords.size === totalWordsReceived;
            
            this.recordTest('Session Deduplication', testPassed, {
                totalWordsReceived: totalWordsReceived,
                uniqueWords: allWords.size,
                duplicatesFound: duplicatesFound.length,
                sessionWordsUsed: sessionStats.wordsUsed,
                effectiveDeduplication: duplicatesFound.length === 0
            });
            
        } catch (error) {
            this.recordTest('Session Deduplication', false, { error: error.message });
        }
    }

    /**
     * Test 6: Fallback System Quality
     */
    async testFallbackSystems() {
        console.log('\n🔍 TEST 6: Fallback System Quality');
        console.log('─'.repeat(50));
        
        // Test different scenarios that should trigger fallbacks
        const fallbackTests = [
            { name: 'Topic Fallback (Animals)', options: { count: 6, difficulty: 'easy', topic: 'animals' } },
            { name: 'Topic Fallback (Food)', options: { count: 6, difficulty: 'medium', topic: 'food' } },
            { name: 'Generic Fallback', options: { count: 8, difficulty: 'hard', allowAI: false } }
        ];
        
        for (const test of fallbackTests) {
            try {
                console.log(`\nTesting: ${test.name}`);
                
                // Start fresh session for each test
                unifiedWordService.startSession(`fallback_${test.name.toLowerCase()}`);
                
                const result = await unifiedWordService.getWords(test.options);
                
                if (result.success && result.words) {
                    console.log(`  Words received: ${result.words.length}`);
                    console.log(`  Source: ${result.source}`);
                    console.log(`  Words: ${result.words.map(w => w.word).join(', ')}`);
                    
                    // Assess word quality
                    const wordQuality = this.assessWordQuality(result.words, test.options.difficulty);
                    const hintQuality = this.assessHintQuality(result.words);
                    
                    console.log(`  Word quality score: ${(wordQuality * 100).toFixed(1)}%`);
                    console.log(`  Hint quality score: ${(hintQuality * 100).toFixed(1)}%`);
                    
                    // Check if source indicates fallback was used
                    const usedFallback = result.source.includes('fallback');
                    
                    const testPassed = result.words.length >= 5 && wordQuality >= 0.8 && hintQuality >= 0.8;
                    
                    this.recordTest(test.name, testPassed, {
                        wordsReceived: result.words.length,
                        source: result.source,
                        usedFallback: usedFallback,
                        wordQuality: wordQuality,
                        hintQuality: hintQuality
                    });
                } else {
                    this.recordTest(test.name, false, { error: 'No words returned' });
                }
                
            } catch (error) {
                this.recordTest(test.name, false, { error: error.message });
            }
        }
    }

    /**
     * Test 7: Word Source Distribution
     */
    async testWordSourceDistribution() {
        console.log('\n🔍 TEST 7: Word Source Distribution');
        console.log('─'.repeat(50));
        
        try {
            const sourceStats = {
                database: 0,
                topic_fallback: 0,
                generic_fallback: 0,
                ai: 0
            };
            
            const testRequests = [
                { count: 10, difficulty: 'easy' },
                { count: 10, difficulty: 'medium' },
                { count: 10, difficulty: 'hard' },
                { count: 8, difficulty: 'medium', topic: 'animals' },
                { count: 8, difficulty: 'medium', topic: 'science' }
            ];
            
            unifiedWordService.startSession('source_distribution_test');
            
            for (const request of testRequests) {
                const result = await unifiedWordService.getWords(request);
                
                if (result.success) {
                    const sources = result.source.split('+');
                    for (const source of sources) {
                        if (sourceStats.hasOwnProperty(source)) {
                            sourceStats[source]++;
                        }
                    }
                }
            }
            
            console.log('\nSource Distribution:');
            const totalRequests = testRequests.length;
            for (const [source, count] of Object.entries(sourceStats)) {
                const percentage = totalRequests > 0 ? (count / totalRequests * 100).toFixed(1) : '0.0';
                console.log(`  ${source}: ${count}/${totalRequests} (${percentage}%)`);
            }
            
            // Test passes if we get reasonable distribution
            const hasDatabase = sourceStats.database > 0;
            const hasFallbacks = sourceStats.topic_fallback > 0 || sourceStats.generic_fallback > 0;
            
            this.recordTest('Word Source Distribution', hasDatabase || hasFallbacks, {
                sourceStats: sourceStats,
                totalRequests: totalRequests,
                hasDatabase: hasDatabase,
                hasFallbacks: hasFallbacks
            });
            
        } catch (error) {
            this.recordTest('Word Source Distribution', false, { error: error.message });
        }
    }

    /**
     * Test 8: Hint Quality Assessment
     */
    async testHintQuality() {
        console.log('\n🔍 TEST 8: Hint Quality Assessment');
        console.log('─'.repeat(50));
        
        try {
            unifiedWordService.startSession('hint_quality_test');
            
            const result = await unifiedWordService.getWords({
                count: 10,
                difficulty: 'medium'
            });
            
            if (result.success && result.words) {
                console.log(`\nAnalyzing ${result.words.length} word hints:`);
                
                const hintAnalysis = {
                    tooShort: [],
                    tooLong: [],
                    lowQuality: [],
                    goodQuality: [],
                    averageLength: 0
                };
                
                let totalLength = 0;
                
                for (const word of result.words) {
                    const hint = word.hint;
                    const length = hint.length;
                    totalLength += length;
                    
                    console.log(`  ${word.word}: "${hint}" (${length} chars)`);
                    
                    if (length < this.qualityCriteria.hintQuality.minLength) {
                        hintAnalysis.tooShort.push(word.word);
                    } else if (length > this.qualityCriteria.hintQuality.maxLength) {
                        hintAnalysis.tooLong.push(word.word);
                    } else if (this.qualityCriteria.hintQuality.shouldNotContain.some(bad => hint.toLowerCase().includes(bad))) {
                        hintAnalysis.lowQuality.push(word.word);
                    } else {
                        hintAnalysis.goodQuality.push(word.word);
                    }
                }
                
                hintAnalysis.averageLength = totalLength / result.words.length;
                
                console.log(`\nHint Analysis:`);
                console.log(`  Average length: ${hintAnalysis.averageLength.toFixed(1)} chars`);
                console.log(`  Good quality: ${hintAnalysis.goodQuality.length}/${result.words.length}`);
                console.log(`  Too short: ${hintAnalysis.tooShort.length}`);
                console.log(`  Too long: ${hintAnalysis.tooLong.length}`);
                console.log(`  Low quality: ${hintAnalysis.lowQuality.length}`);
                
                const qualityScore = hintAnalysis.goodQuality.length / result.words.length;
                const testPassed = qualityScore >= 0.8; // 80% of hints should be good quality
                
                this.recordTest('Hint Quality Assessment', testPassed, {
                    qualityScore: qualityScore,
                    averageLength: hintAnalysis.averageLength,
                    goodQuality: hintAnalysis.goodQuality.length,
                    totalHints: result.words.length,
                    analysis: hintAnalysis
                });
                
            } else {
                this.recordTest('Hint Quality Assessment', false, { error: 'No words returned' });
            }
            
        } catch (error) {
            this.recordTest('Hint Quality Assessment', false, { error: error.message });
        }
    }

    /**
     * Test 9: Edge Cases and Error Handling
     */
    async testEdgeCases() {
        console.log('\n🔍 TEST 9: Edge Cases and Error Handling');
        console.log('─'.repeat(50));
        
        const edgeCases = [
            { name: 'Zero words requested', options: { count: 0, difficulty: 'medium' } },
            { name: 'Excessive words requested', options: { count: 1000, difficulty: 'medium' } },
            { name: 'Invalid difficulty', options: { count: 5, difficulty: 'impossible' } },
            { name: 'Unknown topic', options: { count: 5, difficulty: 'medium', topic: 'quantum_unicorns' } },
            { name: 'Minimum words with AI disabled', options: { count: 5, difficulty: 'hard', allowAI: false } }
        ];
        
        for (const edgeCase of edgeCases) {
            try {
                console.log(`\nTesting: ${edgeCase.name}`);
                
                const result = await unifiedWordService.getWords(edgeCase.options);
                
                console.log(`  Success: ${result.success}`);
                console.log(`  Words returned: ${result.words?.length || 0}`);
                console.log(`  Source: ${result.source || 'N/A'}`);
                if (!result.success) {
                    console.log(`  Error: ${result.error}`);
                }
                
                // Edge cases should either succeed gracefully or fail with appropriate errors
                const handledGracefully = result.success || (result.error && result.error.length > 0);
                
                this.recordTest(`Edge Case: ${edgeCase.name}`, handledGracefully, {
                    success: result.success,
                    wordsReturned: result.words?.length || 0,
                    error: result.error,
                    handledGracefully: handledGracefully
                });
                
            } catch (error) {
                // Catching exceptions is also acceptable for edge cases
                console.log(`  Exception caught: ${error.message}`);
                this.recordTest(`Edge Case: ${edgeCase.name}`, true, { 
                    exceptionHandled: true, 
                    error: error.message 
                });
            }
        }
    }

    /**
     * Test 10: Performance and Scalability
     */
    async testPerformance() {
        console.log('\n🔍 TEST 10: Performance and Scalability');
        console.log('─'.repeat(50));
        
        try {
            const performanceTests = [
                { name: 'Single request timing', count: 10, iterations: 1 },
                { name: 'Batch request timing', count: 5, iterations: 10 },
                { name: 'Large session timing', count: 20, iterations: 5 }
            ];
            
            for (const perfTest of performanceTests) {
                console.log(`\nTesting: ${perfTest.name}`);
                
                unifiedWordService.startSession(`perf_${perfTest.name.replace(/\s+/g, '_')}`);
                
                const startTime = Date.now();
                let totalWords = 0;
                let successfulRequests = 0;
                
                for (let i = 0; i < perfTest.iterations; i++) {
                    try {
                        const result = await unifiedWordService.getWords({
                            count: perfTest.count,
                            difficulty: 'medium'
                        });
                        
                        if (result.success) {
                            totalWords += result.words.length;
                            successfulRequests++;
                        }
                    } catch (error) {
                        console.log(`    Request ${i + 1} failed: ${error.message}`);
                    }
                }
                
                const endTime = Date.now();
                const totalTime = endTime - startTime;
                const averageTime = totalTime / perfTest.iterations;
                
                console.log(`  Total time: ${totalTime}ms`);
                console.log(`  Average per request: ${averageTime.toFixed(1)}ms`);
                console.log(`  Successful requests: ${successfulRequests}/${perfTest.iterations}`);
                console.log(`  Total words: ${totalWords}`);
                console.log(`  Words per second: ${(totalWords / (totalTime / 1000)).toFixed(1)}`);
                
                // Performance test passes if average request time is reasonable and success rate is good
                const reasonableTime = averageTime < 5000; // Less than 5 seconds per request
                const goodSuccessRate = successfulRequests / perfTest.iterations >= 0.8; // 80% success rate
                
                const testPassed = reasonableTime && goodSuccessRate;
                
                this.recordTest(`Performance: ${perfTest.name}`, testPassed, {
                    totalTime: totalTime,
                    averageTime: averageTime,
                    successfulRequests: successfulRequests,
                    totalRequests: perfTest.iterations,
                    totalWords: totalWords,
                    wordsPerSecond: totalWords / (totalTime / 1000),
                    reasonableTime: reasonableTime,
                    goodSuccessRate: goodSuccessRate
                });
            }
            
        } catch (error) {
            this.recordTest('Performance Testing', false, { error: error.message });
        }
    }

    // ===========================================
    // HELPER METHODS FOR QUALITY ASSESSMENT
    // ===========================================

    /**
     * Assess topic relevance of words
     */
    assessTopicRelevance(words, topic) {
        const relevantWords = [];
        
        for (const wordData of words) {
            if (this.isTopicRelevant(wordData.word, topic)) {
                relevantWords.push(wordData.word);
            }
        }
        
        return relevantWords;
    }

    /**
     * Check if a word is relevant to a topic
     */
    isTopicRelevant(word, topic) {
        const wordLower = word.toLowerCase();
        const topicKeywords = this.qualityCriteria.topicRelevance[topic] || [];
        
        return topicKeywords.some(keyword => 
            wordLower.includes(keyword) || keyword.includes(wordLower)
        );
    }

    /**
     * Assess overall word quality
     */
    assessWordQuality(words, difficulty) {
        if (!words || words.length === 0) return 0;
        
        const criteria = this.qualityCriteria.wordLength[difficulty];
        const validWords = words.filter(w => {
            const length = w.word.length;
            return length >= criteria.min && length <= criteria.max && 
                   w.word.match(/^[A-Z]+$/) && // Only uppercase letters
                   w.word.length >= 3; // Minimum readable length
        });
        
        return validWords.length / words.length;
    }

    /**
     * Assess hint quality
     */
    assessHintQuality(words) {
        if (!words || words.length === 0) return 0;
        
        const goodHints = words.filter(w => {
            const hint = w.hint;
            return hint && 
                   hint.length >= this.qualityCriteria.hintQuality.minLength &&
                   hint.length <= this.qualityCriteria.hintQuality.maxLength &&
                   !this.qualityCriteria.hintQuality.shouldNotContain.some(bad => 
                       hint.toLowerCase().includes(bad)
                   );
        });
        
        return goodHints.length / words.length;
    }

    /**
     * Record test result
     */
    recordTest(testName, passed, details = {}) {
        this.results.totalTests++;
        if (passed) {
            this.results.passedTests++;
        } else {
            this.results.failedTests++;
        }
        
        this.results.testResults.push({
            name: testName,
            passed: passed,
            details: details,
            timestamp: new Date().toISOString()
        });
        
        const status = passed ? '✅ PASSED' : '❌ FAILED';
        console.log(`\n${status}: ${testName}`);
    }

    /**
     * Display final test results
     */
    displayFinalResults() {
        console.log('\n🏁 FINAL TEST RESULTS');
        console.log('═'.repeat(80));
        
        const passRate = (this.results.passedTests / this.results.totalTests * 100).toFixed(1);
        
        console.log(`\n📊 SUMMARY:`);
        console.log(`  Total Tests: ${this.results.totalTests}`);
        console.log(`  Passed: ${this.results.passedTests}`);
        console.log(`  Failed: ${this.results.failedTests}`);
        console.log(`  Pass Rate: ${passRate}%`);
        
        console.log(`\n📋 DETAILED RESULTS:`);
        for (const result of this.results.testResults) {
            const status = result.passed ? '✅' : '❌';
            console.log(`  ${status} ${result.name}`);
            
            if (!result.passed && result.details.error) {
                console.log(`      Error: ${result.details.error}`);
            }
        }
        
        // Quality Assessment
        console.log(`\n🎯 QUALITY ASSESSMENT:`);
        
        if (passRate >= 90) {
            console.log('  🌟 EXCELLENT - Service is production ready!');
        } else if (passRate >= 80) {
            console.log('  ✅ GOOD - Service is mostly reliable with minor issues');
        } else if (passRate >= 70) {
            console.log('  ⚠️  FAIR - Service needs improvement before production');
        } else {
            console.log('  ❌ POOR - Service has significant issues that need addressing');
        }
        
        // Recommendations
        console.log(`\n💡 RECOMMENDATIONS:`);
        const failedTests = this.results.testResults.filter(r => !r.passed);
        
        if (failedTests.length === 0) {
            console.log('  • All tests passed! Service is ready for production use.');
        } else {
            console.log('  • Focus on fixing the following areas:');
            failedTests.forEach(test => {
                console.log(`    - ${test.name}`);
            });
        }
        
        console.log(`\n🎉 Testing completed at ${new Date().toISOString()}`);
        
        // Exit with appropriate code
        process.exit(this.results.failedTests === 0 ? 0 : 1);
    }
}

// Run the tests
async function runTests() {
    const tester = new unifiedWordServiceTester();
    await tester.runAllTests();
}

// Execute if run directly
if (import.meta.url === `file://${process.argv[1]}`) {
    runTests().catch(error => {
        console.error('❌ Test runner failed:', error);
        process.exit(1);
    });
}

export { unifiedWordServiceTester, runTests };
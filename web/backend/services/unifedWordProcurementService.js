// unifiedWordProcurementService.js - Unified Word Procurement Service
// Used by both Word Search and Word Snake generators

import { supabase } from '../config/database.js';
import { callAI } from '../utils/aiClient.js';
import { USAGE_CATEGORIES } from '../utils/usageTracker.js';

/**
 * Unified Word Procurement Service
 * Hierarchy: Database → Topic Fallbacks → Generic Fallbacks → AI (last resort)
 * Features: Session deduplication, quality scoring, topic awareness
 */
export class UnifiedWordService {
    constructor() {
        this.debugMode = true;
        
        // Session-based word tracking for deduplication
        this.usedWordsInSession = new Set();
        this.sessionStartTime = Date.now();
        this.sessionId = this.generateSessionId();
        
        // Difficulty configurations (unified from both services)
        this.difficultyConfig = {
            easy: {
                syllable_count: [1, 2],
                word_length: [3, 7],        // Expanded range
                frequency_rank: [1, 5000],
                readability_score: [0.7, 1.0],
                cognitive_load: [0, 0.4],
                educational_level: ['elementary', 'middle_school'],
                maxWords: 12,
                minWords: 3                 // REDUCED from 5
            },
            medium: {
                syllable_count: [2, 3],
                word_length: [4, 9],        // Expanded range  
                frequency_rank: [1000, 15000],
                readability_score: [0.5, 0.8],
                cognitive_load: [0.3, 0.7],
                educational_level: ['middle_school', 'high_school'],
                maxWords: 15,
                minWords: 5                 // REDUCED from 8
            },
            hard: {
                syllable_count: [3, 5],
                word_length: [5, 12],       // Expanded range
                frequency_rank: [5000, 50000],
                readability_score: [0.3, 0.7],
                cognitive_load: [0.6, 1.0],
                educational_level: ['high_school', 'college'],
                maxWords: 20,
                minWords: 8                 // REDUCED from 10
            }
        };
        
        // Quality thresholds
        this.thresholds = {
            minCrosswordScore: 12.0,
            minTopicRelevance: 0.6,
            maxWordLength: 15,
            minWordLength: 3
        };
    }

    // ===========================================
    // SESSION MANAGEMENT & DEDUPLICATION
    // ===========================================

    /**
     * Start a new word procurement session
     */
    startSession(sessionType = 'default') {
        this.usedWordsInSession.clear();
        this.sessionStartTime = Date.now();
        this.sessionId = this.generateSessionId();
        this.sessionType = sessionType;
        
        this.debugLog(`Started new word session: ${this.sessionId} (${sessionType})`);
        return this.sessionId;
    }

    /**
     * Get current session statistics
     */
    getSessionStats() {
        return {
            sessionId: this.sessionId,
            sessionType: this.sessionType,
            wordsUsed: this.usedWordsInSession.size,
            sessionDuration: Date.now() - this.sessionStartTime,
            recentWords: Array.from(this.usedWordsInSession).slice(-10)
        };
    }

    /**
     * Filter out words already used in this session
     */
    filterSessionDuplicates(wordList) {
        const filteredWords = [];
        const duplicatesFound = [];
        
        for (const wordData of wordList) {
            const word = wordData.word.toUpperCase().trim();
            
            if (!this.usedWordsInSession.has(word)) {
                filteredWords.push(wordData);
                this.usedWordsInSession.add(word);
            } else {
                duplicatesFound.push(word);
            }
        }
        
        this.debugLog(`Session dedup: ${filteredWords.length} unique, ${duplicatesFound.length} duplicates filtered`);
        
        // More aggressive session management for batch requests
        if (filteredWords.length < 2 && this.usedWordsInSession.size > 15) {
            this.debugLog(`⚠️ Session exhausted (${this.usedWordsInSession.size} words), doing partial reset`);
            
            // Keep only the most recent 15 words (reduced from 20)
            const recentWords = Array.from(this.usedWordsInSession).slice(-15);
            this.usedWordsInSession.clear();
            recentWords.forEach(word => this.usedWordsInSession.add(word));
            
            // Re-filter with smaller session
            return this.filterSessionDuplicates(wordList);
        }
        
        return {
            words: filteredWords,
            duplicatesRemoved: duplicatesFound.length,
            duplicates: duplicatesFound
        };
    }

    /**
     * Generate unique session ID
     */
    generateSessionId() {
        return `ws_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    // ===========================================
    // MAIN WORD PROCUREMENT METHOD
    // ===========================================

    /**
     * Get words using unified hierarchy: Database → Topic Fallbacks → Generic Fallbacks → AI
     */
    async getWords(options = {}) {
        const {
            count = 10,
            difficulty = 'medium',
            topic = null,
            puzzleType = 'general',
            allowAI = true,
            requireTopicMatch = false
        } = options;
    
        this.debugLog(`Getting ${count} words: difficulty=${difficulty}, topic=${topic}, type=${puzzleType}`);
    
        const config = this.difficultyConfig[difficulty] || this.difficultyConfig.medium;
        const targetCount = Math.min(count, config.maxWords);
        
        let words = [];
        let source = 'unknown';
    
        try {
            // Phase 1: Try Database First (but be smarter about topics)
            this.debugLog('Phase 1: Attempting database word procurement...');
            const dbResult = await this.getDatabaseWords(targetCount, difficulty, topic, puzzleType);
            
            if (dbResult.success && dbResult.words.length >= Math.min(targetCount, config.minWords)) {
                // If we have a topic, check if database words are relevant
                if (topic) {
                    const relevantWords = dbResult.words.filter(word => 
                        this.isWordRelevantToTopic(word.word, topic)
                    );
                    
                    if (relevantWords.length >= Math.min(targetCount, config.minWords)) {
                        words = relevantWords;
                        source = 'database';
                        this.debugLog(`Database SUCCESS with topic relevance: Got ${words.length} words`);
                    } else {
                        this.debugLog(`Database words not topic-relevant, will use fallbacks`);
                        words = []; // Don't use irrelevant database words for topics
                    }
                } else {
                    words = dbResult.words;
                    source = 'database';
                    this.debugLog(`Database SUCCESS: Got ${words.length} words`);
                }
            } else {
                this.debugLog(`Database INSUFFICIENT: Got ${dbResult.words?.length || 0} words, need ${config.minWords}`);
            }
    
            // Phase 2: Topic-Specific Fallbacks (prioritize for topic requests)
            if (words.length < Math.min(targetCount, config.minWords) && topic) {
                this.debugLog('Phase 2: Attempting topic fallback words...');
                const topicWords = this.getTopicFallbackWords(topic, difficulty, targetCount - words.length);
                
                if (topicWords.length > 0) {
                    words = [...words, ...topicWords];
                    source = words.length === topicWords.length ? 'topic_fallback' : 'database+topic_fallback';
                    this.debugLog(`Topic fallback: Added ${topicWords.length} words (total: ${words.length})`);
                }
            }
    
            // Phase 3: Generic Fallbacks (if still need more words)
            if (words.length < Math.min(targetCount, config.minWords)) {
                this.debugLog('Phase 3: Attempting generic fallback words...');
                const genericWords = this.getGenericFallbackWords(difficulty, targetCount - words.length);
                
                words = [...words, ...genericWords];
                source = source.includes('fallback') ? source + '+generic' : 
                        source === 'database' ? 'database+generic' : 'generic_fallback';
                this.debugLog(`Generic fallback: Added ${genericWords.length} words (total: ${words.length})`);
            }
    
            // Phase 4: AI Generation (last resort, if allowed and still insufficient)
            if (words.length < config.minWords && allowAI) {
                this.debugLog('Phase 4: Attempting AI word generation (last resort)...');
                try {
                    const aiWords = await this.getAIWords(topic, difficulty, targetCount - words.length);
                    
                    if (aiWords.length > 0) {
                        words = [...words, ...aiWords];
                        source = source + '+ai';
                        this.debugLog(`AI generation: Added ${aiWords.length} words (total: ${words.length})`);
                    }
                } catch (aiError) {
                    this.debugLog(`AI generation failed: ${aiError.message}`, 'error');
                }
            }
    
            // Phase 5: Session Deduplication
            const dedupResult = this.filterSessionDuplicates(words);
            const finalWords = dedupResult.words.slice(0, targetCount);
    
            // Final validation
            if (finalWords.length < config.minWords) {
                throw new Error(`Insufficient words: got ${finalWords.length}, need ${config.minWords}`);
            }
    
            this.debugLog(`SUCCESS: Returning ${finalWords.length} words from ${source}`, 'success');
    
            return {
                success: true,
                words: finalWords,
                source: source,
                stats: {
                    requested: count,
                    returned: finalWords.length,
                    sessionDuplicates: dedupResult.duplicatesRemoved,
                    source: source,
                    sessionStats: this.getSessionStats()
                }
            };
    
        } catch (error) {
            this.debugLog(`Word procurement failed: ${error.message}`, 'error');
            return {
                success: false,
                error: error.message,
                words: [],
                source: source,
                stats: this.getSessionStats()
            };
        }
    }

    // ===========================================
    // DATABASE WORD PROCUREMENT
    // ===========================================

    /**
     * Get words from database with intelligent filtering
     */
    async getDatabaseWords(count, difficulty, topic, puzzleType) {
        this.debugLog(`📚 Fetching words for ${difficulty} difficulty from database`);
        
        try {
            // Slightly adjusted length ranges for better compliance
            const lengthConfig = {
                easy: { min: 3, max: 6 },    // Keep tight for easy
                medium: { min: 4, max: 8 },  // Tightened max (was 9)
                hard: { min: 5, max: 15 }    // Keep wide for hard
            };
            
            const range = lengthConfig[difficulty] || { min: 3, max: 15 };
            
            // Adjusted score ranges for better medium difficulty filtering
            const scoreConfig = {
                easy: { min: 4.0, max: 12.0 },
                medium: { min: 6.0, max: 16.0 },  // Slightly narrower range
                hard: { min: 12.0, max: 25.0 }
            };
            
            const scoreRange = scoreConfig[difficulty] || scoreConfig.medium;
            
            let query = supabase
                .from('common_words')
                .select('word, definition, part_of_speech, crossword_score')
                .eq('has_definition', true)
                .not('definition', 'is', null)
                .gte('crossword_score', scoreRange.min)
                .lte('crossword_score', scoreRange.max)
                .in('part_of_speech', ['n', 'v', 'a', 's']);
            
            const { data: words, error } = await query
                .order('crossword_score', { ascending: false })
                .limit(count * 10);
            
            if (error) {
                throw new Error(`Database query failed: ${error.message}`);
            }
            
            this.debugLog(`📊 Database returned ${words?.length || 0} words`);
            
            if (!words || words.length === 0) {
                return { success: false, words: [], error: 'No words found in database' };
            }
            
            // Apply length filtering
            const lengthFilteredWords = words.filter(word => {
                const length = word.word?.length || 0;
                return length >= range.min && length <= range.max;
            });
            
            this.debugLog(`🔍 After length filtering: ${lengthFilteredWords.length} words`);
            
            // If we still don't have enough for medium difficulty, be more lenient
            let finalWords = lengthFilteredWords;
            
            if (finalWords.length < count && difficulty === 'medium') {
                this.debugLog(`🔄 Medium difficulty fallback: trying slightly longer words`);
                // Allow up to 9 letters for medium as fallback
                const mediumFallback = words.filter(w => w.word && w.word.length >= 4 && w.word.length <= 9);
                if (mediumFallback.length > finalWords.length) {
                    finalWords = mediumFallback;
                    this.debugLog(`🔄 Medium fallback found ${finalWords.length} words`);
                }
            }
            
            if (finalWords.length === 0) {
                return { success: false, words: [], error: 'No words match criteria' };
            }
            
            // Select random subset
            const shuffled = finalWords.sort(() => Math.random() - 0.5);
            const selectedWords = shuffled.slice(0, count);
            
            // Format for unified interface
            const formattedWords = selectedWords.map(word => ({
                word: word.word.toUpperCase().trim(),
                hint: this.cleanDefinition(word.definition),
                metadata: {
                    source: 'database',
                    crossword_score: word.crossword_score,
                    part_of_speech: this.translatePartOfSpeech(word.part_of_speech),
                    word_length: word.word.length
                }
            }));
            
            this.debugLog(`📖 Selected ${formattedWords.length} words from database`);
            
            return {
                success: true,
                words: formattedWords,
                source: 'database'
            };
            
        } catch (error) {
            this.debugLog(`❌ Database word procurement failed: ${error.message}`, 'error');
            return { success: false, words: [], error: error.message };
        }
    }

    isWordRelevantToTopic(word, topic) {
        if (!topic || !word) return false;
        
        const wordLower = word.toLowerCase().trim();
        const topicLower = topic.toLowerCase().trim();
        
        // Direct topic name matching
        if (wordLower.includes(topicLower) || topicLower.includes(wordLower)) {
            return true;
        }
        
        // Quick topic keyword matching (enhanced but lightweight)
        const topicKeywords = {
            animals: [
                'cat', 'dog', 'bird', 'fish', 'lion', 'tiger', 'bear', 'wolf', 'fox', 'deer',
                'horse', 'cow', 'pig', 'sheep', 'goat', 'rabbit', 'mouse', 'rat', 'elephant',
                'giraffe', 'zebra', 'hippo', 'rhino', 'monkey', 'ape', 'gorilla', 'kangaroo',
                'koala', 'panda', 'whale', 'dolphin', 'seal', 'penguin', 'eagle', 'hawk',
                'owl', 'snake', 'lizard', 'turtle', 'frog', 'shark', 'octopus', 'crab',
                'butterfly', 'bee', 'ant', 'spider', 'squirrel', 'raccoon', 'skunk',
                'hedgehog', 'porcupine', 'beaver', 'otter', 'bat', 'mole', 'shrew',
                'lemur', 'orangutan', 'chimpanzee', 'baboon', 'bison', 'buffalo', 'moose',
                'elk', 'caribou', 'reindeer', 'antelope', 'gazelle', 'cheetah', 'leopard',
                'jaguar', 'lynx', 'bobcat', 'cougar', 'puma', 'hyena', 'jackal', 'coyote'
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
                'cucumber', 'pepper', 'mushroom', 'avocado', 'coconut', 'pineapple'
            ],
            sports: [
                'soccer', 'football', 'basketball', 'baseball', 'tennis', 'golf', 'hockey',
                'volleyball', 'swimming', 'running', 'cycling', 'boxing', 'wrestling',
                'skiing', 'skating', 'surfing', 'climbing', 'hiking', 'fishing', 'hunting',
                'racing', 'marathon', 'sprint', 'jump', 'throw', 'catch', 'kick', 'hit',
                'serve', 'spike', 'dunk', 'goal', 'score', 'win', 'lose', 'team', 'player',
                'coach', 'referee', 'game', 'match', 'tournament', 'championship', 'league',
                'season', 'training', 'practice', 'exercise', 'fitness', 'workout', 'gym',
                'court', 'field', 'track', 'pool', 'rink', 'slope', 'mountain', 'ocean',
                'river', 'lake', 'trail', 'park', 'stadium', 'arena', 'ball', 'racket',
                'club', 'bat', 'stick', 'glove', 'helmet', 'uniform', 'shoe', 'equipment'
            ],
            science: [
                'atom', 'molecule', 'element', 'compound', 'energy', 'force', 'gravity',
                'magnetism', 'electricity', 'light', 'sound', 'wave', 'radiation', 'nuclear',
                'quantum', 'physics', 'chemistry', 'biology', 'geology', 'astronomy',
                'mathematics', 'equation', 'formula', 'theory', 'law', 'hypothesis',
                'experiment', 'research', 'study', 'analysis', 'data', 'measurement',
                'observation', 'discovery', 'invention', 'technology', 'engineering',
                'computer', 'software', 'hardware', 'program', 'algorithm', 'database',
                'network', 'internet', 'robot', 'artificial', 'intelligence', 'machine',
                'cell', 'dna', 'rna', 'protein', 'gene', 'chromosome', 'organism',
                'bacteria', 'virus', 'tissue', 'organ', 'system', 'evolution', 'species',
                'hydrogen', 'oxygen', 'carbon', 'nitrogen', 'sodium', 'calcium', 'iron',
                'planet', 'star', 'galaxy', 'universe', 'solar', 'moon', 'earth', 'mars'
            ]
        };
        
        // Check direct keyword matches
        const keywords = topicKeywords[topicLower] || [];
        for (const keyword of keywords) {
            if (wordLower.includes(keyword) || keyword.includes(wordLower)) {
                return true;
            }
        }
        
        // Handle topic aliases
        const aliases = {
            'wildlife': 'animals',
            'cooking': 'food', 
            'kitchen': 'food',
            'exercise': 'sports',
            'fitness': 'sports',
            'game': 'sports',
            'games': 'sports',
            'technology': 'science',
            'computer': 'science',
            'physics': 'science',
            'chemistry': 'science',
            'biology': 'science'
        };
        
        const aliasedTopic = aliases[topicLower];
        if (aliasedTopic) {
            const aliasKeywords = topicKeywords[aliasedTopic] || [];
            for (const keyword of aliasKeywords) {
                if (wordLower.includes(keyword) || keyword.includes(wordLower)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Calculate topic relevance score (0-1)
     */
    calculateTopicRelevance(word, topicData) {
        let maxScore = 0;
        
        // Exact keyword match (highest score)
        if (topicData.primary.includes(word)) {
            maxScore = Math.max(maxScore, 1.0);
        }
        
        // Secondary keyword match
        if (topicData.secondary.includes(word)) {
            maxScore = Math.max(maxScore, 0.9);
        }
        
        // Related terms match
        if (topicData.related.includes(word)) {
            maxScore = Math.max(maxScore, 0.8);
        }
        
        // Substring matching for compound words
        for (const keyword of [...topicData.primary, ...topicData.secondary]) {
            if (word.includes(keyword) || keyword.includes(word)) {
                maxScore = Math.max(maxScore, 0.75);
            }
        }
        
        // Fuzzy/partial matching
        for (const keyword of topicData.primary) {
            if (this.fuzzyMatch(word, keyword)) {
                maxScore = Math.max(maxScore, 0.7);
            }
        }
        
        return maxScore;
    }
    
    /**
     * Simple fuzzy matching for similar words
     */
    fuzzyMatch(word1, word2) {
        if (word1.length < 3 || word2.length < 3) return false;
        
        // Check if they share significant substring
        const minLength = Math.min(word1.length, word2.length);
        const threshold = Math.max(3, Math.floor(minLength * 0.6));
        
        for (let i = 0; i <= word1.length - threshold; i++) {
            const substr = word1.substring(i, i + threshold);
            if (word2.includes(substr)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Comprehensive topic data with primary, secondary, and related terms
     */
    getEnhancedTopicData(topic) {
        const topicDatabase = {
            // ANIMALS - Massively expanded
            animals: {
                primary: [
                    // Mammals - Common
                    'cat', 'dog', 'horse', 'cow', 'pig', 'sheep', 'goat', 'rabbit', 'mouse', 'rat',
                    'bear', 'wolf', 'fox', 'deer', 'lion', 'tiger', 'leopard', 'cheetah', 'jaguar',
                    // Mammals - Large
                    'elephant', 'giraffe', 'zebra', 'hippo', 'rhino', 'buffalo', 'moose', 'elk', 'bison',
                    // Mammals - Primates
                    'monkey', 'ape', 'gorilla', 'chimp', 'orangutan', 'baboon', 'lemur', 'macaque',
                    // Mammals - Marine
                    'whale', 'dolphin', 'seal', 'walrus', 'otter', 'beaver', 'manatee',
                    // Mammals - Small/Exotic
                    'kangaroo', 'koala', 'panda', 'sloth', 'armadillo', 'hedgehog', 'porcupine',
                    'skunk', 'raccoon', 'opossum', 'mole', 'shrew', 'bat', 'squirrel', 'chipmunk',
                    // Birds - Common
                    'bird', 'chicken', 'duck', 'goose', 'turkey', 'swan', 'pigeon', 'crow', 'raven',
                    'robin', 'sparrow', 'cardinal', 'jay', 'finch', 'canary', 'parrot', 'cockatoo',
                    // Birds - Raptors
                    'eagle', 'hawk', 'falcon', 'owl', 'vulture', 'condor', 'kestrel', 'osprey',
                    // Birds - Exotic
                    'penguin', 'ostrich', 'emu', 'peacock', 'flamingo', 'pelican', 'albatross',
                    'toucan', 'macaw', 'hummingbird', 'woodpecker', 'kingfisher', 'crane', 'heron', 'stork',
                    // Reptiles
                    'snake', 'lizard', 'gecko', 'iguana', 'chameleon', 'turtle', 'tortoise',
                    'crocodile', 'alligator', 'komodo', 'python', 'cobra', 'viper', 'rattlesnake',
                    // Amphibians
                    'frog', 'toad', 'salamander', 'newt', 'tadpole',
                    // Fish
                    'fish', 'shark', 'tuna', 'salmon', 'trout', 'bass', 'cod', 'mackerel', 'sardine',
                    'goldfish', 'catfish', 'angelfish', 'swordfish', 'marlin', 'barracuda',
                    // Marine Life
                    'octopus', 'squid', 'jellyfish', 'starfish', 'seahorse', 'crab', 'lobster',
                    'shrimp', 'clam', 'oyster', 'scallop', 'mussel', 'coral', 'anemone',
                    // Insects
                    'ant', 'bee', 'wasp', 'fly', 'mosquito', 'butterfly', 'moth', 'beetle',
                    'cricket', 'grasshopper', 'dragonfly', 'ladybug', 'spider', 'scorpion',
                    'centipede', 'millipede', 'cockroach', 'termite', 'flea', 'tick'
                ],
                secondary: [
                    'mammal', 'reptile', 'amphibian', 'insect', 'arachnid', 'crustacean',
                    'vertebrate', 'invertebrate', 'predator', 'prey', 'carnivore', 'herbivore',
                    'omnivore', 'nocturnal', 'diurnal', 'aquatic', 'terrestrial', 'arboreal',
                    'domestic', 'wild', 'exotic', 'endangered', 'extinct', 'species', 'genus'
                ],
                related: [
                    'zoo', 'safari', 'jungle', 'forest', 'ocean', 'farm', 'pet', 'wildlife',
                    'habitat', 'ecosystem', 'migration', 'hibernate', 'instinct', 'pack',
                    'herd', 'flock', 'swarm', 'colony', 'nest', 'den', 'burrow', 'habitat'
                ]
            },
    
            // FOOD - Comprehensive expansion
            food: {
                primary: [
                    // Grains & Starches
                    'bread', 'rice', 'pasta', 'noodle', 'wheat', 'corn', 'oats', 'barley',
                    'quinoa', 'potato', 'yam', 'sweet', 'cereal', 'flour', 'grain',
                    // Proteins
                    'meat', 'beef', 'pork', 'chicken', 'turkey', 'lamb', 'duck', 'fish',
                    'salmon', 'tuna', 'cod', 'shrimp', 'crab', 'lobster', 'egg', 'tofu',
                    'beans', 'lentils', 'chickpea', 'soy', 'protein',
                    // Dairy
                    'milk', 'cheese', 'butter', 'cream', 'yogurt', 'ice', 'cottage', 'cheddar',
                    'mozzarella', 'parmesan', 'swiss', 'gouda', 'brie', 'feta',
                    // Fruits
                    'apple', 'banana', 'orange', 'grape', 'strawberry', 'blueberry', 'raspberry',
                    'blackberry', 'cherry', 'peach', 'pear', 'plum', 'apricot', 'mango',
                    'pineapple', 'coconut', 'avocado', 'lemon', 'lime', 'grapefruit',
                    'watermelon', 'cantaloupe', 'honeydew', 'kiwi', 'papaya', 'guava',
                    'fig', 'date', 'raisin', 'cranberry', 'pomegranate', 'passion',
                    // Vegetables
                    'tomato', 'onion', 'garlic', 'carrot', 'celery', 'broccoli', 'cauliflower',
                    'spinach', 'lettuce', 'cabbage', 'kale', 'brussels', 'asparagus',
                    'artichoke', 'cucumber', 'zucchini', 'squash', 'pumpkin', 'pepper',
                    'jalapeno', 'mushroom', 'truffle', 'beet', 'radish', 'turnip',
                    // Nuts & Seeds
                    'almond', 'walnut', 'pecan', 'cashew', 'pistachio', 'peanut', 'hazelnut',
                    'macadamia', 'brazil', 'pine', 'sunflower', 'pumpkin', 'sesame',
                    'flax', 'chia', 'seed', 'nut',
                    // Prepared Foods
                    'pizza', 'sandwich', 'burger', 'taco', 'burrito', 'salad', 'soup',
                    'stew', 'curry', 'sushi', 'ramen', 'pancake', 'waffle', 'omelet',
                    // Desserts
                    'cake', 'cookie', 'pie', 'chocolate', 'candy', 'ice', 'cream',
                    'pudding', 'jello', 'donut', 'muffin', 'brownie',
                    // Beverages
                    'water', 'juice', 'coffee', 'tea', 'soda', 'milk', 'wine', 'beer',
                    'cocktail', 'smoothie', 'lemonade',
                    // Condiments & Seasonings
                    'salt', 'pepper', 'sugar', 'honey', 'maple', 'syrup', 'ketchup',
                    'mustard', 'mayo', 'vinegar', 'oil', 'olive', 'sauce', 'spice',
                    'herb', 'basil', 'oregano', 'thyme', 'rosemary', 'sage', 'parsley',
                    'cilantro', 'dill', 'mint', 'ginger', 'cinnamon', 'nutmeg', 'vanilla'
                ],
                secondary: [
                    'recipe', 'cooking', 'baking', 'grilling', 'frying', 'boiling', 'steaming',
                    'organic', 'fresh', 'frozen', 'canned', 'dried', 'processed', 'natural',
                    'healthy', 'nutritious', 'vitamin', 'mineral', 'calorie', 'carbs', 'fat'
                ],
                related: [
                    'kitchen', 'restaurant', 'chef', 'cook', 'meal', 'breakfast', 'lunch',
                    'dinner', 'snack', 'appetite', 'hunger', 'taste', 'flavor', 'delicious',
                    'grocery', 'market', 'farm', 'harvest', 'agriculture', 'cuisine'
                ]
            },
    
            // SPORTS - Comprehensive expansion
            sports: {
                primary: [
                    // Ball Sports
                    'soccer', 'football', 'basketball', 'baseball', 'tennis', 'golf',
                    'volleyball', 'softball', 'cricket', 'rugby', 'polo', 'lacrosse',
                    'handball', 'racquetball', 'squash', 'badminton', 'ping', 'pong',
                    'table', 'hockey', 'field', 'ice', 'roller',
                    // Water Sports
                    'swimming', 'diving', 'surfing', 'sailing', 'rowing', 'kayaking',
                    'canoeing', 'waterskiing', 'wakeboard', 'windsurfing', 'fishing',
                    'snorkeling', 'scuba', 'polo', 'synchronized',
                    // Winter Sports
                    'skiing', 'snowboard', 'skating', 'figure', 'speed', 'bobsled',
                    'luge', 'skeleton', 'curling', 'biathlon', 'cross', 'country',
                    'alpine', 'freestyle', 'nordic', 'jumping', 'slalom', 'downhill',
                    'giant', 'moguls', 'aerials', 'halfpipe', 'slopestyle',
                    // Track & Field
                    'running', 'sprinting', 'marathon', 'hurdles', 'relay', 'steeplechase',
                    'long', 'jump', 'high', 'pole', 'vault', 'triple', 'shot', 'put',
                    'discus', 'hammer', 'javelin', 'decathlon', 'heptathlon', 'pentathlon',
                    // Combat Sports
                    'boxing', 'wrestling', 'judo', 'karate', 'taekwondo', 'kung', 'fu',
                    'aikido', 'jiu', 'jitsu', 'muay', 'thai', 'kickboxing', 'mma',
                    'fencing', 'sumo',
                    // Gymnastics
                    'gymnastics', 'artistic', 'rhythmic', 'trampoline', 'tumbling',
                    'floor', 'vault', 'bars', 'beam', 'rings', 'pommel',
                    // Cycling & Motor
                    'cycling', 'biking', 'mountain', 'road', 'bmx', 'motocross',
                    'supercross', 'enduro', 'trial', 'speedway', 'drag', 'racing',
                    'formula', 'nascar', 'indycar', 'rally', 'karting',
                    // Other Individual
                    'archery', 'shooting', 'rifle', 'pistol', 'trap', 'skeet',
                    'bowling', 'darts', 'billiards', 'pool', 'snooker', 'chess',
                    'checkers', 'backgammon',
                    // Equestrian
                    'equestrian', 'dressage', 'show', 'jumping', 'eventing', 'polo',
                    'rodeo', 'bull', 'riding', 'barrel', 'racing', 'roping',
                    // Fitness/Exercise
                    'yoga', 'pilates', 'aerobics', 'crossfit', 'weightlifting',
                    'powerlifting', 'bodybuilding', 'calisthenics', 'cardio',
                    // Adventure Sports
                    'climbing', 'mountaineering', 'rappelling', 'bungee', 'skydiving',
                    'paragliding', 'hang', 'gliding', 'base', 'jumping'
                ],
                secondary: [
                    'athlete', 'player', 'team', 'coach', 'referee', 'umpire', 'judge',
                    'tournament', 'championship', 'league', 'season', 'playoff', 'finals',
                    'victory', 'defeat', 'win', 'loss', 'tie', 'score', 'point', 'goal',
                    'touchdown', 'homerun', 'slam', 'dunk', 'serve', 'spike', 'tackle'
                ],
                related: [
                    'stadium', 'arena', 'field', 'court', 'track', 'pool', 'gym',
                    'fitness', 'training', 'practice', 'exercise', 'workout', 'competition',
                    'olympic', 'professional', 'amateur', 'varsity', 'college', 'high', 'school'
                ]
            },
    
            // SCIENCE - Comprehensive expansion
            science: {
                primary: [
                    // Physics
                    'atom', 'molecule', 'element', 'compound', 'energy', 'force',
                    'motion', 'gravity', 'magnetism', 'electricity', 'light', 'sound',
                    'wave', 'frequency', 'amplitude', 'velocity', 'acceleration',
                    'momentum', 'friction', 'pressure', 'temperature', 'heat',
                    'radiation', 'nuclear', 'quantum', 'relativity', 'particle',
                    'electron', 'proton', 'neutron', 'photon', 'quark', 'boson',
                    // Chemistry
                    'chemical', 'reaction', 'acid', 'base', 'salt', 'ph', 'ion',
                    'bond', 'covalent', 'ionic', 'metallic', 'oxidation', 'reduction',
                    'catalyst', 'enzyme', 'polymer', 'crystal', 'solution', 'mixture',
                    // Elements (common ones)
                    'hydrogen', 'helium', 'lithium', 'carbon', 'nitrogen', 'oxygen',
                    'fluorine', 'neon', 'sodium', 'magnesium', 'aluminum', 'silicon',
                    'phosphorus', 'sulfur', 'chlorine', 'argon', 'potassium', 'calcium',
                    'iron', 'copper', 'zinc', 'silver', 'gold', 'mercury', 'lead',
                    'uranium', 'plutonium',
                    // Biology
                    'cell', 'nucleus', 'membrane', 'mitochondria', 'chromosome', 'gene',
                    'dna', 'rna', 'protein', 'amino', 'carbohydrate', 'lipid', 'enzyme',
                    'hormone', 'vitamin', 'mineral', 'bacteria', 'virus', 'organism',
                    'tissue', 'organ', 'system', 'evolution', 'mutation', 'species',
                    'ecosystem', 'photosynthesis', 'respiration', 'metabolism',
                    // Earth Science
                    'geology', 'rock', 'mineral', 'fossil', 'sediment', 'igneous',
                    'metamorphic', 'sedimentary', 'volcano', 'earthquake', 'plate',
                    'tectonic', 'erosion', 'weathering', 'atmosphere', 'climate',
                    'weather', 'precipitation', 'evaporation', 'condensation',
                    // Astronomy
                    'planet', 'star', 'galaxy', 'universe', 'solar', 'system',
                    'orbit', 'satellite', 'comet', 'asteroid', 'meteor', 'nebula',
                    'supernova', 'black', 'hole', 'telescope', 'space', 'rocket',
                    'astronaut', 'cosmology',
                    // Technology/Computer Science
                    'computer', 'processor', 'memory', 'storage', 'software', 'hardware',
                    'program', 'algorithm', 'data', 'database', 'network', 'internet',
                    'server', 'client', 'protocol', 'encryption', 'artificial', 'intelligence',
                    'machine', 'learning', 'neural', 'network', 'robot', 'automation',
                    // Mathematics
                    'number', 'equation', 'formula', 'variable', 'function', 'graph',
                    'geometry', 'algebra', 'calculus', 'statistics', 'probability',
                    'theorem', 'proof', 'integer', 'fraction', 'decimal', 'percent',
                    'ratio', 'proportion', 'angle', 'triangle', 'circle', 'sphere'
                ],
                secondary: [
                    'experiment', 'hypothesis', 'theory', 'law', 'principle', 'method',
                    'observation', 'measurement', 'analysis', 'research', 'study',
                    'investigation', 'discovery', 'invention', 'innovation', 'technology',
                    'engineering', 'laboratory', 'microscope', 'telescope', 'instrument'
                ],
                related: [
                    'scientist', 'researcher', 'professor', 'student', 'university',
                    'college', 'school', 'education', 'knowledge', 'learning', 'study',
                    'book', 'journal', 'publication', 'conference', 'symposium',
                    'nobel', 'prize', 'breakthrough', 'advancement', 'progress'
                ]
            },
    
            // NATURE - New comprehensive category
            nature: {
                primary: [
                    // Landscapes
                    'mountain', 'hill', 'valley', 'canyon', 'cliff', 'plateau', 'mesa',
                    'desert', 'oasis', 'forest', 'jungle', 'woodland', 'grove', 'meadow',
                    'prairie', 'grassland', 'savanna', 'tundra', 'marsh', 'swamp',
                    'wetland', 'bog', 'lagoon', 'estuary', 'delta', 'peninsula', 'island',
                    // Water Bodies
                    'ocean', 'sea', 'lake', 'pond', 'river', 'stream', 'creek', 'brook',
                    'waterfall', 'rapids', 'spring', 'geyser', 'glacier', 'iceberg',
                    // Weather & Sky
                    'sun', 'moon', 'star', 'cloud', 'rain', 'snow', 'hail', 'sleet',
                    'fog', 'mist', 'dew', 'frost', 'wind', 'breeze', 'storm', 'thunder',
                    'lightning', 'rainbow', 'aurora', 'sunrise', 'sunset', 'twilight',
                    // Plants
                    'tree', 'bush', 'shrub', 'grass', 'moss', 'fern', 'vine', 'flower',
                    'petal', 'stem', 'leaf', 'branch', 'trunk', 'root', 'seed', 'fruit',
                    'bark', 'thorn', 'pollen', 'nectar',
                    // Trees
                    'oak', 'maple', 'pine', 'birch', 'cedar', 'willow', 'elm', 'ash',
                    'cherry', 'apple', 'palm', 'bamboo', 'redwood', 'sequoia',
                    // Flowers
                    'rose', 'lily', 'tulip', 'daisy', 'sunflower', 'orchid', 'violet',
                    'iris', 'daffodil', 'peony', 'carnation', 'hibiscus', 'jasmine',
                    // Natural Phenomena
                    'erosion', 'weathering', 'avalanche', 'landslide', 'earthquake',
                    'volcano', 'eruption', 'tsunami', 'hurricane', 'tornado', 'cyclone',
                    'drought', 'flood', 'wildfire', 'eclipse', 'tide', 'current'
                ],
                secondary: [
                    'natural', 'wild', 'pristine', 'untouched', 'scenic', 'beautiful',
                    'serene', 'peaceful', 'majestic', 'spectacular', 'breathtaking',
                    'environment', 'ecosystem', 'habitat', 'biodiversity', 'conservation',
                    'preservation', 'sustainability', 'ecology', 'climate', 'seasonal'
                ],
                related: [
                    'hiking', 'camping', 'backpacking', 'climbing', 'fishing', 'hunting',
                    'photography', 'birdwatching', 'botanist', 'geologist', 'naturalist',
                    'ranger', 'park', 'reserve', 'sanctuary', 'wilderness', 'trail'
                ]
            },
    
            // Alternative topic mappings
            wildlife: 'animals',
            cooking: 'food',
            kitchen: 'food',
            exercise: 'sports',
            fitness: 'sports',
            game: 'sports',
            games: 'sports',
            technology: 'science',
            computer: 'science',
            physics: 'science',
            chemistry: 'science',
            biology: 'science',
            math: 'science',
            mathematics: 'science',
            environment: 'nature',
            geography: 'nature',
            plants: 'nature',
            weather: 'nature'
        };
    
        // Handle topic aliases
        const normalizedTopic = topicDatabase[topic] || topic;
        if (typeof normalizedTopic === 'string') {
            return topicDatabase[normalizedTopic];
        }
    
        return topicDatabase[topic] || null;
    }        
    
    /**
     * Translate your single-letter part of speech codes to full words
     */
    translatePartOfSpeech(pos) {
        const translations = {
            'n': 'noun',
            'v': 'verb', 
            'a': 'adjective',
            's': 'adverb',
            'r': 'adverb' // assuming 'r' is also adverb-like
        };
        return translations[pos] || pos;
    }
    
    /**
     * Alternative query with different difficulty levels based on crossword_score
     */
    async getDatabaseWordsWithScoreDifficulty(count, difficulty, topic, puzzleType) {
        this.debugLog(`📚 Fetching words for ${difficulty} difficulty using score-based filtering`);
        
        try {
            // Define difficulty by crossword_score ranges (based on your avg of 12.4)
            const scoreRanges = {
                easy: { min: 5.0, max: 10.0 },   // Below average
                medium: { min: 10.0, max: 15.0 }, // Around average  
                hard: { min: 15.0, max: 25.0 }    // Above average
            };
            
            const range = scoreRanges[difficulty] || scoreRanges.medium;
            
            let query = supabase
                .from('common_words')
                .select('word, definition, part_of_speech, crossword_score')
                .eq('has_definition', true)
                .not('definition', 'is', null)
                .gte('crossword_score', range.min)
                .lte('crossword_score', range.max)
                .in('part_of_speech', ['n', 'v', 'a', 's']);
            
            const { data: words, error } = await query
                .order('crossword_score', { ascending: false })
                .limit(count * 4);
            
            if (error) {
                throw new Error(`Database query failed: ${error.message}`);
            }
            
            if (!words || words.length === 0) {
                // Fallback to any words in that score range
                const fallbackQuery = await supabase
                    .from('common_words')
                    .select('word, definition, part_of_speech, crossword_score')
                    .eq('has_definition', true)
                    .not('definition', 'is', null)
                    .gte('crossword_score', range.min)
                    .order('crossword_score', { ascending: false })
                    .limit(count * 2);
                    
                if (fallbackQuery.error || !fallbackQuery.data) {
                    return { success: false, words: [], error: 'No words found for difficulty level' };
                }
                
                words = fallbackQuery.data;
            }
            
            // Filter by word length for difficulty
            const config = this.difficultyConfig[difficulty];
            const filteredWords = words.filter(word => 
                word.word && 
                word.word.length >= config.word_length[0] && 
                word.word.length <= config.word_length[1]
            );
            
            if (filteredWords.length === 0) {
                return { success: false, words: [], error: 'No words match length criteria' };
            }
            
            // Select words
            const shuffled = filteredWords.sort(() => Math.random() - 0.5);
            const selectedWords = shuffled.slice(0, count);
            
            const formattedWords = selectedWords.map(word => ({
                word: word.word.toUpperCase().trim(),
                hint: this.cleanDefinition(word.definition),
                metadata: {
                    source: 'database_score_filtered',
                    crossword_score: word.crossword_score,
                    part_of_speech: this.translatePartOfSpeech(word.part_of_speech),
                    difficulty_range: `${range.min}-${range.max}`,
                    original_pos: word.part_of_speech
                }
            }));
            
            this.debugLog(`📖 Selected ${formattedWords.length} words using score-based difficulty`);
            
            return {
                success: true,
                words: formattedWords,
                source: 'database'
            };
            
        } catch (error) {
            this.debugLog(`❌ Score-based database query failed: ${error.message}`, 'error');
            return { success: false, words: [], error: error.message };
        }
    }
    
    /**
     * Simple database query - just get good words regardless of advanced filtering
     */
    async getDatabaseWordsSimple(count, difficulty) {
        this.debugLog(`📚 Simple database word fetch for ${difficulty}`);
        
        try {
            const { data: words, error } = await supabase
                .from('common_words')
                .select('word, definition, part_of_speech, crossword_score')
                .eq('has_definition', true)
                .not('definition', 'is', null)
                .gte('crossword_score', 8.0) // Reasonable threshold
                .in('part_of_speech', ['n', 'v', 'a']) // Focus on nouns, verbs, adjectives
                .order('crossword_score', { ascending: false })
                .limit(count * 5);
            
            if (error || !words || words.length === 0) {
                return { success: false, words: [], error: error?.message || 'No words found' };
            }
            
            // Apply difficulty through word length only
            const lengthRanges = {
                easy: [3, 6],
                medium: [4, 8], 
                hard: [5, 12]
            };
            
            const [minLen, maxLen] = lengthRanges[difficulty] || [3, 12];
            
            const filteredWords = words
                .filter(word => word.word.length >= minLen && word.word.length <= maxLen)
                .sort(() => Math.random() - 0.5)
                .slice(0, count)
                .map(word => ({
                    word: word.word.toUpperCase().trim(),
                    hint: this.cleanDefinition(word.definition),
                    metadata: {
                        source: 'database_simple',
                        crossword_score: word.crossword_score,
                        part_of_speech: this.translatePartOfSpeech(word.part_of_speech),
                        word_length: word.word.length
                    }
                }));
            
            this.debugLog(`✅ Simple query returned ${filteredWords.length} words`);
            
            return {
                success: true,
                words: filteredWords,
                source: 'database'
            };
            
        } catch (error) {
            this.debugLog(`❌ Simple database query failed: ${error.message}`, 'error');
            return { success: false, words: [], error: error.message };
        }
    }
    

    /**
     * Process and format database words
     */
    processDatabaseWords(words, config, targetCount) {
        if (!words || words.length === 0) {
            return { success: false, words: [] };
        }

        // Filter by word length
        const filteredWords = words.filter(word => 
            word.word.length >= config.word_length[0] && 
            word.word.length <= config.word_length[1] &&
            word.word.length >= this.thresholds.minWordLength &&
            word.word.length <= this.thresholds.maxWordLength
        );

        // Shuffle and select
        const shuffled = filteredWords.sort(() => Math.random() - 0.5);
        const selectedWords = shuffled.slice(0, targetCount);

        // Format for unified interface
        const formattedWords = selectedWords.map(word => ({
            word: word.word.toUpperCase().trim(),
            hint: this.cleanDefinition(word.definition),
            metadata: {
                source: 'database',
                syllable_count: word.syllable_count,
                frequency_rank: word.frequency_rank,
                readability_score: word.readability_score,
                crossword_score: word.crossword_score,
                part_of_speech: word.part_of_speech,
                cognitive_load: word.cognitive_load
            }
        }));

        return {
            success: true,
            words: formattedWords,
            source: 'database'
        };
    }

    /**
     * Clean definition for use as hint
     */
    cleanDefinition(definition) {
        if (!definition) return 'Find this word';
        
        let cleaned = definition
            .replace(/^(the |a |an )/i, '')
            .replace(/^(to |of |in |on |at |by |for |with |from )/i, '')
            .replace(/\b(noun|verb|adjective|adverb)\.?\s*/i, '')
            .replace(/\s+/g, ' ')
            .trim();
        
        cleaned = cleaned.charAt(0).toUpperCase() + cleaned.slice(1);
        
        if (cleaned.length > 60) {
            cleaned = cleaned.substring(0, 57) + '...';
        }
        
        return cleaned;
    }

    // ===========================================
    // TOPIC FALLBACK WORDS
    // ===========================================

    /**
     * Get topic-specific fallback words
     */
    getTopicFallbackWords(topic, difficulty, count) {
        const topicLower = topic.toLowerCase();
        let words = [];
    
        // Enhanced topic-specific word banks
        if (topicLower.includes('animal') || topicLower.includes('wildlife')) {
            words = [
                { word: 'TIGER', hint: 'Striped big cat' },
                { word: 'ELEPHANT', hint: 'Large mammal with trunk' },
                { word: 'PENGUIN', hint: 'Antarctic flightless bird' },
                { word: 'DOLPHIN', hint: 'Intelligent marine mammal' },
                { word: 'KANGAROO', hint: 'Australian hopping marsupial' },
                { word: 'GIRAFFE', hint: 'Tallest land animal' },
                { word: 'ZEBRA', hint: 'Striped horse-like animal' },
                { word: 'PANDA', hint: 'Black and white bear' },
                { word: 'LION', hint: 'King of the jungle' },
                { word: 'WHALE', hint: 'Largest marine animal' },
                { word: 'SHARK', hint: 'Ocean predator with fins' },
                { word: 'EAGLE', hint: 'Large bird of prey' },
                { word: 'WOLF', hint: 'Wild canine pack hunter' },
                { word: 'BEAR', hint: 'Large furry omnivore' },
                { word: 'DEER', hint: 'Graceful forest animal' },
                { word: 'RABBIT', hint: 'Small hopping mammal' },
                { word: 'SQUIRREL', hint: 'Tree-climbing nut gatherer' },
                { word: 'MONKEY', hint: 'Primate with long tail' },
                { word: 'SNAKE', hint: 'Legless reptile' },
                { word: 'TURTLE', hint: 'Reptile with shell' }
            ];
        }
        else if (topicLower.includes('food') || topicLower.includes('cooking') || topicLower.includes('kitchen')) {
            words = [
                { word: 'PIZZA', hint: 'Italian dish with cheese and toppings' },
                { word: 'PASTA', hint: 'Italian noodle dish' },
                { word: 'BREAD', hint: 'Baked staple food' },
                { word: 'CHEESE', hint: 'Dairy product' },
                { word: 'APPLE', hint: 'Red or green fruit' },
                { word: 'BANANA', hint: 'Yellow curved fruit' },
                { word: 'ORANGE', hint: 'Citrus fruit' },
                { word: 'TOMATO', hint: 'Red cooking ingredient' },
                { word: 'CHICKEN', hint: 'Common poultry meat' },
                { word: 'SALMON', hint: 'Pink fish rich in omega-3' },
                { word: 'RICE', hint: 'Asian staple grain' },
                { word: 'POTATO', hint: 'Starchy tuber vegetable' },
                { word: 'CARROT', hint: 'Orange root vegetable' },
                { word: 'ONION', hint: 'Layered vegetable that makes you cry' },
                { word: 'GARLIC', hint: 'Pungent cooking ingredient' },
                { word: 'BUTTER', hint: 'Creamy dairy spread' },
                { word: 'HONEY', hint: 'Sweet bee product' },
                { word: 'CHOCOLATE', hint: 'Sweet cocoa treat' },
                { word: 'COFFEE', hint: 'Caffeinated morning drink' },
                { word: 'TEA', hint: 'Steeped leaf beverage' }
            ];
        }
        else if (topicLower.includes('sport') || topicLower.includes('game') || topicLower.includes('exercise')) {
            words = [
                { word: 'SOCCER', hint: 'World\'s most popular sport' },
                { word: 'TENNIS', hint: 'Racket sport played on a court' },
                { word: 'GOLF', hint: 'Sport played with clubs and balls' },
                { word: 'SWIMMING', hint: 'Water sport and exercise' },
                { word: 'RUNNING', hint: 'Basic form of exercise' },
                { word: 'CYCLING', hint: 'Sport using a bicycle' },
                { word: 'BOXING', hint: 'Combat sport with gloves' },
                { word: 'SKIING', hint: 'Winter sport on snow' },
                { word: 'HOCKEY', hint: 'Ice sport with sticks and puck' },
                { word: 'BASEBALL', hint: 'American sport with bat and ball' },
                { word: 'BASKETBALL', hint: 'Sport with hoops and dribbling' },
                { word: 'VOLLEYBALL', hint: 'Net sport with spiking' },
                { word: 'WRESTLING', hint: 'Combat sport with grappling' },
                { word: 'MARATHON', hint: 'Long distance running race' },
                { word: 'SURFING', hint: 'Riding waves on a board' },
                { word: 'CLIMBING', hint: 'Ascending rocks or mountains' },
                { word: 'FISHING', hint: 'Catching aquatic animals' },
                { word: 'RACING', hint: 'Competition of speed' },
                { word: 'TRAINING', hint: 'Physical preparation for sports' },
                { word: 'EXERCISE', hint: 'Physical activity for fitness' }
            ];
        }
        else if (topicLower.includes('science') || topicLower.includes('technology') || topicLower.includes('computer')) {
            words = [
                { word: 'ATOM', hint: 'Basic unit of matter' },
                { word: 'ENERGY', hint: 'Capacity to do work' },
                { word: 'GRAVITY', hint: 'Force that pulls objects down' },
                { word: 'MOLECULE', hint: 'Group of bonded atoms' },
                { word: 'COMPUTER', hint: 'Electronic processing device' },
                { word: 'ROBOT', hint: 'Programmable machine' },
                { word: 'INTERNET', hint: 'Global computer network' },
                { word: 'ELECTRON', hint: 'Negatively charged particle' },
                { word: 'PROTEIN', hint: 'Large biological molecule' },
                { word: 'OXYGEN', hint: 'Gas essential for breathing' },
                { word: 'HYDROGEN', hint: 'Lightest chemical element' },
                { word: 'CARBON', hint: 'Element basis of organic life' },
                { word: 'PLANET', hint: 'Celestial body orbiting a star' },
                { word: 'GALAXY', hint: 'Collection of billions of stars' },
                { word: 'NUCLEUS', hint: 'Center of an atom or cell' },
                { word: 'PHYSICS', hint: 'Science of matter and energy' },
                { word: 'CHEMISTRY', hint: 'Science of chemical reactions' },
                { word: 'BIOLOGY', hint: 'Science of living organisms' },
                { word: 'EQUATION', hint: 'Mathematical statement of equality' },
                { word: 'EXPERIMENT', hint: 'Scientific test or trial' }
            ];
        }
    
        // Filter by difficulty and add metadata
        const config = this.difficultyConfig[difficulty] || this.difficultyConfig.medium;
        const filteredWords = words
            .filter(word => {
                const length = word.word.length;
                return length >= config.word_length[0] && length <= config.word_length[1];
            })
            .map(word => ({
                ...word,
                metadata: {
                    source: 'enhanced_topic_fallback',
                    topic: topic,
                    difficulty: difficulty
                }
            }))
            .sort(() => Math.random() - 0.5) // shuffle
            .slice(0, count);
    
        return filteredWords;
    }    

    getOriginalTopicFallbackWords(topic, difficulty, count) {
        const topicLower = topic.toLowerCase();
        let words = [];
    
        // Animals
        if (topicLower.includes('animal') || topicLower.includes('wildlife')) {
            words = [
                { word: 'ELEPHANT', hint: 'Large mammal with trunk and tusks' },
                { word: 'TIGER', hint: 'Striped big cat' },
                { word: 'PENGUIN', hint: 'Antarctic flightless bird' },
                { word: 'DOLPHIN', hint: 'Intelligent marine mammal' },
                { word: 'KANGAROO', hint: 'Australian hopping marsupial' },
                { word: 'GIRAFFE', hint: 'Tallest land animal' },
                { word: 'ZEBRA', hint: 'Striped horse-like animal' },
                { word: 'PANDA', hint: 'Black and white bear' },
                { word: 'LION', hint: 'King of the jungle' },
                { word: 'WHALE', hint: 'Largest marine animal' }
            ];
        }
        // Food & Cooking
        else if (topicLower.includes('food') || topicLower.includes('cooking') || topicLower.includes('kitchen')) {
            words = [
                { word: 'PIZZA', hint: 'Italian dish with cheese and toppings' },
                { word: 'PASTA', hint: 'Italian noodle dish' },
                { word: 'BREAD', hint: 'Baked staple food' },
                { word: 'CHEESE', hint: 'Dairy product' },
                { word: 'APPLE', hint: 'Red or green fruit' },
                { word: 'BANANA', hint: 'Yellow curved fruit' },
                { word: 'ORANGE', hint: 'Citrus fruit' },
                { word: 'TOMATO', hint: 'Red cooking ingredient' },
                { word: 'ONION', hint: 'Layered vegetable that makes you cry' },
                { word: 'GARLIC', hint: 'Pungent cooking ingredient' }
            ];
        }
        // Sports & Activities
        else if (topicLower.includes('sport') || topicLower.includes('game') || topicLower.includes('exercise')) {
            words = [
                { word: 'SOCCER', hint: 'World\'s most popular sport' },
                { word: 'TENNIS', hint: 'Racket sport played on a court' },
                { word: 'GOLF', hint: 'Sport played with clubs and balls' },
                { word: 'SWIMMING', hint: 'Water sport and exercise' },
                { word: 'RUNNING', hint: 'Basic form of exercise' },
                { word: 'CYCLING', hint: 'Sport using a bicycle' },
                { word: 'CHESS', hint: 'Strategic board game' },
                { word: 'BOXING', hint: 'Combat sport with gloves' },
                { word: 'YOGA', hint: 'Mind-body exercise practice' },
                { word: 'HIKING', hint: 'Walking in nature' }
            ];
        }
        // Science & Technology
        else if (topicLower.includes('science') || topicLower.includes('technology') || topicLower.includes('computer')) {
            words = [
                { word: 'COMPUTER', hint: 'Electronic processing device' },
                { word: 'INTERNET', hint: 'Global computer network' },
                { word: 'ROBOT', hint: 'Programmable machine' },
                { word: 'ATOM', hint: 'Basic unit of matter' },
                { word: 'ENERGY', hint: 'Capacity to do work' },
                { word: 'MOLECULE', hint: 'Group of bonded atoms' },
                { word: 'GRAVITY', hint: 'Force that pulls objects down' },
                { word: 'MAGNET', hint: 'Object that attracts metal' },
                { word: 'SOLAR', hint: 'Related to the sun' },
                { word: 'BATTERY', hint: 'Device that stores electrical energy' }
            ];
        }
    
        // Filter by difficulty and add metadata
        const filteredWords = this.filterWordsByDifficulty(words, difficulty)
            .map(word => ({
                ...word,
                metadata: {
                    source: 'original_topic_fallback',
                    topic: topic,
                    difficulty: difficulty
                }
            }));
    
        return filteredWords.slice(0, count);
    }

    getTopicWordsWithEnhancedMatching(topic, difficulty, count) {
        const topicData = this.getEnhancedTopicData(topic.toLowerCase());
        if (!topicData) {
            return [];
        }
        
        // Create words from topic data with priority weighting
        const allTopicWords = [
            ...topicData.primary.map(word => ({ word, priority: 3 })),        // Highest priority
            ...topicData.secondary.slice(0, 15).map(word => ({ word, priority: 2 })), // Medium priority  
            ...topicData.related.slice(0, 10).map(word => ({ word, priority: 1 }))     // Lower priority
        ];
        
        // Filter by difficulty and format
        const config = this.difficultyConfig[difficulty];
        const formattedWords = allTopicWords
            .filter(item => {
                const wordLength = item.word.length;
                return wordLength >= config.word_length[0] && 
                       wordLength <= config.word_length[1] &&
                       wordLength >= 3; // Minimum reasonable length
            })
            .sort((a, b) => {
                // Sort by priority first, then randomly
                if (a.priority !== b.priority) {
                    return b.priority - a.priority; // Higher priority first
                }
                return Math.random() - 0.5;
            })
            .slice(0, count * 2) // Get more than needed for better selection
            .map(item => ({
                word: item.word.toUpperCase(),
                hint: this.generateTopicHint(item.word, topic),
                metadata: {
                    source: 'enhanced_topic_database',
                    topic: topic,
                    difficulty: difficulty,
                    priority: item.priority,
                    relevanceScore: 1.0
                }
            }))
            .slice(0, count);
        
        return formattedWords;
    }

    /**
 * Generate contextual hints for topic words
 */
generateTopicHint(word, topic) {
    const wordLower = word.toLowerCase();
    const topicLower = topic.toLowerCase();
    
    // Topic-specific hint patterns
    const hintPatterns = {
        animals: {
            // Add specific hints for animals
            'tiger': 'Striped big cat',
            'elephant': 'Large mammal with trunk',
            'penguin': 'Antarctic flightless bird',
            'dolphin': 'Intelligent marine mammal',
            // ... add more as needed
        },
        food: {
            'pizza': 'Italian dish with cheese and toppings',
            'pasta': 'Italian noodle dish',
            'apple': 'Red or green fruit',
            // ... add more as needed
        },
        sports: {
            'soccer': 'World\'s most popular sport',
            'tennis': 'Racket sport on a court',
            'golf': 'Sport with clubs and small ball',
            // ... add more as needed
        },
        science: {
            'atom': 'Basic unit of matter',
            'energy': 'Capacity to do work',
            'gravity': 'Force that pulls objects down',
            // ... add more as needed
        }
    };
    
    // Check for specific hint
    if (hintPatterns[topicLower] && hintPatterns[topicLower][wordLower]) {
        return hintPatterns[topicLower][wordLower];
    }
    
    // Generate generic hint based on topic
    const topicHints = {
        animals: `A type of animal`,
        food: `Something you can eat`,
        sports: `Related to sports or exercise`,
        science: `Scientific term or concept`,
        nature: `Found in nature`
    };
    
    return topicHints[topicLower] || `Related to ${topic}`;
}

    // ===========================================
    // GENERIC FALLBACK WORDS
    // ===========================================

    /**
     * Get generic fallback words when all else fails
     */
    getGenericFallbackWords(difficulty, count) {
        const massiveGenericWords = [
            // Easy words (3-7 letters) - 40+ words
            { word: 'CAT', hint: 'Furry pet that meows', difficulty: 'easy' },
            { word: 'DOG', hint: 'Loyal four-legged friend', difficulty: 'easy' },
            { word: 'SUN', hint: 'Bright star in our sky', difficulty: 'easy' },
            { word: 'MOON', hint: 'Earth\'s natural satellite', difficulty: 'easy' },
            { word: 'STAR', hint: 'Bright point of light in night sky', difficulty: 'easy' },
            { word: 'BOOK', hint: 'Reading material with pages', difficulty: 'easy' },
            { word: 'TREE', hint: 'Tall plant with leaves', difficulty: 'easy' },
            { word: 'BIRD', hint: 'Flying animal with feathers', difficulty: 'easy' },
            { word: 'FISH', hint: 'Swimming animal with fins', difficulty: 'easy' },
            { word: 'HOUSE', hint: 'Place where people live', difficulty: 'easy' },
            { word: 'WATER', hint: 'Clear liquid we drink', difficulty: 'easy' },
            { word: 'FIRE', hint: 'Hot glowing combustion', difficulty: 'easy' },
            { word: 'CLOUD', hint: 'White fluffy formation in sky', difficulty: 'easy' },
            { word: 'GRASS', hint: 'Green ground covering plants', difficulty: 'easy' },
            { word: 'SMILE', hint: 'Happy facial expression', difficulty: 'easy' },
            { word: 'LIGHT', hint: 'Bright illumination', difficulty: 'easy' },
            { word: 'HEART', hint: 'Organ that pumps blood', difficulty: 'easy' },
            { word: 'HAND', hint: 'Body part with five fingers', difficulty: 'easy' },
            { word: 'EYES', hint: 'Body parts used for seeing', difficulty: 'easy' },
            { word: 'BREAD', hint: 'Baked food made from flour', difficulty: 'easy' },
            { word: 'APPLE', hint: 'Red or green fruit', difficulty: 'easy' },
            { word: 'CHAIR', hint: 'Furniture for sitting', difficulty: 'easy' },
            { word: 'TABLE', hint: 'Flat surface for dining', difficulty: 'easy' },
            { word: 'DOOR', hint: 'Entry to a room', difficulty: 'easy' },
            { word: 'WINDOW', hint: 'Glass opening in wall', difficulty: 'easy' },
            { word: 'FLOWER', hint: 'Colorful plant bloom', difficulty: 'easy' },
            { word: 'GARDEN', hint: 'Place to grow plants', difficulty: 'easy' },
            { word: 'ORANGE', hint: 'Citrus fruit', difficulty: 'easy' },
            { word: 'BANANA', hint: 'Yellow curved fruit', difficulty: 'easy' },
            { word: 'PURPLE', hint: 'Color made from red and blue', difficulty: 'easy' },
            { word: 'GREEN', hint: 'Color of grass', difficulty: 'easy' },
            { word: 'YELLOW', hint: 'Color of the sun', difficulty: 'easy' },
            { word: 'BLUE', hint: 'Color of the sky', difficulty: 'easy' },
            { word: 'RED', hint: 'Color of strawberries', difficulty: 'easy' },
            { word: 'BLACK', hint: 'Darkest color', difficulty: 'easy' },
            { word: 'WHITE', hint: 'Lightest color', difficulty: 'easy' },
            { word: 'HAPPY', hint: 'Feeling joyful', difficulty: 'easy' },
            { word: 'LOVE', hint: 'Strong affectionate feeling', difficulty: 'easy' },
            { word: 'PEACE', hint: 'State of harmony', difficulty: 'easy' },
            { word: 'DREAM', hint: 'Images seen while sleeping', difficulty: 'easy' },
            
            // Medium words (4-9 letters) - 40+ words
            { word: 'MUSIC', hint: 'Organized sounds and rhythms', difficulty: 'medium' },
            { word: 'FRIEND', hint: 'Close companion', difficulty: 'medium' },
            { word: 'FAMILY', hint: 'Related group of people', difficulty: 'medium' },
            { word: 'SCHOOL', hint: 'Place of learning', difficulty: 'medium' },
            { word: 'TEACHER', hint: 'Person who educates students', difficulty: 'medium' },
            { word: 'STUDENT', hint: 'Person who learns', difficulty: 'medium' },
            { word: 'KITCHEN', hint: 'Room for cooking food', difficulty: 'medium' },
            { word: 'BEDROOM', hint: 'Room for sleeping', difficulty: 'medium' },
            { word: 'RAINBOW', hint: 'Colorful arc after rain', difficulty: 'medium' },
            { word: 'MOUNTAIN', hint: 'High natural elevation', difficulty: 'medium' },
            { word: 'OCEAN', hint: 'Large body of salt water', difficulty: 'medium' },
            { word: 'PLANET', hint: 'Celestial body orbiting a star', difficulty: 'medium' },
            { word: 'CAMERA', hint: 'Device for taking pictures', difficulty: 'medium' },
            { word: 'BICYCLE', hint: 'Two-wheeled vehicle', difficulty: 'medium' },
            { word: 'COMPUTER', hint: 'Electronic processing device', difficulty: 'medium' },
            { word: 'SANDWICH', hint: 'Food between bread slices', difficulty: 'medium' },
            { word: 'ELEPHANT', hint: 'Large animal with trunk', difficulty: 'medium' },
            { word: 'GIRAFFE', hint: 'Tallest animal', difficulty: 'medium' },
            { word: 'PENGUIN', hint: 'Antarctic bird that swims', difficulty: 'medium' },
            { word: 'DOLPHIN', hint: 'Intelligent marine mammal', difficulty: 'medium' },
            { word: 'BUTTERFLY', hint: 'Colorful flying insect', difficulty: 'medium' },
            { word: 'PRINCESS', hint: 'Royal female', difficulty: 'medium' },
            { word: 'CASTLE', hint: 'Medieval fortress', difficulty: 'medium' },
            { word: 'TREASURE', hint: 'Valuable collection', difficulty: 'medium' },
            { word: 'ADVENTURE', hint: 'Exciting journey', difficulty: 'medium' },
            { word: 'MAGIC', hint: 'Supernatural power', difficulty: 'medium' },
            { word: 'DRAGON', hint: 'Mythical fire-breathing creature', difficulty: 'medium' },
            { word: 'ROCKET', hint: 'Space vehicle', difficulty: 'medium' },
            { word: 'ROBOT', hint: 'Mechanical assistant', difficulty: 'medium' },
            { word: 'SCIENCE', hint: 'Study of natural world', difficulty: 'medium' },
            { word: 'HISTORY', hint: 'Study of past events', difficulty: 'medium' },
            { word: 'LIBRARY', hint: 'Building full of books', difficulty: 'medium' },
            { word: 'HOSPITAL', hint: 'Place for medical care', difficulty: 'medium' },
            { word: 'AIRPORT', hint: 'Place where planes land', difficulty: 'medium' },
            { word: 'FOOTBALL', hint: 'Popular American sport', difficulty: 'medium' },
            { word: 'BASEBALL', hint: 'Sport with bats and balls', difficulty: 'medium' },
            { word: 'SWIMMING', hint: 'Water sport activity', difficulty: 'medium' },
            { word: 'PAINTING', hint: 'Art made with colors', difficulty: 'medium' },
            { word: 'DRAWING', hint: 'Art made with pencils', difficulty: 'medium' },
            { word: 'DANCING', hint: 'Moving to music', difficulty: 'medium' },
            { word: 'SINGING', hint: 'Making music with voice', difficulty: 'medium' },
            
            // Hard words (5-15 letters) - 30+ words  
            { word: 'MYSTERY', hint: 'Something unknown or unexplained', difficulty: 'hard' },
            { word: 'JOURNEY', hint: 'Long trip or voyage', difficulty: 'hard' },
            { word: 'WISDOM', hint: 'Deep knowledge and understanding', difficulty: 'hard' },
            { word: 'COURAGE', hint: 'Bravery in face of danger', difficulty: 'hard' },
            { word: 'FREEDOM', hint: 'State of being free', difficulty: 'hard' },
            { word: 'HARMONY', hint: 'Pleasant agreement or accord', difficulty: 'hard' },
            { word: 'VICTORY', hint: 'Success in struggle or contest', difficulty: 'hard' },
            { word: 'DISCOVERY', hint: 'Finding something new', difficulty: 'hard' },
            { word: 'CHALLENGE', hint: 'Difficult but stimulating task', difficulty: 'hard' },
            { word: 'IMAGINATION', hint: 'Ability to form mental images', difficulty: 'hard' },
            { word: 'CELEBRATION', hint: 'Joyful recognition of event', difficulty: 'hard' },
            { word: 'KNOWLEDGE', hint: 'Information and understanding', difficulty: 'hard' },
            { word: 'BEAUTIFUL', hint: 'Pleasing to look at', difficulty: 'hard' },
            { word: 'WONDERFUL', hint: 'Inspiring delight or admiration', difficulty: 'hard' },
            { word: 'BRILLIANT', hint: 'Exceptionally bright or talented', difficulty: 'hard' },
            { word: 'FANTASTIC', hint: 'Extraordinarily good', difficulty: 'hard' },
            { word: 'INCREDIBLE', hint: 'Impossible to believe', difficulty: 'hard' },
            { word: 'MARVELOUS', hint: 'Causing wonder or astonishment', difficulty: 'hard' },
            { word: 'SPECTACULAR', hint: 'Beautiful in dramatic way', difficulty: 'hard' },
            { word: 'MAGNIFICENT', hint: 'Impressively beautiful', difficulty: 'hard' },
            { word: 'EXTRAORDINARY', hint: 'Very unusual or remarkable', difficulty: 'hard' },
            { word: 'PERSONALITY', hint: 'Individual character traits', difficulty: 'hard' },
            { word: 'RELATIONSHIP', hint: 'Connection between people', difficulty: 'hard' },
            { word: 'ENVIRONMENT', hint: 'Natural world around us', difficulty: 'hard' },
            { word: 'TEMPERATURE', hint: 'How hot or cold something is', difficulty: 'hard' },
            { word: 'ELECTRICITY', hint: 'Form of energy with charges', difficulty: 'hard' },
            { word: 'MATHEMATICS', hint: 'Study of numbers and shapes', difficulty: 'hard' },
            { word: 'GEOGRAPHY', hint: 'Study of Earth and places', difficulty: 'hard' },
            { word: 'PSYCHOLOGY', hint: 'Study of mind and behavior', difficulty: 'hard' },
            { word: 'PHILOSOPHY', hint: 'Study of fundamental questions', difficulty: 'hard' }
        ];
    
        // Filter by difficulty level with more inclusive logic
        const config = this.difficultyConfig[difficulty];
        const availableWords = massiveGenericWords.filter(word => {
            const wordLength = word.word.length;
            const isValidLength = wordLength >= config.word_length[0] && wordLength <= config.word_length[1];
            
            // More inclusive difficulty matching
            let isValidDifficulty = false;
            if (difficulty === 'easy') {
                isValidDifficulty = word.difficulty === 'easy';
            } else if (difficulty === 'medium') {
                isValidDifficulty = ['easy', 'medium'].includes(word.difficulty);
            } else { // hard
                isValidDifficulty = true; // Include all difficulties for hard
            }
            
            return isValidLength && isValidDifficulty;
        });
    
        // Add metadata and shuffle
        const formattedWords = availableWords.map(word => ({
            ...word,
            metadata: {
                source: 'generic_fallback_expanded',
                difficulty: difficulty,
                pool_size: availableWords.length
            }
        }));
    
        const shuffled = formattedWords.sort(() => Math.random() - 0.5);
        this.debugLog(`🎯 Generic fallback pool: ${availableWords.length} words available for ${difficulty}`);
        
        return shuffled.slice(0, count);
    }

    /**
     * Filter words by difficulty criteria
     */
    filterWordsByDifficulty(words, difficulty) {
        const config = this.difficultyConfig[difficulty];
        
        return words.filter(word => {
            const length = word.word.length;
            return length >= config.word_length[0] && length <= config.word_length[1];
        });
    }

    // ===========================================
    // AI WORD GENERATION (LAST RESORT)
    // ===========================================

    /**
     * Generate words using AI as last resort
     */
    async getAIWords(topic, difficulty, count) {
        const config = this.difficultyConfig[difficulty];
        
        const prompt = topic ? 
            this.createTopicAIPrompt(topic, difficulty, count) :
            this.createGeneralAIPrompt(difficulty, count);
    
        try {
            const response = await callAI(prompt, null, 2, {
                category: USAGE_CATEGORIES.WORD_GENERATION,
                puzzleType: 'unified_service',
                difficulty: difficulty,
                topic: topic || 'general'
            });
    
            // The response should now be properly cleaned JSON
            let parsed;
            try {
                parsed = JSON.parse(response);
            } catch (parseError) {
                this.debugLog(`JSON parse error: ${parseError.message}`, 'error');
                this.debugLog(`Response was: "${response.substring(0, 200)}"`, 'error');
                throw new Error('Invalid AI response format');
            }
            
            if (!parsed.words || !Array.isArray(parsed.words)) {
                throw new Error('Invalid AI response format');
            }
    
            // Validate and format words
            const validWords = parsed.words
                .filter(w => w.word && w.hint && 
                           w.word.length >= config.word_length[0] && 
                           w.word.length <= config.word_length[1])
                .map(w => ({
                    word: w.word.toUpperCase().replace(/[^A-Z]/g, ''),
                    hint: w.hint.trim(),
                    metadata: {
                        source: 'ai_generation',
                        topic: topic,
                        difficulty: difficulty
                    }
                }))
                .filter((w, index, self) => 
                    w.word.length >= this.thresholds.minWordLength && 
                    self.findIndex(other => other.word === w.word) === index
                );
    
            this.debugLog(`AI generated ${validWords.length} valid words`);
            return validWords.slice(0, count);
    
        } catch (error) {
            this.debugLog(`AI word generation failed: ${error.message}`, 'error');
            throw error;
        }
    }

    /**
     * Create AI prompt for topic-specific words
     */
    createTopicAIPrompt(topic, difficulty, count) {
        const config = this.difficultyConfig[difficulty];
        
        return `Generate exactly ${count} words for a word puzzle about ${topic}.
    
    Requirements:
    - Word length: ${config.word_length[0]} to ${config.word_length[1]} letters
    - All words MUST be related to ${topic}
    - Simple, common words only
    - Include a short hint for each word
    
    Examples for ${topic}:
    ${this.getTopicExamples(topic)}
    
    Return ONLY this JSON format:
    {
      "words": [
        {"word": "TIGER", "hint": "Large striped cat"},
        {"word": "ELEPHANT", "hint": "Animal with trunk"}
      ]
    }
    
    Generate ${count} words about ${topic}:`;
    }
    
    createGeneralAIPrompt(difficulty, count) {
        const config = this.difficultyConfig[difficulty];
        
        return `Generate exactly ${count} words for a ${difficulty} difficulty word puzzle.
    
    Requirements:
    - Word length: ${config.word_length[0]} to ${config.word_length[1]} letters
    - Mix of topics: animals, food, objects, nature
    - Simple, common words only
    - Include a short hint for each word
    
    Return ONLY this JSON format:
    {
      "words": [
        {"word": "HOUSE", "hint": "Place where people live"},
        {"word": "WATER", "hint": "Clear liquid we drink"}
      ]
    }
    
    Generate ${count} words:`;
    }
    
    // Fix 5: Helper method for topic examples
    getTopicExamples(topic) {
        const examples = {
            animals: 'CAT, DOG, BIRD, FISH, TIGER, LION',
            food: 'PIZZA, BREAD, APPLE, CHEESE, PASTA',
            sports: 'SOCCER, TENNIS, GOLF, RUNNING',
            science: 'ATOM, ENERGY, GRAVITY, MOLECULE'
        };
        
        return examples[topic.toLowerCase()] || 'EXAMPLE, SAMPLE, WORD';
    }
    

    // ===========================================
    // UTILITY METHODS
    // ===========================================

    /**
     * Debug logging
     */
    debugLog(message, type = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString().split('T')[1].split('.')[0];
            const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '🔍';
            console.log(`${emoji} [${timestamp}] UnifiedWordService: ${message}`);
        }
    }

    /**
     * Set debug mode
     */
    setDebugMode(enabled) {
        this.debugMode = enabled;
        this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'}`);
    }

    /**
     * Health check
     */
    async healthCheck() {
        try {
            // Test database connection
            const { data, error } = await supabase
                .from('common_words')
                .select('word')
                .limit(1);

            if (error) {
                throw new Error(`Database error: ${error.message}`);
            }

            return {
                status: 'healthy',
                database: 'connected',
                sessionId: this.sessionId,
                wordsInSession: this.usedWordsInSession.size,
                timestamp: new Date().toISOString()
            };

        } catch (error) {
            return {
                status: 'unhealthy',
                error: error.message,
                sessionId: this.sessionId,
                timestamp: new Date().toISOString()
            };
        }
    }
}

// Export singleton instance
export const unifiedWordService = new UnifiedWordService();

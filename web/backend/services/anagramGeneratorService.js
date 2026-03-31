import { supabase } from '../config/database.js';

export class AnagramGenerator {
    constructor() {
        this.debugMode = false;
        this.sessionWords = new Set();
        this.wordPool = new Map(); // Cache words by difficulty
        this.initialized = false;
        
        this.strategies = {
            easy: ['reverse', 'shuffle'],
            medium: ['shuffle', 'vowelShift', 'patternBreak'],
            hard: ['multipass', 'deceptive', 'complex']
        };
    }

    /**
     * Main generation method - matches existing anagram service interface
     */
    async generateAnagramPuzzle(difficulty = 'medium', topic = null, count = 1) {
        this.log(`Generating ${count} anagram(s) for ${difficulty} difficulty`);
        
        try {
            await this.ensureWordPool(difficulty);
            
            const words = await this.getWordsForGeneration(difficulty, count);
            
            if (words.length === 0) {
                return {
                    success: false,
                    error: 'No words available for anagram generation',
                    anagrams: []
                };
            }

            const anagrams = [];
            
            for (const wordData of words.slice(0, count)) {
                const scrambleResult = this.scrambleWord(wordData.word, difficulty);
                
                if (scrambleResult.success) {
                    anagrams.push({
                        originalWord: wordData.word,
                        scrambledWord: scrambleResult.scrambled,
                        hint: wordData.hint,
                        difficulty: difficulty,
                        topic: topic,
                        strategy: scrambleResult.strategy,
                        metadata: {
                            wordLength: wordData.word.length,
                            generatedAt: new Date().toISOString(),
                            sessionId: `db_${Date.now()}`
                        }
                    });
                    
                    this.sessionWords.add(wordData.word);
                }
            }
            
            this.log(`Generated ${anagrams.length} valid anagrams`);
            
            return {
                success: true,
                anagrams: anagrams,
                stats: {
                    requested: count,
                    generated: anagrams.length,
                    wordsConsidered: words.length,
                    existingAnagramsSkipped: 0, // Database doesn't track this way
                    wordServiceStats: { source: 'common_words_db' }
                }
            };
            
        } catch (error) {
            this.log(`Anagram generation failed: ${error.message}`, 'error');
            return {
                success: false,
                error: error.message,
                anagrams: []
            };
        }
    }

    /**
     * Ensure word pool is loaded for difficulty
     */
    async ensureWordPool(difficulty) {
        if (this.wordPool.has(difficulty)) return;

        this.log(`Loading word pool for ${difficulty}...`);

        const lengthConfig = {
            easy: { min: 4, max: 6 },      // Increased min from 3 to 4 to avoid tiny words
            medium: { min: 5, max: 9 },    // Expanded range
            hard: { min: 7, max: 12 }
        };

        const config = lengthConfig[difficulty] || lengthConfig.medium;

        // Fetch words from BOTH common_words and word_frequency for larger pool
        const { data: commonWords, error: commonError } = await supabase
            .from('common_words')
            .select('word, definition')
            .eq('has_definition', true);

        if (commonError) {
            throw new Error(`Failed to load common_words: ${commonError.message}`);
        }

        // Also fetch from word_frequency for additional variety
        const { data: freqWords, error: freqError } = await supabase
            .from('word_frequency')
            .select('word')
            .gte('frequency', 10)  // Only reasonably common words
            .limit(10000);

        // Combine both sources
        const allWordsMap = new Map();

        // Add common_words first (they have definitions)
        (commonWords || []).forEach(w => {
            if (w.word) {
                allWordsMap.set(w.word.toUpperCase(), {
                    word: w.word,
                    definition: w.definition
                });
            }
        });

        // Add word_frequency words that aren't already in common_words
        (freqWords || []).forEach(w => {
            if (w.word && !allWordsMap.has(w.word.toUpperCase())) {
                allWordsMap.set(w.word.toUpperCase(), {
                    word: w.word,
                    definition: null
                });
            }
        });

        this.log(`Combined word sources: ${allWordsMap.size} total words`);

        // Load existing anagram answers to exclude already-used words
        // Limit to last 50000 to avoid memory issues but still catch recent duplicates
        const { data: existingAnagrams } = await supabase
            .from('puzzles')
            .select('answer')
            .ilike('type', '%anagram%')
            .not('answer', 'is', null)
            .order('timestamp', { ascending: false })
            .limit(50000);

        const usedWords = new Set(
            (existingAnagrams || []).map(p => p.answer?.toUpperCase?.() || '')
        );
        this.log(`Excluding ${usedWords.size} already-used anagram words`);

        // Filter by length, validity, and exclude already-used words
        const validWords = Array.from(allWordsMap.values())
            .filter(w => {
                if (!w.word) return false;
                const len = w.word.length;
                return len >= config.min && len <= config.max;
            })
            .filter(w => this.isValidAnagramWord(w.word))
            .filter(w => !usedWords.has(w.word.toUpperCase())) // Exclude used words
            .map(w => ({
                word: w.word.toUpperCase(),
                hint: w.definition && w.definition.trim() ? w.definition.trim() : `${w.word.length}-letter word`,
                length: w.word.length
            }));

        // Shuffle for randomness
        this.shuffleArray(validWords);

        this.wordPool.set(difficulty, validWords);
        this.log(`Loaded ${validWords.length} unique unused words for ${difficulty} difficulty`);

        // If pool is very small, log a warning
        if (validWords.length < 100) {
            console.warn(`[AnagramGenerator] WARNING: Only ${validWords.length} unused words available for ${difficulty}. May need to reset used words in database.`);
        }
    }

    /**
     * Get words for generation, cycling through available pool
     */
    async getWordsForGeneration(difficulty, count) {
        const pool = this.wordPool.get(difficulty);
        if (!pool) return [];
        
        // Get unused words from pool
        const unusedWords = pool.filter(w => !this.sessionWords.has(w.word));
        
        if (unusedWords.length < count) {
            this.log(`Warning: Only ${unusedWords.length} unused words available for ${difficulty}`);
            
            // If running low, reset session to allow reuse
            if (unusedWords.length === 0) {
                this.log('Resetting session to reuse words');
                this.sessionWords.clear();
                return pool.slice(0, count);
            }
        }
        
        return unusedWords.slice(0, count);
    }

    /**
     * Scramble word using difficulty-appropriate strategy
     */
    scrambleWord(word, difficulty) {
        const availableStrategies = this.strategies[difficulty] || this.strategies.medium;
        const strategy = availableStrategies[Math.floor(Math.random() * availableStrategies.length)];
        
        let scrambled = this.applyStrategy(word, strategy);
        let attempts = 1;
        
        // Ensure word is scrambled
        while (scrambled === word && attempts < 3) {
            const fallback = availableStrategies[attempts % availableStrategies.length];
            scrambled = this.applyStrategy(word, fallback);
            attempts++;
        }
        
        return {
            success: scrambled !== word,
            scrambled: scrambled,
            strategy: strategy,
            attempts: attempts
        };
    }

    /**
     * Apply scrambling strategy
     */
    applyStrategy(word, strategy) {
        const letters = word.split('');
        
        switch (strategy) {
            case 'reverse':
                return letters.reverse().join('');
                
            case 'shuffle':
                return this.shuffle(letters).join('');
                
            case 'vowelShift':
                return this.shiftVowels(word);
                
            case 'patternBreak':
                return this.breakPatterns(word);
                
            case 'multipass':
                return this.multiShuffle(letters, 3);
                
            case 'deceptive':
                return this.deceptivePattern(letters);
                
            case 'complex':
                return this.complexShuffle(letters);
                
            default:
                return this.shuffle(letters).join('');
        }
    }

    // Scrambling algorithms
    shuffle(array) {
        const arr = [...array];
        for (let i = arr.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [arr[i], arr[j]] = [arr[j], arr[i]];
        }
        return arr;
    }

    shiftVowels(word) {
        const vowels = 'AEIOU';
        const letters = word.split('');
        const vowelPos = [];
        const vowelChars = [];
        
        letters.forEach((char, i) => {
            if (vowels.includes(char)) {
                vowelPos.push(i);
                vowelChars.push(char);
            }
        });
        
        if (vowelChars.length > 1) {
            const shifted = [vowelChars.pop(), ...vowelChars];
            vowelPos.forEach((pos, i) => {
                letters[pos] = shifted[i];
            });
        }
        
        return letters.join('');
    }

    breakPatterns(word) {
        const letters = word.split('');
        const patterns = ['TH', 'ER', 'AN', 'IN', 'ED'];
        
        for (const pattern of patterns) {
            const idx = word.indexOf(pattern);
            if (idx !== -1 && idx < letters.length - 1) {
                [letters[idx], letters[idx + 1]] = [letters[idx + 1], letters[idx]];
                break;
            }
        }
        
        return this.shuffle(letters).join('');
    }

    multiShuffle(letters, passes) {
        let result = [...letters];
        for (let i = 0; i < passes; i++) {
            result = this.shuffle(result);
        }
        return result.join('');
    }

    deceptivePattern(letters) {
        const vowels = letters.filter(c => 'AEIOU'.includes(c));
        const consonants = letters.filter(c => !'AEIOU'.includes(c));
        
        const result = [];
        let vi = 0, ci = 0;
        
        for (let i = 0; i < letters.length; i++) {
            if (i % 2 === 0 && ci < consonants.length) {
                result.push(consonants[ci++]);
            } else if (vi < vowels.length) {
                result.push(vowels[vi++]);
            } else {
                result.push(consonants[ci++] || vowels[vi++]);
            }
        }
        
        return result.join('');
    }

    complexShuffle(letters) {
        const mid = Math.floor(letters.length / 2);
        const first = this.shuffle(letters.slice(0, mid));
        const second = this.shuffle(letters.slice(mid));
        return [...first, ...second].join('');
    }

    /**
     * Validate word for anagram use
     */
    isValidAnagramWord(word) {
        if (!word || typeof word !== 'string') return false;
        if (word.length < 3) return false;
        if (!/^[A-Za-z]+$/.test(word)) return false;
        if (!/[AEIOUaeiou]/.test(word)) return false;
        
        const letterCounts = {};
        for (const letter of word.toLowerCase()) {
            letterCounts[letter] = (letterCounts[letter] || 0) + 1;
            if (letterCounts[letter] > Math.ceil(word.length / 2)) {
                return false;
            }
        }
        
        return true;
    }

    shuffleArray(array) {
        for (let i = array.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [array[i], array[j]] = [array[j], array[i]];
        }
    }

    log(message, type = 'info') {
        if (!this.debugMode) return;
        const timestamp = new Date().toISOString().split('T')[1].split('.')[0];
        console.log(`[${timestamp}] DropInAnagram: ${message}`);
    }

    setDebugMode(enabled) {
        this.debugMode = enabled;
    }
}
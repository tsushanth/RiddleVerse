// services/WordFrequencyManager.js
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class WordFrequencyManager {
    constructor() {
        this.debugMode = true; // Enable debug logging
        this.isLoaded = true; // Always ready since we use database
        this.loadPromise = null;
        
        console.log('📚 WordFrequencyManager initialized (database mode)');
      }

      async load() {
        // ✅ No longer needed - database is always ready
        this.isLoaded = true;
        console.log('✅ WordFrequencyManager ready (database mode)');
        return Promise.resolve();
      }
      
      async _loadData() {
        // ✅ No longer needed - using database
        this.isLoaded = true;
        return Promise.resolve();
      }

    _loadWordsForRange(minFrequency, minLength, maxLength, limit = 100) {
        if (!fs.existsSync(this.frequencyPath)) {
            return [];
        }
    
        const words = [];
        const content = fs.readFileSync(this.frequencyPath, 'utf-8');
        const lines = content.trim().split('\n');
    
        for (const line of lines) {
            const [word, freq] = line.split('\t');
            if (word && freq) {
                const frequency = parseInt(freq);
                const cleanWord = word.toLowerCase().trim();
                
                // Check if word meets criteria
                if (this._isValidWord(cleanWord) && 
                    frequency >= minFrequency &&
                    cleanWord.length >= minLength && 
                    cleanWord.length <= maxLength &&
                    this.isValidAnagramWord(cleanWord)) {
                    
                    words.push({
                        word: cleanWord,
                        frequency: frequency,
                        length: cleanWord.length
                    });
                    
                    // Cache this word for future use
                    this.words.set(cleanWord, frequency);
                    
                    if (words.length >= limit) break;
                }
            }
        }
    
        this.shuffleArray(words);
        return words;
    }

    _isValidWord(word) {
        // Filter criteria for puzzle-worthy words
        return word.length >= 3 && 
               word.length <= 15 && 
               /^[a-z]+$/.test(word) && // Only letters
               !word.includes('\''); // No contractions
    }

    _indexByLength(word) {
        const length = word.length;
        if (!this.wordsByLength.has(length)) {
            this.wordsByLength.set(length, []);
        }
        this.wordsByLength.get(length).push(word);
    }

    _indexByFrequency(word, frequency) {
        // Create frequency tiers for difficulty levels
        let tier;
        if (frequency > 10000) tier = 'very_common';
        else if (frequency > 1000) tier = 'common';
        else if (frequency > 100) tier = 'moderate';
        else if (frequency > 10) tier = 'uncommon';
        else tier = 'rare';

        if (!this.wordsByFrequency.has(tier)) {
            this.wordsByFrequency.set(tier, []);
        }
        this.wordsByFrequency.get(tier).push(word);
    }

    _estimateMemoryUsage() {
        // Rough estimation
        const avgWordLength = 6;
        const avgFrequency = 4; // bytes for number
        const mapOverhead = 50; // rough overhead per entry
        
        const totalBytes = this.words.size * (avgWordLength + avgFrequency + mapOverhead);
        return Math.round(totalBytes / 1024 / 1024); // Convert to MB
    }

    getMemoryEstimate() {
        if (!this.words) {  // ✅ Changed from this.words
            return 'No data loaded';
        }
        
        const wordCount = this.words.size;  // ✅ Changed from this.words
        const estimatedBytes = wordCount * 75;
        
        if (estimatedBytes > 1024 * 1024) {
            return `~${Math.round(estimatedBytes / (1024 * 1024))}MB`;
        } else if (estimatedBytes > 1024) {
            return `~${Math.round(estimatedBytes / 1024)}KB`;
        } else {
            return `~${estimatedBytes}B`;
        }
    }

    // === ANAGRAM METHODS ===
    getWordsForAnagrams(difficulty = 'Medium', count = 50) {
        if (!this.isLoaded) {
            throw new Error('WordFrequencyManager not loaded yet');
        }

        const tierMap = {
            'Easy': 'very_common',
            'Medium': 'common',
            'Hard': 'moderate',
            'Expert': 'uncommon'
        };

        const tier = tierMap[difficulty] || 'common';
        const words = this.wordsByFrequency.get(tier) || [];
        
        // Filter for good anagram words (4-8 letters)
        const anagramWords = words.filter(word => 
            word.length >= 4 && 
            word.length <= 8 &&
            this._hasInterestingLetters(word)
        );

        return this.shuffleArray(anagramWords).slice(0, count);
    }

    _hasInterestingLetters(word) {
        // Avoid words with too many repeated letters
        const letterCount = {};
        for (const letter of word) {
            letterCount[letter] = (letterCount[letter] || 0) + 1;
        }
        
        const maxRepeats = Math.max(...Object.values(letterCount));
        return maxRepeats <= Math.ceil(word.length / 3); // Max 1/3 repeated letters
    }

    // === SYNONYM/ANTONYM METHODS ===
    getWordPairsForSynonyms(difficulty = 'Medium', count = 10) {
        // For now, return common words that we can pair with external synonym API
        const tierMap = {
            'Easy': 'very_common',
            'Medium': 'common',
            'Hard': 'moderate',
            'Expert': 'uncommon'
        };

        const tier = tierMap[difficulty] || 'common';
        const words = this.wordsByFrequency.get(tier) || [];
        
        // Filter for good synonym words (adjectives, verbs, nouns)
        const baseWords = words.filter(word => 
            word.length >= 3 && 
            word.length <= 10
        );

        return this.shuffleArray(baseWords).slice(0, count);
    }

    // === WORD ASSOCIATION METHODS ===
    getWordsForAssociation(category, difficulty = 'Medium', count = 20) {
        // Return words filtered by category and difficulty
        const tierMap = {
            'Easy': ['very_common', 'common'],
            'Medium': ['common', 'moderate'],
            'Hard': ['moderate', 'uncommon'],
            'Expert': ['uncommon', 'rare']
        };

        const tiers = tierMap[difficulty] || ['common'];
        let allWords = [];
        
        for (const tier of tiers) {
            const tierWords = this.wordsByFrequency.get(tier) || [];
            allWords = allWords.concat(tierWords);
        }

        // For now, return random words. In future, add category filtering
        return this.shuffleArray(allWords).slice(0, count);
    }

    // === UTILITY METHODS ===
    getWordFrequency(word) {
        return this.words.get(word.toLowerCase()) || 0;
    }

    getWordsByLength(length, difficulty = 'Medium', count = 100) {
        const lengthWords = this.wordsByLength.get(length) || [];
        
        const tierMap = {
            'Easy': 'very_common',
            'Medium': 'common',
            'Hard': 'moderate',
            'Expert': 'uncommon'
        };

        const tier = tierMap[difficulty] || 'common';
        const tierWords = new Set(this.wordsByFrequency.get(tier) || []);
        
        const filteredWords = lengthWords.filter(word => tierWords.has(word));
        return this.shuffleArray(filteredWords).slice(0, count);
    }

    async getRandomWords(count = 10, difficulty = 'Medium', minLength = 3, maxLength = 12) {
        try {
          const { supabase } = await import('../config/database.js');
          
          const frequencyMap = {
            'Easy': 10000,
            'Medium': 1000,
            'Hard': 100,
            'Expert': 10
          };
          
          const minFrequency = frequencyMap[difficulty] || 1000;
          
          const { data: words, error } = await supabase
            .from('word_frequency')
            .select('word, frequency, length')
            .gte('frequency', minFrequency)
            .gte('length', minLength)
            .lte('length', maxLength)
            .limit(count * 3);
          
          if (error) {
            console.error('❌ Database error in getRandomWords:', error);
            return [];
          }
          
          if (!words || words.length === 0) return [];
          
          const shuffled = words.sort(() => Math.random() - 0.5).slice(0, count);
          return shuffled.map(w => w.word);
          
        } catch (error) {
          console.error('❌ Database connection error in getRandomWords:', error);
          return [];
        }
       }

    // === STATS AND DEBUGGING ===
    async getStats() {
        try {
          const { supabase } = await import('../config/database.js');
          
          // Get total count
          const { count: totalWords, error: countError } = await supabase
            .from('word_frequency')
            .select('*', { count: 'exact', head: true });
          
          if (countError) {
            return {
              isLoaded: false,
              error: `Database error: ${countError.message}`,
              totalWords: 0
            };
          }
          
          // Get frequency distribution
          const { data: freqData, error: freqError } = await supabase
            .from('word_frequency')
            .select('frequency');
          
          let frequencyRanges = {
            veryCommon: 0,
            common: 0,
            moderate: 0,
            uncommon: 0
          };
          
          if (!freqError && freqData) {
            for (const { frequency } of freqData) {
              if (frequency > 10000) frequencyRanges.veryCommon++;
              else if (frequency > 1000) frequencyRanges.common++;
              else if (frequency > 100) frequencyRanges.moderate++;
              else frequencyRanges.uncommon++;
            }
          }
          
          return {
            isLoaded: true,
            totalWords: totalWords || 0,
            memoryEstimate: '0MB (database mode)',
            loadTime: '0ms (database mode)',
            sourceFile: 'PostgreSQL database',
            anagramCapable: true,
            anagramSuitableWords: totalWords || 0,
            anagramPercentage: 100,
            frequencyRanges,
            capabilities: {
              canGenerateAnagrams: (totalWords || 0) > 1000,
              estimatedCapacity: totalWords || 0,
              qualityLevel: (totalWords || 0) > 100000 ? 'excellent' : 
                           (totalWords || 0) > 50000 ? 'good' : 
                           (totalWords || 0) > 10000 ? 'fair' : 'limited'
            }
          };
          
        } catch (error) {
          return {
            isLoaded: false,
            error: `Database connection error: ${error.message}`,
            totalWords: 0
          };
        }
      }

    /**
     * Check if word is suitable for anagram generation
     */
    isValidAnagramWord(word) {
        if (!word || typeof word !== 'string') return false;
        
        // Length constraints
        if (word.length < 3 || word.length > 15) return false;
        
        // Only alphabetic characters
        if (!/^[a-z]+$/i.test(word)) return false;
        
        // No excessive repeated letters (like "aaa" or "ssss")
        if (/(.)\1{2,}/.test(word)) return false;
        
        // Filter out problematic words
        const excludePatterns = [
            /^[aeiou]$/i,                    // Single vowels
            /^[bcdfghjklmnpqrstvwxyz]$/i,    // Single consonants
            /^(www|http|com|org|net)$/i,     // Web-related terms
            /^(mr|mrs|dr|jr|sr)$/i,          // Titles
            /^(st|nd|rd|th)$/i,              // Ordinal suffixes
            /^(am|pm)$/i,                    // Time indicators
            /^(ok|um|uh|ah|oh)$/i,           // Interjections
        ];
        
        return !excludePatterns.some(pattern => pattern.test(word));
    }

    findWordsContaining(letters, difficulty = 'Medium', count = 20) {
        const tierMap = {
            'Easy': 'very_common',
            'Medium': 'common',
            'Hard': 'moderate',
            'Expert': 'uncommon'
        };

        const tier = tierMap[difficulty] || 'common';
        const words = this.wordsByFrequency.get(tier) || [];
        
        const matchingWords = words.filter(word => {
            return letters.every(letter => word.includes(letter));
        });

        return this.shuffleArray(matchingWords).slice(0, count);
    }
    
    
    /**
     * Utility method to shuffle array (Fisher-Yates)
     */
    shuffleArray(array) {
        for (let i = array.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [array[i], array[j]] = [array[j], array[i]];
        }
        return array;
    }
    
    async getWordsForDifficulty(minFrequency, minLength, maxLength, limit = 100) {
        this.debugLog(`🔍 Getting words from database: freq>=${minFrequency}, length ${minLength}-${maxLength}, limit=${limit}`);
        
        try {
          const { supabase } = await import('../config/database.js');
          
          const { data: words, error } = await supabase
            .from('word_frequency')
            .select('word, frequency, length')
            .gte('frequency', minFrequency)
            .gte('length', minLength)
            .lte('length', maxLength)
            .limit(limit * 3);
          
          if (error) {
            console.error('❌ Database error in getWordsForDifficulty:', error);
            return [];
          }
          
          if (!words || words.length === 0) {
            console.warn(`⚠️ No words found matching criteria: freq>=${minFrequency}, length ${minLength}-${maxLength}`);
            return [];
          }
          
          const shuffled = words.sort(() => Math.random() - 0.5).slice(0, limit);
          console.log(`✅ Retrieved ${shuffled.length} words from database`);
          return shuffled; // Return full objects with word, frequency, length
          
        } catch (error) {
          console.error('❌ Database connection error in getWordsForDifficulty:', error);
          return [];
        }
       }

    /**
     * Get sample words for testing
     */
    getSampleWords(difficulty = 'Medium', count = 5) {
        const thresholds = {
            Easy: { minFreq: 10000, lengthRange: [3, 5] },
            Medium: { minFreq: 1000, lengthRange: [4, 8] },
            Hard: { minFreq: 100, lengthRange: [6, 10] },
            Expert: { minFreq: 10, lengthRange: [8, 15] }
        };
    
        const threshold = thresholds[difficulty];
        if (!threshold) {
            return { error: `Invalid difficulty: ${difficulty}` };
        }
    
        const words = this.getWordsForDifficulty(
            threshold.minFreq, 
            threshold.lengthRange[0], 
            threshold.lengthRange[1], 
            count * 2
        );
    
        // Add some randomization
        this.shuffleArray(words);
    
        return {
            difficulty,
            sampleWords: words.slice(0, count).map(w => ({
                word: w.word,
                frequency: w.frequency,
                length: w.length,
                commonality: w.frequency > 10000 ? 'very common' :
                            w.frequency > 1000 ? 'common' :
                            w.frequency > 100 ? 'uncommon' : 'rare'
            })),
            totalAvailable: words.length
        };
    }
    
    /**
     * Validate word frequency data integrity
     */
    validateIntegrity() {
        if (!this.isLoaded) {
            return { valid: false, error: 'Data not loaded' };
        }
    
        const issues = [];
        let validWords = 0;
        let invalidWords = 0;
    
        // Sample validation of first 1000 words
        let checked = 0;
        for (const [word, frequency] of this.words.entries()) {
            checked++;
            
            if (typeof word !== 'string' || word.length === 0) {
                issues.push(`Invalid word format: ${word}`);
                invalidWords++;
            } else if (typeof frequency !== 'number' || frequency < 0) {
                issues.push(`Invalid frequency for "${word}": ${frequency}`);
                invalidWords++;
            } else {
                validWords++;
            }
    
            if (checked >= 1000) break; // Sample check only
        }
    
        return {
            valid: issues.length === 0,
            checked,
            validWords,
            invalidWords,
            issues: issues.slice(0, 10), // Limit to first 10 issues
            integrity: validWords / (validWords + invalidWords)
        };
    }
}

// Global singleton instance
export const wordFrequencyManager = new WordFrequencyManager();

// Auto-load during module import (but don't block)
setTimeout(() => {
    wordFrequencyManager.load().catch(error => {
        console.error('❌ Failed to auto-load WordFrequencyManager:', error);
    });
}, 100); // Small delay to not block server startup

export default wordFrequencyManager;
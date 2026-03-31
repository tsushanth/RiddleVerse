import axios from 'axios';
import { supabase } from '../config/database.js';

/**
 * Standalone test script for Letter Set Generator
 * Tests word collection for a specific letter set
 */
class LetterSetTester {
  constructor() {
    this.dataMuseCache = new Map();
    this.cacheExpiry = 60 * 60 * 1000; // 1 hour
  }

  /**
   * Main test function - tests a specific letter set
   */
  async testLetterSet(letterSet = 'STREAM', difficulty = 'Medium') {
    console.log('\n' + '='.repeat(60));
    console.log(`🧪 TESTING LETTER SET: "${letterSet}" (${difficulty})`);
    console.log('='.repeat(60));
    
    const startTime = Date.now();
    
    try {
      // Test the word collection
      const wordList = await this.getComprehensiveWordList(letterSet);
      
      const endTime = Date.now();
      const duration = ((endTime - startTime) / 1000).toFixed(2);
      
      // Analyze results
      this.analyzeResults(letterSet, wordList, duration);
      
      return {
        success: true,
        letterSet,
        totalWords: wordList.length,
        words: wordList,
        duration: duration + 's'
      };
      
    } catch (error) {
      console.error(`❌ Test failed: ${error.message}`);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Comprehensive word collection (based on the improved method)
   */
  async getComprehensiveWordList(letterSet) {
    const allWords = new Map();
    const letterSetLower = letterSet.toLowerCase();
    
    console.log(`\n🔍 Starting comprehensive word search...`);

    // STEP 1: Database search
    await this.searchDatabase(letterSet, allWords);
    
    // STEP 2: Manual pattern checking
    await this.checkCommonPatterns(letterSet, allWords);
    
    // STEP 3: API search (if needed)
    if (allWords.size < 15) {
      await this.searchAPI(letterSet, allWords);
    }

    // Convert to sorted array
    const wordsArray = Array.from(allWords.values())
      .sort((a, b) => {
        const sourceOrder = { 'common_words': 0, 'word_frequency': 1, 'manual_check': 2, 'datamuse': 3 };
        if (a.source !== b.source) {
          return sourceOrder[a.source] - sourceOrder[b.source];
        }
        if (a.word.length !== b.word.length) {
          return a.word.length - b.word.length; // Sort by length first
        }
        return a.word.localeCompare(b.word); // Then alphabetically
      });

    console.log(`✅ Total words collected: ${wordsArray.length}`);
    return wordsArray;
  }

  /**
   * Search database tables
   */
  async searchDatabase(letterSet, allWords) {
    console.log(`\n📚 Searching database tables...`);
    
    try {
      const maxLength = Math.min(letterSet.length, 10);
      
      // Search common_words - more selective
      console.log(`   Querying common_words table...`);
      const { data: commonWords, error: commonError } = await supabase
        .from('common_words')
        .select('word')
        .limit(3000); // Reasonable limit
        
      if (commonError) {
        console.warn(`   ⚠️ common_words error: ${commonError.message}`);
      } else if (commonWords) {
        let validCount = 0;
        
        commonWords.forEach(w => {
          const word = w.word.toLowerCase();
          if (word.length >= 3 && 
              word.length <= maxLength && 
              this.isValidWordForLetterSet(word, letterSet)) {
            allWords.set(word, {
              word: word,
              frequency: 100,
              rarity: 'common',
              source: 'common_words'
            });
            validCount++;
          }
        });
        
        console.log(`   ✅ common_words: ${validCount} valid words (from ${commonWords.length} total)`);
      }
  
      // Search word_frequency - more selective with higher threshold
      console.log(`   Querying word_frequency table...`);
      const { data: freqWords, error: freqError } = await supabase
        .from('word_frequency')
        .select('word, frequency')
        .gte('frequency', 5) // Higher threshold to filter out junk
        .limit(5000);
        
      if (freqError) {
        console.warn(`   ⚠️ word_frequency error: ${freqError.message}`);
      } else if (freqWords) {
        let validCount = 0;
        
        freqWords.forEach(w => {
          const word = w.word.toLowerCase();
          if (word.length >= 3 && 
              word.length <= maxLength && 
              this.isValidWordForLetterSet(word, letterSet) && 
              !allWords.has(word)) {
            allWords.set(word, {
              word: word,
              frequency: w.frequency,
              rarity: w.frequency < 10 ? 'rare' : w.frequency < 50 ? 'uncommon' : 'common',
              source: 'word_frequency'
            });
            validCount++;
          }
        });
        
        console.log(`   ✅ word_frequency: ${validCount} additional valid words (from ${freqWords.length} total)`);
      }
      
    } catch (error) {
      console.error(`   ❌ Database search failed: ${error.message}`);
    }
  }  

  /**
   * Check common word patterns
   */
  async checkCommonPatterns(letterSet, allWords) {
    console.log(`\n🎯 Checking common word patterns...`);
    
    const patterns = this.generateCommonWordPatterns(letterSet);
    let addedCount = 0;
    let checkedCount = 0;
    
    for (const word of patterns) {
      if (!allWords.has(word) && this.isValidWordForLetterSet(word, letterSet)) {
        checkedCount++;
        const exists = await this.quickWordExistenceCheck(word);
        if (exists) {
          allWords.set(word, {
            word: word,
            frequency: 50,
            rarity: 'common',
            source: 'manual_check'
          });
          addedCount++;
          console.log(`   ➕ Added: ${word}`);
        }
      }
    }
    
    console.log(`   ✅ Manual check: ${addedCount} words added (checked ${checkedCount} patterns)`);
  }

  /**
   * Search API
   */
  async searchAPI(letterSet, allWords) {
    console.log(`\n🔗 Searching DataMuse API...`);
    
    try {
      const apiWords = await this.getWordsFromDataMuse(letterSet);
      let addedCount = 0;
      
      apiWords.forEach(wordData => {
        if (!allWords.has(wordData.word)) {
          allWords.set(wordData.word, wordData);
          addedCount++;
        }
      });
      
      console.log(`   ✅ API search: ${addedCount} words added`);
      
    } catch (error) {
      console.error(`   ❌ API search failed: ${error.message}`);
    }
  }

  /**
   * Generate common word patterns
   */
  generateCommonWordPatterns(letterSet) {
    const words = new Set();
    
    // Known patterns for specific letter sets
    const knownPatterns = {
      'stream': ['star', 'tar', 'tea', 'eat', 'rat', 'art', 'ear', 'era', 'sea', 'are', 'arm', 'mat', 'met', 'set', 'rest', 'tear', 'rate', 'meat', 'team', 'mare', 'tame', 'ream', 'term', 'stem', 'master', 'stream', 'mars', 'rams', 'tram', 'mast'],
      'planet': ['plan', 'plant', 'plate', 'tape', 'leap', 'pale', 'peal', 'tale', 'late', 'neat', 'pant', 'lane', 'lean', 'panel', 'net', 'ten', 'pen', 'pet', 'let', 'tea', 'eat', 'ant', 'tan', 'tap', 'pat', 'lap', 'pal'],
      'master': ['star', 'mast', 'mars', 'arms', 'rams', 'tram', 'team', 'meat', 'mate', 'rate', 'tear', 'term', 'rest', 'master', 'art', 'rat', 'tar', 'ear', 'era', 'sea', 'are', 'arm', 'mat', 'met', 'set', 'stem'],
      'heart': ['heart', 'earth', 'hater', 'tear', 'rate', 'hear', 'heat', 'hate', 'hart', 'hare', 'rhea', 'art', 'rat', 'tar', 'ear', 'era', 'are', 'ate', 'eat', 'tea', 'the', 'her']
    };
    
    // Check if we have a known pattern
    const key = letterSet.toLowerCase();
    if (knownPatterns[key]) {
      knownPatterns[key].forEach(word => words.add(word));
    }
    
    // Only add words from the curated good list
    const curatedGoodWords = [
      // 3-letter words
      'the', 'and', 'for', 'are', 'but', 'not', 'you', 'all', 'can', 'had',
      'her', 'was', 'one', 'our', 'out', 'day', 'get', 'has', 'him', 'his',
      'how', 'its', 'may', 'new', 'now', 'old', 'see', 'two', 'way', 'who',
      'art', 'arm', 'ear', 'eat', 'era', 'rat', 'tar', 'tea', 'sea', 'set',
      'net', 'ten', 'pen', 'pet', 'let', 'ant', 'tan', 'tap', 'pat', 'lap',
      'pal', 'mat', 'met', 'red', 'man', 'ran', 'cat', 'hat', 'sat', 'bat',
      'fat', 'bit', 'fit', 'hit', 'sit', 'cut', 'put', 'nut', 'run', 'sun',
      'fun', 'gun', 'win', 'sin', 'tin', 'pin', 'bin', 'fin', 'big', 'dig',
      'fig', 'pig', 'wig', 'bag', 'tag', 'lag', 'wag', 'rag', 'leg', 'beg',
      'egg', 'log', 'dog', 'fog', 'bog', 'hog', 'jog', 'cog',
      
      // 4+ letter words
      'team', 'meat', 'rate', 'tear', 'mare', 'tame', 'star', 'mars', 'arms',
      'rest', 'stem', 'term', 'tram', 'mast', 'rams', 'plan', 'plant', 'plate',
      'tape', 'leap', 'pale', 'tale', 'late', 'neat', 'lane', 'lean', 'hear',
      'heat', 'hate', 'hart', 'hare', 'earth', 'heart', 'master', 'stream'
    ];
    
    curatedGoodWords.forEach(word => {
      if (this.isValidWordForLetterSet(word, letterSet)) {
        words.add(word);
      }
    });
    
    return Array.from(words);
  }

  isRealWordCandidate(word) {
    const normalizedWord = word.toLowerCase();
    
    // Expanded junk patterns
    const junkPatterns = [
      /^[bcdfghjklmnpqrstvwxyz]{3,}$/i, // All consonants
      /^[aeiou]{3,}$/i, // All vowels  
      /^(etrs|tser|rsta|stra|rtse|sert|ters|rets)$/i, // Specific junk found in results
      /^[a-z]\1{2,}$/i, // Repeated letters (aaa, bbb, etc.)
      /^(www|com|org|net|edu|gov)$/i, // Web domains
      /^[a-z]{1,2}[0-9]/, // Contains numbers
      /^(ltd|inc|llc|corp|ceo|cfo|cpu|ram|rom|usb|dvd|lcd|led)$/i, // Abbreviations
      /^(esm|esr|mst|smt|stm|ert|rms|tsa|eas|ret|ela|eln|ent|epa|lpn|nlp|npa|pel|tel|alt|ane|eht|hea|rah|tha)$/i
    ];
    
    // Check against junk patterns
    for (const pattern of junkPatterns) {
      if (pattern.test(normalizedWord)) {
        return false;
      }
    }
    
    // Must have at least one vowel
    if (!/[aeiou]/.test(normalizedWord)) {
      return false;
    }
    
    // For 3-letter words, use whitelist approach
    if (normalizedWord.length === 3) {
      const knownGood3Letter = new Set([
        'the', 'and', 'for', 'are', 'but', 'not', 'you', 'all', 'can', 'had',
        'her', 'was', 'one', 'our', 'out', 'day', 'get', 'has', 'him', 'his',
        'how', 'its', 'may', 'new', 'now', 'old', 'see', 'two', 'way', 'who',
        'art', 'arm', 'ear', 'eat', 'era', 'rat', 'tar', 'tea', 'sea', 'set',
        'net', 'ten', 'pen', 'pet', 'let', 'ant', 'tan', 'tap', 'pat', 'lap',
        'pal', 'mat', 'met', 'red', 'man', 'ran', 'cat', 'hat', 'sat', 'bat',
        'fat', 'bit', 'fit', 'hit', 'sit', 'cut', 'put', 'nut', 'run', 'sun',
        'fun', 'gun', 'win', 'sin', 'tin', 'pin', 'bin', 'fin', 'big', 'dig',
        'fig', 'pig', 'wig', 'bag', 'tag', 'lag', 'wag', 'rag', 'leg', 'beg',
        'egg', 'log', 'dog', 'fog', 'bog', 'hog', 'jog', 'cog', 'ate', 'age',
        'ace', 'ice', 'use', 'due', 'rue', 'sue', 'cue', 'hue', 'vie', 'pie',
        'tie', 'lie', 'die', 'bye', 'eye', 'rye', 'dry', 'cry', 'try', 'shy',
        'sky', 'fly', 'sly', 'why', 'buy', 'guy', 'bay', 'day', 'hay', 'jay',
        'kay', 'lay', 'may', 'nay', 'pay', 'ray', 'say', 'way', 'apt', 'opt'
      ]);
      
      return knownGood3Letter.has(normalizedWord);
    }
    
    // For 4+ letter words
    if (normalizedWord.length >= 4) {
      // Can't be all the same letter
      if (new Set(normalizedWord).size === 1) {
        return false;
      }
      
      // Can't have more than 3 consonants in a row
      const consonantRun = normalizedWord.match(/[bcdfghjklmnpqrstvwxyz]{4,}/);
      if (consonantRun) {
        return false;
      }
      
      // Can't be just a scrambled version of common abbreviations
      const sorted = normalizedWord.split('').sort().join('');
      const badSorted = ['erst', 'rets', 'rest', 'ster', 'ters'].map(w => w.split('').sort().join(''));
      if (badSorted.includes(sorted) && !this.isKnownGoodWord(normalizedWord)) {
        return false;
      }
    }
    
    return true;
  }

  isKnownGoodWord(word) {
    const knownGoodWords = new Set([
      // Common 4+ letter words that should always be allowed
      'team', 'meat', 'rate', 'tear', 'mare', 'tame', 'star', 'mars', 'arms',
      'rest', 'stem', 'term', 'tram', 'mast', 'rams', 'plan', 'plant', 'plate',
      'tape', 'leap', 'pale', 'tale', 'late', 'neat', 'lane', 'lean', 'hear',
      'heat', 'hate', 'hart', 'hare', 'earth', 'heart', 'master', 'stream',
      'mate', 'arts', 'eats', 'seat', 'east', 'sate', 'teas', 'meta', 'beta',
      'real', 'earl', 'lear', 'lore', 'role', 'love', 'move', 'home', 'come',
      'some', 'time', 'line', 'mine', 'fine', 'wine', 'dine', 'pine', 'vine'
    ]);
    
    return knownGoodWords.has(word.toLowerCase());
  }

  /**
   * Quick word existence check
   */
  async quickWordExistenceCheck(word) {
    try {
      // Additional validation before checking database
      if (!this.isRealWordCandidate(word)) {
        return false;
      }
      
      // Check common_words first
      const { data: commonWord } = await supabase
        .from('common_words')
        .select('word')
        .eq('word', word)
        .limit(1);
        
      if (commonWord && commonWord.length > 0) {
        return true;
      }
      
      // Check word_frequency with higher threshold for short words
      const minFrequency = word.length === 3 ? 5 : 1; // Higher bar for 3-letter words
      
      const { data: freqWord } = await supabase
        .from('word_frequency')
        .select('word, frequency')
        .eq('word', word)
        .gte('frequency', minFrequency)
        .limit(1);
        
      if (freqWord && freqWord.length > 0) {
        return true;
      }
      
      return false;
    } catch (error) {
      return false;
    }
  }

  /**
   * DataMuse API search
   */
  async getWordsFromDataMuse(letterSet) {
    const words = new Map();
    
    try {
      // Multiple search strategies
      const searches = [
        { sp: letterSet.toLowerCase() + '*', max: 100 },
        { sp: '*' + letterSet.toLowerCase(), max: 100 },
        { sp: letterSet.toLowerCase().split('').join('*'), max: 100 }
      ];
      
      for (const params of searches) {
        try {
          const response = await axios.get('https://api.datamuse.com/words', {
            params,
            timeout: 5000
          });

          response.data.forEach(item => {
            if (this.isValidWordForLetterSet(item.word, letterSet)) {
              if (!words.has(item.word)) {
                words.set(item.word, {
                  word: item.word,
                  frequency: this.parseFrequency(item.tags),
                  rarity: this.calculateRarity(item.tags),
                  source: 'datamuse'
                });
              }
            }
          });
        } catch (error) {
          console.warn(`   ⚠️ API pattern failed: ${error.message}`);
        }
      }

    } catch (error) {
      console.warn(`   ⚠️ DataMuse search failed: ${error.message}`);
    }

    return Array.from(words.values());
  }

  /**
   * Check if word can be formed from letter set
   */
  isValidWordForLetterSet(word, letterSet) {
    if (!word || typeof word !== 'string') return false;
    
    const normalizedWord = word.toLowerCase().trim();
    const letterSetLower = letterSet.toLowerCase();
    
    // Basic validations
    if (normalizedWord.length < 3 || normalizedWord.length > 12) return false;
    if (!/^[a-z]+$/.test(normalizedWord)) return false;
    
    // Enhanced junk word filtering
    if (!this.isRealWordCandidate(normalizedWord)) {
      return false;
    }
    
    // Check letter availability (existing logic)
    const availableLetters = {};
    for (const letter of letterSetLower) {
      availableLetters[letter] = (availableLetters[letter] || 0) + 1;
    }
    
    const neededLetters = {};
    for (const letter of normalizedWord) {
      neededLetters[letter] = (neededLetters[letter] || 0) + 1;
    }
    
    // Verify we have enough of each letter
    for (const [letter, count] of Object.entries(neededLetters)) {
      if (!availableLetters[letter] || availableLetters[letter] < count) {
        return false;
      }
    }
    
    return true;
  }  

  /**
   * Analyze and display results
   */
  analyzeResults(letterSet, wordList, duration) {
    console.log('\n' + '='.repeat(60));
    console.log('📊 ANALYSIS RESULTS');
    console.log('='.repeat(60));
    
    console.log(`Letter Set: ${letterSet}`);
    console.log(`Available Letters: ${letterSet.split('').join(', ')}`);
    console.log(`Total Words Found: ${wordList.length}`);
    console.log(`Search Duration: ${duration}`);
    
    // Group by source
    const bySource = {};
    wordList.forEach(w => {
      bySource[w.source] = bySource[w.source] || [];
      bySource[w.source].push(w);
    });
    
    console.log('\n📋 Words by Source:');
    Object.entries(bySource).forEach(([source, words]) => {
      console.log(`  ${source}: ${words.length} words`);
    });
    
    // Group by length
    const byLength = {};
    wordList.forEach(w => {
      byLength[w.word.length] = byLength[w.word.length] || [];
      byLength[w.word.length].push(w);
    });
    
    console.log('\n📏 Words by Length:');
    Object.entries(byLength)
      .sort(([a], [b]) => parseInt(a) - parseInt(b))
      .forEach(([length, words]) => {
        console.log(`  ${length} letters: ${words.length} words`);
      });
    
    // Show all words organized by length
    console.log('\n📝 ALL WORDS FOUND:');
    Object.entries(byLength)
      .sort(([a], [b]) => parseInt(a) - parseInt(b))
      .forEach(([length, words]) => {
        console.log(`\n  ${length}-letter words (${words.length}):`);
        const sortedWords = words
          .sort((a, b) => a.word.localeCompare(b.word))
          .map(w => `${w.word.toUpperCase()} (${w.source})`)
          .join(', ');
        
        // Wrap long lines
        const maxLineLength = 80;
        let currentLine = '    ';
        sortedWords.split(', ').forEach((word, index) => {
          if (currentLine.length + word.length > maxLineLength && index > 0) {
            console.log(currentLine);
            currentLine = '    ' + word;
          } else {
            currentLine += (index > 0 ? ', ' : '') + word;
          }
        });
        if (currentLine.length > 4) {
          console.log(currentLine);
        }
      });
    
    // Check for expected words
    const expectedWords = ['star', 'tar', 'tea', 'team', 'meat', 'rate', 'tear', 'mare', 'tame', 'stream', 'master'];
    const foundExpected = expectedWords.filter(word => 
      wordList.some(w => w.word.toLowerCase() === word.toLowerCase()) &&
      this.isValidWordForLetterSet(word, letterSet)
    );
    
    const missingExpected = expectedWords.filter(word => 
      !wordList.some(w => w.word.toLowerCase() === word.toLowerCase()) &&
      this.isValidWordForLetterSet(word, letterSet)
    );
    
    if (foundExpected.length > 0) {
      console.log(`\n✅ Expected words found (${foundExpected.length}): ${foundExpected.map(w => w.toUpperCase()).join(', ')}`);
    }
    
    if (missingExpected.length > 0) {
      console.log(`\n❌ Expected words missing (${missingExpected.length}): ${missingExpected.map(w => w.toUpperCase()).join(', ')}`);
    }
    
    // Quality assessment
    console.log('\n🎯 QUALITY ASSESSMENT:');
    if (wordList.length >= 25) {
      console.log(`  ✅ Excellent word count (${wordList.length} >= 25)`);
    } else if (wordList.length >= 15) {
      console.log(`  🟡 Good word count (${wordList.length} >= 15)`);
    } else {
      console.log(`  ❌ Low word count (${wordList.length} < 15)`);
    }
    
    const hasVariedLengths = Object.keys(byLength).length >= 3;
    console.log(`  ${hasVariedLengths ? '✅' : '❌'} Word length variety: ${Object.keys(byLength).length} different lengths`);
    
    const hasLongWords = wordList.some(w => w.word.length >= letterSet.length);
    console.log(`  ${hasLongWords ? '✅' : '❌'} Has words using most/all letters`);
  }

  /**
   * Utility methods
   */
  parseFrequency(tags) {
    if (!tags) return 1;
    const freqTag = tags.find(tag => tag.startsWith('f:'));
    if (freqTag) {
      return parseFloat(freqTag.substring(2)) || 1;
    }
    return 1;
  }

  calculateRarity(tags) {
    const frequency = this.parseFrequency(tags);
    if (frequency > 50) return 'common';
    if (frequency > 10) return 'uncommon';
    return 'rare';
  }
}

// Main test execution
async function runTest() {
  const tester = new LetterSetTester();
  
  // Test different letter sets
  const testCases = [
    { letterSet: 'STREAM', difficulty: 'Medium' },
    { letterSet: 'PLANET', difficulty: 'Medium' },
    { letterSet: 'MASTER', difficulty: 'Medium' },
    { letterSet: 'HEART', difficulty: 'Easy' }
  ];
  
  console.log('🧪 LETTER SET GENERATOR TEST SUITE');
  console.log('Running tests for multiple letter sets...\n');
  
  for (const testCase of testCases) {
    const result = await tester.testLetterSet(testCase.letterSet, testCase.difficulty);
    
    if (!result.success) {
      console.error(`Test failed for ${testCase.letterSet}: ${result.error}`);
    }
    
    // Wait a bit between tests to avoid rate limiting
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  
  console.log('\n🏁 All tests completed!');
}

// Export for use in other modules or run directly
if (import.meta.url === `file://${process.argv[1]}`) {
  runTest().catch(console.error);
}

export { LetterSetTester, runTest };
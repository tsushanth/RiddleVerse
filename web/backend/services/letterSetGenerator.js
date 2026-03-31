import axios from 'axios';
import { supabase } from '../config/database.js';

export class LetterSetGenerator {
  constructor() {
    this.dataMuseCache = new Map();
    this.cacheExpiry = 60 * 60 * 1000; // 1 hour
    this.debugMode = false;
    
    // Track used letter sets in memory (for current session)
    this.usedLetterSets = new Set();
    
    // REDUCED minimum word thresholds - more realistic
    this.minWordThresholds = {
      'Easy': 8,    // Reduced from 15
      'Medium': 12, // Reduced from 25 
      'Hard': 18    // Reduced from 35
    };

    // Better curated letter sets that actually work
    this.emergencyFallbacks = {
      'Easy': ['STEAM', 'HEART', 'PLATE', 'STONE', 'LINER', 'GRACE'],
      'Medium': ['STREAM', 'PLANET', 'MASTER', 'FRAMES', 'LISTEN', 'GRAPES'], 
      'Hard': ['STRANGE', 'CHAPTER', 'KITCHEN', 'PRINTED', 'SMARTLY', 'GRASPED']
    };
    
    // High-frequency letters for better word formation
    this.letterFrequency = {
      'E': 12.02, 'T': 9.10, 'A': 8.12, 'O': 7.68, 'I': 6.97, 'N': 6.75,
      'S': 6.33, 'H': 6.09, 'R': 5.99, 'D': 4.25, 'L': 4.03, 'C': 2.78,
      'U': 2.76, 'M': 2.41, 'W': 2.36, 'F': 2.23, 'G': 2.02, 'Y': 1.97,
      'P': 1.93, 'B': 1.29, 'V': 0.98, 'K': 0.77, 'J': 0.15, 'X': 0.15,
      'Q': 0.10, 'Z': 0.07
    };

    this.vowels = ['A', 'E', 'I', 'O', 'U'];
    this.consonants = ['B', 'C', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z'];
    
    // Pre-tested letter sets that work well - EXPANDED for variety
    this.knownGoodSets = {
      'Easy': [
        // Original 16
        'STEAM', 'HEART', 'PLATE', 'STONE', 'GRACE', 'TRACE', 'PLACE', 'SPACE',
        'HOUSE', 'MOUSE', 'NOTES', 'STORE', 'SHORE', 'HORSE', 'LEARN', 'CLEAR',
        // Additional 50+ for variety
        'TRAIN', 'BRAIN', 'GRAIN', 'DRAIN', 'PLAIN', 'CHAIN', 'STAIN', 'PAINT',
        'SAINT', 'FAINT', 'RAISE', 'PRAISE', 'PHASE', 'CHASE', 'SHARE', 'SPARE',
        'SCARE', 'STARE', 'GLARE', 'FLARE', 'BLARE', 'SNARE', 'SWEAR', 'SPEAR',
        'TEARS', 'YEARS', 'FEARS', 'GEARS', 'BEARS', 'WEARS', 'PEARS', 'NEARS',
        'DEALS', 'MEALS', 'SEALS', 'HEALS', 'PEALS', 'STEAL', 'REALM', 'DREAM',
        'CREAM', 'GLEAM', 'STEAM', 'TEAMS', 'BEATS', 'SEATS', 'MEATS', 'FEATS',
        'RATES', 'GATES', 'DATES', 'MATES', 'HATES', 'LATES', 'FATES', 'CRATE',
        'GRATE', 'PLATE', 'SLATE', 'SKATE', 'STATE', 'TRADE', 'GRADE', 'SHADE',
        'BLADE', 'GLADE', 'SPADE', 'BRAKE', 'STAKE', 'AWAKE', 'SHAPE', 'GRAPE',
        'DRAPE', 'FRAME', 'BLAME', 'SHAME', 'FLAME', 'CRANE', 'PLANE', 'BRANE'
      ],
      'Medium': [
        // Original 14
        'STREAM', 'MASTER', 'FRAMES', 'LISTEN', 'GRAPES', 'STRIPE', 'PERSON',
        'MONTHS', 'PLANTS', 'TRAINS', 'BREATH', 'THREAD', 'SPREAD', 'PLEASE',
        // Additional 50+ for variety
        'PLANET', 'MENTAL', 'RENTAL', 'DENTAL', 'GENTLE', 'CASTLE', 'MANTLE',
        'HANDLE', 'CANDLE', 'SAMPLE', 'TEMPLE', 'SIMPLE', 'DIMPLE', 'RIPPLE',
        'BATTLE', 'CATTLE', 'RATTLE', 'SETTLE', 'KETTLE', 'METTLE', 'NETTLE',
        'BOTTLE', 'MOTTLE', 'DOTTLE', 'SISTER', 'MISTER', 'BLISTER', 'FISTER',
        'LISTER', 'POSTER', 'ROSTER', 'FOSTER', 'MASTER', 'FASTER', 'CASTER',
        'PASTER', 'TASTER', 'WASTER', 'EASTER', 'FEASTER', 'BASTER', 'PLASTER',
        'DREAMS', 'CREAMS', 'GLEAMS', 'STEAMS', 'BEAMS', 'REAMS', 'SEAMS', 'TEAMS',
        'STREAM', 'SCREAM', 'BREAM', 'BREAST', 'YEAST', 'BEAST', 'FEAST', 'LEAST',
        'HEARTS', 'STARTS', 'CHARTS', 'DARTS', 'PARTS', 'CARTS', 'TARTS', 'WARTS',
        'PLATES', 'SKATES', 'STATES', 'GRATES', 'CRATES', 'SLATES', 'TRADES', 'GRADES',
        'SHADES', 'BLADES', 'GLADES', 'SPADES', 'BRAKES', 'STAKES', 'SHAPES', 'DRAPES'
      ],
      'Hard': [
        // Original 12
        'STRANGE', 'CHAPTER', 'KITCHEN', 'PRINTED', 'GRASPED', 'BLANKET',
        'PROMISE', 'MACHINE', 'PERHAPS', 'FRIENDS', 'TALKING', 'HISTORY',
        // Additional 50+ for variety
        'STRANGE', 'CHAPTER', 'CHARTED', 'STARTED', 'SMARTED', 'PARTED', 'DARTED',
        'PLANTED', 'SLANTED', 'GRANTED', 'CHANTED', 'RANTING', 'WANTING', 'PANTING',
        'LASTING', 'CASTING', 'FASTING', 'TASTING', 'WASTING', 'BASTING', 'MASTING',
        'HOSTING', 'POSTING', 'COSTING', 'ROSTING', 'TOASTING', 'BOASTING', 'ROASTING',
        'MASTERS', 'PLASTERS', 'CASTERS', 'TASTERS', 'BLASTERS', 'DISASTERS',
        'PLANETS', 'MARKETS', 'TARGETS', 'CARPETS', 'BASKETS', 'GASKETS', 'CASKETS',
        'BRACKETS', 'PACKETS', 'RACKETS', 'JACKETS', 'ROCKETS', 'POCKETS', 'SOCKETS',
        'LOCKERS', 'ROCKERS', 'MOCKERS', 'DOCKERS', 'BLOCKERS', 'KNOCKERS', 'SHOCKERS',
        'TEACHERS', 'PREACHERS', 'REACHERS', 'LEECHERS', 'BLEACHERS', 'SCREECHERS',
        'CHAPTERS', 'CAPTURES', 'RAPTURES', 'FRACTURES', 'PICTURES', 'LECTURES',
        'CULTURES', 'VULTURES', 'POSTURES', 'GESTURES', 'PASTURES', 'MIXTURES',
        'TEXTURES', 'FIXTURES', 'CREATURES', 'FEATURES', 'TREASURES', 'MEASURES'
      ]
    };
  }

  /**
   * Generate complete letter set puzzle with all possible words
   */
  async generateLetterSetPuzzle(difficulty = 'Medium') {
    console.log(`🎯 Generating letter set puzzle (${difficulty})`);
    
    try {
      // Use a more reliable selection approach
      const letterSetResult = await this.selectReliableLetterSet(difficulty);
      let letterSet = letterSetResult.letterSet || letterSetResult;
      
      console.log(`🎯 Selected letter set: "${letterSet}"`);
      
      let wordList;
      
      // If we have pre-calculated words, use them
      if (letterSetResult.preCalculatedWords) {
        console.log(`🚀 Using pre-calculated words from stats table`);
        wordList = letterSetResult.preCalculatedWords.map(w => ({
          word: w,
          frequency: 100,
          rarity: 'common',
          source: 'letter_set_stats'
        }));
      } else {
        // Get words using improved method
        wordList = await this.getWordsForLetterSetImproved(letterSet, 500);
      }
      
      console.log(`📝 Found ${wordList?.length || 0} potential words`);
      
      if (!wordList || wordList.length < 5) {
        console.log(`⚠️ Too few words (${wordList?.length || 0}), trying fallback letter set`);
        // Try a known good fallback
        const fallbackSet = this.getKnownGoodLetterSet(difficulty);
        console.log(`🔄 Trying fallback set: "${fallbackSet}"`);
        wordList = await this.getWordsForLetterSetImproved(fallbackSet, 500);
        letterSet = fallbackSet;
      }

      // Filter and validate words
      const validWords = (wordList || []).filter(w => 
        w && w.word && typeof w.word === 'string' && w.word.length >= 3
      );

      console.log(`✅ Valid words after filtering: ${validWords.length}`);

      if (validWords.length < 5) {
        throw new Error(`Still insufficient words for letter set: ${letterSet} (only ${validWords.length} valid)`);
      }

      // Find the key word
      const keyWord = this.findKeyWord(letterSet, validWords);

      // Generate puzzle data with more lenient requirements
      const puzzleData = {
        letterSet: letterSet.toUpperCase(),
        letters: letterSet.toUpperCase().split(''),
        keyWord: keyWord,
        difficulty: difficulty,
        timeLimit: this.getTimeLimit(difficulty),
        allWords: validWords.map(w => ({
          word: w.word.toUpperCase(),
          length: w.word.length,
          points: this.calculateWordPoints(w.word, w),
          rarity: w.rarity || 'common',
          frequency: w.frequency || 1,
          usesAllLetters: this.usesAllLetters(w.word, letterSet)
        })),
        scoring: {
          basePointsPerWord: difficulty === 'Easy' ? 10 : difficulty === 'Medium' ? 15 : 20,
          lengthMultiplier: {
            3: 1.0, 4: 1.2, 5: 1.5, 6: 2.0, 7: 2.5, 8: 3.0, 9: 4.0, 10: 5.0
          },
          rarityBonus: {
            common: 0,
            uncommon: 5,
            rare: 15
          },
          allLettersBonus: 25,
          speedBonus: {
            maxBonus: 100,
            timeWindow: 30
          }
        },
        targets: {
          bronze: Math.max(1, Math.ceil(validWords.length * 0.15)),  // At least 1
          silver: Math.max(2, Math.ceil(validWords.length * 0.35)),  // At least 2
          gold: Math.max(3, Math.ceil(validWords.length * 0.60))     // At least 3
        },
        hint: `Form words using the letters ${letterSet.toUpperCase()} - ${validWords.length} possible words!`,
        metadata: {
          generatedAt: new Date().toISOString(),
          totalWords: validWords.length,
          source: letterSetResult.source || 'improved_method',
          expectedDifficulty: difficulty,
          letterSetSource: letterSetResult.source || 'fallback',
          keyWordFound: !!keyWord
        }
      };

      console.log(`🎉 Successfully generated puzzle with ${validWords.length} words`);

      return {
        success: true,
        puzzleData: puzzleData,
        generationMethod: `letter_set_${letterSetResult.source || 'improved'}`
      };

    } catch (error) {
      console.error(`❌ Letter set generation error:`, error);
      return {
        success: false,
        message: error.message,
        fallbackSuggested: true
      };
    }
  }

  /**
   * More reliable letter set selection
   */
  async selectReliableLetterSet(difficulty) {
    console.log(`🎯 Selecting reliable letter set for ${difficulty} difficulty`);
    
    // Load used sets from database first
    await this.loadUsedLetterSets();
    
    // STEP 1: Try stats table first
    try {
      const statsResult = await this.getUnusedLetterSetFromStats(difficulty);
      if (statsResult) {
        console.log(`✅ Selected from stats: "${statsResult.letterSet}"`);
        await this.markLetterSetAsUsed(statsResult.letterSet, difficulty, statsResult.words.length);
        return {
          letterSet: statsResult.letterSet,
          preCalculatedWords: statsResult.words,
          source: 'letter_set_stats'
        };
      }
    } catch (error) {
      console.warn(`⚠️ Stats table failed: ${error.message}`);
    }
    
    // STEP 2: Try known good sets (checking against used sets)
    const knownGoodSets = this.knownGoodSets[difficulty] || this.knownGoodSets['Medium'];
    const shuffledSets = this.shuffleArray([...knownGoodSets]);
    
    for (const candidateSet of shuffledSets) {
      if (!this.usedLetterSets.has(candidateSet.toUpperCase())) {
        console.log(`✅ Using known good set: "${candidateSet}"`);
        this.usedLetterSets.add(candidateSet.toUpperCase());
        await this.markLetterSetAsUsed(candidateSet, difficulty, 0);
        return {
          letterSet: candidateSet,
          source: 'known_good'
        };
      }
    }
    
    // STEP 3: Try database generation with improved logic
    const databaseSet = await this.generateLetterSetFromDatabaseImproved(difficulty);
    if (databaseSet) {
      console.log(`✅ Generated from database: "${databaseSet}"`);
      return {
        letterSet: databaseSet,
        source: 'database_improved'
      };
    }
    
    // STEP 4: Use emergency fallback
    console.log(`💥 Using emergency fallback`);
    return {
      letterSet: this.getEmergencyFallbackLetterSet(difficulty),
      source: 'emergency_fallback'
    };
  }

  /**
   * Get known good letter set
   */
  getKnownGoodLetterSet(difficulty) {
    const sets = this.knownGoodSets[difficulty] || this.knownGoodSets['Medium'];
    return sets[Math.floor(Math.random() * sets.length)];
  }

  /**
   * Improved database generation
   */
  async generateLetterSetFromDatabaseImproved(difficulty) {
    console.log(`🗄️ Improved database generation for ${difficulty}`);
    
    const setLength = this.getLetterSetLength(difficulty);
    const minThreshold = this.minWordThresholds[difficulty] || 8;
    
    try {
      // Get words that are more likely to form good letter sets
      const { data: candidateWords, error } = await supabase
        .from('common_words')
        .select('word')
        .eq('length(word)', setLength)
        .order('word')
        .limit(200);
        
      if (error || !candidateWords || candidateWords.length === 0) {
        console.warn(`⚠️ No candidates from common_words`);
        return null;
      }
      
      // Test a reasonable number of candidates
      const shuffled = this.shuffleArray(candidateWords).slice(0, 30);
      
      for (const candidate of shuffled) {
        const letterSet = candidate.word.toUpperCase();
        
        if (await this.isLetterSetAlreadyUsed(letterSet)) {
          continue;
        }
        
        // Quick test with improved word finding
        const testWords = await this.quickTestLetterSet(letterSet);
        
        if (testWords >= minThreshold) {
          console.log(`✅ Found good candidate "${letterSet}" with ~${testWords} words`);
          this.usedLetterSets.add(letterSet);
          await this.markLetterSetAsUsed(letterSet, difficulty, testWords);
          return letterSet;
        }
      }
      
    } catch (error) {
      console.error(`❌ Database generation failed: ${error.message}`);
    }
    
    return null;
  }

  /**
   * Quick test for letter set viability
   */
  async quickTestLetterSet(letterSet) {
    try {
      const words = await this.getWordsForLetterSetImproved(letterSet, 100);
      return words.filter(w => this.isValidWordForLetterSet(w.word, letterSet)).length;
    } catch (error) {
      console.error(`❌ Quick test failed for ${letterSet}: ${error.message}`);
      return 0;
    }
  }

  /**
   * Improved word finding method
   */
  async getWordsForLetterSetImproved(letterSet, maxWords = 500) {
    const cacheKey = `letterset_improved_${letterSet}_${maxWords}`;
    
    if (this.dataMuseCache.has(cacheKey)) {
      const cached = this.dataMuseCache.get(cacheKey);
      if (Date.now() - cached.timestamp < this.cacheExpiry) {
        return cached.words;
      }
    }

    const allWords = new Map();
    const letterSetLower = letterSet.toLowerCase();

    console.log(`🔍 Improved word search for "${letterSet}"`);

    // STEP 1: Enhanced database search with client-side filtering
    try {
      const maxLength = Math.min(letterSet.length, 10);
      
      // Search common_words with client-side filtering
      console.log(`📚 Searching common_words...`);
      const { data: commonWords, error: commonError } = await supabase
        .from('common_words')
        .select('word')
        .limit(3000);
        
      if (commonError) {
        console.warn(`⚠️ common_words error: ${commonError.message}`);
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
        
        console.log(`✅ common_words: ${validCount} valid words (from ${commonWords.length} total)`);
      }

      // Search word_frequency with higher threshold
      console.log(`📊 Searching word_frequency...`);
      const { data: freqWords, error: freqError } = await supabase
        .from('word_frequency')
        .select('word, frequency')
        .gte('frequency', 5) // Higher threshold to filter out junk
        .limit(5000);
        
      if (freqError) {
        console.warn(`⚠️ word_frequency error: ${freqError.message}`);
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
        
        console.log(`✅ word_frequency: ${validCount} additional valid words (from ${freqWords.length} total)`);
      }
      
    } catch (error) {
      console.error(`❌ Database search failed: ${error.message}`);
    }

    // STEP 2: Add missing common words through manual checking
    await this.addMissingCommonWords(letterSet, allWords);

    // STEP 3: Enhanced API search if needed (only if we have too few words)
    if (allWords.size < 15) {
      console.log(`🔗 Supplementing with API (current: ${allWords.size})`);
      
      try {
        const apiWords = await this.getWordsFromDataMuse(letterSet);
        apiWords.forEach(wordData => {
          if (!allWords.has(wordData.word)) {
            allWords.set(wordData.word, wordData);
          }
        });
        
        console.log(`🔗 API search added words, total: ${allWords.size}`);
        
      } catch (error) {
        console.error(`❌ API search failed: ${error.message}`);
      }
    }

    // Convert to array and sort
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
      })
      .slice(0, maxWords);

    console.log(`✅ Total words found for "${letterSet}": ${wordsArray.length}`);

    // Cache the result
    this.dataMuseCache.set(cacheKey, {
      words: wordsArray,
      timestamp: Date.now()
    });

    return wordsArray;
  }

  async addMissingCommonWords(letterSet, allWords) {
    console.log(`🎯 Checking common word patterns...`);
    
    const patterns = this.generateCommonWordPatterns(letterSet);
    let addedCount = 0;
    
    for (const word of patterns) {
      if (!allWords.has(word) && this.isValidWordForLetterSet(word, letterSet)) {
        const exists = await this.quickWordExistenceCheck(word);
        if (exists) {
          allWords.set(word, {
            word: word,
            frequency: 50,
            rarity: 'common',
            source: 'manual_check'
          });
          addedCount++;
          console.log(`➕ Added: ${word}`);
        }
      }
    }
    
    console.log(`✅ Manual check: ${addedCount} words added (checked ${patterns.length} patterns)`);
  }

  generateCommonWordPatterns(letterSet) {
    const words = new Set();
    
    // Known patterns for specific letter sets
    const knownPatterns = {
      'stream': ['star', 'tar', 'tea', 'eat', 'rat', 'art', 'ear', 'era', 'sea', 'are', 'arm', 'mat', 'met', 'set', 'rest', 'tear', 'rate', 'meat', 'team', 'mare', 'tame', 'ream', 'term', 'stem', 'master', 'stream', 'mars', 'rams', 'tram', 'mast', 'sat', 'arms'],
      'planet': ['plan', 'plant', 'plate', 'tape', 'leap', 'pale', 'peal', 'tale', 'late', 'neat', 'pant', 'lane', 'lean', 'panel', 'net', 'ten', 'pen', 'pet', 'let', 'tea', 'eat', 'ant', 'tan', 'tap', 'pat', 'lap', 'pal'],
      'master': ['star', 'mast', 'mars', 'arms', 'rams', 'tram', 'team', 'meat', 'mate', 'rate', 'tear', 'term', 'rest', 'master', 'art', 'rat', 'tar', 'ear', 'era', 'sea', 'are', 'arm', 'mat', 'met', 'set', 'stem', 'eat', 'tea', 'sat', 'mare', 'tame', 'stream'],
      'heart': ['heart', 'earth', 'hater', 'tear', 'rate', 'hear', 'heat', 'hate', 'hart', 'hare', 'rhea', 'art', 'rat', 'tar', 'ear', 'era', 'are', 'ate', 'eat', 'tea', 'the', 'her', 'hat']
    };
    
    // Check if we have a known pattern
    const key = letterSet.toLowerCase();
    if (knownPatterns[key]) {
      knownPatterns[key].forEach(word => words.add(word));
    }
    
    // Add curated common words that might work
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
      'egg', 'log', 'dog', 'fog', 'bog', 'hog', 'jog', 'cog', 'ate', 'age',
      'ace', 'ice', 'use', 'due', 'rue', 'sue', 'cue', 'hue', 'vie', 'pie',
      'tie', 'lie', 'die', 'bye', 'eye', 'rye', 'dry', 'cry', 'try', 'shy',
      'sky', 'fly', 'sly', 'why', 'buy', 'guy', 'bay', 'day', 'hay', 'jay',
      'kay', 'lay', 'may', 'nay', 'pay', 'ray', 'say', 'way', 'apt', 'opt',
      
      // 4+ letter words
      'team', 'meat', 'rate', 'tear', 'mare', 'tame', 'star', 'mars', 'arms',
      'rest', 'stem', 'term', 'tram', 'mast', 'rams', 'plan', 'plant', 'plate',
      'tape', 'leap', 'pale', 'tale', 'late', 'neat', 'lane', 'lean', 'hear',
      'heat', 'hate', 'hart', 'hare', 'earth', 'heart', 'master', 'stream',
      'mate', 'arts', 'seat', 'east', 'sate', 'meta', 'real', 'earl', 'role',
      'time', 'line', 'mine', 'fine', 'wine', 'dine', 'pine', 'vine', 'panel'
    ];
    
    curatedGoodWords.forEach(word => {
      if (this.isValidWordForLetterSet(word, letterSet)) {
        words.add(word);
      }
    });
    
    return Array.from(words);
  }

  async quickWordExistenceCheck(word) {
    try {
      // Check common_words first
      const { data: commonWord } = await supabase
        .from('common_words')
        .select('word')
        .eq('word', word)
        .limit(1);
        
      if (commonWord && commonWord.length > 0) {
        return true;
      }
      
      // Check word_frequency with reasonable threshold
      const { data: freqWord } = await supabase
        .from('word_frequency')
        .select('word')
        .eq('word', word)
        .gte('frequency', 3) // Reasonable threshold
        .limit(1);
        
      if (freqWord && freqWord.length > 0) {
        return true;
      }
      
      return false;
    } catch (error) {
      return false;
    }
  }

  async quickWordExistenceCheck(word) {
    try {
      // Check common_words first (most reliable)
      const { data: commonWord } = await supabase
        .from('common_words')
        .select('word')
        .eq('word', word)
        .limit(1);
        
      if (commonWord && commonWord.length > 0) {
        return true;
      }
      
      // Check word_frequency  
      const { data: freqWord } = await supabase
        .from('word_frequency')
        .select('word')
        .eq('word', word)
        .limit(1);
        
      if (freqWord && freqWord.length > 0) {
        return true;
      }
      
      return false;
    } catch (error) {
      console.warn(`⚠️ Quick existence check failed for ${word}: ${error.message}`);
      return false;
    }
  }

  async getWordsFromDataMuseEnhanced(letterSet) {
    const words = new Map();
    
    try {
      console.log(`🔗 Enhanced DataMuse search for ${letterSet}...`);
      
      // Strategy 1: Broader wildcard patterns
      const patterns = [
        letterSet.toLowerCase(),
        letterSet.toLowerCase() + '*',
        '*' + letterSet.toLowerCase(),
        '*' + letterSet.toLowerCase() + '*',
        letterSet.toLowerCase().split('').join('*'),
        '???' + letterSet.toLowerCase().slice(0, 2) + '*',
        letterSet.toLowerCase().slice(0, 3) + '???'
      ];
      
      for (const pattern of patterns) {
        try {
          const response = await axios.get('https://api.datamuse.com/words', {
            params: {
              sp: pattern,
              max: 100
            },
            timeout: 5000
          });
  
          response.data.forEach(item => {
            if (this.isValidWordForLetterSet(item.word, letterSet)) {
              if (!words.has(item.word)) {
                words.set(item.word, {
                  word: item.word,
                  frequency: this.parseFrequency(item.tags),
                  rarity: this.calculateRarity(item.tags),
                  source: 'datamuse_enhanced'
                });
              }
            }
          });
        } catch (error) {
          console.warn(`⚠️ Pattern ${pattern} failed: ${error.message}`);
        }
      }
  
      // Strategy 2: Search by common endings
      const commonEndings = ['s', 'ed', 'ing', 'er', 'est', 'ly', 'al'];
      for (const ending of commonEndings) {
        try {
          const response = await axios.get('https://api.datamuse.com/words', {
            params: {
              sp: '*' + ending,
              max: 200
            },
            timeout: 5000
          });
  
          response.data.forEach(item => {
            if (this.isValidWordForLetterSet(item.word, letterSet)) {
              if (!words.has(item.word)) {
                words.set(item.word, {
                  word: item.word,
                  frequency: this.parseFrequency(item.tags),
                  rarity: this.calculateRarity(item.tags),
                  source: 'datamuse_enhanced'
                });
              }
            }
          });
        } catch (error) {
          console.warn(`⚠️ Ending search ${ending} failed: ${error.message}`);
        }
      }
  
      console.log(`🔗 Enhanced API found ${words.size} words`);
  
    } catch (error) {
      console.warn(`⚠️ Enhanced DataMuse search failed: ${error.message}`);
    }
  
    return Array.from(words.values());
  }

  /**
   * Enhanced DataMuse API search
   */
  async getWordsFromDataMuse(letterSet) {
    const words = new Map();
    
    try {
      // Strategy 1: Use spelled-like pattern
      const spelledLikePattern = letterSet.toLowerCase().split('').join('*') + '*';
      const response1 = await axios.get('https://api.datamuse.com/words', {
        params: {
          sp: spelledLikePattern,
          max: 100
        },
        timeout: 5000
      });

      response1.data.forEach(item => {
        if (this.isValidWordForLetterSet(item.word, letterSet)) {
          words.set(item.word, {
            word: item.word,
            frequency: this.parseFrequency(item.tags),
            rarity: this.calculateRarity(item.tags),
            source: 'datamuse'
          });
        }
      });

      // Strategy 2: Use anagram patterns
      const response2 = await axios.get('https://api.datamuse.com/words', {
        params: {
          sp: '?????' + (letterSet.length > 5 ? '?'.repeat(letterSet.length - 5) : ''),
          max: 200
        },
        timeout: 5000
      });

      response2.data.forEach(item => {
        if (this.isValidWordForLetterSet(item.word, letterSet) && !words.has(item.word)) {
          words.set(item.word, {
            word: item.word,
            frequency: this.parseFrequency(item.tags),
            rarity: this.calculateRarity(item.tags),
            source: 'datamuse'
          });
        }
      });

    } catch (error) {
      console.warn(`⚠️ DataMuse API failed: ${error.message}`);
    }

    return Array.from(words.values());
  }

  /**
   * Improved word validation
   */
  isValidWordForLetterSet(word, letterSet) {
    if (!word || typeof word !== 'string') return false;
    
    const normalizedWord = word.toLowerCase().trim();
    const letterSetLower = letterSet.toLowerCase();
    
    // Basic validations
    if (normalizedWord.length < 3 || normalizedWord.length > 12) return false;
    if (!/^[a-z]+$/.test(normalizedWord)) return false;
    
    // Enhanced word quality filtering
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

  isRealWordCandidate(word) {
    const normalizedWord = word.toLowerCase();
    
    // Filter out obvious junk patterns
    const junkPatterns = [
      /^[bcdfghjklmnpqrstvwxyz]{3,}$/i, // All consonants
      /^[aeiou]{3,}$/i, // All vowels  
      /^(etrs|tser|rsta|stra|rtse|sert|ters|rets)$/i, // Known junk combinations
      /^[a-z]\1{2,}$/i, // Repeated letters (aaa, bbb, etc.)
      /^(www|com|org|net|edu|gov)$/i, // Web domains
      /^(ltd|inc|llc|corp|ceo|cfo|cpu|ram|rom|usb|dvd|lcd|led)$/i, // Abbreviations
      /^(esm|esr|mst|smt|stm|ert|rms|tsa|eas|ret|ela|eln|ent|epa|lpn|nlp|npa|pel|tel|alt|ane|eht|hea|rah|tha)$/i // Database junk
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
    }
    
    return true;
  }

  /**
   * Get unused letter set from stats table
   */
  async getUnusedLetterSetFromStats(difficulty) {
    try {
      const { data, error } = await supabase
        .from('letter_set_stats')
        .select('letter_set, words')
        .eq('difficulty', difficulty);
      
      if (error) {
        console.error(`❌ Stats table error: ${error.message}`);
        return null;
      }
      
      const candidates = (data || []).filter(ls => !this.usedLetterSets.has(ls.letter_set));
      if (candidates.length === 0) return null;
      
      const selected = candidates[Math.floor(Math.random() * candidates.length)];
      this.usedLetterSets.add(selected.letter_set);
      
      return {
        letterSet: selected.letter_set,
        words: selected.words || []
      };
    } catch (error) {
      console.error(`❌ Stats table access failed: ${error.message}`);
      return null;
    }
  }

  // Keep existing utility methods...
  
  findKeyWord(letterSet, wordList) {
    let keyWord = null;
    let maxScore = 0;
    
    for (const wordData of wordList) {
      const word = wordData.word;
      const score = word.length + (this.usesAllLetters(word, letterSet) ? 10 : 0);
      
      if (score > maxScore) {
        maxScore = score;
        keyWord = word;
      }
    }
    
    return keyWord?.toUpperCase() || null;
  }

  usesAllLetters(word, letterSet) {
    const wordLetters = new Set(word.toLowerCase().split(''));
    const setLetters = new Set(letterSet.toLowerCase().split(''));
    
    return setLetters.size === wordLetters.size && 
           [...setLetters].every(letter => wordLetters.has(letter));
  }

  getLetterSetLength(difficulty) {
    switch (difficulty) {
      case 'Easy': return 5;
      case 'Medium': return 6;
      case 'Hard': return 7;
      default: return 6;
    }
  }

  getTimeLimit(difficulty) {
    const timeLimits = {
      'Easy': 120,    // 2 minutes
      'Medium': 180,  // 3 minutes
      'Hard': 240     // 4 minutes
    };
    return timeLimits[difficulty] || 180;
  }

  calculateWordPoints(word, wordData) {
    const basePoints = 10;
    const lengthBonus = Math.max(0, word.length - 3) * 3;
    const rarityBonus = wordData.rarity === 'rare' ? 15 : 
                      wordData.rarity === 'uncommon' ? 5 : 0;
    return basePoints + lengthBonus + rarityBonus;
  }

  getEmergencyFallbackLetterSet(difficulty) {
    const emergency = this.emergencyFallbacks[difficulty] || this.emergencyFallbacks['Medium'];
    return emergency[Math.floor(Math.random() * emergency.length)];
  }

  setDebugMode(enabled) {
    this.debugMode = enabled;
  }

  shuffleArray(array) {
    const shuffled = [...array];
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
  }

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

  async isLetterSetAlreadyUsed(letterSet) {
    if (this.usedLetterSets.has(letterSet)) {
      return true;
    }
    
    try {
      const { data: existing, error } = await supabase
        .from('puzzles')
        .select('puzzleid')
        .ilike('type', 'letterset')
        .eq('question', letterSet)
        .limit(1);
        
      if (error) {
        console.log(`⚠️ Could not check puzzles table: ${error.message}`);
      } else if (existing && existing.length > 0) {
        this.usedLetterSets.add(letterSet);
        return true;
      }
      
      return false;
      
    } catch (error) {
      console.log(`⚠️ Database check failed: ${error.message}`);
      return false;
    }
  }

  async markLetterSetAsUsed(letterSet, difficulty, wordCount) {
    try {
      const { error } = await supabase
        .from('used_letter_sets')
        .insert({
          letter_set: letterSet,
          difficulty: difficulty,
          word_count: wordCount,
          used_at: new Date().toISOString(),
          status: 'used'
        });
          
      if (error && error.code !== '23505') { // Ignore duplicate key errors
        console.warn(`⚠️ Could not mark letter set as used: ${error.message}`);
      } else {
        console.log(`✅ Marked letter set "${letterSet}" as used`);
      }
    } catch (error) {
      console.warn(`⚠️ Error marking letter set as used: ${error.message}`);
    }
  }

  // Add validation method for user submissions
  async validateWord(letterSet, word, userWords = []) {
    if (!word || typeof word !== 'string') {
      return { valid: false, reason: 'Invalid word format' };
    }

    const normalizedWord = word.toLowerCase().trim();

    if (normalizedWord.length < 3) {
      return { valid: false, reason: 'Word must be at least 3 letters long' };
    }

    if (userWords.includes(normalizedWord.toUpperCase())) {
      return { valid: false, reason: 'Word already used' };
    }

    if (!this.isValidWordForLetterSet(normalizedWord, letterSet)) {
      return { valid: false, reason: 'Word cannot be formed from available letters' };
    }

    // Quick existence check
    const wordData = await this.checkWordExists(normalizedWord);
    if (!wordData.exists) {
      return { valid: false, reason: 'Word not found in dictionary' };
    }

    return {
      valid: true,
      word: normalizedWord.toUpperCase(),
      points: this.calculateWordPoints(normalizedWord, wordData),
      rarity: wordData.rarity,
      frequency: wordData.frequency,
      usesAllLetters: this.usesAllLetters(normalizedWord, letterSet)
    };
  }

  async checkWordExists(word) {
    try {
      // Check database first
      const { data: dbWord } = await supabase
        .from('common_words')
        .select('word')
        .eq('word', word)
        .limit(1);

      if (dbWord && dbWord.length > 0) {
        return { exists: true, frequency: 100, rarity: 'common' };
      }

      // Check word_frequency table
      const { data: freqWord } = await supabase
        .from('word_frequency')
        .select('frequency')
        .eq('word', word)
        .limit(1);

      if (freqWord && freqWord.length > 0) {
        const freq = freqWord[0].frequency;
        return {
          exists: true,
          frequency: freq,
          rarity: freq < 10 ? 'rare' : freq < 100 ? 'uncommon' : 'common'
        };
      }

      // Fallback to API
      const response = await axios.get('https://api.datamuse.com/words', {
        params: { sp: word, max: 1 },
        timeout: 3000
      });

      if (response.data.length > 0 && response.data[0].word === word) {
        return {
          exists: true,
          frequency: this.parseFrequency(response.data[0].tags),
          rarity: this.calculateRarity(response.data[0].tags)
        };
      }

    } catch (error) {
      console.warn('Word existence check failed:', error.message);
    }

    return { exists: false };
  }

  async loadUsedSetsFromDatabase() {
    try {
      const { data: usedSets, error } = await supabase
        .from('used_letter_sets')
        .select('letter_set')
        .eq('status', 'used')
        .gte('used_at', new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()); // Only last 24 hours
        
      if (error) {
        console.warn(`⚠️ Could not load used sets: ${error.message}`);
        return;
      }
      
      if (usedSets) {
        usedSets.forEach(row => {
          this.usedLetterSets.add(row.letter_set.toUpperCase());
        });
        console.log(`📚 Loaded ${usedSets.length} recently used letter sets`);
      }
    } catch (error) {
      console.warn(`⚠️ Error loading used sets: ${error.message}`);
    }
  }

  async loadUsedLetterSets() {
    try {
      const { data: usedSets, error } = await supabase
        .from('used_letter_sets')
        .select('letter_set')
        .eq('status', 'used')
        .gte('used_at', new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()); // Only last 24 hours
        
      if (error) {
        console.warn(`⚠️ Could not load used sets: ${error.message}`);
        return;
      }
      
      if (usedSets) {
        usedSets.forEach(row => {
          this.usedLetterSets.add(row.letter_set.toUpperCase());
        });
        console.log(`📚 Loaded ${usedSets.length} recently used letter sets`);
      }
    } catch (error) {
      console.warn(`⚠️ Error loading used sets: ${error.message}`);
    }
  }
}
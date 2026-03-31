// Simplified Crypto Quote Generator - Store only quote + author
// Client will handle all crypto puzzle generation
// NO EXPRESS DEPENDENCIES - Pure generator functions only

/**
 * Simplified crypto quote generator that only fetches and validates quotes
 * All puzzle generation (encoding, mapping, etc.) happens client-side
 */
class SimplifiedCryptoQuoteGenerator {
    constructor() {
      this.debugMode = false;
      
      // API configuration (same as before)
      this.zenQuotesURL = 'https://zenquotes.io/api/random';
      this.apiNinjasURL = 'https://api.api-ninjas.com/v1/quotes';  
      this.apiKey = 'SyYaNj+G0Tf5f5+SgW6SYw==lZoSwNkVEcd2XwMd';
      
      // Simplified difficulty config - only for quote selection
      this.difficultyConfig = {
        easy: {
          minLength: 20,
          maxLength: 60,
          tags: ['wisdom', 'motivational', 'success', 'happiness']
        },
        medium: {
          minLength: 40, 
          maxLength: 100,
          tags: ['wisdom', 'philosophy', 'science', 'life']
        },
        hard: {
          minLength: 60,
          maxLength: 140,
          tags: ['philosophy', 'science', 'literature']
        },
        expert: {
          minLength: 80,
          maxLength: 180,
          tags: ['philosophy', 'literature', 'science']
        }
      };
  
      this.thresholds = {
        maxAttempts: 15,
        minWords: 3,
        maxWords: 20
      };
    }
  
    /**
     * SIMPLIFIED: Generate only quote data for storage
     * Client will handle all crypto puzzle creation
     */
    async generateQuoteForStorage(difficulty = 'medium') {
      this.debugLog(`🔐 Fetching quote for crypto puzzle: ${difficulty} difficulty`);
      
      try {
        const quote = await this.fetchQuoteFromAPI(difficulty);
        
        if (!quote) {
          throw new Error('Failed to fetch suitable quote');
        }
  
        // Clean the quote text
        const cleanText = this.cleanQuoteText(quote.content);
        
        // SIMPLIFIED: Return only what we need to store
        const quoteData = {
          puzzleId: this.generateUUID(),
          puzzleType: 'crypto',
          quote: cleanText,                    // ← ONLY store the quote text
          author: quote.author || 'Unknown',   // ← ONLY store the author
          difficulty: difficulty,
          source: quote.source || 'api',
          timestamp: new Date().toISOString(),
          // Client will generate all crypto data from just quote + author
          wordCount: cleanText.split(/\s+/).length,
          characterCount: cleanText.length
        };
        
        this.debugLog(`✅ Quote prepared for storage: "${cleanText.substring(0, 40)}..." by ${quote.author}`);
        
        return quoteData;
        
      } catch (error) {
        this.debugLog(`❌ Quote generation failed: ${error.message}`, 'error');
        throw error;
      }
    }
  
    // Keep existing API methods (fetchQuoteFromZenQuotes, fetchQuoteFromAPINinjas, etc.)
    // but remove all crypto-specific processing
  
    async fetchQuoteFromZenQuotes() {
      try {
        this.debugLog('🧘 Fetching quote from ZenQuotes API');
        
        const response = await fetch(this.zenQuotesURL);
        
        if (!response.ok) {
          throw new Error(`ZenQuotes API failed: ${response.status} ${response.statusText}`);
        }
        
        const quotes = await response.json();
        const quote = quotes && quotes.length > 0 ? quotes[0] : null;
        
        if (!quote) {
          throw new Error('No quote returned from ZenQuotes');
        }
        
        const transformedQuote = {
          content: quote.q,
          author: quote.a,
          category: 'wisdom',
          tags: ['wisdom'],
          source: 'zenquotes'
        };
        
        this.debugLog(`✅ ZenQuotes: "${transformedQuote.content.substring(0, 40)}..." by ${transformedQuote.author}`);
        return transformedQuote;
        
      } catch (error) {
        this.debugLog(`❌ ZenQuotes failed: ${error.message}`, 'error');
        throw error;
      }
    }
  
    async fetchQuoteFromAPINinjas() {
      try {
        this.debugLog('🥷 Fetching quote from API Ninjas');
        
        const headers = {
          'X-Api-Key': this.apiKey,
          'Content-Type': 'application/json'
        };
        
        const response = await fetch(this.apiNinjasURL, {
          method: 'GET',
          headers: headers
        });
        
        if (!response.ok) {
          throw new Error(`API Ninjas failed: ${response.status} ${response.statusText}`);
        }
        
        const quotes = await response.json();
        const quote = quotes && quotes.length > 0 ? quotes[0] : null;
        
        if (!quote) {
          throw new Error('No quote returned from API Ninjas');
        }
        
        const transformedQuote = {
          content: quote.quote,
          author: quote.author,
          category: quote.category || 'wisdom',
          tags: [quote.category || 'wisdom'],
          source: 'api_ninjas'
        };
        
        this.debugLog(`✅ API Ninjas: "${transformedQuote.content.substring(0, 40)}..." by ${transformedQuote.author}`);
        return transformedQuote;
        
      } catch (error) {
        this.debugLog(`❌ API Ninjas failed: ${error.message}`, 'error');
        throw error;
      }
    }
  
    async fetchQuoteFromAPI(difficulty) {
      const config = this.difficultyConfig[difficulty] || this.difficultyConfig.medium;
      let attempts = 0;
      
      while (attempts < this.thresholds.maxAttempts) {
        try {
          this.debugLog(`🎯 Fetching quote for ${difficulty} difficulty (attempt ${attempts + 1})`);
          
          let quote = null;
          
          // Try ZenQuotes first
          try {
            quote = await this.fetchQuoteFromZenQuotes();
          } catch (zenError) {
            this.debugLog('⚠️ ZenQuotes failed, trying API Ninjas...', 'warning');
            
            try {
              quote = await this.fetchQuoteFromAPINinjas();
            } catch (ninjaError) {
              this.debugLog('⚠️ Both APIs failed, will retry or use fallback', 'warning');
              throw new Error(`All APIs failed - ZenQuotes: ${zenError.message}, API Ninjas: ${ninjaError.message}`);
            }
          }
          
          // Validate the quote
          if (quote && this.validateQuote(quote, config)) {
            this.debugLog(`🎉 Valid quote found: "${quote.content.substring(0, 40)}..."`);
            return quote;
          }
          
          attempts++;
          
        } catch (error) {
          attempts++;
          this.debugLog(`❌ Fetch attempt ${attempts} failed: ${error.message}`, 'error');
          
          if (attempts >= this.thresholds.maxAttempts) {
            this.debugLog('📦 All attempts failed, using fallback quote');
            return this.getFallbackQuote(difficulty);
          }
          
          await new Promise(resolve => setTimeout(resolve, 1000 * attempts));
        }
      }
      
      return this.getFallbackQuote(difficulty);
    }
  
    validateQuote(quote, config) {
      if (!quote || !quote.content || !quote.author) {
        return false;
      }
      
      const content = quote.content.trim();
      const wordCount = content.split(/\s+/).length;
      
      // Check length
      if (content.length < config.minLength || content.length > config.maxLength) {
        return false;
      }
      
      // Check word count
      if (wordCount < this.thresholds.minWords || wordCount > this.thresholds.maxWords) {
        return false;
      }
      
      // Check for suitable content (at least 70% letters)
      const letterCount = (content.match(/[a-zA-Z]/g) || []).length;
      const letterRatio = letterCount / content.length;
      
      if (letterRatio < 0.7) {
        return false;
      }
      
      return true;
    }
  
    cleanQuoteText(text) {
      return text
        .replace(/[""]/g, '"')
        .replace(/['']/g, "'")
        .replace(/[^\w\s'".,!?-]/g, '')
        .replace(/\s+/g, ' ')
        .trim();
    }
  
    getFallbackQuote(difficulty) {
      const fallbacks = {
        easy: {
          content: "The only way to do great work is to love what you do",
          author: "Steve Jobs",
          tags: ["motivational", "work"]
        },
        medium: {
          content: "In the middle of difficulty lies opportunity", 
          author: "Albert Einstein",
          tags: ["wisdom", "opportunity"]
        },
        hard: {
          content: "The unexamined life is not worth living",
          author: "Socrates", 
          tags: ["philosophy", "wisdom"]
        },
        expert: {
          content: "I think therefore I am",
          author: "René Descartes",
          tags: ["philosophy", "existence"]
        }
      };
      
      return fallbacks[difficulty] || fallbacks.medium;
    }
  
    generateUUID() {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0;
        const v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
      });
    }
  
    debugLog(message, type = 'info') {
      if (this.debugMode) {
        const timestamp = new Date().toISOString();
        const emoji = type === 'error' ? '❌' : type === 'warning' ? '⚠️' : type === 'success' ? '✅' : '🔐';
        console.log(`${emoji} [${timestamp}] SIMPLIFIED_CRYPTO: ${message}`);
      }
    }
  
    setDebugMode(enabled) {
      this.debugMode = enabled;
      this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'}`);
    }
  
    async healthCheck() {
      // Same health check logic as before
      // ... (keep existing health check method)
    }
  }
  
  // Export the simplified generator
  export const simplifiedCryptoQuoteGenerator = new SimplifiedCryptoQuoteGenerator();
  
  /**
   * MODIFIED: Enhanced generator that only returns quote + author
   */
  export class SimplifiedEnhancedCryptoQuoteGenerator {
    constructor() {
      this.service = simplifiedCryptoQuoteGenerator;
    }
  
    /**
     * Generate crypto quote data for storage (quote + author only)
     */
    async generateCryptoQuoteData(difficulty = 'medium') {
      try {
        console.log(`🔐 Simplified crypto quote generation for ${difficulty} difficulty`);
        
        const quoteData = await this.service.generateQuoteForStorage(difficulty);
        
        // SIMPLIFIED: Format for storage - only quote and author
        const result = {
          puzzleId: quoteData.puzzleId,
          puzzleType: 'crypto',
          quote: quoteData.quote,         // ← Just the quote text
          author: quoteData.author,       // ← Just the author
          difficulty: difficulty,
          source: quoteData.source,
          timestamp: quoteData.timestamp,
          wordCount: quoteData.wordCount,
          characterCount: quoteData.characterCount
        };
  
        console.log(`✅ Simplified crypto quote data: "${quoteData.quote.substring(0, 40)}..." by ${quoteData.author}`);
        
        return {
          success: true,
          puzzleData: result
        };
  
      } catch (error) {
        console.error(`❌ Simplified crypto quote generation failed: ${error.message}`);
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
  
  export const simplifiedEnhancedCryptoQuoteGenerator = new SimplifiedEnhancedCryptoQuoteGenerator();
  
  // MODIFIED: Updated integration for puzzleGeneration.js
  // In your generateRandomizedPuzzle method, replace the crypto section with:
  
  /*
  } else if (puzzleType.toLowerCase() === 'crypto' || puzzleType.toLowerCase() === 'crypto_quote') {
    this.debugLog(`🔐 Using simplified crypto quote generation...`);
    
    const { simplifiedEnhancedCryptoQuoteGenerator } = await import('./cryptoQuoteGenerator.js');
    result = await simplifiedEnhancedCryptoQuoteGenerator.generateCryptoQuoteData(difficulty);
    
    if (result.success) {
      // SIMPLIFIED: Store only quote and author - client generates puzzle
      const cryptoForStorage = {
        question: result.puzzleData.quote,           // ← Store quote as question
        answer: result.puzzleData.author,            // ← Store author as answer  
        hint: `Quote by ${result.puzzleData.author}`, // ← Simple hint
        difficulty: difficulty,
        options: [],
        metadata: {
          author: result.puzzleData.author,
          source: result.puzzleData.source,
          wordCount: result.puzzleData.wordCount,
          characterCount: result.puzzleData.characterCount,
          generatedAt: result.puzzleData.timestamp,
          puzzleType: 'crypto_quote',
          clientSideGeneration: true  // ← Flag for client
        }
      };
      
      result.puzzleData = cryptoForStorage;
    }
  */
  
  // MODIFIED: Remove processCryptoQuote method from puzzleGeneration.js entirely
  // The crypto case in processPuzzle should just return: 
  // return { success: true, puzzle };
  
  // Export only the generator classes - no Express routes
  export { SimplifiedCryptoQuoteGenerator };
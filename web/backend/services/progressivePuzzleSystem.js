import https from 'https';
import http from 'http';
import crypto from 'crypto';
import { callAI } from '../utils/aiClient.js';

class ProgressivePuzzleSystem {
    constructor(config = {}) {
        this.pixabayKey = process.env.PIXABAY_API_KEY;
        this.openaiKey = process.env.OPENAI_API_KEY;
        this.debugMode = config.debug || false;
        this.useAIImageGeneration = config.useAIGeneration !== false; // Default to TRUE
        this.supabase = null;
        this.minImageValidationScore = config.minImageValidationScore || 6; // Minimum score to accept image
        
        this.categories = [
            'famous_people', 'landmarks', 'animals', 'inventions', 'movies', 
            'books', 'historical_events', 'sports', 'science', 'art'
        ];
        
        this.blurLevels = [
            { step: 1, blur: 25, description: 'Heavy blur - Hardest clue' },
            { step: 2, blur: 18, description: 'Medium-heavy blur' },
            { step: 3, blur: 12, description: 'Medium blur' },
            { step: 4, blur: 6, description: 'Light blur - Easiest clue' },
            { step: 5, blur: 0, description: 'Clear - Final reveal' }
        ];

        this.initializeDatabase();
    }

    debugLog(operation, data = {}, level = 'info') {
        if (!this.debugMode) return;
        
        const timestamp = new Date().toISOString();
        const icons = {
            info: 'ℹ️',
            success: '✅',
            warning: '⚠️',
            error: '❌',
            start: '🚀',
            end: '🏁'
        };
        
        console.log(`${icons[level] || 'ℹ️'} [${timestamp}] ${operation.toUpperCase()}`);
        if (Object.keys(data).length > 0) {
            console.log(JSON.stringify(data, null, 2));
        }
        console.log('─'.repeat(60));
    }

    async initializeDatabase() {
        try {
            const { supabase } = await import('../config/database.js');
            this.supabase = supabase;
            
            if (!this.supabase) {
                throw new Error('Supabase client is undefined');
            }
            
            this.log('Database connection initialized');
        } catch (error) {
            this.debugLog('database_init_failed', {
                error: error.message,
                hasSupabase: !!this.supabase
            }, 'error');
            this.log(`Database initialization failed: ${error.message}`, 'error');
            this.supabase = null;
        }
    }

    log(message, type = 'info') {
        if (!this.debugMode) return;
        const timestamp = new Date().toISOString();
        console.log(`[${timestamp}] PUZZLE_SYSTEM (${type.toUpperCase()}): ${message}`);
    }

    // ============ FIXED DEDUPLICATION - Now prevents duplicates in the list itself ============

    async getRecentPuzzleAnswers(targetCategory = null, limit = 30) {
        this.debugLog('deduplication_start', {
            targetCategory,
            limit,
            databaseAvailable: !!this.supabase
        }, 'start');

        try {
            if (!this.supabase) {
                this.log('Database not available, returning empty recent answers', 'warning');
                return [];
            }

            // Query MORE records to account for duplicates we'll filter out
            const { data, error } = await this.supabase
                .from('puzzles')
                .select('question, answer, timestamp, difficulty')
                .eq('type', 'progressiverevelation')
                .order('timestamp', { ascending: false })
                .limit(limit * 3); // Query 3x to ensure we get enough UNIQUE records

            if (error) {
                this.debugLog('supabase_query_error', { error: error.message }, 'error');
                throw error;
            }

            const sameCategory = [];
            const otherCategories = [];
            let parseErrors = 0;
            const seenAnswers = new Set(); // CRITICAL: Track unique answers only

            for (const puzzle of data || []) {
                try {
                    const questionData = JSON.parse(puzzle.question);
                    const answerData = JSON.parse(puzzle.answer);
                    
                    if (!answerData.correctAnswer) {
                        this.log('Skipping puzzle with missing correctAnswer', 'warning');
                        continue;
                    }

                    const normalizedAnswer = answerData.correctAnswer.toLowerCase().trim();
                    
                    // CRITICAL FIX: Skip if we've already added this answer
                    if (seenAnswers.has(normalizedAnswer)) {
                        this.log(`Skipping duplicate in dedup list: ${answerData.correctAnswer}`, 'warning');
                        continue;
                    }
                    seenAnswers.add(normalizedAnswer);

                    const puzzleInfo = {
                        answer: answerData.correctAnswer,
                        category: questionData.category,
                        timestamp: puzzle.timestamp,
                        difficulty: puzzle.difficulty
                    };

                    if (targetCategory && questionData.category === targetCategory) {
                        sameCategory.push(puzzleInfo);
                    } else {
                        otherCategories.push(puzzleInfo);
                    }
                } catch (parseError) {
                    parseErrors++;
                    this.log(`Error parsing puzzle data: ${parseError.message}`, 'warning');
                }
            }

            // Build final dedup list - prioritize same category
            const recentAnswers = [];
            if (targetCategory) {
                // Add up to 20 from same category
                recentAnswers.push(...sameCategory.slice(0, 20));
                // Fill remaining with others
                const remaining = limit - recentAnswers.length;
                if (remaining > 0) {
                    recentAnswers.push(...otherCategories.slice(0, remaining));
                }
            } else {
                // No specific category, just get most recent unique up to limit
                recentAnswers.push(...sameCategory, ...otherCategories);
                recentAnswers.splice(limit);
            }

            this.debugLog('deduplication_results', {
                totalQueried: data?.length || 0,
                totalFound: recentAnswers.length,
                uniqueAnswers: seenAnswers.size,
                sameCategoryUnique: sameCategory.length,
                otherCategoriesUnique: otherCategories.length,
                parseErrors,
                answersToAvoid: recentAnswers.map(r => `${r.answer} (${r.category})`).slice(0, 10)
            }, 'success');

            return recentAnswers;

        } catch (error) {
            this.debugLog('deduplication_failed', {
                error: error.message,
                stack: error.stack?.split('\n').slice(0, 3),
                targetCategory,
                limit
            }, 'error');
            return [];
        }
    }

    formatRecentAnswersForPrompt(recentAnswers, targetCategory) {
        if (!recentAnswers || recentAnswers.length === 0) {
            return "No recent puzzles found in database - you have full creative freedom!";
        }

        // Group by category
        const byCategory = {};
        recentAnswers.forEach(item => {
            if (!byCategory[item.category]) {
                byCategory[item.category] = [];
            }
            byCategory[item.category].push(item.answer);
        });

        let prompt = "=".repeat(70) + "\n";
        prompt += "🚫 CRITICAL: RECENT PUZZLE ANSWERS - NEVER DUPLICATE THESE\n";
        prompt += "=".repeat(70) + "\n\n";

        // Show target category first
        if (targetCategory && byCategory[targetCategory]) {
            prompt += `⚠️  ${targetCategory.toUpperCase()} (YOUR CATEGORY - ABSOLUTELY DO NOT USE THESE):\n`;
            byCategory[targetCategory].forEach((answer, idx) => {
                prompt += `   ${idx + 1}. ${answer}\n`;
            });
            prompt += '\n';
            delete byCategory[targetCategory];
        }

        // Show other categories
        const otherCats = Object.entries(byCategory);
        if (otherCats.length > 0) {
            prompt += "Other categories (ALSO FORBIDDEN - do not use):\n";
            otherCats.forEach(([category, answers]) => {
                prompt += `\n${category}:\n`;
                answers.slice(0, 15).forEach((answer, idx) => {
                    prompt += `   - ${answer}\n`;
                });
                if (answers.length > 15) {
                    prompt += `   ... and ${answers.length - 15} more\n`;
                }
            });
        }

        prompt += "\n" + "=".repeat(70) + "\n";
        prompt += `⚠️  TOTAL FORBIDDEN ANSWERS: ${recentAnswers.length}\n`;
        prompt += "=".repeat(70) + "\n\n";
        prompt += "✅ REQUIREMENT: Choose something COMPLETELY DIFFERENT from all answers above.\n";
        prompt += "✅ Think creatively - pick a unique, fresh answer players haven't seen!\n";
        prompt += "✅ Double-check your answer is NOT in the forbidden list above.\n\n";
        
        return prompt;
    }

    isDuplicateAnswer(newAnswer, recentAnswers) {
        const normalizedNew = newAnswer.toLowerCase().trim();
        
        for (const recent of recentAnswers) {
            const normalizedRecent = recent.answer.toLowerCase().trim();
            
            // Exact match
            if (normalizedNew === normalizedRecent) {
                return { isDuplicate: true, matchType: 'exact', matchedAnswer: recent.answer };
            }
            
            // Similar match (contains check)
            if (normalizedRecent.length > 4) {
                if (normalizedNew.includes(normalizedRecent) || normalizedRecent.includes(normalizedNew)) {
                    return { isDuplicate: true, matchType: 'similar', matchedAnswer: recent.answer };
                }
            }
        }
        
        return { isDuplicate: false };
    }

    async generateProgressivePuzzle(category = null, difficulty = 'medium') {
        const selectedCategory = category || this.getRandomCategory();
        
        this.debugLog('puzzle_generation_start', {
            selectedCategory,
            difficulty,
            wasRandomlySelected: !category
        }, 'start');
        
        // Get recent answers to avoid duplication
        const recentAnswers = await this.getRecentPuzzleAnswers(selectedCategory, 30);
        const recentAnswersPrompt = this.formatRecentAnswersForPrompt(recentAnswers, selectedCategory);
        
        // Show dedup list in debug mode
        if (this.debugMode && recentAnswers.length > 0) {
            console.log('\n📋 DEDUPLICATION LIST BEING SENT TO AI:');
            console.log(recentAnswersPrompt);
        }
        
        const prompt = `You are creating a progressive revelation puzzle for the category: ${selectedCategory}

${recentAnswersPrompt}

Create a puzzle with:
1. A specific, unique answer (person, place, thing, concept) NOT in the recent answers above
2. Four progressive clues (hardest to easiest) that gradually reveal the answer
3. Search terms for finding a clear image

CRITICAL: Your answer MUST be completely different from ALL answers listed above.

Clue progression:
- Level 1 (Hardest): Very cryptic, requires deep knowledge, metaphorical
- Level 2 (Hard): Indirect hints, requires good knowledge
- Level 3 (Medium): More direct clues, still requires thinking  
- Level 4 (Easy): Almost obvious, very direct hints

Return ONLY valid JSON in this exact format:
{
  "answer": "Your Unique Answer Here",
  "category": "${selectedCategory}",
  "clues": [
    {
      "level": 1,
      "difficulty": "hardest", 
      "text": "Most cryptic clue here",
      "hint": "Optional hint if needed"
    },
    {
      "level": 2,
      "difficulty": "hard",
      "text": "Hard clue here", 
      "hint": "Optional hint"
    },
    {
      "level": 3,
      "difficulty": "medium",
      "text": "Medium clue here",
      "hint": "Optional hint" 
    },
    {
      "level": 4,
      "difficulty": "easy",
      "text": "Easy clue here",
      "hint": "Optional hint"
    }
  ],
  "searchTerms": {
    "primary": "best search terms for clear image",
    "alternatives": ["backup search 1", "backup search 2"],
    "imageType": "photo"
  },
  "metadata": {
    "estimatedDifficulty": "${difficulty}",
    "timeEstimate": "60-300 seconds",
    "knowledgeArea": "specific domain"
  }
}`;

        try {
            this.debugLog('ai_request', {
                promptLength: prompt.length,
                estimatedTokens: Math.ceil(prompt.length / 4),
                recentAnswersToAvoid: recentAnswers.length,
                model: 'gpt-3.5-turbo'
            }, 'info');
            
            const response = await this.queryLLM(prompt, 'gpt-3.5-turbo');
            const puzzleData = this.parseJSONResponse(response);
            
            if (this.validatePuzzleStructure(puzzleData)) {
                // Check for duplicates
                const duplicationCheck = this.isDuplicateAnswer(puzzleData.answer, recentAnswers);
                
                this.debugLog('puzzle_validation', {
                    generatedAnswer: puzzleData.answer,
                    category: puzzleData.category,
                    isDuplicate: duplicationCheck.isDuplicate,
                    matchType: duplicationCheck.matchType,
                    matchedAnswer: duplicationCheck.matchedAnswer,
                    cluesGenerated: puzzleData.clues?.length || 0,
                    hasSearchTerms: !!puzzleData.searchTerms?.primary
                }, duplicationCheck.isDuplicate ? 'error' : 'success');
                
                if (duplicationCheck.isDuplicate) {
                    throw new Error(
                        `AI generated ${duplicationCheck.matchType} duplicate: "${puzzleData.answer}" ` +
                        `matches recent answer "${duplicationCheck.matchedAnswer}"`
                    );
                }
                
                return puzzleData;
            } else {
                throw new Error('Invalid puzzle structure');
            }
            
        } catch (error) {
            this.debugLog('puzzle_generation_failed', {
                error: error.message,
                selectedCategory,
                difficulty
            }, 'error');
            throw error;
        }
    }

    // ============ IMAGE OPERATIONS ============

    async getSingleAnswerImage(searchTerms, answer) {
        this.debugLog('image_search_start', {
            answer,
            primarySearchTerm: searchTerms.primary,
            alternativeCount: searchTerms.alternatives?.length || 0,
            aiGenerationEnabled: this.useAIImageGeneration
        }, 'start');

        const attemptedSearches = [];

        try {
            // Try primary Pixabay search
            this.log(`Trying primary search: ${searchTerms.primary}`, 'info');
            const pixabayImages = await this.searchPixabayImages(searchTerms.primary);
            attemptedSearches.push({ term: searchTerms.primary, found: pixabayImages.length });
            
            if (pixabayImages.length > 0) {
                const bestImage = pixabayImages[0];
                const imageBuffer = await this.downloadImage(bestImage.largeImageURL);
                const validation = await this.validateImageRelevance(imageBuffer, answer, searchTerms.primary);
                
                this.debugLog('image_validation', {
                    searchTerm: searchTerms.primary,
                    imageId: bestImage.id,
                    validationScore: validation.score,
                    isRelevant: validation.isRelevant,
                    confidence: validation.confidence,
                    imageSizeKB: Math.round(imageBuffer.length / 1024)
                }, validation.isRelevant ? 'success' : 'warning');
                
                if (validation.isRelevant && validation.score >= this.minImageValidationScore) {
                    this.log(`✅ Found suitable image from primary search (score: ${validation.score})`, 'success');
                    return {
                        source: 'pixabay',
                        buffer: imageBuffer,
                        metadata: bestImage,
                        validation
                    };
                } else {
                    this.log(`⚠️  Primary image score too low: ${validation.score}/${this.minImageValidationScore}`, 'warning');
                }
            } else {
                this.log(`No images found for primary search: ${searchTerms.primary}`, 'warning');
            }

            // Try alternatives
            for (const altTerm of searchTerms.alternatives || []) {
                this.log(`Trying alternative search: ${altTerm}`, 'info');
                const altImages = await this.searchPixabayImages(altTerm);
                attemptedSearches.push({ term: altTerm, found: altImages.length });
                
                if (altImages.length > 0) {
                    const imageBuffer = await this.downloadImage(altImages[0].largeImageURL);
                    const validation = await this.validateImageRelevance(imageBuffer, answer, altTerm);
                    
                    if (validation.isRelevant && validation.score >= this.minImageValidationScore) {
                        this.debugLog('alternative_success', {
                            altTerm,
                            validationScore: validation.score
                        }, 'success');
                        this.log(`✅ Found suitable image from alternative: ${altTerm} (score: ${validation.score})`, 'success');
                        return {
                            source: 'pixabay',
                            buffer: imageBuffer,
                            metadata: altImages[0],
                            validation
                        };
                    } else {
                        this.log(`⚠️  Alternative image score too low: ${validation.score}/${this.minImageValidationScore}`, 'warning');
                    }
                }
            }

            // Fallback to DALL-E if enabled
            if (this.useAIImageGeneration) {
                this.debugLog('fallback_to_dalle', { 
                    reason: 'no_suitable_pixabay_images',
                    attemptedSearches,
                    answer
                }, 'info');
                
                this.log('📸 No suitable Pixabay images found, generating with DALL-E...', 'info');
                const dalleResult = await this.generateAIImage(answer, searchTerms);
                this.log(`✅ Successfully generated DALL-E image for: ${answer}`, 'success');
                return dalleResult;
            }

            // No suitable image found and DALL-E disabled
            throw new Error(`No suitable image found for "${answer}". Tried: ${attemptedSearches.map(s => `${s.term} (${s.found} found)`).join(', ')}`);

        } catch (error) {
            this.debugLog('image_search_failed', {
                error: error.message,
                answer,
                searchTerms,
                attemptedSearches,
                aiGenerationEnabled: this.useAIImageGeneration
            }, 'error');
            throw error;
        }
    }

    async uploadSingleImage(puzzleId, imageBuffer) {
        try {
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const fileName = `progressive-puzzles/${puzzleId}-clear-${timestamp}.jpg`;
            
            const { error } = await this.supabase.storage
                .from('puzzle-images')
                .upload(fileName, imageBuffer, { 
                    contentType: 'image/jpeg',
                    upsert: true,
                    cacheControl: '31536000'
                });

            if (error) throw new Error(`Upload failed: ${error.message}`);

            const { data: { publicUrl } } = this.supabase.storage
                .from('puzzle-images')
                .getPublicUrl(fileName);

            this.log(`Image uploaded successfully: ${fileName}`, 'info');

            return {
                success: true,
                imageUrl: publicUrl,
                fileName: fileName,
                uploadedAt: new Date().toISOString()
            };

        } catch (error) {
            this.debugLog('image_upload_failed', {
                error: error.message,
                puzzleId,
                imageSizeKB: Math.round(imageBuffer.length / 1024)
            }, 'error');
            return { success: false, error: error.message };
        }
    }

    async generateCompletePuzzle(category = null, difficulty = 'medium') {
        const maxRetries = 3; // Increased retries for duplicate issues
        const safeDifficulty = (difficulty || 'medium').toString().toLowerCase();
        
        this.debugLog('complete_puzzle_start', {
            category,
            difficulty: safeDifficulty,
            maxRetries
        }, 'start');
        
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                const puzzleData = await this.generateProgressivePuzzle(category, safeDifficulty);
                
                const validation = {
                    hasPuzzleData: !!puzzleData,
                    hasAnswer: !!puzzleData?.answer,
                    hasCategory: !!puzzleData?.category,
                    hasSearchTerms: !!puzzleData?.searchTerms?.primary,
                    hasClues: !!puzzleData?.clues,
                    correctClueCount: puzzleData?.clues?.length === 4
                };
                
                const isValid = Object.values(validation).every(Boolean);
                
                if (!isValid) {
                    this.debugLog('puzzle_validation_failed', { attempt, validation }, 'error');
                    throw new Error('Invalid puzzle structure');
                }
                
                const puzzleHash = this.generatePuzzleHash(puzzleData.answer, puzzleData.category);
                const imageResult = await this.getSingleAnswerImage(puzzleData.searchTerms, puzzleData.answer);
                const uploadResult = await this.uploadSingleImage(puzzleHash, imageResult.buffer);
                
                if (!uploadResult || !uploadResult.success) {
                    throw new Error(`Image upload failed: ${uploadResult?.error || 'Unknown error'}`);
                }
    
                this.debugLog('puzzle_complete', {
                    attempt,
                    puzzleId: puzzleHash,
                    answer: puzzleData.answer,
                    category: puzzleData.category,
                    imageSource: imageResult.source,
                    validationScore: imageResult.validation?.score || 'N/A',
                    imageUrl: uploadResult.imageUrl
                }, 'success');
    
                const completePuzzle = {
                    puzzleId: puzzleHash,
                    type: 'progressiverevelation',
                    category: puzzleData.category,
                    clues: puzzleData.clues || [],
                    image: {
                        url: uploadResult.imageUrl,
                        fileName: uploadResult.fileName,
                        blurLevels: this.blurLevels,
                        uploadedAt: uploadResult.uploadedAt
                    },
                    gameFlow: {
                        totalSteps: 5,
                        timePerClue: 60000,
                        maxTime: 300000,
                        scoringSystem: {
                            correctAtStep1: 100,
                            correctAtStep2: 80,
                            correctAtStep3: 60,
                            correctAtStep4: 40,
                            correctAtStep5: 20
                        }
                    },
                    clientConfig: {
                        blurProperty: 'filter',
                        blurFunction: 'blur',
                        blurUnit: 'px',
                        transitionDuration: '0.5s',
                        fallbackBlur: 'opacity: 0.3'
                    },
                    instructions: "Guess what's in the image! Each wrong answer reveals a clearer image and an easier clue.",
                    timeLimit: 300000,
                    difficulty: safeDifficulty
                };
    
                const answerData = {
                    correctAnswer: puzzleData.answer,
                    clues: puzzleData.clues || [],
                    scoringBreakdown: {
                        correctAtStep1: 100,
                        correctAtStep2: 80,
                        correctAtStep3: 60,
                        correctAtStep4: 40,
                        correctAtStep5: 20
                    },
                    imageValidation: imageResult?.validation || {},
                    searchTerms: puzzleData.searchTerms || {}
                };
    
                try {
                    await this.storePuzzleHash(puzzleHash, {
                        type: 'progressiverevelation',
                        difficulty: safeDifficulty
                    });
                } catch (hashError) {
                    this.log(`Warning: Failed to store puzzle hash: ${hashError.message}`, 'warning');
                }
                
                return { 
                    success: true, 
                    puzzle: completePuzzle, 
                    answerData: answerData,
                    puzzleHash: puzzleHash
                };
    
            } catch (error) {
                this.debugLog('attempt_failed', {
                    attempt,
                    maxRetries,
                    error: error.message,
                    category,
                    difficulty: safeDifficulty
                }, 'error');
                
                if (attempt === maxRetries) {
                    return { 
                        success: false, 
                        error: error.message,
                        details: {
                            category: category,
                            difficulty: safeDifficulty,
                            attempts: maxRetries,
                            lastError: error.message
                        }
                    };
                }
                
                await new Promise(resolve => setTimeout(resolve, 1000));
            }
        }
    }

    // ============ UTILITY METHODS ============

    generatePuzzleHash(answer, category, type = 'progressiverevelation') {
        const timestamp = Date.now();
        const randomSalt = crypto.randomBytes(8).toString('hex');
        const hashContent = `${answer.toLowerCase().trim()}_${category}_${type}_${timestamp}_${randomSalt}`;
        return crypto.createHash('sha256').update(hashContent).digest('hex').substring(0, 16);
    }

    async storePuzzleHash(hash, puzzle) {
        try {
            if (!this.supabase) return { success: false, error: 'No database connection' };
            
            const { data, error } = await this.supabase
                .from('puzzle_hashes')
                .insert({
                    hash: hash,
                    type: puzzle.type || 'progressiverevelation',
                    difficulty: puzzle.difficulty || 'medium'
                })
                .select()
                .single();

            if (error) throw error;
            return { success: true, data };
        } catch (error) {
            this.log(`Error storing puzzle hash: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    validatePuzzleStructure(puzzle) {
        return puzzle.answer && 
               puzzle.clues && 
               puzzle.clues.length === 4 &&
               puzzle.searchTerms &&
               puzzle.searchTerms.primary &&
               puzzle.category;
    }

    getRandomCategory() {
        return this.categories[Math.floor(Math.random() * this.categories.length)];
    }

    async queryLLM(prompt, model = 'gpt-3.5-turbo') {
        try {
            const response = await callAI(prompt, model, 2, {
                category: 'puzzle_generation',
                puzzleType: 'progressiverevelation'
            });
            return response;
        } catch (error) {
            throw new Error(`LLM query failed: ${error.message}`);
        }
    }

    parseJSONResponse(response) {
        try {
            let content = response.trim();
            if (content.includes('```json')) {
                content = content.replace(/```json\s*/g, '').replace(/\s*```/g, '');
            } else if (content.includes('```')) {
                content = content.replace(/```\s*/g, '').replace(/\s*```/g, '');
            }
            return JSON.parse(content.trim());
        } catch (error) {
            this.debugLog('json_parse_failed', {
                error: error.message,
                responsePreview: response.substring(0, 200) + '...',
                responseLength: response.length
            }, 'error');
            throw new Error(`Failed to parse JSON: ${error.message}`);
        }
    }

    async searchPixabayImages(query, perPage = 5) {
        const maxRetries = 3;
        const retryDelay = 2000; // 2 seconds
        
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return await new Promise((resolve, reject) => {
                    const searchUrl = `https://pixabay.com/api/?key=${this.pixabayKey}&q=${encodeURIComponent(query)}&image_type=photo&orientation=all&min_width=640&min_height=480&per_page=${perPage}&safesearch=true&order=popular`;
                    
                    const req = https.get(searchUrl, { timeout: 10000 }, (res) => {
                        let data = '';
                        res.on('data', (chunk) => data += chunk);
                        res.on('end', () => {
                            try {
                                const response = JSON.parse(data);
                                this.log(`Pixabay search for "${query}": ${response.hits?.length || 0} results`, 'info');
                                resolve(response.hits || []);
                            } catch (error) {
                                reject(new Error(`Pixabay parse error: ${error.message}`));
                            }
                        });
                    });
                    
                    req.on('timeout', () => {
                        req.destroy();
                        reject(new Error('Pixabay request timeout'));
                    });
                    
                    req.on('error', reject);
                });
            } catch (error) {
                this.log(`Pixabay attempt ${attempt}/${maxRetries} failed: ${error.message}`, 'warning');
                
                if (attempt === maxRetries) {
                    this.log(`All Pixabay attempts failed for: ${query}`, 'error');
                    return []; // Return empty array instead of throwing
                }
                
                // Wait before retry
                await new Promise(resolve => setTimeout(resolve, retryDelay * attempt));
            }
        }
        
        return [];
    }

    async downloadImage(imageUrl) {
        return new Promise((resolve, reject) => {
            const protocol = imageUrl.startsWith('https:') ? https : http;
            protocol.get(imageUrl, (res) => {
                if (res.statusCode !== 200) {
                    reject(new Error(`Download failed: ${res.statusCode}`));
                    return;
                }
                const chunks = [];
                res.on('data', (chunk) => chunks.push(chunk));
                res.on('end', () => resolve(Buffer.concat(chunks)));
            }).on('error', reject);
        });
    }

    async validateImageRelevance(imageBuffer, expectedAnswer, searchTerm) {
        try {
            const base64Image = imageBuffer.toString('base64');
            
            const prompt = `Analyze this image for "${expectedAnswer}". Rate 1-10 relevance:
- 10: Perfect match
- 8-9: Very good match
- 6-7: Good match
- 4-5: Partial match
- 1-3: Poor match

JSON only:
{
  "score": number,
  "isRelevant": boolean (true if >= 6),
  "confidence": "high|medium|low",
  "description": "what you see",
  "reasoning": "why this score"
}`;

            const anthropicKey = process.env.ANTHROPIC_API_KEY;
            const requestBody = JSON.stringify({
                model: "claude-haiku-4-5-20251001",
                max_tokens: 300,
                messages: [{
                    role: "user",
                    content: [
                        {
                            type: "image",
                            source: {
                                type: "base64",
                                media_type: "image/jpeg",
                                data: base64Image
                            }
                        },
                        { type: "text", text: prompt + "\n\nRespond with ONLY valid JSON." }
                    ]
                }]
            });

            return new Promise((resolve, reject) => {
                const options = {
                    hostname: 'api.anthropic.com',
                    port: 443,
                    path: '/v1/messages',
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'x-api-key': anthropicKey,
                        'anthropic-version': '2023-06-01',
                        'Content-Length': Buffer.byteLength(requestBody)
                    }
                };

                const req = https.request(options, (res) => {
                    let data = '';
                    res.on('data', (chunk) => data += chunk);
                    res.on('end', () => {
                        try {
                            const response = JSON.parse(data);
                            if (response.error) {
                                reject(new Error(`Anthropic error: ${response.error.message}`));
                                return;
                            }
                            let content = (response.content?.[0]?.type === 'text' ? response.content[0].text : '').trim();
                            if (content.includes('```json')) {
                                content = content.replace(/```json\s*/g, '').replace(/\s*```/g, '');
                            }
                            resolve(JSON.parse(content));
                        } catch (error) {
                            reject(new Error(`Parse error: ${error.message}`));
                        }
                    });
                });

                req.on('error', reject);
                req.write(requestBody);
                req.end();
            });

        } catch (error) {
            return {
                score: 0,
                isRelevant: false,
                confidence: "low",
                description: "Validation failed",
                reasoning: error.message
            };
        }
    }

    async generateAIImage(answer, searchTerms) {
        this.log(`🎨 Generating DALL-E image for: ${answer}`, 'info');
        
        try {
            // Create a detailed prompt for better image quality
            const prompt = `A clear, high-quality, professional photograph of ${answer}. 
Professional photography, studio lighting, sharp focus, centered composition, 
recognizable and iconic representation. Photorealistic style.`.trim();
            
            this.debugLog('dalle_generation_start', {
                answer,
                prompt,
                model: 'dall-e-3'
            }, 'info');
            
            const requestBody = JSON.stringify({
                model: "dall-e-3",
                prompt: prompt,
                n: 1,
                size: "1024x1024",
                quality: "standard",
                style: "natural",
                response_format: "b64_json"
            });

            return new Promise((resolve, reject) => {
                const options = {
                    hostname: 'api.openai.com',
                    port: 443,
                    path: '/v1/images/generations',
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${this.openaiKey}`,
                        'Content-Length': Buffer.byteLength(requestBody)
                    },
                    timeout: 60000 // 60 second timeout for DALL-E
                };

                const req = https.request(options, (res) => {
                    let data = '';
                    res.on('data', (chunk) => data += chunk);
                    res.on('end', () => {
                        try {
                            const response = JSON.parse(data);
                            
                            if (response.error) {
                                this.debugLog('dalle_error', {
                                    error: response.error,
                                    answer
                                }, 'error');
                                reject(new Error(`DALL-E error: ${response.error.message}`));
                                return;
                            }

                            const imageData = response.data[0];
                            const imageBuffer = Buffer.from(imageData.b64_json, 'base64');
                            
                            this.debugLog('dalle_generation_success', {
                                answer,
                                imageSizeKB: Math.round(imageBuffer.length / 1024),
                                revisedPrompt: imageData.revised_prompt
                            }, 'success');
                            
                            resolve({
                                source: 'dall-e-3',
                                buffer: imageBuffer,
                                metadata: {
                                    prompt: prompt,
                                    model: "dall-e-3",
                                    generated: new Date().toISOString(),
                                    revised_prompt: imageData.revised_prompt
                                },
                                validation: {
                                    score: 10,
                                    isRelevant: true,
                                    confidence: "high",
                                    description: "AI-generated image",
                                    reasoning: "Generated specifically for this answer"
                                }
                            });

                        } catch (error) {
                            this.debugLog('dalle_parse_error', {
                                error: error.message,
                                response: data.substring(0, 200)
                            }, 'error');
                            reject(new Error(`DALL-E parse error: ${error.message}`));
                        }
                    });
                });

                req.on('error', (error) => {
                    this.debugLog('dalle_request_error', {
                        error: error.message,
                        answer
                    }, 'error');
                    reject(error);
                });
                
                req.on('timeout', () => {
                    req.destroy();
                    reject(new Error('DALL-E request timeout after 60 seconds'));
                });

                req.write(requestBody);
                req.end();
            });

        } catch (error) {
            this.debugLog('dalle_generation_failed', {
                error: error.message,
                answer
            }, 'error');
            throw new Error(`DALL-E generation failed: ${error.message}`);
        }
    }
}

export { ProgressivePuzzleSystem };
// oddOneOutPuzzle.js - V2.3 with aiClient integration

import crypto from 'crypto';
import { URL } from 'url';
import axios from 'axios';
import https from 'https';
import http from 'http';
import { callAI, callAIWithRetry } from '../utils/aiClient.js';

class OddOneOutPuzzleSystem {
    constructor(config = {}) {
        this.pixabayKey = process.env.PIXABAY_API_KEY;
        this.anthropicKey = process.env.ANTHROPIC_API_KEY;
        this.debugMode = config.debug || false;
        this.puzzleCache = new Map();
        this.duplicateHashes = new Set();
        this.maxRetries = config.maxRetries || 8;
        this.useAIFallback = config.useAIFallback !== false;
        
        // DEDUPLICATION: Track recent puzzles for similarity checking
        this.recentPuzzles = [];
        this.recentPuzzleLimit = config.recentPuzzleLimit || 10;
        this.similarityThreshold = config.similarityThreshold || 0.7;
        
        // Expanded hierarchical categories
        this.topicCategories = {
            animals: ['mammals', 'birds', 'reptiles', 'marine-life', 'insects', 'pets', 'wild-animals', 'farm-animals', 'endangered-species'],
            food: ['fruits', 'vegetables', 'desserts', 'beverages', 'grains', 'dairy', 'meat', 'seafood', 'international-cuisine'],
            vehicles: ['cars', 'aircraft', 'boats', 'trains', 'bikes', 'historic-vehicles', 'emergency-vehicles', 'space-vehicles'],
            nature: ['weather', 'landscapes', 'plants', 'minerals', 'seasons', 'natural-phenomena', 'ecosystems', 'celestial'],
            objects: ['tools', 'furniture', 'electronics', 'toys', 'kitchenware', 'art-supplies', 'office-items', 'household'],
            sports: ['ball-sports', 'water-sports', 'winter-sports', 'combat-sports', 'racket-sports', 'extreme-sports'],
            music: ['string-instruments', 'wind-instruments', 'percussion', 'keyboard-instruments', 'genres', 'composers'],
            science: ['physics', 'chemistry', 'astronomy', 'biology', 'geology', 'inventions', 'lab-equipment'],
            architecture: ['ancient', 'modern', 'residential', 'monuments', 'bridges', 'religious', 'sustainable'],
            geography: ['continents', 'countries', 'cities', 'mountains', 'rivers', 'landmarks', 'islands'],
            professions: ['medical', 'creative', 'technical', 'service', 'academic', 'trades', 'emergency'],
            colors: ['warm-colors', 'cool-colors', 'primary-colors', 'pastels', 'neon', 'earth-tones', 'patterns']
        };
        
        // Pattern types for varied logic
        this.patternTypes = [
            { name: 'category_exclusion', description: 'Three items from one category, one from another', weight: 3 },
            { name: 'attribute_difference', description: 'Three items sharing an attribute, one different', weight: 3 },
            { name: 'functional_difference', description: 'Three items with same purpose, one different', weight: 2 },
            { name: 'temporal_difference', description: 'Three items from same era/season, one different', weight: 2 },
            { name: 'size_difference', description: 'Three items of similar scale, one very different', weight: 2 },
            { name: 'origin_difference', description: 'Three items from same origin/region, one different', weight: 2 },
            { name: 'state_difference', description: 'Three items in same state (solid/liquid/gas), one different', weight: 1 },
            { name: 'sensory_difference', description: 'Three items with same sensory property, one different', weight: 1 }
        ];
        
        // Track recent usage for diversity
        this.recentCategories = [];
        this.recentPatterns = [];
        this.recentThemes = [];
        this.diversityWindowSize = config.diversityWindow || 15;
        
        // Thematic modifiers
        this.thematicModifiers = [
            'seasonal', 'cultural', 'temporal', 'geographic', 
            'functional', 'aesthetic', 'size-based', 'complexity-based'
        ];
        
        this.validateConfig();
    }

    validateConfig() {
        if (!this.pixabayKey) {
            throw new Error('PIXABAY_API_KEY environment variable required');
        }
        if (!this.anthropicKey) {
            console.warn('ANTHROPIC_API_KEY not set - AI fallback disabled');
            this.useAIFallback = false;
        }
        this.log('Enhanced API keys validated', 'success');
    }

    log(message, type = 'info') {
        if (!this.debugMode) return;
        const timestamp = new Date().toISOString();
        const emoji = {
            info: 'ℹ️',
            error: '❌',
            success: '✅',
            warning: '⚠️',
            debug: '🔍'
        };
        console.log(`[${timestamp}] ODD_ONE_OUT (${type.toUpperCase()}): ${emoji[type] || 'ℹ️'} ${message}`);
    }

    // Get unused category for diversity
    getRandomCategory() {
        const mainCategories = Object.keys(this.topicCategories);
        
        const availableMain = mainCategories.filter(
            cat => !this.recentCategories.some(recent => recent.main === cat)
        );
        
        const selectedMain = availableMain.length > 0
            ? availableMain[Math.floor(Math.random() * availableMain.length)]
            : mainCategories[Math.floor(Math.random() * mainCategories.length)];
        
        const subCategories = this.topicCategories[selectedMain];
        
        const availableSub = subCategories.filter(
            sub => !this.recentCategories.some(recent => recent.sub === sub)
        );
        
        const selectedSub = availableSub.length > 0
            ? availableSub[Math.floor(Math.random() * availableSub.length)]
            : subCategories[Math.floor(Math.random() * subCategories.length)];
        
        return { main: selectedMain, sub: selectedSub };
    }

    // Get unused pattern type (weighted random)
    getRandomPatternType() {
        const availablePatterns = this.patternTypes.filter(
            pattern => !this.recentPatterns.some(p => p.name === pattern.name)
        );
        
        const patternsToUse = availablePatterns.length > 0 ? availablePatterns : this.patternTypes;
        
        const totalWeight = patternsToUse.reduce((sum, p) => sum + p.weight, 0);
        let random = Math.random() * totalWeight;
        
        for (const pattern of patternsToUse) {
            random -= pattern.weight;
            if (random <= 0) {
                return pattern;
            }
        }
        
        return patternsToUse[0];
    }

    // Track usage for diversity
    trackUsage(category, pattern, theme) {
        this.recentCategories.push(category);
        this.recentPatterns.push(pattern);
        if (theme) this.recentThemes.push(theme);
        
        if (this.recentCategories.length > this.diversityWindowSize) {
            this.recentCategories.shift();
        }
        if (this.recentPatterns.length > this.diversityWindowSize) {
            this.recentPatterns.shift();
        }
        if (this.recentThemes.length > this.diversityWindowSize) {
            this.recentThemes.shift();
        }
    }

    // Apply thematic modifier
    applyThematicModifier(category, pattern) {
        if (Math.random() > 0.3) {
            const modifier = this.thematicModifiers[Math.floor(Math.random() * this.thematicModifiers.length)];
            return { modifier, text: `with ${modifier} focus` };
        }
        return { modifier: null, text: '' };
    }

    // DEDUPLICATION: Check if puzzle is too similar to recent ones
    isPuzzleTooSimilar(newPuzzle) {
        if (this.recentPuzzles.length === 0) {
            this.log('No recent puzzles to compare against', 'debug');
            return false;
        }
        
        for (const recentPuzzle of this.recentPuzzles) {
            const similarity = this.calculatePuzzleSimilarity(newPuzzle, recentPuzzle);
            
            if (similarity >= this.similarityThreshold) {
                this.log(`⚠️ Puzzle too similar (${(similarity * 100).toFixed(1)}%) to recent puzzle: "${recentPuzzle.scenario.name}"`, 'warning');
                this.log(`   New: ${newPuzzle.scenario.name}`, 'debug');
                this.log(`   New items: ${newPuzzle.images.map(i => i.item.name).join(', ')}`, 'debug');
                this.log(`   Recent items: ${recentPuzzle.images.map(i => i.item.name).join(', ')}`, 'debug');
                return true;
            }
        }
        
        this.log('✅ Puzzle is sufficiently different from recent puzzles', 'debug');
        return false;
    }

    // DEDUPLICATION: Calculate similarity between two puzzles
    calculatePuzzleSimilarity(puzzle1, puzzle2) {
        let similarityScore = 0;
        let breakdown = [];
        
        // Check 1: Same main category (weight: 0.25)
        if (puzzle1.theme === puzzle2.theme) {
            similarityScore += 0.25;
            breakdown.push('category(25%)');
        }
        
        // Check 2: Same sub-category (weight: 0.2)
        if (puzzle1.subTheme && puzzle2.subTheme && puzzle1.subTheme === puzzle2.subTheme) {
            similarityScore += 0.2;
            breakdown.push('sub-category(20%)');
        }
        
        // Check 3: Same pattern type (weight: 0.15)
        if (puzzle1.scenario.patternType === puzzle2.scenario.patternType) {
            similarityScore += 0.15;
            breakdown.push('pattern(15%)');
        }
        
        // Check 4: Similar scenario theme (weight: 0.2)
        const theme1Words = new Set(puzzle1.scenario.theme.toLowerCase().split(/\s+/).filter(w => w.length > 3));
        const theme2Words = new Set(puzzle2.scenario.theme.toLowerCase().split(/\s+/).filter(w => w.length > 3));
        const commonWords = [...theme1Words].filter(word => theme2Words.has(word));
        if (commonWords.length > 0 && (theme1Words.size > 0 || theme2Words.size > 0)) {
            const themeOverlap = commonWords.length / Math.max(theme1Words.size, theme2Words.size);
            const themeScore = 0.2 * themeOverlap;
            similarityScore += themeScore;
            breakdown.push(`theme(${(themeScore * 100).toFixed(0)}%)`);
        }
        
        // Check 5: Overlapping items (weight: 0.2)
        const items1 = new Set(puzzle1.images.map(img => img.item.name.toLowerCase().trim()));
        const items2 = new Set(puzzle2.images.map(img => img.item.name.toLowerCase().trim()));
        const commonItems = [...items1].filter(item => items2.has(item));
        if (commonItems.length > 0) {
            const itemScore = 0.2 * (commonItems.length / 4);
            similarityScore += itemScore;
            breakdown.push(`items(${(itemScore * 100).toFixed(0)}%)[${commonItems.join(',')}]`);
        }
        
        if (this.debugMode && similarityScore > 0.3) {
            this.log(`Similarity: ${(similarityScore * 100).toFixed(1)}% | Matches: ${breakdown.join(', ')}`, 'debug');
        }
        
        return similarityScore;
    }

    // DEDUPLICATION: Add puzzle to recent history
    addToRecentPuzzles(puzzle) {
        const puzzleSnapshot = {
            puzzleId: puzzle.puzzleId,
            theme: puzzle.theme,
            subTheme: puzzle.subTheme,
            scenario: {
                name: puzzle.scenario.name,
                theme: puzzle.scenario.theme,
                patternType: puzzle.scenario.patternType
            },
            images: puzzle.images.map(img => ({
                item: { name: img.item.name }
            })),
            generated: puzzle.generated
        };
        
        this.recentPuzzles.push(puzzleSnapshot);
        
        if (this.recentPuzzles.length > this.recentPuzzleLimit) {
            this.recentPuzzles.shift();
        }
        
        this.log(`Added to recent puzzles (now tracking ${this.recentPuzzles.length})`, 'debug');
    }

    // DEDUPLICATION: Load recent puzzles from database on startup
    async loadRecentPuzzlesFromDatabase() {
        try {
            const { supabase } = await import('../config/database.js');
            const { data, error } = await supabase
                .from('generated_puzzles')
                .select('puzzle_data')
                .order('created_at', { ascending: false })
                .limit(this.recentPuzzleLimit);
            
            if (error) throw error;
            
            if (data && data.length > 0) {
                this.recentPuzzles = data.map(row => ({
                    puzzleId: row.puzzle_data.puzzleId,
                    theme: row.puzzle_data.theme,
                    subTheme: row.puzzle_data.subTheme,
                    scenario: {
                        name: row.puzzle_data.scenario.name,
                        theme: row.puzzle_data.scenario.theme,
                        patternType: row.puzzle_data.scenario.patternType
                    },
                    images: row.puzzle_data.images.map(img => ({
                        item: { name: img.item.name }
                    })),
                    generated: row.puzzle_data.generated
                }));
                
                this.log(`Loaded ${this.recentPuzzles.length} recent puzzles from database`, 'success');
            }
        } catch (error) {
            this.log(`Could not load recent puzzles from database: ${error.message}`, 'warning');
        }
    }

    // Enhanced scenario generation with diversity
    async generatePuzzleScenarios(category, count = 1) {
        const patternType = this.getRandomPatternType();
        const thematicMod = this.applyThematicModifier(category, patternType);
        
        const categoryInfo = typeof category === 'object' 
            ? `${category.sub} (within ${category.main})`
            : category;

        const basePrompt = `Generate ${count} "odd one out" puzzle scenario for: ${categoryInfo}

RULES:
- Pattern: ${patternType.name} - ${patternType.description}
- Return ONLY valid JSON, NO extra text
- ALL fields must be filled (no empty "", no "...", no placeholders)
- pixabayQuery: simple 2-3 words (no quotes, no special chars)
- Be creative, avoid generic examples (no apple/orange/banana/carrot or lion/eagle/dolphin/elephant)

EXAMPLE (use this structure):
[{
  "name": "Frozen Desserts",
  "category": "${typeof category === 'object' ? category.main : category}",
  "subCategory": "${typeof category === 'object' ? category.sub : category}",
  "theme": "Frozen desserts",
  "explanation": "Cookie is not frozen",
  "difficulty": "easy",
  "patternType": "${patternType.name}",
  "items": [
    {"name": "Ice Cream", "pixabayQuery": "ice cream cone", "aiPrompt": "Vanilla ice cream cone, food photography", "belongsToGroup": true, "reason": "Frozen dessert"},
    {"name": "Popsicle", "pixabayQuery": "popsicle frozen", "aiPrompt": "Colorful popsicles, summer photo", "belongsToGroup": true, "reason": "Frozen dessert"},
    {"name": "Frozen Yogurt", "pixabayQuery": "frozen yogurt cup", "aiPrompt": "Frozen yogurt with toppings", "belongsToGroup": true, "reason": "Frozen dessert"},
    {"name": "Cookie", "pixabayQuery": "chocolate chip cookie", "aiPrompt": "Fresh baked cookie, food photo", "belongsToGroup": false, "reason": "Baked, not frozen"}
  ]
}]

Generate ${count} creative scenario(s) now:`;

        try {
            const response = await this.queryLLM(basePrompt, 'gpt-3.5-turbo', 'creative');
            
            this.log(`Raw LLM Response (first 300 chars): ${response.substring(0, 300)}`, 'debug');
            
            const scenarios = this.parseJSONResponse(response);
            
            if (scenarios.length === 0) {
                this.log(`No valid scenarios parsed, falling back to hardcoded scenarios`, 'warning');
                return this.getFallbackScenarios(category);
            }
            
            this.log(`Generated ${scenarios.length} diverse scenarios for ${categoryInfo}`, 'success');
            
            const validScenarios = scenarios.filter(scenario => this.validateScenarioStructure(scenario));
            
            if (validScenarios.length === 0) {
                this.log(`No valid scenarios after structure validation, using fallback`, 'warning');
                return this.getFallbackScenarios(category);
            }
            
            return this.selectRandomScenarios(validScenarios, 1);
            
        } catch (error) {
            this.log(`Error generating scenarios for ${categoryInfo}: ${error.message}`, 'error');
            return this.getFallbackScenarios(category);
        }
    }

    selectRandomScenarios(scenarios, count) {
        if (scenarios.length === 0) return [];
        if (scenarios.length <= count) return scenarios;
        
        const shuffled = [...scenarios].sort(() => Math.random() - 0.5);
        return shuffled.slice(0, count);
    }

    validateScenarioStructure(scenario) {
        if (!scenario.items || scenario.items.length !== 4) {
            this.log(`Invalid scenario: wrong number of items (${scenario.items?.length})`, 'debug');
            return false;
        }
        
        // Check each item has required fields and they're not empty/placeholder
        const isValid = scenario.items.every(item => {
            const hasName = item.name && item.name.length > 0 && item.name !== '...' && item.name !== 'placeholder';
            const hasQuery = item.pixabayQuery && item.pixabayQuery.length > 0 && item.pixabayQuery !== '...' && item.pixabayQuery !== 'placeholder';
            const hasPrompt = item.aiPrompt && item.aiPrompt.length > 0 && item.aiPrompt !== '...' && item.aiPrompt !== 'placeholder';
            const hasReason = item.reason && item.reason.length > 0 && item.reason !== '...' && item.reason !== 'placeholder';
            
            if (!hasName || !hasQuery || !hasPrompt || !hasReason) {
                this.log(`Invalid item: ${item.name} - missing fields`, 'debug');
                return false;
            }
            
            return true;
        });
        
        const hasOneOdd = scenario.items.filter(item => !item.belongsToGroup).length === 1;
        
        if (!isValid || !hasOneOdd) {
            this.log(`Scenario validation failed: ${!isValid ? 'incomplete fields' : 'wrong odd count'}`, 'debug');
        }
        
        return isValid && hasOneOdd;
    }

    parseJSONResponse(response) {
        try {
            // Try to extract JSON from code blocks first
            const codeBlockMatch = response.match(/```json\s*([\s\S]*?)\s*```/);
            if (codeBlockMatch) {
                return JSON.parse(this.cleanJSON(codeBlockMatch[1]));
            }
            
            // Try to find JSON array in response
            const jsonMatch = response.match(/\[[\s\S]*\]/);
            if (jsonMatch) {
                return JSON.parse(this.cleanJSON(jsonMatch[0]));
            }
            
            // Try direct parse
            return JSON.parse(this.cleanJSON(response));
            
        } catch (error) {
            this.log(`Failed to parse JSON response: ${error.message}`, 'error');
            this.log(`Response preview: ${response.substring(0, 500)}...`, 'debug');
            return [];
        }
    }

    // Clean common JSON formatting issues from LLM responses
    cleanJSON(jsonString) {
        // Remove trailing commas before closing brackets/braces
        let cleaned = jsonString
            // Remove comments
            .replace(/\/\*[\s\S]*?\*\//g, '')
            .replace(/\/\/.*/g, '')
            // Fix escaped quotes inside values (common LLM mistake): "name": "\"Ocean Breeze\"" -> "name": "Ocean Breeze"
            .replace(/:\s*"\\"/g, ': "')
            .replace(/\\""/g, '"')
            // Remove trailing commas
            .replace(/,(\s*[}\]])/g, '$1')
            // Fix empty string values
            .replace(/:\s*""\s*,/g, ': null,')
            .replace(/:\s*""\s*}/g, ': null}')
            // Fix placeholder values like "..." 
            .replace(/:\s*"\.\.\."/g, ': "placeholder"')
            // Fix unquoted property names (common LLM mistake)
            .replace(/(\{|,)\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*:/g, '$1"$2":');
        
        return cleaned;
    }

    // Generate varied and specific search terms for Pixabay
    generateSearchVariations(baseQuery) {
        const variations = [baseQuery];
        
        // More aggressive modifiers for better Pixabay results
        variations.push(`${baseQuery} isolated white background`);
        variations.push(`${baseQuery} professional photo`);
        variations.push(`${baseQuery} closeup detailed`);
        variations.push(`single ${baseQuery} clear background`);
        variations.push(`${baseQuery} studio shot`);
        
        // Add single-word simplification as last resort
        const words = baseQuery.split(/\s+/);
        if (words.length > 1) {
            variations.push(words[0]);
            variations.push(words[words.length - 1]);
        }
        
        this.log(`Generated ${variations.length} search variations for: ${baseQuery}`, 'debug');
        return variations;
    }

    // Acquire images for puzzle items (with DALL-E fallback and better error handling)
    async acquireImages(scenario) {
        this.log(`Acquiring images for scenario: ${scenario.name}`, 'info');
        
        const images = [];
        const failedItems = [];
        
        for (let i = 0; i < scenario.items.length; i++) {
            const item = scenario.items[i];
            let imageAcquired = false;
            
            try {
                // Try multiple search variations for Pixabay
                const searchVariations = this.generateSearchVariations(item.pixabayQuery);
                
                for (let searchAttempt = 0; searchAttempt < searchVariations.length && !imageAcquired; searchAttempt++) {
                    const searchQuery = searchVariations[searchAttempt];
                    this.log(`Trying Pixabay search ${searchAttempt + 1}/${searchVariations.length} for ${item.name}: "${searchQuery}"`, 'debug');
                    
                    const pixabayResult = await this.tryPixabaySearch(item, searchQuery);
                    
                    if (pixabayResult.success) {
                        images.push({
                            ...pixabayResult,
                            item,
                            index: i,
                            isOddOneOut: !item.belongsToGroup
                        });
                        imageAcquired = true;
                        this.log(`✅ Pixabay success for ${item.name}`, 'success');
                        break;
                    }
                }
                
                // DALL-E FALLBACK: If all Pixabay searches failed
                if (!imageAcquired && this.useAIFallback) {
                    this.log(`⚠️ All Pixabay attempts failed for ${item.name}, using DALL-E`, 'warning');
                    const aiResult = await this.tryAIGeneration(item, `${scenario.name}_${i}`);
                    
                    if (aiResult.success) {
                        images.push({
                            ...aiResult,
                            item,
                            index: i,
                            isOddOneOut: !item.belongsToGroup
                        });
                        imageAcquired = true;
                        this.log(`✅ DALL-E success for ${item.name}`, 'success');
                    }
                }
                
                if (!imageAcquired) {
                    failedItems.push(item.name);
                    this.log(`❌ Could not acquire image for ${item.name} via any method`, 'error');
                }
                
            } catch (error) {
                failedItems.push(item.name);
                this.log(`Failed to acquire image for ${item.name}: ${error.message}`, 'error');
            }
        }
        
        if (failedItems.length > 0) {
            this.log(`⚠️ Failed to acquire ${failedItems.length} images: ${failedItems.join(', ')}`, 'warning');
        }
        
        return images;
    }

    async tryPixabaySearch(item, searchQuery = null) {
        try {
            const query = searchQuery || item.pixabayQuery;
            this.log(`Searching Pixabay for: ${query}`, 'info');
            
            const images = await this.searchPixabayImages(query, 10);
            if (!images.length) {
                this.log(`No Pixabay results for: ${query}`, 'debug');
                return { success: false, error: 'No Pixabay images found' };
            }

            // Try multiple images if download fails
            const maxImageAttempts = Math.min(images.length, 3);
            
            for (let imgIndex = 0; imgIndex < maxImageAttempts; imgIndex++) {
                const selectedImage = images[imgIndex];
                
                try {
                    this.log(`Attempting download ${imgIndex + 1}/${maxImageAttempts} for ${item.name}`, 'debug');
                    const imageBuffer = await this.downloadImage(selectedImage.largeImageURL || selectedImage.webformatURL);
                    
                    const uploadResult = await this.uploadImageToSupabase(
                        `odd-${item.name.replace(/\s+/g, '-')}-${Date.now()}`, 
                        imageBuffer,
                        selectedImage.largeImageURL
                    );
                    
                    if (uploadResult.success) {
                        return {
                            success: true,
                            url: uploadResult.supabaseUrl,
                            fileName: uploadResult.fileName,
                            originalSource: selectedImage.largeImageURL,
                            photographer: selectedImage.photographer,
                            uploadedAt: uploadResult.uploadedAt,
                            source: 'pixabay'
                        };
                    }
                } catch (imageError) {
                    this.log(`Failed to process Pixabay image ${imgIndex + 1}: ${imageError.message}`, 'debug');
                }
            }
            
            return { success: false, error: 'Failed to process any Pixabay images' };
            
        } catch (error) {
            return { success: false, error: error.message };
        }
    }

    async tryAIGeneration(item, puzzleId) {
        try {
            this.log(`Generating AI image for: ${item.name}`, 'info');
            
            const enhancedPrompt = `${item.aiPrompt}, high quality, professional photography style, clean composition, detailed`;
            
            const imageBuffer = await this.generateAIImage(enhancedPrompt);
            
            const uploadResult = await this.uploadImageToSupabase(
                `ai-odd-${puzzleId}`, 
                imageBuffer
            );
            
            if (uploadResult.success) {
                return {
                    success: true,
                    url: uploadResult.supabaseUrl,
                    fileName: uploadResult.fileName,
                    originalPrompt: enhancedPrompt,
                    uploadedAt: uploadResult.uploadedAt,
                    source: 'dall-e-3',
                    isGenerated: true
                };
            }
            
            return { success: false, error: 'Failed to upload AI image to Supabase' };
            
        } catch (error) {
            this.log(`AI generation failed: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    async generateAIImage(prompt) {
        try {
            this.log(`Generating DALL-E image...`, 'info');
            
            // Use aiClient's generateImage function
            const { generateImage } = await import('../utils/aiClient.js');
            const imageBuffer = await generateImage(prompt, {
                model: "dall-e-3",
                size: "1024x1024",
                quality: "hd",
                style: "natural",
                category: 'puzzle_generation',
                puzzleType: 'odd_one_out'
            });

            this.log(`DALL-E image generated successfully`, 'success');
            return imageBuffer;

        } catch (error) {
            this.log(`Image generation error: ${error.message}`, 'error');
            throw error;
        }
    }

    async uploadImageToSupabase(puzzleId, imageBuffer, originalUrl = '') {
        try {
            const { supabase } = await import('../config/database.js');
            const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
            const hash = crypto.createHash('md5').update(originalUrl || puzzleId.toString()).digest('hex').substring(0, 8);
            const fileName = `odd-one-out/${puzzleId}-${hash}-${timestamp}.jpg`;
            
            this.log(`Uploading to Supabase: ${fileName}`, 'debug');
            
            const { error } = await supabase.storage
                .from('puzzle-images')
                .upload(fileName, imageBuffer, { 
                    contentType: 'image/jpeg',
                    upsert: true,
                    cacheControl: '31536000'
                });

            if (error) {
                throw error;
            }

            const { data: { publicUrl } } = supabase.storage
                .from('puzzle-images')
                .getPublicUrl(fileName);

            this.log(`Successfully uploaded to Supabase: ${publicUrl}`, 'success');

            return {
                success: true,
                supabaseUrl: publicUrl,
                fileName: fileName,
                uploadedAt: new Date().toISOString()
            };

        } catch (error) {
            this.log(`Supabase upload failed: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    async searchPixabayImages(searchQuery, perPage = 10) {
        const axios = require('axios');
        const url = `https://pixabay.com/api/?key=${this.pixabayKey}&q=${encodeURIComponent(searchQuery)}&image_type=photo&per_page=${perPage}&safesearch=true&min_width=400&min_height=400&order=popular`;
        
        try {
            const response = await axios.get(url, { timeout: 10000 });
            
            if (response.status < 200 || response.status >= 300) {
                throw new Error(`Pixabay API error: ${response.status}`);
            }
            
            return response.data.hits.map(hit => ({
                id: hit.id,
                largeImageURL: hit.largeImageURL,
                webformatURL: hit.webformatURL,
                photographer: hit.user,
                width: hit.imageWidth,
                height: hit.imageHeight,
                tags: hit.tags,
                views: hit.views,
                downloads: hit.downloads,
                likes: hit.likes
            }));
            
        } catch (error) {
            this.log(`Pixabay search error: ${error.message}`, 'error');
            return [];
        }
    }

    async generatePuzzleQuestions(scenario, images) {
        const oddItem = images.find(img => img.isOddOneOut);
        const groupItems = images.filter(img => !img.isOddOneOut);
        
        const prompt = `Create questions for an "odd one out" puzzle.

Scenario: ${scenario.theme}
Items: ${images.map(img => img.item.name).join(', ')}
Odd One Out: ${oddItem?.item.name}
Group Theme: ${scenario.explanation}

Generate 2 questions:
1. Direct "which is odd" question
2. "What do the other 3 have in common" question

Return as JSON:
[
  {
    "question": "Which item is the odd one out?",
    "type": "single_choice",
    "options": ["${images[0]?.item.name}", "${images[1]?.item.name}", "${images[2]?.item.name}", "${images[3]?.item.name}"],
    "correctAnswer": "${oddItem?.item.name}",
    "explanation": "${scenario.explanation}",
    "points": 15,
    "hint": "Look for what the other three have in common"
  },
  {
    "question": "What do the other three items have in common?",
    "type": "text_input",
    "correctAnswer": "${scenario.theme}",
    "explanation": "The three items share: ${scenario.theme}",
    "points": 10,
    "hint": "Think about categories or properties"
  }
]`;

        try {
            const response = await this.queryLLM(prompt, 'gpt-3.5-turbo');
            return this.parseJSONResponse(response);
        } catch (error) {
            this.log(`Question generation failed: ${error.message}`, 'error');
            return this.getFallbackQuestions(scenario, images);
        }
    }

    // MAIN PUZZLE GENERATION (ENHANCED WITH DEDUPLICATION)
    async generateCompletePuzzle(category = null, difficulty = null, imageCount = 4) {
        const maxRetries = 8;
        const maxDedupRetries = 5;
        
        // Load recent puzzles on first run
        if (this.recentPuzzles.length === 0) {
            await this.loadRecentPuzzlesFromDatabase();
        }
        
        const difficultyLevels = ['easy', 'medium', 'hard'];
        const selectedDifficulty = difficulty || difficultyLevels[Math.floor(Math.random() * difficultyLevels.length)];
        
        let dedupRetryCount = 0;
        let totalAttempts = 0;
        
        while (totalAttempts < maxRetries) {
            totalAttempts++;
            
            try {
                const selectedCategory = category || this.getRandomCategory();
                const patternType = this.getRandomPatternType();
                
                const categoryDisplay = typeof selectedCategory === 'object'
                    ? `${selectedCategory.main}/${selectedCategory.sub}`
                    : selectedCategory;
                
                this.log(`\n🎲 Generating puzzle: ${categoryDisplay} [${patternType.name}] (total attempt ${totalAttempts}/${maxRetries}, dedup retry ${dedupRetryCount}/${maxDedupRetries})`, 'info');
                
                const scenarios = await this.generatePuzzleScenarios(selectedCategory, 5);
                if (!scenarios.length) {
                    this.log(`No scenarios generated for category: ${categoryDisplay}`, 'warning');
                    continue;
                }

                const scenario = scenarios[0];
                this.log(`Selected scenario: "${scenario.name}"`, 'debug');

                const puzzleHash = this.generatePuzzleHash(scenario, patternType.name, selectedDifficulty);
                
                if (await this.isPuzzleDuplicate(puzzleHash)) {
                    this.log(`Duplicate hash detected, retrying`, 'warning');
                    continue;
                }

                const images = await this.acquireImages(scenario);
                
                if (images.length < 4) {
                    this.log(`❌ Only acquired ${images.length}/4 images, retrying with new scenario`, 'warning');
                    continue;
                }

                this.shuffleArray(images);
                const questions = await this.generatePuzzleQuestions(scenario, images);

                const puzzle = {
                    puzzleId: puzzleHash,
                    type: 'odd_one_out',
                    theme: typeof selectedCategory === 'object' ? selectedCategory.main : selectedCategory,
                    subTheme: typeof selectedCategory === 'object' ? selectedCategory.sub : null,
                    title: `Odd One Out - ${this.getThemeDisplayName(selectedCategory)}`,
                    description: `Look at the images carefully and find the one that doesn't belong with the others.`,
                    scenario: {
                        name: scenario.name,
                        theme: scenario.theme,
                        explanation: scenario.explanation,
                        difficulty: scenario.difficulty,
                        patternType: patternType.name
                    },
                    totalImages: images.length,
                    images: images.map(img => ({
                        id: crypto.randomUUID(),
                        url: img.url,
                        fileName: img.fileName,
                        source: img.source,
                        item: {
                            name: img.item.name,
                            belongsToGroup: img.item.belongsToGroup,
                            reason: img.item.reason
                        },
                        isOddOneOut: img.isOddOneOut,
                        photographer: img.photographer,
                        uploadedAt: img.uploadedAt,
                        originalIndex: img.index
                    })),
                    questions: questions,
                    difficulty: selectedDifficulty,
                    timeLimit: 30000,
                    instructions: {
                        task: 'Identify which item is the odd one out',
                        method: 'Find the pattern among 3 items, identify what breaks it',
                        scoring: 'Points awarded for correct identification and explanation'
                    },
                    correctAnswer: {
                        oddOneOut: images.find(img => img.isOddOneOut)?.item.name,
                        theme: scenario.theme,
                        explanation: scenario.explanation
                    },
                    generated: new Date().toISOString(),
                    apiStats: {
                        scenario: scenario.name,
                        pixabayImages: images.filter(img => img.source === 'pixabay').length,
                        aiImages: images.filter(img => img.source === 'dall-e-3').length,
                        storageProvider: 'supabase',
                        modelUsed: 'gpt-3.5-turbo',
                        patternType: patternType.name,
                        totalAttempts: totalAttempts,
                        dedupRetries: dedupRetryCount
                    },
                    version: '2.2-complete',
                    generationMethod: 'odd_one_out_diversified_deduped'
                };

                // DEDUPLICATION CHECK
                if (this.isPuzzleTooSimilar(puzzle)) {
                    dedupRetryCount++;
                    
                    if (dedupRetryCount < maxDedupRetries) {
                        this.log(`🔄 Puzzle too similar, generating completely new puzzle (dedup retry ${dedupRetryCount}/${maxDedupRetries})`, 'warning');
                        category = null; // Force new category
                        continue;
                    } else {
                        this.log(`⚠️ Max deduplication retries reached (${maxDedupRetries}), accepting this puzzle`, 'warning');
                    }
                }

                this.trackUsage(selectedCategory, patternType, scenario.theme);
                this.addToRecentPuzzles(puzzle);
                await this.storePuzzleHash(puzzleHash, puzzle);
                this.puzzleCache.set(puzzleHash, puzzle);

                this.log(`✨ Successfully generated diverse puzzle: "${scenario.name}" (${dedupRetryCount} dedup retries, ${totalAttempts} total attempts)`, 'success');
                this.log(`   Items: ${puzzle.images.map(i => i.item.name).join(', ')}`, 'success');
                this.log(`   Sources: ${puzzle.apiStats.pixabayImages} Pixabay, ${puzzle.apiStats.aiImages} DALL-E`, 'success');
                
                return { success: true, puzzle };

            } catch (error) {
                this.log(`❌ Attempt ${totalAttempts} failed: ${error.message}`, 'error');
                if (totalAttempts >= maxRetries) {
                    return { 
                        success: false, 
                        error: `Failed after ${totalAttempts} attempts: ${error.message}`,
                        attempts: totalAttempts,
                        dedupRetries: dedupRetryCount
                    };
                }
            }
        }
        
        return { 
            success: false, 
            error: `Failed to generate puzzle after ${maxRetries} attempts`,
            attempts: totalAttempts,
            dedupRetries: dedupRetryCount
        };
    }

    generatePuzzleHash(scenario, patternType, difficulty) {
        const hashContent = `${scenario.name}_${patternType}_${difficulty}_${Date.now()}`;
        return crypto.createHash('md5').update(hashContent).digest('hex');
    }

    async isPuzzleDuplicate(puzzleHash) {
        if (this.duplicateHashes.has(puzzleHash)) {
            return true;
        }

        try {
            const { supabase } = await import('../config/database.js');
            const { data } = await supabase
                .from('generated_puzzles')
                .select('id')
                .eq('puzzle_hash', puzzleHash)
                .single();
            
            if (data) {
                this.duplicateHashes.add(puzzleHash);
                return true;
            }
            return false;
        } catch (error) {
            return false;
        }
    }

    async storePuzzleHash(puzzleHash, puzzleData) {
        this.duplicateHashes.add(puzzleHash);
        
        try {
            const { supabase } = await import('../config/database.js');
            await supabase
                .from('generated_puzzles')
                .insert({
                    puzzle_hash: puzzleHash,
                    puzzle_data: puzzleData,
                    created_at: new Date().toISOString()
                });
        } catch (error) {
            this.log(`Failed to store puzzle hash: ${error.message}`, 'warning');
        }
    }

    shuffleArray(array) {
        for (let i = array.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [array[i], array[j]] = [array[j], array[i]];
        }
    }

    getThemeDisplayName(category) {
        if (typeof category === 'object') {
            return `${category.main.charAt(0).toUpperCase() + category.main.slice(1)} - ${category.sub}`;
        }
        
        const displayNames = {
            animals: 'Animals & Wildlife',
            food: 'Food & Cuisine', 
            vehicles: 'Vehicles & Transportation',
            colors: 'Colors & Patterns',
            shapes: 'Shapes & Geometry',
            sports: 'Sports & Games',
            music: 'Musical Elements',
            nature: 'Natural Wonders',
            objects: 'Objects & Items',
            science: 'Science & Discovery',
            architecture: 'Architecture & Buildings',
            geography: 'Geography & Places',
            professions: 'Jobs & Careers'
        };
        return displayNames[category] || category.charAt(0).toUpperCase() + category.slice(1);
    }

    getFallbackScenarios(category) {
        const catName = typeof category === 'object' ? category.main : category;
        
        const fallbacks = {
            animals: [{
                name: "Mammals vs Birds",
                category: catName,
                theme: "Mammals",
                explanation: "Eagle is the only bird among mammals",
                difficulty: "easy",
                items: [
                    { name: "Lion", pixabayQuery: "lion wild animal", aiPrompt: "Lion in savanna, wildlife photography", belongsToGroup: true, reason: "Mammal" },
                    { name: "Elephant", pixabayQuery: "elephant wildlife", aiPrompt: "African elephant, detailed wildlife photo", belongsToGroup: true, reason: "Mammal" },
                    { name: "Dolphin", pixabayQuery: "dolphin ocean", aiPrompt: "Dolphin jumping from ocean, marine photography", belongsToGroup: true, reason: "Mammal" },
                    { name: "Eagle", pixabayQuery: "eagle bird flying", aiPrompt: "Bald eagle soaring, bird photography", belongsToGroup: false, reason: "Bird, not mammal" }
                ]
            }],
            food: [{
                name: "Fruits vs Vegetables", 
                category: catName,
                theme: "Fruits",
                explanation: "Carrot is a vegetable, others are fruits",
                difficulty: "easy",
                items: [
                    { name: "Apple", pixabayQuery: "red apple fruit", aiPrompt: "Fresh red apple, food photography", belongsToGroup: true, reason: "Fruit" },
                    { name: "Orange", pixabayQuery: "orange citrus fruit", aiPrompt: "Fresh orange, citrus photography", belongsToGroup: true, reason: "Fruit" },
                    { name: "Banana", pixabayQuery: "banana yellow fruit", aiPrompt: "Ripe yellow banana, fruit photography", belongsToGroup: true, reason: "Fruit" },
                    { name: "Carrot", pixabayQuery: "carrot vegetable", aiPrompt: "Fresh orange carrot, vegetable photography", belongsToGroup: false, reason: "Vegetable" }
                ]
            }]
        };
        
        return fallbacks[catName] || fallbacks.animals;
    }

    getFallbackQuestions(scenario, images) {
        const oddItem = images.find(img => img.isOddOneOut);
        return [{
            question: "Which item is the odd one out?",
            type: "single_choice",
            options: images.map(img => img.item.name),
            correctAnswer: oddItem?.item.name,
            explanation: scenario.explanation,
            points: 15,
            hint: "Look for the item that doesn't fit the pattern"
        }];
    }

    async queryLLM(prompt, model = 'gpt-3.5-turbo', creativity = 'balanced') {
        const creativitySettings = {
            conservative: { temperature: 0.3, top_p: 0.9, presence_penalty: 0.3, frequency_penalty: 0.3 },
            balanced: { temperature: 0.7, top_p: 0.95, presence_penalty: 0.6, frequency_penalty: 0.5 },
            creative: { temperature: 0.9, top_p: 0.98, presence_penalty: 0.8, frequency_penalty: 0.7 }
        };
        
        const settings = creativitySettings[creativity] || creativitySettings.balanced;
        
        try {
            this.log(`Calling AI with model: ${model}, creativity: ${creativity}`, 'debug');
            
            // Use aiClient with retry logic
            const response = await callAIWithRetry(prompt, model, 3);
            
            this.log(`Received AI response (${response.length} chars)`, 'debug');
            return response;

        } catch (error) {
            this.log(`LLM query failed: ${error.message}`, 'error');
            throw new Error(`LLM query failed: ${error.message}`);
        }
    }

    async downloadImage(imageUrl) {
        return new Promise((resolve, reject) => {
            try {
                const https = require('https');
                const http = require('http');
                const urlObj = new URL(imageUrl);
                const protocol = urlObj.protocol === 'https:' ? https : http;
                
                const req = protocol.request(urlObj, (res) => {
                    if (res.statusCode !== 200) {
                        reject(new Error(`Download failed: ${res.statusCode}`));
                        return;
                    }

                    const chunks = [];
                    res.on('data', chunk => chunks.push(chunk));
                    res.on('end', () => resolve(Buffer.concat(chunks)));
                });
                
                req.on('error', reject);
                req.setTimeout(30000, () => {
                    req.destroy();
                    reject(new Error('Download timeout'));
                });
                req.end();
            } catch (error) {
                reject(error);
            }
        });
    }

    displayPuzzle(puzzle) {
        console.log('\n🎯 "ODD ONE OUT" PUZZLE SYSTEM V2.2');
        console.log('═'.repeat(60));
        console.log(`📋 Title: ${puzzle.title}`);
        console.log(`🎯 Theme: ${puzzle.theme}${puzzle.subTheme ? ` / ${puzzle.subTheme}` : ''}`);
        console.log(`💡 Scenario: ${puzzle.scenario.name}`);
        console.log(`🔗 Connection: ${puzzle.scenario.theme}`);
        console.log(`❌ Odd One: ${puzzle.scenario.explanation}`);
        console.log(`🎨 Pattern: ${puzzle.scenario.patternType}`);
        console.log(`⚡ Difficulty: ${puzzle.difficulty}`);
        console.log(`🖼️ Total Images: ${puzzle.totalImages}`);
        console.log(`⏱️ Time Limit: ${puzzle.timeLimit / 1000}s`);
        
        console.log('\n🖼️ ITEMS:');
        puzzle.images.forEach((img, index) => {
            const status = img.isOddOneOut ? '❌ ODD ONE OUT' : '✅ BELONGS TO GROUP';
            
            console.log(`\n${index + 1}. ${img.item.name}`);
            console.log(`   Status: ${status}`);
            console.log(`   Reason: ${img.item.reason}`);
            console.log(`   Source: ${img.source}`);
            if (img.photographer) {
                console.log(`   Photo by: ${img.photographer}`);
            }
        });
        
        console.log('\n❓ QUESTIONS:');
        puzzle.questions.forEach((q, index) => {
            console.log(`\n${index + 1}. ${q.question}`);
            console.log(`   Type: ${q.type}`);
            console.log(`   Correct Answer: ${q.correctAnswer}`);
            console.log(`   Points: ${q.points}`);
        });
        
        console.log('\n📊 STATS:');
        console.log(`   Pixabay Images: ${puzzle.apiStats.pixabayImages}`);
        console.log(`   AI Generated: ${puzzle.apiStats.aiImages}`);
        console.log(`   Pattern Type: ${puzzle.apiStats.patternType}`);
        console.log(`   Total Attempts: ${puzzle.apiStats.totalAttempts}`);
        console.log(`   Dedup Retries: ${puzzle.apiStats.dedupRetries}`);
        console.log(`   Version: ${puzzle.version}`);
        console.log('═'.repeat(60));
    }

    async testDiversification(runs = 10) {
        console.log(`\n🧪 Testing Puzzle Diversification + Deduplication (${runs} runs)...\n`);
        
        const puzzles = [];
        const categories = new Set();
        const themes = new Set();
        const patterns = new Set();
        const difficulties = new Set();
        
        for (let i = 0; i < runs; i++) {
            const result = await this.generateCompletePuzzle();
            
            if (result.success) {
                puzzles.push(result.puzzle);
                categories.add(result.puzzle.theme);
                themes.add(result.puzzle.scenario.theme);
                patterns.add(result.puzzle.scenario.patternType);
                difficulties.add(result.puzzle.difficulty);
                
                console.log(`✅ Puzzle ${i+1}: ${result.puzzle.theme} - ${result.puzzle.scenario.name} [${result.puzzle.scenario.patternType}]`);
                console.log(`   Items: ${result.puzzle.images.map(img => img.item.name).join(', ')}`);
            } else {
                console.log(`❌ Puzzle ${i+1}: Failed`);
            }
        }
        
        console.log('\n📊 DIVERSIFICATION ANALYSIS:');
        console.log(`   Unique Categories: ${categories.size}/${runs} (${(categories.size/runs*100).toFixed(1)}%)`);
        console.log(`   Unique Themes: ${themes.size}/${runs} (${(themes.size/runs*100).toFixed(1)}%)`);
        console.log(`   Unique Patterns: ${patterns.size}/${runs} (${(patterns.size/runs*100).toFixed(1)}%)`);
        console.log(`   Unique Difficulties: ${difficulties.size}/${runs}`);
        console.log(`   Overall Diversity Score: ${((categories.size + themes.size + patterns.size) / (runs * 3) * 100).toFixed(1)}%`);
        console.log(`\n🔍 DEDUPLICATION:`);
        console.log(`   Recent puzzles tracked: ${this.recentPuzzles.length}`);
        console.log(`   Similarity threshold: ${(this.similarityThreshold * 100).toFixed(0)}%`);
        
        return {
            puzzles,
            stats: {
                categories: categories.size,
                themes: themes.size,
                patterns: patterns.size,
                difficulties: difficulties.size,
                diversityScore: (categories.size + themes.size + patterns.size) / (runs * 3),
                recentPuzzlesTracked: this.recentPuzzles.length
            }
        };
    }
}

export { OddOneOutPuzzleSystem };

console.log(`
✨ "ODD ONE OUT" PUZZLE SYSTEM - V2.3 (WITH AI CLIENT)

🔥 FEATURES:
   • 12+ main categories with 80+ sub-categories
   • 8 different logical pattern types
   • Weighted random selection
   • Recent usage tracking (avoids repetition)
   • Thematic modifiers for variety
   • Randomized difficulty levels
   • Multiple Pixabay search variations (7 per item)
   • Automatic DALL-E fallback for failed searches
   • AI Client with model rotation (gpt-3.5, deepseek, claude, gemini)
   • Better retry logic and error handling

🛡️ DEDUPLICATION:
   • Tracks last 10 generated puzzles
   • Multi-factor similarity scoring (5 factors)
   • 70% similarity threshold
   • Automatic retry on similar puzzle detection
   • Loads recent puzzles from database
   • Separate retry counters (8 total, 5 dedup)
   • Detailed similarity logging

📊 SIMILARITY CHECKS:
   • Main category: 25%
   • Sub-category: 20%
   • Pattern type: 15%
   • Theme overlap: 20%
   • Item overlap: 20%

🎯 USAGE:
   const system = new OddOneOutPuzzleSystem({ 
     debug: true,
     recentPuzzleLimit: 10,
     similarityThreshold: 0.7
   });
   const result = await system.generateCompletePuzzle();
   
🧪 TEST:
   await system.testDiversification(10);
`);
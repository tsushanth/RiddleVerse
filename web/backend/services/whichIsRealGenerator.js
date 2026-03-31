// services/whichIsRealGenerator.js - AI Literacy Education Puzzle Generator

import axios from 'axios';
import https from 'https';
import http from 'http';

export class WhichIsRealGenerator {
    constructor() {
        this.debugMode = false;
        this.pixabayKey = process.env.PIXABAY_API_KEY || "51870401-10c08576f17e1cb89793c39c7";
        this.openaiKey = process.env.OPENAI_API_KEY || "YOUR_OPENAI_KEY_HERE";
        
        // Category themes for diverse puzzles - expanded query pool for variety
        this.categories = [
            {
                name: 'landmarks',
                displayName: 'Famous Landmarks',
                queries: [
                    'eiffel tower paris',
                    'colosseum rome',
                    'taj mahal india',
                    'great wall china',
                    'statue liberty new york',
                    'big ben london',
                    'golden gate bridge',
                    'pyramids egypt giza',
                    'machu picchu peru',
                    'sydney opera house',
                    'petra jordan',
                    'angkor wat cambodia',
                    'stonehenge england',
                    'christ redeemer rio',
                    'acropolis athens',
                    'neuschwanstein castle'
                ]
            },
            {
                name: 'animals',
                displayName: 'Animals & Wildlife',
                queries: [
                    'golden retriever dog portrait',
                    'tabby cat close up',
                    'african elephant',
                    'red fox nature',
                    'owl bird perched',
                    'tiger face closeup',
                    'dolphin swimming',
                    'butterfly on flower',
                    'panda bear eating bamboo',
                    'polar bear arctic',
                    'peacock feathers display',
                    'hummingbird flower',
                    'koala eucalyptus tree',
                    'giraffe savanna',
                    'penguin antarctica',
                    'sea turtle underwater',
                    'wolf howling',
                    'parrot colorful bird',
                    'deer forest',
                    'horse running field'
                ]
            },
            {
                name: 'food',
                displayName: 'Food & Cuisine',
                queries: [
                    'sushi plate restaurant',
                    'burger fries meal',
                    'pasta italian dish',
                    'pizza margherita',
                    'fruit salad bowl',
                    'chocolate cake dessert',
                    'coffee latte art',
                    'fresh vegetables market',
                    'steak dinner plate',
                    'ice cream sundae',
                    'tacos mexican food',
                    'croissant breakfast',
                    'ramen noodles bowl',
                    'pancakes maple syrup',
                    'grilled salmon',
                    'fresh baked bread',
                    'colorful macarons',
                    'curry indian food',
                    'dim sum chinese'
                ]
            },
            {
                name: 'nature',
                displayName: 'Natural Wonders',
                queries: [
                    'mountain lake landscape',
                    'sunset beach ocean',
                    'waterfall rainforest',
                    'cherry blossom tree',
                    'autumn forest path',
                    'desert sand dunes',
                    'northern lights aurora',
                    'tropical island paradise',
                    'grand canyon view',
                    'lavender field provence',
                    'coral reef underwater',
                    'volcano eruption',
                    'glacier ice cave',
                    'redwood forest trees',
                    'tulip field netherlands',
                    'lightning storm',
                    'rainbow over valley',
                    'starry night sky milky way',
                    'foggy mountain morning',
                    'crystal clear lake reflection'
                ]
            },
            {
                name: 'architecture',
                displayName: 'Architecture & Buildings',
                queries: [
                    'modern skyscraper city',
                    'historic cathedral interior',
                    'japanese temple garden',
                    'victorian house exterior',
                    'suspension bridge architecture',
                    'ancient ruins columns',
                    'art deco building',
                    'contemporary museum',
                    'gothic church architecture',
                    'mosque interior dome',
                    'pagoda asian temple',
                    'lighthouse coastal',
                    'castle medieval europe',
                    'adobe pueblo building',
                    'glass modern building',
                    'wooden cabin mountains',
                    'treehouse architecture',
                    'underwater hotel',
                    'rooftop garden city'
                ]
            },
            {
                name: 'transportation',
                displayName: 'Vehicles & Transportation',
                queries: [
                    'classic vintage car',
                    'modern sports car',
                    'steam locomotive train',
                    'sailboat ocean',
                    'hot air balloon sky',
                    'bicycle park path',
                    'motorcycle road',
                    'airplane flying clouds',
                    'helicopter aerial view',
                    'yacht luxury boat',
                    'cable car mountains',
                    'subway metro train',
                    'cruise ship ocean',
                    'tractor farm field',
                    'fire truck emergency',
                    'scooter vespa italy',
                    'kayak river adventure',
                    'snowmobile winter'
                ]
            },
            {
                name: 'portraits',
                displayName: 'People & Portraits',
                queries: [
                    'elderly woman portrait',
                    'child laughing happy',
                    'musician playing guitar',
                    'chef cooking kitchen',
                    'athlete running',
                    'dancer ballet',
                    'artist painting studio',
                    'farmer harvest',
                    'fisherman boat',
                    'street performer',
                    'yoga meditation',
                    'scientist laboratory'
                ]
            },
            {
                name: 'objects',
                displayName: 'Objects & Still Life',
                queries: [
                    'vintage pocket watch',
                    'stack old books',
                    'antique camera',
                    'musical instruments collection',
                    'pottery ceramics handmade',
                    'jewelry diamonds gold',
                    'telescope astronomy',
                    'typewriter vintage',
                    'chess pieces board',
                    'globe world map',
                    'hourglass sand timer',
                    'compass navigation'
                ]
            }
        ];
    }

    setDebugMode(enabled) {
        this.debugMode = enabled;
    }

    debugLog(message, type = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString();
            const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '🔍';
            console.log(`${emoji} [${timestamp}] WHICH_IS_REAL: ${message}`);
        }
    }

    // Select random category and query
    selectRandomSubject() {
        const category = this.categories[Math.floor(Math.random() * this.categories.length)];
        const query = category.queries[Math.floor(Math.random() * category.queries.length)];
        
        return {
            category: category.name,
            displayCategory: category.displayName,
            pixabayQuery: query,
            subjectName: query.split(' ').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
        };
    }

    // Search Pixabay for real images with timeout
    async searchPixabay(query, timeoutMs = 15000) {
        return new Promise((resolve, reject) => {
            // Fetch more images (20) to have a larger pool for random selection
            const url = `https://pixabay.com/api/?key=${this.pixabayKey}&q=${encodeURIComponent(query)}&image_type=photo&per_page=20&safesearch=true&min_width=512&min_height=512&order=popular`;

            const timeoutId = setTimeout(() => {
                reject(new Error(`Pixabay search timed out after ${timeoutMs}ms`));
            }, timeoutMs);

            https.get(url, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    clearTimeout(timeoutId);
                    try {
                        const result = JSON.parse(data);
                        if (result.hits && result.hits.length > 0) {
                            // Randomly select from available images instead of always picking first
                            const randomIndex = Math.floor(Math.random() * result.hits.length);
                            this.debugLog(`📸 Selected image ${randomIndex + 1} of ${result.hits.length} for query "${query}"`);
                            resolve(result.hits[randomIndex]);
                        } else {
                            reject(new Error('No images found on Pixabay'));
                        }
                    } catch (error) {
                        reject(error);
                    }
                });
            }).on('error', (err) => {
                clearTimeout(timeoutId);
                reject(err);
            });
        });
    }

    // Download image from URL with timeout
    async downloadImage(imageUrl, timeoutMs = 30000) {
        return new Promise((resolve, reject) => {
            const urlObj = new URL(imageUrl);
            const protocol = urlObj.protocol === 'https:' ? https : http;
            
            const timeoutId = setTimeout(() => {
                reject(new Error(`Image download timed out after ${timeoutMs}ms`));
            }, timeoutMs);
            
            protocol.get(urlObj, (res) => {
                if (res.statusCode !== 200) {
                    clearTimeout(timeoutId);
                    reject(new Error(`Download failed with status: ${res.statusCode}`));
                    return;
                }

                const chunks = [];
                res.on('data', chunk => chunks.push(chunk));
                res.on('end', () => {
                    clearTimeout(timeoutId);
                    resolve(Buffer.concat(chunks));
                });
                res.on('error', (err) => {
                    clearTimeout(timeoutId);
                    reject(err);
                });
            }).on('error', (err) => {
                clearTimeout(timeoutId);
                reject(err);
            });
        });
    }

    // Convert image to PNG for DALL-E
    async convertToPNG(imageBuffer) {
        try {
            const sharp = await import('sharp');
            return await sharp.default(imageBuffer)
                .resize(1024, 1024, { 
                    fit: 'cover',
                    position: 'center'
                })
                .png()
                .toBuffer();
        } catch (error) {
            throw new Error(`Image conversion failed: ${error.message}. Install sharp: npm install sharp`);
        }
    }

    // Generate AI variation using DALL-E 3 (prompt-based)
    // The old DALL-E 2 variations endpoint (/v1/images/variations) returns 502.
    // Instead, we use DALL-E 3 with a descriptive prompt based on the subject.
    async generateAIVariation(pngBuffer, subjectDescription) {
        const prompt = `A photorealistic photograph of ${subjectDescription || 'the subject'}. ` +
            'Shot with a professional DSLR camera, natural lighting, realistic details, ' +
            'high resolution. The image should look like an authentic photograph taken by a skilled photographer.';

        try {
            const response = await axios.post(
                'https://api.openai.com/v1/images/generations',
                {
                    model: 'dall-e-3',
                    prompt,
                    n: 1,
                    size: '1024x1024',
                    response_format: 'b64_json',
                    quality: 'standard'
                },
                {
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${this.openaiKey}`
                    },
                    timeout: 120000
                }
            );

            if (!response.data?.data?.[0]?.b64_json) {
                throw new Error('No image data received from DALL-E 3');
            }

            return Buffer.from(response.data.data[0].b64_json, 'base64');
        } catch (error) {
            if (error.response?.status === 400) {
                throw new Error(`DALL-E 3 rejected prompt: ${error.response?.data?.error?.message || 'unknown'}`);
            }
            throw error;
        }
    }

    // Main puzzle generation
    async generateWhichIsRealPuzzle(difficulty = 'medium', maxRetries = 2) {
        this.debugLog(`🚀 Starting "Which Is Real" puzzle generation (${difficulty})`);

        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            this.debugLog(`🔄 Attempt ${attempt}/${maxRetries}`);
            
            try {
                // Validate API keys
                if (!this.pixabayKey || this.pixabayKey === 'YOUR_PIXABAY_KEY_HERE') {
                    throw new Error('PIXABAY_API_KEY not configured');
                }
                if (!this.openaiKey || this.openaiKey === 'YOUR_OPENAI_KEY_HERE') {
                    throw new Error('OPENAI_API_KEY not configured');
                }

                // Select random subject
                const subject = this.selectRandomSubject();
                this.debugLog(`🎨 Selected subject: ${subject.subjectName} (${subject.displayCategory})`);

                // Generate puzzle data
                const puzzleResult = await this.generatePuzzleData(subject, difficulty);
                
                if (puzzleResult.success) {
                    // Format for puzzle system
                    const formattedData = this.formatForPuzzleSystem(puzzleResult.data);
                    
                    this.debugLog(`✅ Successfully generated puzzle on attempt ${attempt}`, 'success');
                    return {
                        success: true,
                        puzzleData: formattedData,
                        attempts: attempt
                    };
                } else {
                    this.debugLog(`❌ Attempt ${attempt} failed: ${puzzleResult.error}`, 'error');
                    
                    if (attempt === maxRetries) {
                        return { 
                            success: false, 
                            message: puzzleResult.error || 'Failed to generate puzzle',
                            attempts: attempt
                        };
                    }
                    
                    // Wait before retry
                    await new Promise(resolve => setTimeout(resolve, 2000));
                }

            } catch (error) {
                this.debugLog(`❌ Error on attempt ${attempt}: ${error.message}`, 'error');
                
                if (attempt === maxRetries) {
                    return {
                        success: false,
                        message: `Puzzle generation error after ${maxRetries} attempts: ${error.message}`,
                        attempts: attempt
                    };
                }
                
                // Wait before retry
                await new Promise(resolve => setTimeout(resolve, 2000));
            }
        }
    }

    async generatePuzzleData(subject, difficulty) {
        this.debugLog('🎨 Generating "Which Is Real" puzzle...');
        
        const puzzleId = `which_is_real_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        
        let realImageBuffer, aiImageBuffer;
        let pixabayData;

        try {
            // Step 1: Download real image from Pixabay
            this.debugLog(`📥 Step 1: Searching Pixabay for "${subject.pixabayQuery}"...`);
            pixabayData = await this.searchPixabay(subject.pixabayQuery);
            
            this.debugLog(`✅ Found image by ${pixabayData.user}`);
            this.debugLog(`   Resolution: ${pixabayData.imageWidth}x${pixabayData.imageHeight}`);
            
            const imageUrl = pixabayData.largeImageURL || pixabayData.webformatURL;
            realImageBuffer = await this.downloadImage(imageUrl);
            
            this.debugLog(`✅ Downloaded real image (${realImageBuffer.length} bytes)`);

            // Step 2: Convert to PNG
            this.debugLog(`🔄 Step 2: Converting to PNG format...`);
            const pngBuffer = await this.convertToPNG(realImageBuffer);
            
            const sizeInMB = (pngBuffer.length / (1024 * 1024)).toFixed(2);
            this.debugLog(`✅ Converted to PNG (${sizeInMB} MB)`);
            
            if (pngBuffer.length > 4 * 1024 * 1024) {
                throw new Error('Image is larger than 4MB after conversion');
            }

            // Step 3: Generate AI variation using DALL-E 3
            this.debugLog(`🤖 Step 3: Generating AI variation for "${subject.subjectName}"...`);
            aiImageBuffer = await this.generateAIVariation(pngBuffer, subject.subjectName);
            
            this.debugLog(`✅ Generated AI image (${aiImageBuffer.length} bytes)`);

        } catch (error) {
            this.debugLog(`❌ Error in puzzle generation: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }

        // Randomly assign which position (A or B) gets the real image
        const realPosition = Math.random() > 0.5 ? 'image_a' : 'image_b';
        const aiPosition = realPosition === 'image_a' ? 'image_b' : 'image_a';

        // Create puzzle data structure
        const puzzleData = {
            puzzleId: puzzleId,
            type: 'which_is_real',
            category: subject.category,
            displayCategory: subject.displayCategory,
            subjectName: subject.subjectName,
            description: `Can you identify which image is a real photograph and which is AI-generated?`,
            
            // Image data (buffers to be uploaded later)
            images: {
                [realPosition]: {
                    buffer: realImageBuffer,
                    type: 'real',
                    source: 'pixabay',
                    photographer: pixabayData.user,
                    originalUrl: pixabayData.largeImageURL,
                    supabaseUrl: null  // To be filled after upload
                },
                [aiPosition]: {
                    buffer: aiImageBuffer,
                    type: 'ai_generated',
                    source: 'dall-e-variation',
                    supabaseUrl: null  // To be filled after upload
                }
            },
            
            // Correct answer
            correctAnswer: realPosition,
            
            // Difficulty and timing
            difficulty: this.calculateDifficulty(difficulty),
            timeLimit: this.getTimeLimitForDifficulty(difficulty),
            
            // Educational clues
            expertClues: {
                real: [
                    "Look for natural imperfections and authentic details",
                    "Check for realistic lighting and shadows",
                    "Notice organic composition and natural framing",
                    "Real photos often have subtle inconsistencies that feel authentic"
                ],
                ai: [
                    "AI images may show slight symmetry issues",
                    "Look for overly smooth or perfect textures",
                    "Check for unusual artifacts or inconsistencies",
                    "AI can struggle with complex reflections and natural wear"
                ]
            },
            
            // Instructions
            instructions: {
                task: 'Identify which image is a real photograph vs AI-generated',
                method: 'Look carefully at details, lighting, textures, and overall authenticity',
                scoring: 'Correct identification earns points. Add your reasoning to help others learn!',
                tips: [
                    'Take your time to examine both images carefully',
                    'Look for natural imperfections in real photos',
                    'AI images may have subtle tells in textures or symmetry',
                    'Consider lighting, shadows, and overall composition'
                ]
            },
            
            // Metadata
            generatedAt: new Date().toISOString(),
            pixabayQuery: subject.pixabayQuery,
            imageGenerated: true,
            requiresUpload: true,
            
            // Reasoning system ready
            reasoningEnabled: true,
            communityLearning: true
        };

        this.debugLog(`✅ Puzzle data generated successfully`);
        this.debugLog(`   Puzzle ID: ${puzzleId}`);
        this.debugLog(`   Correct Answer: ${realPosition}`);
        this.debugLog(`   Subject: ${subject.subjectName}`);
        this.debugLog(`   Difficulty: ${puzzleData.difficulty}`);

        return { success: true, data: puzzleData };
    }

    // Format for puzzle system (like FindObjectGenerator)
    formatForPuzzleSystem(puzzleData) {
        const questionData = {
            puzzleId: puzzleData.puzzleId,
            type: puzzleData.type,
            category: puzzleData.category,
            displayCategory: puzzleData.displayCategory,
            subjectName: puzzleData.subjectName,
            description: puzzleData.description,
            
            // Image URLs will be filled after Supabase upload
            imageA: {
                url: null,  // To be filled
                position: 'image_a'
            },
            imageB: {
                url: null,  // To be filled
                position: 'image_b'
            },
            
            instructions: puzzleData.instructions,
            timeLimit: puzzleData.timeLimit,
            expertClues: puzzleData.expertClues,
            
            // For reasoning system
            reasoningEnabled: puzzleData.reasoningEnabled,
            communityLearning: puzzleData.communityLearning
        };

        const answerData = {
            correctAnswer: puzzleData.correctAnswer,
            imageDetails: {
                image_a: {
                    type: puzzleData.images.image_a.type,
                    source: puzzleData.images.image_a.source,
                    photographer: puzzleData.images.image_a.photographer
                },
                image_b: {
                    type: puzzleData.images.image_b.type,
                    source: puzzleData.images.image_b.source
                }
            },
            expertClues: puzzleData.expertClues,
            educationalValue: {
                learningGoal: 'Develop ability to identify AI-generated vs real images',
                keySkills: ['Visual analysis', 'Pattern recognition', 'Critical thinking', 'AI literacy']
            }
        };

        return {
            question: JSON.stringify(questionData),
            answer: JSON.stringify(answerData),
            hint: `Examine both images carefully. Look for natural imperfections, authentic lighting, and realistic details that distinguish real photographs from AI generations.`,
            difficulty: puzzleData.difficulty,
            metadata: {
                category: puzzleData.category,
                displayCategory: puzzleData.displayCategory,
                subjectName: puzzleData.subjectName,
                imageGenerated: puzzleData.imageGenerated,
                generatedAt: puzzleData.generatedAt,
                puzzleType: 'visual_comparison',
                requiresImageUpload: true,
                
                // Image buffers for upload (not in question/answer)
                imageBuffers: {
                    image_a: puzzleData.images.image_a.buffer,
                    image_b: puzzleData.images.image_b.buffer
                },
                
                // Supabase upload info
                uploadPaths: {
                    image_a: `which-is-real/${puzzleData.puzzleId}_a.jpg`,
                    image_b: `which-is-real/${puzzleData.puzzleId}_b.jpg`
                },
                
                // Reasoning system
                reasoningEnabled: true,
                communityLearning: true,
                
                // Educational
                educationalPuzzle: true,
                aiLiteracy: true
            }
        };
    }

    // Helper methods
    calculateDifficulty(requestedDifficulty) {
        // Map requested difficulty to actual puzzle difficulty
        const difficultyMap = {
            'easy': 'Medium',      // Even "easy" is medium because AI is good
            'medium': 'Hard',      // This is the reality - it's hard!
            'hard': 'Very Hard'    // Expert level
        };
        
        return difficultyMap[requestedDifficulty.toLowerCase()] || 'Hard';
    }

    getTimeLimitForDifficulty(difficulty) {
        switch (difficulty.toLowerCase()) {
            case 'easy': return 90000;     // 1.5 minutes
            case 'medium': return 120000;  // 2 minutes
            case 'hard': return 180000;    // 3 minutes
            default: return 120000;
        }
    }

    // Upload helper (to be called during integration)
    async uploadImagesToSupabase(puzzleData) {
        const { supabase } = await import('../config/database.js');
        const uploadResults = {};
    
        try {
            // ✅ Extract puzzleId from the question data
            let puzzleId;
            if (puzzleData.puzzleId) {
                puzzleId = puzzleData.puzzleId;
            } else if (puzzleData.metadata?.puzzleId) {
                puzzleId = puzzleData.metadata.puzzleId;
            } else {
                // Parse from question JSON
                const questionData = JSON.parse(puzzleData.question);
                puzzleId = questionData.puzzleId;
            }
    
            if (!puzzleId) {
                throw new Error('puzzleId not found in puzzle data');
            }
    
            this.debugLog(`📤 Uploading images for puzzle: ${puzzleId}`);
    
            // Upload image_a
            const pathA = `which-is-real/${puzzleId}_a.jpg`;
            const { error: errorA } = await supabase.storage
                .from('puzzle-images')
                .upload(pathA, puzzleData.metadata.imageBuffers.image_a, { 
                    contentType: 'image/jpeg',
                    upsert: true
                });
    
            if (errorA) throw new Error(`Failed to upload image A: ${errorA.message}`);
    
            const { data: { publicUrl: urlA } } = supabase.storage
                .from('puzzle-images')
                .getPublicUrl(pathA);
    
            uploadResults.image_a = urlA;
    
            // Upload image_b
            const pathB = `which-is-real/${puzzleId}_b.jpg`;
            const { error: errorB } = await supabase.storage
                .from('puzzle-images')
                .upload(pathB, puzzleData.metadata.imageBuffers.image_b, { 
                    contentType: 'image/jpeg',
                    upsert: true
                });
    
            if (errorB) throw new Error(`Failed to upload image B: ${errorB.message}`);
    
            const { data: { publicUrl: urlB } } = supabase.storage
                .from('puzzle-images')
                .getPublicUrl(pathB);
    
            uploadResults.image_b = urlB;
    
            this.debugLog(`✅ Both images uploaded to Supabase`, 'success');
            return { success: true, urls: uploadResults };
    
        } catch (error) {
            this.debugLog(`❌ Failed to upload images: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    // Update puzzle data with Supabase URLs (call after upload)
    updatePuzzleWithUrls(puzzleData, uploadUrls) {
        const question = JSON.parse(puzzleData.question);
        question.imageA.url = uploadUrls.image_a;
        question.imageB.url = uploadUrls.image_b;
        
        puzzleData.question = JSON.stringify(question);
        
        // Remove buffers from metadata after successful upload
        delete puzzleData.metadata.imageBuffers;
        
        this.debugLog(`✅ Puzzle data updated with Supabase URLs`, 'success');
        return puzzleData;
    }
}

// Export singleton instance
export const whichIsRealGenerator = new WhichIsRealGenerator();
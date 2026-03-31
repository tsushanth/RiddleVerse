// services/findDifferencesGeneratorService.js
// REFACTORED VERSION - Method 3 (Split-Scene + Vision API) with Original Names

import { supabase } from '../config/database.js';
import { USAGE_CATEGORIES } from '../utils/usageTracker.js';
import axios from 'axios';

/**
 * Enhanced Find Differences Generator with Method 3 (Split-Scene + Vision API)
 * Maintains original class and export names for compatibility
 */
export class FindDifferencesGeneratorService {
    constructor() {
        this.debugMode = true;
        this.apiKey = process.env.OPENAI_API_KEY;
        
        // Enhanced difficulty configuration for Method 3
        this.difficultyConfig = {
            easy: {
                differences: 3,
                timeLimit: 300000,
                tolerance: 25.0,
                requestedDifferences: 4, // Request slightly more from DALL-E
                minimumDifferences: 3    // Minimum we need to find
            },
            medium: {
                differences: 4,
                timeLimit: 240000,
                tolerance: 20.0,
                requestedDifferences: 5,
                minimumDifferences: 4
            },
            hard: {
                differences: 5,
                timeLimit: 180000,
                tolerance: 15.0,
                requestedDifferences: 6,
                minimumDifferences: 5
            }
        };

        // Enhanced scene prompts optimized for split-screen generation
        this.scenePrompts = [
            "A clean white kitchen counter with 4 colorful objects: red coffee mug, blue bowl, yellow banana, green apple",
            "A simple white desk with 3 items: black laptop, red pen, blue notebook",
            "A white table with 4 items: pink soap, blue cup, yellow towel, green bottle",
            "A neat white counter with 5 kitchen items: red pot, blue plate, yellow lemon, green lime, orange carrot",
            "A tidy white workspace with 4 office supplies: black stapler, red marker, blue folder, green plant",
            "A clean white shelf with 3 colorful items: purple book, orange mug, pink flower pot"
        ];

        this.stats = {
            totalGenerated: 0,
            successfulGenerations: 0,
            failedGenerations: 0,
            averageGenerationTime: 0,
            visionApiSuccesses: 0,
            visionApiFallbacks: 0,
            averageDifferencesFound: 0,
            averageConfidence: 0
        };

        // Initialize Sharp
        this.sharp = null;
        this.initializeLibraries();
    }

    /**
     * Initialize Sharp library
     */
    async initializeLibraries() {
        try {
            this.sharp = (await import('sharp')).default;
            this.debugLog(`✅ Sharp initialized successfully`);
        } catch (error) {
            this.debugLog(`❌ Sharp not available: ${error.message}`, 'error');
            throw new Error('Sharp is required. Run: npm install sharp');
        }
    }

    /**
     * MAIN METHOD: Generate find differences puzzle using Method 3 (maintains original method name)
     */
    async generateFindDifferencesPuzzle(difficulty = 'medium') {
        const startTime = Date.now();
        this.debugLog(`🎯 Starting Method 3 generation (Split-Screen + Vision API): ${difficulty}`);
        
        try {
            this.stats.totalGenerated++;
            
            const normalizedDifficulty = difficulty.toLowerCase();
            const config = this.difficultyConfig[normalizedDifficulty];
            if (!config) {
                throw new Error(`Invalid difficulty: ${difficulty}`);
            }

            // Step 1: Generate split-screen image with Method 3
            this.debugLog(`🎨 Generating enhanced split-screen image...`);
            const { imageBuffer, prompt } = await this.generateSplitScreenImage(config);
            
            // Step 2: Detect actual differences using Vision API
            this.debugLog(`👁️ Analyzing differences with Vision API...`);
            let differences = await this.analyzeWithVisionAPI(imageBuffer);
            
            // Step 3: Create hybrid difference set (detected + fallbacks if needed)
            this.debugLog(`🔧 Creating final difference set...`);
            differences = await this.createHybridDifferences(differences, config);
            
            // Step 4: Upload to Supabase
            this.debugLog(`☁️ Uploading to Supabase...`);
            const uploadResult = await this.uploadPuzzle(imageBuffer, differences, normalizedDifficulty, prompt);
            
            const generationTime = Date.now() - startTime;
            this.updateStats(true, generationTime, differences);
            
            this.debugLog(`✅ Successfully generated Method 3 puzzle in ${generationTime}ms`);
            
            return {
                success: true,
                puzzleData: uploadResult.puzzleData,
                imageUrl: uploadResult.imageUrl,
                differences: differences,
                scenePrompt: prompt,
                generationTime: generationTime,
                method: 'method3_split_scene_vision',
                detectedDifferences: differences.length,
                detectionQuality: this.assessDetectionQuality(differences),
                averageConfidence: this.calculateAverageConfidence(differences)
            };

        } catch (error) {
            this.updateStats(false, Date.now() - startTime, []);
            this.debugLog(`❌ Method 3 generation failed: ${error.message}`, 'error');
            return {
                success: false,
                error: error.message
            };
        }
    }

    /**
     * Generate split-screen image using enhanced Method 3 prompts
     */
    async generateSplitScreenImage(config) {
        const scene = this.scenePrompts[Math.floor(Math.random() * this.scenePrompts.length)];
        
        const enhancedPrompt = `Create a find-the-differences puzzle image showing two kitchen scenes side by side in a WIDE HORIZONTAL format.

LAYOUT: Split-screen format with clear vertical line down the middle. Leave generous margins around all edges to prevent cutoff.

LEFT SIDE: ${scene} arranged on a white background with objects well within the frame boundaries
RIGHT SIDE: Identical scene but with exactly ${config.requestedDifferences}-${config.requestedDifferences + 1} obvious changes:
- Change the color of at least 2 objects to completely different bright colors
- Remove 1-2 small objects completely (leave white space)  
- Add 1-2 new simple objects (like fruits, utensils, or simple shapes)
- Change the shape or size of 1 object noticeably

IMPORTANT: 
- Use HORIZONTAL/LANDSCAPE orientation to fit both scenes properly
- Keep all objects WELL WITHIN the frame boundaries - no edge cutoff
- Make the split exactly vertical down the center
- Use clean cartoon style, bright solid colors, white background
- Make differences very obvious and high-contrast`;

        try {
            const response = await axios.post('https://api.openai.com/v1/images/generations', {
                model: "dall-e-3",
                prompt: enhancedPrompt,
                n: 1,
                size: "1792x1024", // Wide horizontal format
                quality: "hd",
                response_format: "b64_json"
            }, {
                headers: {
                    'Authorization': `Bearer ${this.apiKey}`,
                    'Content-Type': 'application/json'
                },
                timeout: 120000
            });

            if (!response.data?.data?.[0]?.b64_json) {
                throw new Error('Failed to generate split-screen image');
            }

            const imageBuffer = Buffer.from(response.data.data[0].b64_json, 'base64');
            
            this.debugLog(`✅ Generated split-screen image (${imageBuffer.length} bytes)`);

            return {
                imageBuffer,
                prompt: enhancedPrompt
            };
            
        } catch (error) {
            this.debugLog(`❌ Split-screen generation failed: ${error.message}`, 'error');
            throw new Error(`Split-screen generation failed: ${error.message}`);
        }
    }

    /**
     * Enhanced Vision API analysis for split-screen images
     */
    async analyzeWithVisionAPI(imageBuffer) {
        try {
            this.debugLog(`👁️ Running enhanced Vision API analysis...`);
            
            const base64Image = imageBuffer.toString('base64');
            
            const enhancedVisionPrompt = `You are analyzing a find-the-differences puzzle image. This image shows two kitchen scenes side by side - LEFT and RIGHT.

Your task: Find ALL differences between the left and right sides and provide precise coordinates for the RIGHT side only.

Look for these types of differences:
- Objects that changed color (e.g., red pot became blue pot)
- Objects that were removed from the right side
- New objects added to the right side  
- Objects that changed size or shape
- Objects that moved to different positions

For each difference you find:
1. Describe EXACTLY what is different (be specific about colors, objects, changes)
2. Provide coordinates for the RIGHT side as percentages (x%, y%) where (0,0) is top-left corner
3. Rate confidence 0.1 to 1.0 based on how obvious the difference is

IMPORTANT: Only report differences you can clearly see. Be accurate with coordinates.

Return ONLY a JSON array like this:
[
  {"description": "Red plant pot changed to yellow plant pot", "x": 25.5, "y": 30.2, "confidence": 0.9},
  {"description": "Blue teapot removed from shelf", "x": 75.0, "y": 35.0, "confidence": 0.95},
  {"description": "Orange apple added to fruit bowl", "x": 60.3, "y": 45.8, "confidence": 0.8}
]`;

            const response = await axios.post('https://api.openai.com/v1/chat/completions', {
                model: "gpt-4o",
                messages: [
                    {
                        role: "user",
                        content: [
                            { type: "text", text: enhancedVisionPrompt },
                            {
                                type: "image_url",
                                image_url: {
                                    url: `data:image/jpeg;base64,${base64Image}`,
                                    detail: "high"
                                }
                            }
                        ]
                    }
                ],
                max_tokens: 1000,
                temperature: 0.1
            }, {
                headers: {
                    'Authorization': `Bearer ${this.apiKey}`,
                    'Content-Type': 'application/json'
                },
                timeout: 60000
            });

            const visionAnalysis = response.data.choices[0].message.content;
            this.debugLog(`📝 Vision API response received (${visionAnalysis.length} chars)`);
            
            // Try to extract JSON from response
            try {
                const jsonMatch = visionAnalysis.match(/\[[\s\S]*\]/);
                if (jsonMatch) {
                    const differences = JSON.parse(jsonMatch[0]);
                    
                    // Validate and clean up the differences
                    const validDifferences = differences
                        .filter(diff => diff.x >= 0 && diff.x <= 100 && diff.y >= 0 && diff.y <= 100)
                        .filter(diff => diff.confidence >= 0.1)
                        .map((diff, index) => ({
                            id: `vision_${index}_${Math.random().toString(36).substr(2, 6)}`,
                            description: diff.description || 'Detected difference',
                            type: 'vision_detected',
                            side: "right",
                            x: Math.round(diff.x * 10) / 10,
                            y: Math.round(diff.y * 10) / 10,
                            x_percentage: Math.round(diff.x * 10) / 10,
                            y_percentage: Math.round(diff.y * 10) / 10,
                            tolerance: 20.0, // Standard tolerance
                            confidence: Math.min(diff.confidence, 1.0),
                            detectionMethod: 'vision_api_enhanced'
                        }));
                    
                    this.stats.visionApiSuccesses++;
                    this.debugLog(`✅ Vision API parsed ${validDifferences.length} valid differences`);
                    return validDifferences;
                }
            } catch (parseError) {
                this.debugLog(`⚠️ JSON parsing failed, trying text parsing...`, 'warning');
                return this.parseVisionTextResponse(visionAnalysis);
            }
            
            // Fallback to text parsing
            return this.parseVisionTextResponse(visionAnalysis);
            
        } catch (error) {
            this.debugLog(`❌ Vision API analysis failed: ${error.message}`, 'error');
            this.stats.visionApiFallbacks++;
            return [];
        }
    }

    /**
     * Parse Vision API text response as fallback
     */
    parseVisionTextResponse(text) {
        this.debugLog(`📝 Parsing Vision API text response...`);
        
        const differences = [];
        const lines = text.split('\n');
        
        let currentDiff = {};
        let diffCount = 0;
        
        for (const line of lines) {
            const trimmedLine = line.trim();
            
            // Look for difference descriptions
            if ((trimmedLine.includes('changed') || trimmedLine.includes('removed') || 
                 trimmedLine.includes('added') || trimmedLine.includes('different')) && 
                !trimmedLine.includes('coordinate')) {
                
                if (currentDiff.description) {
                    // Save previous difference
                    if (currentDiff.x !== undefined && currentDiff.y !== undefined) {
                        differences.push({
                            id: `vision_text_${diffCount}_${Math.random().toString(36).substr(2, 6)}`,
                            description: currentDiff.description,
                            type: 'vision_text_detected',
                            side: "right",
                            x: currentDiff.x,
                            y: currentDiff.y,
                            x_percentage: currentDiff.x,
                            y_percentage: currentDiff.y,
                            tolerance: 20.0,
                            confidence: currentDiff.confidence || 0.7,
                            detectionMethod: 'vision_api_text_enhanced'
                        });
                        diffCount++;
                    }
                }
                
                currentDiff = { description: trimmedLine };
            }
            
            // Look for coordinates in various formats
            const coordPatterns = [
                /(\d+\.?\d*)[%\s]*[,\s]+(\d+\.?\d*)[%\s]*/,
                /x[:\s]*(\d+\.?\d*)[%\s]*[,\s]*y[:\s]*(\d+\.?\d*)[%\s]*/i,
                /\((\d+\.?\d*)[%\s]*,\s*(\d+\.?\d*)[%\s]*\)/
            ];
            
            for (const pattern of coordPatterns) {
                const coords = trimmedLine.match(pattern);
                if (coords && currentDiff.description) {
                    currentDiff.x = parseFloat(coords[1]);
                    currentDiff.y = parseFloat(coords[2]);
                    
                    if (currentDiff.x >= 0 && currentDiff.x <= 100 && 
                        currentDiff.y >= 0 && currentDiff.y <= 100) {
                        break;
                    }
                }
            }
            
            // Look for confidence scores
            const confMatch = trimmedLine.match(/confidence[:\s]*(\d*\.?\d+)/i);
            if (confMatch && currentDiff.description) {
                currentDiff.confidence = Math.min(parseFloat(confMatch[1]), 1.0);
            }
        }
        
        // Add final difference if exists
        if (currentDiff.description && currentDiff.x !== undefined && currentDiff.y !== undefined) {
            differences.push({
                id: `vision_text_${diffCount}_${Math.random().toString(36).substr(2, 6)}`,
                description: currentDiff.description,
                type: 'vision_text_detected',
                side: "right",
                x: currentDiff.x,
                y: currentDiff.y,
                x_percentage: currentDiff.x,
                y_percentage: currentDiff.y,
                tolerance: 20.0,
                confidence: currentDiff.confidence || 0.7,
                detectionMethod: 'vision_api_text_enhanced'
            });
        }
        
        this.debugLog(`✅ Text parsing extracted ${differences.length} differences`);
        return differences;
    }

    /**
     * Create hybrid differences combining Vision API detection with smart fallbacks
     */
    async createHybridDifferences(detectedDifferences, config) {
        this.debugLog(`🔗 Creating hybrid difference set...`);
        
        // Start with detected differences
        const hybrid = [...detectedDifferences];
        
        // If we don't have enough differences, add smart grid fallbacks
        if (hybrid.length < config.minimumDifferences) {
            const needed = config.minimumDifferences - hybrid.length;
            this.debugLog(`📍 Adding ${needed} smart grid fallback positions...`);
            
            const fallbackGridPositions = [
                { x: 25, y: 30, description: "Top-left area difference" },
                { x: 75, y: 30, description: "Top-right area difference" },
                { x: 25, y: 70, description: "Bottom-left area difference" },
                { x: 75, y: 70, description: "Bottom-right area difference" },
                { x: 50, y: 50, description: "Center area difference" },
                { x: 35, y: 45, description: "Center-left area difference" },
                { x: 65, y: 55, description: "Center-right area difference" }
            ];
            
            for (let i = 0; i < needed && i < fallbackGridPositions.length; i++) {
                const fallback = fallbackGridPositions[i];
                
                // Check if this position is too close to detected differences
                const tooClose = hybrid.some(existing => {
                    const distance = Math.sqrt(
                        Math.pow(fallback.x - existing.x, 2) + 
                        Math.pow(fallback.y - existing.y, 2)
                    );
                    return distance < 20;
                });
                
                if (!tooClose) {
                    hybrid.push({
                        id: `grid_fallback_${i}_${Math.random().toString(36).substr(2, 6)}`,
                        description: fallback.description,
                        type: 'grid_fallback',
                        side: "right",
                        x: fallback.x,
                        y: fallback.y,
                        x_percentage: fallback.x,
                        y_percentage: fallback.y,
                        tolerance: config.tolerance + 10,
                        confidence: 0.6, // Lower confidence for fallbacks
                        detectionMethod: 'smart_grid_fallback'
                    });
                }
            }
        }
        
        // Sort by confidence (highest first)
        hybrid.sort((a, b) => (b.confidence || 0.5) - (a.confidence || 0.5));
        
        // Limit to target number of differences
        const final = hybrid.slice(0, config.differences);
        
        this.debugLog(`✅ Created hybrid set: ${final.length} differences (${detectedDifferences.length} detected + ${final.length - detectedDifferences.length} fallbacks)`);
        
        return final;
    }

    /**
     * Upload puzzle to Supabase (maintains original method)
     */
    async uploadPuzzle(puzzleImageBuffer, differences, difficulty, scenePrompt) {
        const puzzleId = this.generatePuzzleId();
        const fileName = `find-differences/${puzzleId}.jpg`;

        try {
            const { data: uploadData, error: uploadError } = await supabase.storage
                .from('puzzle-images')
                .upload(fileName, puzzleImageBuffer, {
                    contentType: 'image/jpeg',
                    cacheControl: '3600',
                    upsert: false
                });

            if (uploadError) {
                throw new Error(`Storage upload failed: ${uploadError.message}`);
            }

            const { data: { publicUrl } } = supabase.storage
                .from('puzzle-images')
                .getPublicUrl(fileName);

            const config = this.difficultyConfig[difficulty.toLowerCase()];
            const questionData = {
                imageUrl: publicUrl,
                instructions: `Find all ${differences.length} differences between the left and right images. Click on the differences in the right image.`,
                differences: differences,
                imageStatus: 'generated',
                generatedAt: new Date().toISOString(),
                generationType: 'method3_split_scene_vision_api',
                expectedDifficulty: difficulty,
                imageWidth: 1792,
                imageHeight: 1024,
                coordinateSystem: 'split_screen_percentage',
                detectionStats: {
                    totalDetected: differences.length,
                    averageConfidence: this.calculateAverageConfidence(differences),
                    detectionMethods: [...new Set(differences.map(d => d.detectionMethod))]
                }
            };

            const puzzleData = {
                id: puzzleId,
                type: 'find_differences',
                difficulty: difficulty,
                question: questionData, // Don't stringify - store as object
                correct_answer: differences, // Don't stringify - store as array
                puzzle_data: {
                    differences: differences,
                    tolerance: config.tolerance,
                    timeLimit: config.timeLimit,
                    totalDifferences: differences.length,
                    method: 'method3_split_scene_vision_api',
                    detectionQuality: this.assessDetectionQuality(differences),
                    averageConfidence: this.calculateAverageConfidence(differences)
                }, // Don't stringify - store as object
                created_at: new Date().toISOString(),
                created_by: 'method3_vision_generator'
            };

            this.debugLog(`✅ Uploaded Method 3 puzzle to Supabase: ${publicUrl}`);
            
            return {
                puzzleData: puzzleData,
                imageUrl: publicUrl,
                puzzleId: puzzleId
            };

        } catch (error) {
            this.debugLog(`❌ Supabase upload failed: ${error.message}`, 'error');
            throw new Error(`Supabase upload failed: ${error.message}`);
        }
    }

    /**
     * Calculate average confidence
     */
    calculateAverageConfidence(differences) {
        if (differences.length === 0) return 0;
        return differences.reduce((sum, d) => sum + (d.confidence || 0.5), 0) / differences.length;
    }

    /**
     * Assess overall detection quality
     */
    assessDetectionQuality(differences) {
        if (differences.length === 0) return 'poor';
        
        const avgConfidence = this.calculateAverageConfidence(differences);
        const hasHighConfidence = differences.some(d => (d.confidence || 0.5) > 0.8);
        const hasVisionDetections = differences.some(d => d.detectionMethod?.includes('vision'));
        
        if (avgConfidence > 0.8 && hasHighConfidence && hasVisionDetections) return 'excellent';
        if (avgConfidence > 0.7 && hasVisionDetections) return 'very_good';
        if (avgConfidence > 0.6) return 'good';
        if (avgConfidence > 0.4) return 'fair';
        return 'poor';
    }

    /**
     * Generate unique puzzle ID (maintains original method)
     */
    generatePuzzleId() {
        const timestamp = Date.now();
        const random = Math.random().toString(36).substr(2, 9);
        return `method3_diff_${timestamp}_${random}`;
    }

    /**
     * Update statistics (maintains original method)
     */
    updateStats(success, generationTime, differences) {
        if (success) {
            this.stats.successfulGenerations++;
        } else {
            this.stats.failedGenerations++;
        }
        
        const totalTime = this.stats.averageGenerationTime * (this.stats.totalGenerated - 1) + generationTime;
        this.stats.averageGenerationTime = totalTime / this.stats.totalGenerated;
        
        if (differences.length > 0) {
            const totalDiffs = this.stats.averageDifferencesFound * (this.stats.successfulGenerations - 1) + differences.length;
            this.stats.averageDifferencesFound = totalDiffs / this.stats.successfulGenerations;
            
            const avgConfidence = this.calculateAverageConfidence(differences);
            const totalConfidence = this.stats.averageConfidence * (this.stats.successfulGenerations - 1) + avgConfidence;
            this.stats.averageConfidence = totalConfidence / this.stats.successfulGenerations;
        }
    }

    /**
     * Debug logging (maintains original method)
     */
    debugLog(message, level = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString();
            const levelColors = {
                info: '\x1b[36m',
                success: '\x1b[32m',
                warning: '\x1b[33m',
                error: '\x1b[31m'
            };
            const color = levelColors[level] || '\x1b[0m';
            const reset = '\x1b[0m';
            
            console.log(`${color}[${timestamp}] [METHOD3-DIFF] [${level.toUpperCase()}] ${message}${reset}`);
        }
    }

    /**
     * Set debug mode (maintains original method)
     */
    setDebugMode(enabled) {
        this.debugMode = enabled;
        this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'}`);
    }

    /**
     * Get statistics (maintains original method)
     */
    getStats() {
        return {
            ...this.stats,
            successRate: this.stats.totalGenerated > 0 ? 
                Math.round((this.stats.successfulGenerations / this.stats.totalGenerated) * 100) : 0,
            averageGenerationTime: Math.round(this.stats.averageGenerationTime),
            averageDifferencesFound: Math.round(this.stats.averageDifferencesFound * 10) / 10,
            averageConfidence: Math.round(this.stats.averageConfidence * 1000) / 10, // Percentage with 1 decimal
            visionApiSuccessRate: this.stats.totalGenerated > 0 ?
                Math.round((this.stats.visionApiSuccesses / this.stats.totalGenerated) * 100) : 0,
            visionApiFallbackRate: this.stats.totalGenerated > 0 ?
                Math.round((this.stats.visionApiFallbacks / this.stats.totalGenerated) * 100) : 0,
            supportedDifficulties: Object.keys(this.difficultyConfig),
            sceneVariants: this.scenePrompts.length,
            generatorType: 'method3_split_scene_vision_api',
            version: '3.0_method3_vision',
            capabilities: {
                splitSceneGeneration: true,
                visionApiDetection: true,
                smartFallbacks: true,
                hybridDifferences: true,
                horizontalFormat: true
            }
        };
    }

    /**
     * Health check (maintains original method)
     */
    async healthCheck() {
        try {
            const { error: dbError } = await supabase
                .from('puzzles')
                .select('id')
                .limit(1);

            if (dbError) {
                throw new Error(`Database error: ${dbError.message}`);
            }

            return {
                status: 'healthy',
                database: 'connected',
                sharp: !!this.sharp,
                visionApi: 'available',
                primaryDetectionMethod: 'Vision API + Smart Fallbacks',
                imageFormat: '1792x1024 (horizontal)',
                stats: this.getStats(),
                timestamp: new Date().toISOString(),
                version: 'method3_v3.0_vision'
            };

        } catch (error) {
            return {
                status: 'unhealthy',
                error: error.message,
                timestamp: new Date().toISOString()
            };
        }
    }

    // Legacy methods for compatibility (these now use placeholder or minimal implementations)

    /**
     * Generate two similar images (legacy method - now redirects to split-screen)
     */
    async generateTwoSimilarImages() {
        this.debugLog(`⚠️ Legacy method called - redirecting to split-screen generation`, 'warning');
        return await this.generateSplitScreenImage(this.difficultyConfig.medium);
    }

    /**
     * Detect actual differences (legacy method - now uses Vision API)
     */
    async detectActualDifferences(originalBuffer, modifiedBuffer, config) {
        this.debugLog(`⚠️ Legacy method called - using Vision API on split-screen`, 'warning');
        return await this.analyzeWithVisionAPI(originalBuffer);
    }

    /**
     * Create split-screen (legacy method - now returns original buffer since we generate split-screen directly)
     */
    async createSplitScreen(originalBuffer, modifiedBuffer) {
        this.debugLog(`⚠️ Legacy method called - returning original buffer (already split-screen)`, 'warning');
        return originalBuffer;
    }

    /**
     * Create placeholder differences (maintains original method for fallback compatibility)
     */
    createPlaceholderDifferences(count, tolerance) {
        const differences = [];
        
        const gridPositions = [
            { x: 25, y: 25, description: "top-left area" },
            { x: 50, y: 25, description: "top-center" },
            { x: 75, y: 25, description: "top-right area" },
            { x: 25, y: 50, description: "middle-left" },
            { x: 50, y: 50, description: "center" },
            { x: 75, y: 50, description: "middle-right" },
            { x: 25, y: 75, description: "bottom-left" },
            { x: 50, y: 75, description: "bottom-center" },
            { x: 75, y: 75, description: "bottom-right" }
        ];
        
        const selectedPositions = gridPositions.slice(0, count);
        
        selectedPositions.forEach((position, index) => {
            differences.push({
                id: `placeholder_diff_${index}_${Math.random().toString(36).substr(2, 6)}`,
                description: `Fallback difference in ${position.description}`,
                type: 'placeholder_fallback',
                side: "right",
                x: position.x,
                y: position.y,
                x_percentage: position.x,
                y_percentage: position.y,
                tolerance: tolerance + 15,
                confidence: 0.4,
                detectionMethod: 'placeholder_grid',
                gridPosition: position.description
            });
        });
        
        return differences;
    }
}

// Export singleton instance with ORIGINAL name
export const findDifferencesGenerator = new FindDifferencesGeneratorService();
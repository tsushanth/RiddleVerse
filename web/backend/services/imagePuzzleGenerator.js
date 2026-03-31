// services/imagePuzzleGenerator.js - Enhanced AI Image Puzzle Generator

import axios from 'axios';

export class ImagePuzzleGenerator {
    constructor() {
        this.debugMode = false;
        this.apiKey = process.env.OPENAI_API_KEY;
        
        // Expanded and categorized image themes for better scalability
        this.imageCategories = {
            nature: {
                name: "Nature & Landscapes",
                themes: [
                    {
                        theme: "Mountain Lake",
                        description: "Serene mountain lake with reflections",
                        prompt: "A crystal clear mountain lake reflecting snow-capped peaks, surrounded by pine forests, with a wooden dock extending into the water",
                        difficulty: "medium",
                        tags: ["landscape", "water", "mountains", "peaceful"]
                    },
                    {
                        theme: "Forest Path",
                        description: "Winding path through an enchanted forest",
                        prompt: "A meandering forest path covered with fallen leaves, tall trees creating a canopy, dappled sunlight filtering through branches",
                        difficulty: "medium",
                        tags: ["forest", "path", "trees", "sunlight"]
                    },
                    {
                        theme: "Ocean Sunset",
                        description: "Dramatic sunset over ocean waves",
                        prompt: "A spectacular ocean sunset with golden and orange clouds reflected in gentle waves, seabirds flying in the distance",
                        difficulty: "easy",
                        tags: ["ocean", "sunset", "waves", "sky"]
                    },
                    {
                        theme: "Desert Oasis",
                        description: "Palm trees around a desert spring",
                        prompt: "A lush oasis in the desert with palm trees, clear blue water, sand dunes in the background, and a brilliant blue sky",
                        difficulty: "medium",
                        tags: ["desert", "oasis", "palms", "water"]
                    },
                    {
                        theme: "Waterfall Cascade",
                        description: "Multi-tiered waterfall in tropical setting",
                        prompt: "A magnificent multi-level waterfall cascading over moss-covered rocks, surrounded by lush tropical vegetation and mist",
                        difficulty: "hard",
                        tags: ["waterfall", "tropical", "rocks", "mist"]
                    },
                    {
                        theme: "Autumn Valley",
                        description: "Valley filled with fall colors",
                        prompt: "A peaceful valley in autumn with trees displaying vibrant red, orange, and yellow foliage, a small stream winding through",
                        difficulty: "medium",
                        tags: ["autumn", "valley", "colors", "stream"]
                    }
                ]
            },
            
            urban: {
                name: "Cities & Architecture",
                themes: [
                    {
                        theme: "Modern Skyline",
                        description: "Contemporary city skyline at twilight",
                        prompt: "A modern city skyline at blue hour with illuminated skyscrapers, glass buildings reflecting the evening sky, busy streets below",
                        difficulty: "hard",
                        tags: ["city", "modern", "lights", "evening"]
                    },
                    {
                        theme: "Historic District",
                        description: "Charming old town with cobblestone streets",
                        prompt: "A picturesque historic district with cobblestone streets, old European-style buildings, outdoor cafes, and flower boxes",
                        difficulty: "medium",
                        tags: ["historic", "cobblestone", "buildings", "charming"]
                    },
                    {
                        theme: "Bridge Crossing",
                        description: "Iconic bridge over a river",
                        prompt: "An elegant bridge spanning a wide river with a city skyline in the background, boats in the water, and dramatic lighting",
                        difficulty: "medium",
                        tags: ["bridge", "river", "architecture", "city"]
                    },
                    {
                        theme: "Market Square",
                        description: "Bustling town square with market stalls",
                        prompt: "A vibrant market square with colorful stalls, people shopping, historic buildings surrounding the plaza, fountain in center",
                        difficulty: "hard",
                        tags: ["market", "people", "square", "activity"]
                    },
                    {
                        theme: "Lighthouse Coast",
                        description: "Lighthouse on rocky coastal cliff",
                        prompt: "A classic lighthouse perched on dramatic rocky cliffs overlooking the ocean, with waves crashing below and seabirds circling",
                        difficulty: "medium",
                        tags: ["lighthouse", "coast", "cliffs", "waves"]
                    }
                ]
            },
            
            wildlife: {
                name: "Animals & Wildlife",
                themes: [
                    {
                        theme: "Safari Scene",
                        description: "African wildlife at a watering hole",
                        prompt: "A diverse African safari scene with elephants, giraffes, zebras, and antelopes gathering at a watering hole under acacia trees",
                        difficulty: "hard",
                        tags: ["safari", "elephants", "giraffes", "watering hole"]
                    },
                    {
                        theme: "Butterfly Garden",
                        description: "Colorful butterflies among flowers",
                        prompt: "A magical butterfly garden with dozens of colorful butterflies among blooming flowers, hummingbirds, and garden paths",
                        difficulty: "easy",
                        tags: ["butterflies", "flowers", "garden", "colorful"]
                    },
                    {
                        theme: "Penguin Colony",
                        description: "Penguins on Antarctic ice",
                        prompt: "A colony of emperor penguins on Antarctic ice with icebergs in the background, some penguins diving into crystal clear water",
                        difficulty: "medium",
                        tags: ["penguins", "antarctica", "ice", "colony"]
                    },
                    {
                        theme: "Tropical Birds",
                        description: "Exotic birds in rainforest canopy",
                        prompt: "Vibrant tropical birds including parrots, toucans, and hummingbirds in a lush rainforest canopy with exotic flowers",
                        difficulty: "hard",
                        tags: ["birds", "tropical", "rainforest", "colorful"]
                    },
                    {
                        theme: "Ocean Dolphins",
                        description: "Dolphins jumping in ocean waves",
                        prompt: "A pod of dolphins leaping through ocean waves with a tropical island in the background and clear blue skies",
                        difficulty: "easy",
                        tags: ["dolphins", "ocean", "jumping", "tropical"]
                    }
                ]
            },
            
            fantasy: {
                name: "Fantasy & Magical",
                themes: [
                    {
                        theme: "Enchanted Castle",
                        description: "Magical castle in mystical setting",
                        prompt: "A majestic fairy tale castle on a hilltop surrounded by enchanted forests, with magical sparkles in the air and a rainbow overhead",
                        difficulty: "easy",
                        tags: ["castle", "magical", "fairy tale", "rainbow"]
                    },
                    {
                        theme: "Dragon Valley",
                        description: "Friendly dragons in a mystical valley",
                        prompt: "A peaceful valley with friendly dragons of different colors flying and playing among floating islands and waterfalls",
                        difficulty: "medium",
                        tags: ["dragons", "valley", "floating islands", "mystical"]
                    },
                    {
                        theme: "Wizard Tower",
                        description: "Magical tower with swirling energy",
                        prompt: "A tall wizard's tower with magical energy swirling around it, spell books floating, and mystical creatures in the surrounding garden",
                        difficulty: "hard",
                        tags: ["wizard", "tower", "magic", "spells"]
                    },
                    {
                        theme: "Unicorn Forest",
                        description: "Unicorns in an enchanted forest glade",
                        prompt: "A magical forest glade with unicorns drinking from a crystal stream, fairy lights in the trees, and flowers that glow softly",
                        difficulty: "easy",
                        tags: ["unicorns", "forest", "magical", "fairy lights"]
                    },
                    {
                        theme: "Crystal Cave",
                        description: "Underground cave filled with crystals",
                        prompt: "A magnificent underground cave filled with glowing crystals of various colors, underground pools reflecting the crystal light",
                        difficulty: "hard",
                        tags: ["crystals", "cave", "underground", "glowing"]
                    }
                ]
            },
            
            space: {
                name: "Space & Cosmos",
                themes: [
                    {
                        theme: "Planetary System",
                        description: "Colorful planets orbiting a star",
                        prompt: "A beautiful planetary system with multiple colorful planets of different sizes orbiting a bright star, with asteroid belts and moons",
                        difficulty: "medium",
                        tags: ["planets", "solar system", "space", "orbits"]
                    },
                    {
                        theme: "Nebula Cloud",
                        description: "Colorful cosmic nebula with stars",
                        prompt: "A stunning cosmic nebula with swirling clouds of purple, blue, and pink gases, bright stars scattered throughout, and distant galaxies",
                        difficulty: "hard",
                        tags: ["nebula", "cosmos", "stars", "colorful"]
                    },
                    {
                        theme: "Space Station",
                        description: "Futuristic space station orbiting Earth",
                        prompt: "A magnificent space station orbiting Earth with solar panels, docking bays, and Earth's blue and green surface visible below",
                        difficulty: "hard",
                        tags: ["space station", "earth", "orbit", "futuristic"]
                    },
                    {
                        theme: "Moon Base",
                        description: "Lunar base with Earth in background",
                        prompt: "A futuristic moon base with domed buildings on the lunar surface, Earth rising in the black sky, and astronauts working outside",
                        difficulty: "medium",
                        tags: ["moon", "base", "earth", "astronauts"]
                    },
                    {
                        theme: "Galaxy Spiral",
                        description: "Spiral galaxy with cosmic dust",
                        prompt: "A magnificent spiral galaxy with bright arms of stars and cosmic dust, surrounded by the deep black of space with distant stars",
                        difficulty: "hard",
                        tags: ["galaxy", "spiral", "cosmic", "stars"]
                    }
                ]
            },
            
            seasonal: {
                name: "Seasonal Scenes",
                themes: [
                    {
                        theme: "Winter Village",
                        description: "Cozy village covered in snow",
                        prompt: "A charming winter village with snow-covered houses, smoke rising from chimneys, a frozen pond with ice skaters, and snow-laden pine trees",
                        difficulty: "medium",
                        tags: ["winter", "village", "snow", "cozy"]
                    },
                    {
                        theme: "Spring Meadow",
                        description: "Flower-filled meadow in springtime",
                        prompt: "A vibrant spring meadow filled with wildflowers, butterflies, young lambs playing, and a clear blue sky with fluffy white clouds",
                        difficulty: "easy",
                        tags: ["spring", "meadow", "flowers", "pastoral"]
                    },
                    {
                        theme: "Summer Beach",
                        description: "Tropical beach scene with palm trees",
                        prompt: "A pristine tropical beach with white sand, turquoise water, swaying palm trees, and a beach volleyball game in progress",
                        difficulty: "easy",
                        tags: ["summer", "beach", "tropical", "recreation"]
                    },
                    {
                        theme: "Autumn Harvest",
                        description: "Farm scene with autumn harvest",
                        prompt: "A picturesque farm scene with pumpkin patches, apple orchards, a red barn, and farmers gathering the autumn harvest",
                        difficulty: "medium",
                        tags: ["autumn", "harvest", "farm", "pumpkins"]
                    }
                ]
            },
            
            underwater: {
                name: "Underwater Worlds",
                themes: [
                    {
                        theme: "Coral Garden",
                        description: "Vibrant coral reef ecosystem",
                        prompt: "A thriving coral reef with colorful corals, tropical fish swimming through, sea turtles, and rays of sunlight filtering from above",
                        difficulty: "hard",
                        tags: ["coral", "reef", "tropical fish", "underwater"]
                    },
                    {
                        theme: "Kelp Forest",
                        description: "Underwater kelp forest with marine life",
                        prompt: "A mysterious underwater kelp forest with tall brown kelp swaying, sea otters playing, and various fish swimming between the fronds",
                        difficulty: "medium",
                        tags: ["kelp", "forest", "sea otters", "marine"]
                    },
                    {
                        theme: "Deep Sea",
                        description: "Deep ocean with bioluminescent creatures",
                        prompt: "The mysterious deep sea with bioluminescent jellyfish, glowing fish, and strange deep-sea creatures in the dark blue depths",
                        difficulty: "hard",
                        tags: ["deep sea", "bioluminescent", "jellyfish", "mysterious"]
                    },
                    {
                        theme: "Submarine Adventure",
                        description: "Yellow submarine exploring ocean",
                        prompt: "A cheerful yellow submarine exploring the ocean floor with curious fish looking through the windows and colorful sea plants around",
                        difficulty: "easy",
                        tags: ["submarine", "exploration", "ocean floor", "adventure"]
                    }
                ]
            }
        };
        
        // Difficulty modifiers to prevent puzzle piece appearance in images
        this.difficultyModifiers = {
            easy: {
                visualStyle: "simple, clear composition with bold, distinct areas and high contrast",
                detailLevel: "moderate detail with clear focal points",
                colorScheme: "vibrant, saturated colors with strong contrast"
            },
            medium: {
                visualStyle: "balanced composition with varied textures and good detail distribution",
                detailLevel: "rich detail with multiple points of interest",
                colorScheme: "harmonious color palette with good contrast"
            },
            hard: {
                visualStyle: "complex, intricate composition with fine details throughout",
                detailLevel: "highly detailed with subtle elements and textures",
                colorScheme: "sophisticated color palette with subtle gradations"
            }
        };
    }

    // Get all available themes across all categories
    getAllThemes() {
        const allThemes = [];
        Object.values(this.imageCategories).forEach(category => {
            allThemes.push(...category.themes);
        });
        return allThemes;
    }

    // Get themes by category
    getThemesByCategory(categoryName) {
        return this.imageCategories[categoryName]?.themes || [];
    }

    // Get themes by tags
    getThemesByTags(tags) {
        const allThemes = this.getAllThemes();
        return allThemes.filter(theme => 
            tags.some(tag => theme.tags.includes(tag))
        );
    }

    // Enhanced theme selection with more variety
    selectThemeByDifficulty(difficulty, preferredCategory = null, preferredTags = []) {
        let themes = [];
        
        if (preferredCategory && this.imageCategories[preferredCategory]) {
            themes = this.getThemesByCategory(preferredCategory);
        } else if (preferredTags.length > 0) {
            themes = this.getThemesByTags(preferredTags);
        } else {
            themes = this.getAllThemes();
        }

        // Filter by difficulty preference
        const filteredThemes = themes.filter(theme => {
            switch (difficulty.toLowerCase()) {
                case 'easy':
                    return theme.difficulty === 'easy' || theme.difficulty === 'medium';
                case 'medium':
                    return theme.difficulty === 'medium';
                case 'hard':
                    return theme.difficulty === 'hard' || theme.difficulty === 'medium';
                default:
                    return theme.difficulty === 'medium';
            }
        });

        return filteredThemes[Math.floor(Math.random() * filteredThemes.length)] || themes[0];
    }

    // Enhanced prompt creation to avoid puzzle piece imagery
    createOptimizedImagePrompt(theme, difficulty) {
        const modifier = this.difficultyModifiers[difficulty] || this.difficultyModifiers.medium;
        
        return `Create a beautiful, complete image of: ${theme.prompt}

IMPORTANT - SINGLE COMPLETE IMAGE:
- Create ONE complete, unified image without any divisions, grids, or puzzle piece borders
- NO puzzle pieces, jigsaw lines, or grid overlays
- NO segmented or divided sections that look like puzzle pieces
- Create a seamless, continuous image as a complete artwork

VISUAL STYLE:
- ${modifier.visualStyle}
- ${modifier.detailLevel}
- ${modifier.colorScheme}

COMPOSITION FOR PUZZLE ASSEMBLY (but keep image unified):
- Include distinctive landmarks or features that help identify different areas
- Use natural boundaries like horizons, paths, or color transitions
- Ensure each corner and edge has unique, memorable features
- Create visual flow and leading lines throughout the composition
- Balance detail distribution across the entire image

TECHNICAL REQUIREMENTS:
- High-quality digital illustration with excellent clarity
- Strong visual hierarchy with foreground, middle ground, and background
- Avoid repetitive patterns that make areas look identical
- Include directional elements to aid in puzzle assembly
- Use lighting and shadows to create depth and dimension

Style: Premium digital artwork, highly detailed, vibrant colors, perfect for high-quality puzzle creation. Think museum-quality illustration without any puzzle piece borders or divisions - just a beautiful, complete scene.`;
    }

    // Rest of the class methods remain the same...
    setDebugMode(enabled) {
        this.debugMode = enabled;
    }

    debugLog(message, type = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString();
            const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '📄';
            console.log(`${emoji} [${timestamp}] IMAGE_PUZZLE: ${message}`);
        }
    }

    // Enhanced generation method with category and tag support
    async generateImagePuzzle(difficulty = 'medium', options = {}) {
        const {
            maxRetries = 3,
            preferredCategory = null,
            preferredTags = [],
            customTheme = null
        } = options;

        this.debugLog(`🚀 Starting image puzzle generation (${difficulty})`);

        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            this.debugLog(`🔄 Attempt ${attempt}/${maxRetries}`);
            
            try {
                if (!this.apiKey) {
                    throw new Error('OPENAI_API_KEY environment variable not set');
                }

                // Select theme based on preferences
                const theme = customTheme || this.selectThemeByDifficulty(difficulty, preferredCategory, preferredTags);
                this.debugLog(`🎨 Selected theme: ${theme.theme} from category (${theme.difficulty})`);

                // Generate the puzzle
                const puzzleResult = await this.generateImageAndMetadata(theme, difficulty);
                
                if (puzzleResult.success) {
                    const formattedData = this.formatForPuzzleSystem(puzzleResult.data);
                    
                    this.debugLog(`✅ Successfully generated image puzzle on attempt ${attempt}`, 'success');
                    return {
                        success: true,
                        puzzleData: formattedData,
                        attempts: attempt,
                        imageGenerated: true,
                        selectedTheme: theme
                    };
                } else {
                    this.debugLog(`❌ Attempt ${attempt} failed: ${puzzleResult.error}`, 'error');
                    
                    if (attempt === maxRetries) {
                        return { 
                            success: false, 
                            message: puzzleResult.error || 'Failed to generate image puzzle',
                            attempts: attempt
                        };
                    }
                    
                    await new Promise(resolve => setTimeout(resolve, 2000));
                }

            } catch (error) {
                this.debugLog(`❌ Error on attempt ${attempt}: ${error.message}`, 'error');
                
                if (attempt === maxRetries) {
                    return {
                        success: false,
                        message: `Image puzzle generation error after ${maxRetries} attempts: ${error.message}`,
                        attempts: attempt
                    };
                }
                
                await new Promise(resolve => setTimeout(resolve, 2000));
            }
        }
    }

    // Get available categories for UI
    getAvailableCategories() {
        return Object.keys(this.imageCategories).map(key => ({
            key,
            name: this.imageCategories[key].name,
            themeCount: this.imageCategories[key].themes.length
        }));
    }

    // Get all unique tags for filtering
    getAllTags() {
        const allTags = new Set();
        this.getAllThemes().forEach(theme => {
            theme.tags.forEach(tag => allTags.add(tag));
        });
        return Array.from(allTags).sort();
    }

    // The rest of your existing methods (generateImageAndMetadata, getGridConfiguration, etc.) 
    // remain exactly the same...
    
    async generateImageAndMetadata(theme, difficulty) {
        this.debugLog('🎨 Generating image and puzzle metadata...');
        
        const puzzleId = `img_puzzle_${Date.now()}_${performance.now().toString().replace('.', '')}_${Math.random().toString(36).substr(2, 9)}`;
        
        try {
            // Step 1: Generate optimized image prompt
            const imagePrompt = this.createOptimizedImagePrompt(theme, difficulty);
            
            // Step 2: Generate image with DALL-E
            this.debugLog('🎨 Generating image with DALL-E...');
            const imageResponse = await axios.post('https://api.openai.com/v1/images/generations', {
                model: "dall-e-3",
                prompt: imagePrompt,
                n: 1,
                size: "1024x1024",
                response_format: "b64_json"
            }, {
                headers: {
                    'Authorization': `Bearer ${this.apiKey}`,
                    'Content-Type': 'application/json'
                },
                timeout: 60000
            });

            if (!imageResponse.data?.data?.[0]?.b64_json) {
                throw new Error('No image data received from DALL-E');
            }

            const imageBuffer = Buffer.from(imageResponse.data.data[0].b64_json, 'base64');
            this.debugLog(`📦 Generated image: ${imageBuffer.length} bytes`);

            // Step 3: Upload image to storage and validate URL
            this.debugLog('☁️ Uploading image to Supabase...');
            const imageUrl = await this.uploadImage(puzzleId, imageBuffer);
            
            if (!imageUrl) {
                throw new Error('Failed to get image URL from upload');
            }
            
            this.debugLog(`✅ Image uploaded successfully: ${imageUrl}`);
            
            // Validate that imageUrl is a proper URL
            try {
                new URL(imageUrl);
                this.debugLog(`🔗 Image URL validated: ${imageUrl.substring(0, 50)}...`);
            } catch (urlError) {
                throw new Error(`Invalid image URL generated: ${imageUrl}`);
            }

            // Step 4: Calculate grid configuration based on difficulty
            const gridConfig = this.getGridConfiguration(difficulty);
            
            // Step 5: Generate puzzle pieces metadata
            const puzzleMetadata = this.generatePuzzleMetadata(gridConfig, theme, difficulty);

            // Step 6: Create puzzle data structure with validated image URL
            const puzzleData = {
                puzzleId: puzzleId,
                type: 'image_puzzle',
                theme: theme.theme,
                description: `Assemble this ${theme.theme.toLowerCase()} image by arranging the puzzle pieces`,
                imageUrl: imageUrl,  // ✅ Supabase public URL
                imageWidth: 1024,
                imageHeight: 1024,
                imagePrompt: imagePrompt,
                gridSize: gridConfig.gridSize,
                totalPieces: gridConfig.totalPieces,
                pieceSize: gridConfig.pieceSize,
                puzzleMetadata: puzzleMetadata,
                difficulty: difficulty,
                timeLimit: this.getTimeLimitForDifficulty(difficulty, gridConfig.totalPieces),
                gameSettings: {
                    allowRotation: false, // Keep simple for now
                    snapTolerance: 20, // Pixels
                    showPreview: true,
                    shufflePieces: true,
                    showProgress: true
                },
                imageStatus: 'uploaded', // ✅ Indicates successful Supabase upload
                imageStorageProvider: 'supabase',
                imageGeneratedAt: new Date().toISOString(),
                imageUploadedAt: new Date().toISOString(), // ✅ Track upload time
                generationMethod: 'dalle_grid_puzzle',
                themeComplexity: theme.difficulty
            };

            // ✅ Validate that all required image data is present
            if (!puzzleData.imageUrl || !puzzleData.imageUrl.startsWith('http')) {
                throw new Error(`Invalid image URL in puzzle data: ${puzzleData.imageUrl}`);
            }

            this.debugLog(`✅ Generated image puzzle with ${gridConfig.totalPieces} pieces`);
            this.debugLog(`🖼️ Image URL: ${imageUrl}`);
            this.debugLog(`📏 Grid: ${gridConfig.gridSize}x${gridConfig.gridSize}`);
            this.debugLog(`📂 Storage location: image-puzzles/${puzzleId}.jpg`);

            return { success: true, data: puzzleData };

        } catch (error) {
            this.debugLog(`❌ Error generating image puzzle: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }


    getGridConfiguration(difficulty) {
        switch (difficulty.toLowerCase()) {
            case 'easy':
                return {
                    gridSize: 2, // 2x2 = 4 pieces
                    totalPieces: 4,
                    pieceSize: 512 // Each piece is 512x512 pixels
                };
            case 'medium':
                return {
                    gridSize: 3, // 3x3 = 9 pieces
                    totalPieces: 9,
                    pieceSize: 341 // Each piece is ~341x341 pixels
                };
            case 'hard':
                return {
                    gridSize: 4, // 4x4 = 16 pieces
                    totalPieces: 16,
                    pieceSize: 256 // Each piece is 256x256 pixels
                };
            default:
                return {
                    gridSize: 3,
                    totalPieces: 9,
                    pieceSize: 341
                };
        }
    }

    generatePuzzleMetadata(gridConfig, theme, difficulty) {
        const pieces = [];
        
        for (let row = 0; row < gridConfig.gridSize; row++) {
            for (let col = 0; col < gridConfig.gridSize; col++) {
                const pieceId = row * gridConfig.gridSize + col;
                
                pieces.push({
                    id: pieceId,
                    row: row,
                    col: col,
                    correctPosition: {
                        x: col * gridConfig.pieceSize,
                        y: row * gridConfig.pieceSize
                    },
                    sourceRect: {
                        x: col * gridConfig.pieceSize,
                        y: row * gridConfig.pieceSize,
                        width: gridConfig.pieceSize,
                        height: gridConfig.pieceSize
                    },
                    isCorner: (row === 0 || row === gridConfig.gridSize - 1) && 
                             (col === 0 || col === gridConfig.gridSize - 1),
                    isEdge: row === 0 || row === gridConfig.gridSize - 1 || 
                           col === 0 || col === gridConfig.gridSize - 1,
                    adjacentPieces: this.calculateAdjacentPieces(row, col, gridConfig.gridSize)
                });
            }
        }

        return {
            pieces: pieces,
            gridSize: gridConfig.gridSize,
            pieceSize: gridConfig.pieceSize,
            totalPieces: gridConfig.totalPieces,
            cornerPieces: pieces.filter(p => p.isCorner).length,
            edgePieces: pieces.filter(p => p.isEdge && !p.isCorner).length,
            centerPieces: pieces.filter(p => !p.isEdge).length
        };
    }

    calculateAdjacentPieces(row, col, gridSize) {
        const adjacent = [];
        
        // Check all 4 directions
        const directions = [
            [-1, 0], // top
            [1, 0],  // bottom
            [0, -1], // left
            [0, 1]   // right
        ];
        
        for (const [dRow, dCol] of directions) {
            const newRow = row + dRow;
            const newCol = col + dCol;
            
            if (newRow >= 0 && newRow < gridSize && newCol >= 0 && newCol < gridSize) {
                adjacent.push(newRow * gridSize + newCol);
            }
        }
        
        return adjacent;
    }

    async uploadImage(puzzleId, imageBuffer) {
        try {
            this.debugLog('☁️ Importing Supabase client...');
            const { supabase } = await import('../config/database.js');
            
            // Create organized path for image puzzles
            const fileName = `image-puzzles/${puzzleId}.jpg`;
            this.debugLog(`📁 Uploading to: ${fileName}`);
            
            // Upload the image buffer to Supabase storage
            const { data, error } = await supabase.storage
                .from('puzzle-images')
                .upload(fileName, imageBuffer, { 
                    contentType: 'image/jpeg', 
                    upsert: true,
                    cacheControl: '3600' // Cache for 1 hour
                });

            if (error) {
                this.debugLog(`❌ Supabase upload error: ${error.message}`, 'error');
                throw new Error(`Supabase upload failed: ${error.message}`);
            }

            this.debugLog(`✅ Upload successful: ${data.path}`);

            // Get the public URL for the uploaded image
            const { data: { publicUrl } } = supabase.storage
                .from('puzzle-images')
                .getPublicUrl(fileName);

            if (!publicUrl) {
                throw new Error('Failed to get public URL from Supabase');
            }

            this.debugLog(`🔗 Public URL generated: ${publicUrl}`);
            
            // Verify the URL is accessible (optional but good for validation)
            try {
                const response = await fetch(publicUrl, { method: 'HEAD' });
                if (!response.ok) {
                    this.debugLog(`⚠️ Image URL not immediately accessible: ${response.status}`, 'warning');
                }
            } catch (urlError) {
                this.debugLog(`⚠️ Could not verify image URL accessibility: ${urlError.message}`, 'warning');
                // Don't throw here - the upload succeeded, URL verification is just nice-to-have
            }

            return publicUrl;

        } catch (error) {
            this.debugLog(`❌ Image upload failed: ${error.message}`, 'error');
            throw new Error(`Image upload error: ${error.message}`);
        }
    }

    getTimeLimitForDifficulty(difficulty, pieceCount) {
        const baseTimePerPiece = {
            easy: 30000,   // 30 seconds per piece
            medium: 25000, // 25 seconds per piece  
            hard: 20000    // 20 seconds per piece
        };

        const timePerPiece = baseTimePerPiece[difficulty] || 25000;
        const baseTime = timePerPiece * pieceCount;
        
        // Add bonus time for setup and preview
        const bonusTime = Math.min(60000, pieceCount * 5000); // Up to 1 minute bonus
        
        return baseTime + bonusTime;
    }

    formatForPuzzleSystem(puzzleData) {
        console.log(`🔧 [FORMAT] Formatting puzzle data for storage...`);
        
        // Simple validation - just ensure we have the basic required data
        if (!puzzleData || !puzzleData.puzzleId || !puzzleData.imageUrl) {
            throw new Error('Invalid puzzle data - missing puzzleId or imageUrl');
        }

        console.log(`✅ [FORMAT] Formatting puzzle: ${puzzleData.puzzleId}`);
        console.log(`🖼️ [FORMAT] Image URL: ${puzzleData.imageUrl.substring(0, 50)}...`);

        // Create the question data (what the client needs to display the puzzle)
        const questionData = {
            puzzleId: puzzleData.puzzleId,
            type: "image_puzzle",
            theme: puzzleData.theme,
            description: puzzleData.description,
            imageUrl: puzzleData.imageUrl,
            imageWidth: puzzleData.imageWidth,
            imageHeight: puzzleData.imageHeight,
            gridSize: puzzleData.gridSize,
            totalPieces: puzzleData.totalPieces,
            pieceSize: puzzleData.pieceSize,
            puzzleMetadata: puzzleData.puzzleMetadata,
            timeLimit: puzzleData.timeLimit,
            gameSettings: puzzleData.gameSettings,
            instructions: puzzleData.instructions || "Drag and drop the puzzle pieces to recreate the original image",
            isCustomPuzzle: true,
            generatedAt: new Date().toISOString(),
            version: "1.0"
        };

        // Create the answer data (how to score/validate the puzzle completion)
        const answerData = {
            correctAssembly: puzzleData.puzzleMetadata.pieces.map(piece => ({
                pieceId: piece.id,
                correctRow: piece.row,
                correctCol: piece.col,
                correctPosition: piece.correctPosition || { 
                    x: piece.col * puzzleData.pieceSize, 
                    y: piece.row * puzzleData.pieceSize 
                }
            })),
            totalPieces: puzzleData.totalPieces,
            gridSize: puzzleData.gridSize,
            maxScore: puzzleData.totalPieces * 10,
            puzzleType: "image_assembly"
        };

        // Return formatted data ready for database storage
        const result = {
            question: JSON.stringify(questionData),
            answer: JSON.stringify(answerData),
            hint: `Assemble ${puzzleData.totalPieces} pieces to recreate this beautiful ${puzzleData.theme.toLowerCase()}`,
            difficulty: puzzleData.difficulty || 'medium'
        };

        console.log(`✅ [FORMAT] Successfully formatted puzzle for storage`);
        return result;
    }
    
    // 🔧 Helper method to generate default puzzle metadata when missing
    generateDefaultPuzzleMetadata(gridSize, pieceSize) {
        const pieces = [];
        
        for (let row = 0; row < gridSize; row++) {
            for (let col = 0; col < gridSize; col++) {
                const pieceId = row * gridSize + col;
                
                pieces.push({
                    id: pieceId,
                    row: row,
                    col: col,
                    correctPosition: {
                        x: col * pieceSize,
                        y: row * pieceSize
                    },
                    sourceRect: {
                        x: col * pieceSize,
                        y: row * pieceSize,
                        width: pieceSize,
                        height: pieceSize
                    },
                    isCorner: (row === 0 || row === gridSize - 1) && 
                             (col === 0 || col === gridSize - 1),
                    isEdge: row === 0 || row === gridSize - 1 || 
                           col === 0 || col === gridSize - 1,
                    adjacentPieces: this.calculateAdjacentPieces(row, col, gridSize)
                });
            }
        }
    
        return {
            pieces: pieces,
            gridSize: gridSize,
            pieceSize: pieceSize,
            totalPieces: gridSize * gridSize,
            cornerPieces: pieces.filter(p => p.isCorner).length,
            edgePieces: pieces.filter(p => p.isEdge && !p.isCorner).length,
            centerPieces: pieces.filter(p => !p.isEdge).length,
            generatedFallback: true // Flag to indicate this was auto-generated
        };
    }
    
    // 🎯 Helper methods for enhanced metadata
    getDifficultyModifier(difficulty) {
        const modifiers = {
            easy: 1.0,
            medium: 1.2,
            hard: 1.5
        };
        return modifiers[difficulty.toLowerCase()] || 1.0;
    }
    
    getRecommendedAge(difficulty) {
        const ageMap = {
            easy: "6+",
            medium: "8+", 
            hard: "12+"
        };
        return ageMap[difficulty.toLowerCase()] || "8+";
    }
    
    getSkillLevel(gridSize) {
        if (gridSize <= 2) return "beginner";
        if (gridSize <= 3) return "intermediate";
        return "advanced";
    }
    

    // Additional theme categories you could add for even more variety

additionalCategories = {
    food: {
        name: "Food & Cuisine",
        themes: [
            {
                theme: "Farmers Market",
                description: "Fresh produce at outdoor market",
                prompt: "A vibrant farmers market with colorful fruits and vegetables displayed in wooden crates, flowers, bread stalls, and people shopping",
                difficulty: "medium",
                tags: ["food", "market", "fresh", "colorful", "outdoor"]
            },
            {
                theme: "Bakery Window",
                description: "Artisan bakery display window",
                prompt: "A charming bakery window display with fresh pastries, colorful macarons, wedding cakes, and bread loaves on wooden shelves",
                difficulty: "easy",
                tags: ["bakery", "pastries", "colorful", "display", "cozy"]
            },
            {
                theme: "Sushi Platter",
                description: "Artistic sushi arrangement",
                prompt: "An elegant sushi platter with various colorful sushi rolls, sashimi, wasabi, ginger, and chopsticks on a dark wooden board",
                difficulty: "hard",
                tags: ["sushi", "japanese", "colorful", "artistic", "elegant"]
            }
        ]
    },

    vehicles: {
        name: "Transportation",
        themes: [
            {
                theme: "Classic Cars",
                description: "Vintage automobile collection",
                prompt: "A collection of colorful vintage cars from the 1950s parked at a classic car show with chrome details and period costumes",
                difficulty: "medium",
                tags: ["cars", "vintage", "colorful", "classic", "chrome"]
            },
            {
                theme: "Hot Air Balloons",
                description: "Colorful balloons in sky",
                prompt: "A festival of hot air balloons in various colors and patterns floating against a clear blue sky with mountains below",
                difficulty: "easy",
                tags: ["balloons", "sky", "colorful", "festival", "peaceful"]
            },
            {
                theme: "Steam Train",
                description: "Historic locomotive in countryside",
                prompt: "A classic steam locomotive with billowing smoke traveling through rolling green countryside with a vintage station",
                difficulty: "medium",
                tags: ["train", "steam", "countryside", "vintage", "smoke"]
            }
        ]
    },

    sports: {
        name: "Sports & Recreation",
        themes: [
            {
                theme: "Soccer Stadium",
                description: "Packed stadium during match",
                prompt: "A vibrant soccer stadium filled with cheering fans, colorful team banners, green field, and players in action",
                difficulty: "hard",
                tags: ["soccer", "stadium", "crowd", "colorful", "action"]
            },
            {
                theme: "Ski Resort",
                description: "Winter sports mountain resort",
                prompt: "A bustling ski resort with colorful ski outfits, chairlifts, snow-covered slopes, and a cozy lodge with smoke from chimneys",
                difficulty: "medium",
                tags: ["skiing", "winter", "mountain", "colorful", "lodge"]
            },
            {
                theme: "Beach Volleyball",
                description: "Tournament on tropical beach",
                prompt: "A beach volleyball tournament with players in action, colorful nets, sand, palm trees, and spectators under umbrellas",
                difficulty: "easy",
                tags: ["volleyball", "beach", "tropical", "sand", "action"]
            }
        ]
    },

    cultural: {
        name: "World Cultures",
        themes: [
            {
                theme: "Japanese Garden",
                description: "Traditional zen garden with koi",
                prompt: "A serene Japanese garden with a koi pond, cherry blossom trees, stone lanterns, wooden bridges, and raked gravel patterns",
                difficulty: "medium",
                tags: ["japanese", "garden", "zen", "koi", "peaceful"]
            },
            {
                theme: "Indian Festival",
                description: "Colorful Holi celebration",
                prompt: "A vibrant Holi festival celebration with people throwing colored powder, traditional clothing, decorations, and joyful dancing",
                difficulty: "hard",
                tags: ["indian", "festival", "colorful", "celebration", "traditional"]
            },
            {
                theme: "Mexican Fiesta",
                description: "Traditional Mexican celebration",
                prompt: "A festive Mexican plaza with colorful papel picado banners, mariachi musicians, traditional dancers, and food stalls",
                difficulty: "medium",
                tags: ["mexican", "fiesta", "colorful", "music", "traditional"]
            }
        ]
    },

    technology: {
        name: "Technology & Future",
        themes: [
            {
                theme: "Robot Factory",
                description: "Futuristic manufacturing facility",
                prompt: "A high-tech robot manufacturing facility with colorful LED lights, robotic arms, conveyor belts, and sleek modern design",
                difficulty: "hard",
                tags: ["robots", "factory", "futuristic", "technology", "colorful"]
            },
            {
                theme: "Smart City",
                description: "Connected urban environment",
                prompt: "A futuristic smart city with electric vehicles, solar panels, green buildings, holographic displays, and clean energy systems",
                difficulty: "hard",
                tags: ["smart city", "futuristic", "technology", "green", "modern"]
            },
            {
                theme: "Virtual Reality",
                description: "VR gaming environment",
                prompt: "A colorful virtual reality gaming world with floating platforms, neon lights, digital landscapes, and players with VR headsets",
                difficulty: "medium",
                tags: ["vr", "gaming", "digital", "neon", "futuristic"]
            }
        ]
    },

    hobbies: {
        name: "Hobbies & Crafts",
        themes: [
            {
                theme: "Art Studio",
                description: "Artist's creative workspace",
                prompt: "A bright art studio with colorful paintings on easels, paint palettes, brushes, canvases, and art supplies scattered creatively",
                difficulty: "medium",
                tags: ["art", "studio", "colorful", "creative", "painting"]
            },
            {
                theme: "Quilting Circle",
                description: "Traditional quilting gathering",
                prompt: "A cozy quilting circle with colorful fabric squares, quilting hoops, sewing supplies, and finished quilts displayed on walls",
                difficulty: "easy",
                tags: ["quilting", "fabric", "colorful", "traditional", "cozy"]
            },
            {
                theme: "Rock Climbing",
                description: "Outdoor climbing adventure",
                prompt: "Rock climbers on a colorful cliff face with safety gear, ropes, beautiful mountain views, and equipment scattered below",
                difficulty: "hard",
                tags: ["climbing", "adventure", "outdoor", "mountain", "equipment"]
            }
        ]
    }
};

// Method to easily add new categories
addCategory(categoryKey, categoryData) {
    this.imageCategories[categoryKey] = categoryData;
}

// Method to add themes to existing category
addThemesToCategory(categoryKey, newThemes) {
    if (this.imageCategories[categoryKey]) {
        this.imageCategories[categoryKey].themes.push(...newThemes);
    }
}

// Dynamic theme generation based on user input
generateCustomTheme(userDescription, difficulty = 'medium') {
    return {
        theme: "Custom Scene",
        description: userDescription,
        prompt: `Create a beautiful scene: ${userDescription}`,
        difficulty: difficulty,
        tags: ["custom", "user-generated"]
    };
}
}

// Export for use in puzzle generation system
export const imagePuzzleGenerator = new ImagePuzzleGenerator();
// services/waldoPuzzleGenerator.js - Enhanced with Expanded Scenarios

import axios from 'axios';

export class WaldoPuzzleGenerator {
    constructor() {
        this.debugMode = false;
        this.apiKey = process.env.OPENAI_API_KEY; // kept for DALL-E
        this.anthropicKey = process.env.ANTHROPIC_API_KEY;
        
        // Grid configuration optimized for phone screens
        this.gridConfig = {
            rows: 6,
            cols: 8,
            totalCells: 48
        };
        
        // 🎨 EXPANDED: Scene templates with much more variety
        this.sceneTemplates = [
            // OUTDOOR SCENES
            {
                theme: "Busy Park Scene",
                description: "A crowded public park with many people, trees, benches, playground equipment, dogs, and various outdoor activities",
                complexity: "medium",
                hidingSpots: ["behind trees", "near benches", "by playground equipment", "among crowd"],
                expectedElements: ["trees", "benches", "people", "playground", "dogs", "paths", "fountain", "picnic areas"],
                category: "outdoor"
            },
            {
                theme: "Farmers Market",
                description: "A bustling farmers market with vendors, customers, colorful produce stalls, fruits, vegetables, and shopping activity",
                complexity: "high",
                hidingSpots: ["between stalls", "among produce", "near vendors", "in crowd"],
                expectedElements: ["market stalls", "fruits", "vegetables", "vendors", "customers", "signs", "baskets", "umbrellas"],
                category: "outdoor"
            },
            {
                theme: "Busy Beach",
                description: "A crowded beach scene with people sunbathing, playing, beach umbrellas, chairs, ocean activities, and beach vendors",
                complexity: "medium",
                hidingSpots: ["behind umbrellas", "near beach chairs", "among people", "by water"],
                expectedElements: ["beach umbrellas", "chairs", "people", "ocean", "boats", "beach balls", "towels", "vendors"],
                category: "outdoor"
            },
            {
                theme: "Carnival Scene",
                description: "A colorful carnival with rides, game booths, food stalls, crowds, balloons, and festive activities",
                complexity: "high",
                hidingSpots: ["behind game booths", "near rides", "among crowd", "by food stalls"],
                expectedElements: ["ferris wheel", "game booths", "food stalls", "crowds", "balloons", "prizes", "rides", "flags"],
                category: "outdoor"
            },
            {
                theme: "City Plaza",
                description: "A busy city plaza with people walking, street performers, vendors, benches, fountains, and urban activities",
                complexity: "medium",
                hidingSpots: ["behind fountains", "near vendors", "among crowd", "by buildings"],
                expectedElements: ["fountain", "benches", "people", "vendors", "performers", "buildings", "trees", "statues"],
                category: "outdoor"
            },
            {
                theme: "School Playground",
                description: "A lively school playground during recess with children playing, teachers supervising, playground equipment, and various activities",
                complexity: "easy",
                hidingSpots: ["behind equipment", "near swings", "among children", "by building"],
                expectedElements: ["swings", "slides", "children", "teachers", "playground", "basketball court", "benches", "trees"],
                category: "outdoor"
            },
            {
                theme: "Zoo Adventure",
                description: "A busy zoo with families visiting animal exhibits, zoo keepers, food stands, pathways, and diverse wildlife displays",
                complexity: "medium",
                hidingSpots: ["near animal enclosures", "by food stands", "among visitors", "behind signs"],
                expectedElements: ["animals", "enclosures", "visitors", "pathways", "signs", "food stands", "benches", "trees"],
                category: "outdoor"
            },
            {
                theme: "Music Festival",
                description: "An outdoor music festival with stages, crowds dancing, food trucks, merchandise booths, and festive atmosphere",
                complexity: "high",
                hidingSpots: ["near stages", "by food trucks", "among crowd", "behind booths"],
                expectedElements: ["stage", "crowd", "food trucks", "booths", "speakers", "lights", "banners", "tents"],
                category: "outdoor"
            },
            {
                theme: "Garden Party",
                description: "An elegant garden party with guests mingling, tables with food, decorations, flowers, and outdoor dining",
                complexity: "medium",
                hidingSpots: ["behind flower arrangements", "near tables", "among guests", "by garden features"],
                expectedElements: ["flowers", "tables", "guests", "decorations", "garden", "chairs", "umbrellas", "food"],
                category: "outdoor"
            },
            {
                theme: "Ski Resort Base",
                description: "A busy ski resort base with skiers, equipment rentals, lodge, chairlifts, and winter activities",
                complexity: "medium",
                hidingSpots: ["near equipment", "by lodge", "among skiers", "behind signs"],
                expectedElements: ["skiers", "equipment", "lodge", "chairlift", "snow", "signs", "benches", "trees"],
                category: "outdoor"
            },

            // INDOOR SCENES
            {
                theme: "Shopping Mall",
                description: "A crowded shopping mall with multiple stores, shoppers, escalators, food court, and busy retail activity",
                complexity: "high",
                hidingSpots: ["near store entrances", "by escalators", "in food court", "among shoppers"],
                expectedElements: ["stores", "shoppers", "escalators", "food court", "signs", "plants", "benches", "displays"],
                category: "indoor"
            },
            {
                theme: "Library Lobby",
                description: "A busy library with people reading, bookshelves, study areas, information desk, and quiet activities",
                complexity: "easy",
                hidingSpots: ["between bookshelves", "near study areas", "by information desk", "among readers"],
                expectedElements: ["bookshelves", "readers", "desks", "chairs", "computers", "signs", "plants", "books"],
                category: "indoor"
            },
            {
                theme: "Airport Terminal",
                description: "A bustling airport terminal with travelers, check-in counters, shops, seating areas, and departure boards",
                complexity: "high",
                hidingSpots: ["near gates", "by shops", "in seating areas", "among travelers"],
                expectedElements: ["travelers", "counters", "shops", "seats", "luggage", "signs", "displays", "pillars"],
                category: "indoor"
            },
            {
                theme: "Museum Gallery",
                description: "An art museum gallery with visitors viewing paintings, sculptures, information plaques, and guided tours",
                complexity: "medium",
                hidingSpots: ["near artworks", "by information stands", "among visitors", "behind sculptures"],
                expectedElements: ["paintings", "sculptures", "visitors", "plaques", "benches", "guards", "lights", "walls"],
                category: "indoor"
            },
            {
                theme: "Restaurant Kitchen",
                description: "A busy restaurant kitchen with chefs cooking, waitstaff, kitchen equipment, food preparation, and organized chaos",
                complexity: "high",
                hidingSpots: ["near equipment", "by prep areas", "among staff", "behind counters"],
                expectedElements: ["chefs", "equipment", "food", "counters", "utensils", "plates", "stoves", "storage"],
                category: "indoor"
            },
            {
                theme: "Hospital Lobby",
                description: "A hospital lobby with patients, medical staff, reception desk, waiting areas, and healthcare activity",
                complexity: "medium",
                hidingSpots: ["in waiting areas", "near reception", "by information boards", "among people"],
                expectedElements: ["reception", "seating", "patients", "staff", "signs", "plants", "elevators", "information"],
                category: "indoor"
            },
            {
                theme: "Classroom Scene",
                description: "An active classroom with students learning, teacher instructing, desks, educational materials, and school supplies",
                complexity: "easy",
                hidingSpots: ["near desks", "by whiteboard", "among students", "by bookshelves"],
                expectedElements: ["students", "teacher", "desks", "whiteboard", "books", "supplies", "posters", "plants"],
                category: "indoor"
            },
            {
                theme: "Office Building",
                description: "A busy office environment with workers at desks, meeting rooms, printers, plants, and corporate activity",
                complexity: "medium",
                hidingSpots: ["near desks", "by printers", "in meeting areas", "among workers"],
                expectedElements: ["desks", "computers", "workers", "chairs", "plants", "printers", "whiteboards", "supplies"],
                category: "indoor"
            },

            // SPECIAL EVENT SCENES
            {
                theme: "Wedding Reception",
                description: "An elegant wedding reception with guests dancing, dining tables, decorations, band, and celebration activities",
                complexity: "medium",
                hidingSpots: ["near tables", "by dance floor", "among guests", "behind decorations"],
                expectedElements: ["guests", "tables", "dance floor", "band", "decorations", "flowers", "cake", "chairs"],
                category: "event"
            },
            {
                theme: "Birthday Party",
                description: "A children's birthday party with kids playing games, balloons, cake table, presents, and party activities",
                complexity: "easy",
                hidingSpots: ["near balloons", "by cake table", "among children", "behind presents"],
                expectedElements: ["children", "balloons", "cake", "presents", "games", "decorations", "chairs", "table"],
                category: "event"
            },
            {
                theme: "Art Fair",
                description: "An outdoor art fair with artists displaying work, visitors browsing, booths, sculptures, and creative atmosphere",
                complexity: "medium",
                hidingSpots: ["near artwork", "by booths", "among visitors", "behind displays"],
                expectedElements: ["artwork", "artists", "visitors", "booths", "sculptures", "easels", "tables", "umbrellas"],
                category: "event"
            },
            {
                theme: "Science Fair",
                description: "A school science fair with student projects, judges, displays, experiments, and educational presentations",
                complexity: "medium",
                hidingSpots: ["near projects", "by displays", "among students", "behind boards"],
                expectedElements: ["projects", "students", "judges", "displays", "boards", "experiments", "tables", "equipment"],
                category: "event"
            },

            // TRANSPORTATION SCENES
            {
                theme: "Train Station",
                description: "A busy train station with passengers, ticket counters, departure boards, platforms, and travel activity",
                complexity: "high",
                hidingSpots: ["near platforms", "by ticket counters", "among passengers", "behind pillars"],
                expectedElements: ["passengers", "trains", "platforms", "counters", "boards", "benches", "luggage", "signs"],
                category: "transport"
            },
            {
                theme: "Bus Terminal",
                description: "A crowded bus terminal with travelers, buses, seating areas, information desks, and transit activity",
                complexity: "medium",
                hidingSpots: ["near buses", "in seating areas", "by information desk", "among travelers"],
                expectedElements: ["buses", "travelers", "seating", "information", "signs", "luggage", "vendors", "schedules"],
                category: "transport"
            },
            {
                theme: "Subway Platform",
                description: "An underground subway platform with commuters, trains, benches, signs, and urban transit atmosphere",
                complexity: "medium",
                hidingSpots: ["near trains", "by benches", "among commuters", "behind pillars"],
                expectedElements: ["commuters", "trains", "platform", "benches", "signs", "tracks", "lights", "tiles"],
                category: "transport"
            },

            // SEASONAL SCENES
            {
                theme: "Christmas Market",
                description: "A festive Christmas market with holiday vendors, decorations, shoppers, hot drinks, and winter celebration",
                complexity: "high",
                hidingSpots: ["near vendor stalls", "by decorations", "among shoppers", "behind trees"],
                expectedElements: ["vendors", "shoppers", "decorations", "lights", "stalls", "trees", "snow", "ornaments"],
                category: "seasonal"
            },
            {
                theme: "Halloween Festival",
                description: "A Halloween festival with costumed people, pumpkins, decorations, candy stands, and spooky fun activities",
                complexity: "medium",
                hidingSpots: ["near pumpkins", "by decorations", "among costumed people", "behind stands"],
                expectedElements: ["costumes", "pumpkins", "decorations", "candy", "stands", "lights", "people", "activities"],
                category: "seasonal"
            },
            {
                theme: "Summer Fair",
                description: "A summer county fair with rides, game booths, food vendors, families, and outdoor entertainment",
                complexity: "high",
                hidingSpots: ["near rides", "by game booths", "among families", "behind food stands"],
                expectedElements: ["rides", "booths", "vendors", "families", "games", "food", "prizes", "flags"],
                category: "seasonal"
            },

            // CULTURAL SCENES
            {
                theme: "Street Market",
                description: "A vibrant street market with local vendors, exotic goods, shoppers, street food, and cultural atmosphere",
                complexity: "high",
                hidingSpots: ["between stalls", "near vendors", "among shoppers", "behind goods"],
                expectedElements: ["vendors", "goods", "shoppers", "stalls", "food", "signs", "baskets", "umbrellas"],
                category: "cultural"
            },
            {
                theme: "Temple Festival",
                description: "A colorful temple festival with ceremonies, decorations, participants, traditional activities, and cultural celebration",
                complexity: "medium",
                hidingSpots: ["near decorations", "among participants", "by temple structures", "behind banners"],
                expectedElements: ["temple", "decorations", "participants", "ceremonies", "banners", "flowers", "offerings", "crowd"],
                category: "cultural"
            },

            // NATURE SCENES
            {
                theme: "Camping Ground",
                description: "A busy camping ground with tents, campers, campfires, outdoor activities, and nature setting",
                complexity: "medium",
                hidingSpots: ["near tents", "by campfires", "among campers", "behind trees"],
                expectedElements: ["tents", "campers", "campfires", "trees", "equipment", "chairs", "tables", "nature"],
                category: "nature"
            },
            {
                theme: "Botanical Garden",
                description: "A lush botanical garden with visitors exploring, diverse plants, pathways, greenhouses, and natural beauty",
                complexity: "medium",
                hidingSpots: ["among plants", "near pathways", "by greenhouses", "behind foliage"],
                expectedElements: ["plants", "visitors", "pathways", "greenhouse", "flowers", "trees", "benches", "signs"],
                category: "nature"
            }
        ];

        // 🎯 EXPANDED: More diverse hidden objects with better variety
        this.hiddenObjects = [
            // EASY OBJECTS (Bright colors, distinctive shapes)
            {
                name: "red balloon",
                description: "a small red balloon",
                size: "small",
                difficulty: "easy",
                searchHint: "Look for something floating in the air",
                color: "red"
            },
            {
                name: "orange ball",
                description: "a small orange ball",
                size: "small",
                difficulty: "easy",
                searchHint: "Spot the round toy",
                color: "orange"
            },
            {
                name: "yellow sun hat",
                description: "a bright yellow sun hat",
                size: "small",
                difficulty: "easy",
                searchHint: "Find the sunny head accessory",
                color: "yellow"
            },
            {
                name: "blue backpack",
                description: "a small blue backpack",
                size: "small",
                difficulty: "easy",
                searchHint: "Look for something to carry things",
                color: "blue"
            },
            {
                name: "green apple",
                description: "a bright green apple",
                size: "small",
                difficulty: "easy",
                searchHint: "Find the healthy snack",
                color: "green"
            },
            {
                name: "purple kite",
                description: "a small purple kite",
                size: "small",
                difficulty: "easy",
                searchHint: "Spot the flying toy",
                color: "purple"
            },

            // MEDIUM OBJECTS (Moderately challenging)
            {
                name: "yellow cat",
                description: "a small yellow cat sitting",
                size: "small", 
                difficulty: "medium",
                searchHint: "Find the furry friend hiding somewhere",
                color: "yellow"
            },
            {
                name: "blue umbrella",
                description: "a small blue umbrella",
                size: "medium",
                difficulty: "medium",
                searchHint: "Spot the object that keeps you dry",
                color: "blue"
            },
            {
                name: "pink hat",
                description: "a small pink hat",
                size: "small",
                difficulty: "medium",
                searchHint: "Find the head accessory",
                color: "pink"
            },
            {
                name: "red coffee cup",
                description: "a small red coffee cup",
                size: "small",
                difficulty: "medium",
                searchHint: "Look for the morning drink",
                color: "red"
            },
            {
                name: "green camera",
                description: "a small green camera",
                size: "small",
                difficulty: "medium",
                searchHint: "Find the picture-taking device",
                color: "green"
            },
            {
                name: "orange teddy bear",
                description: "a small orange teddy bear",
                size: "small",
                difficulty: "medium",
                searchHint: "Spot the cuddly toy",
                color: "orange"
            },
            {
                name: "purple sunglasses",
                description: "small purple sunglasses",
                size: "small",
                difficulty: "medium",
                searchHint: "Find the eye protection",
                color: "purple"
            },
            {
                name: "blue bicycle",
                description: "a small blue bicycle",
                size: "medium",
                difficulty: "medium",
                searchHint: "Look for the two-wheeled vehicle",
                color: "blue"
            },

            // HARD OBJECTS (Challenging to find)
            {
                name: "green book",
                description: "a small green book",
                size: "small",
                difficulty: "hard",
                searchHint: "Look for something you read",
                color: "green"
            },
            {
                name: "purple flower",
                description: "a small purple flower",
                size: "small",
                difficulty: "hard",
                searchHint: "Find the beautiful bloom",
                color: "purple"
            },
            {
                name: "white dove",
                description: "a small white dove",
                size: "small",
                difficulty: "hard",
                searchHint: "Look for the bird of peace",
                color: "white"
            },
            {
                name: "red watch",
                description: "a small red wristwatch",
                size: "small",
                difficulty: "hard",
                searchHint: "Find the time-keeping device",
                color: "red"
            },
            {
                name: "yellow pencil",
                description: "a bright yellow pencil",
                size: "small",
                difficulty: "hard",
                searchHint: "Spot the writing tool",
                color: "yellow"
            },
            {
                name: "blue butterfly",
                description: "a small blue butterfly",
                size: "small",
                difficulty: "hard",
                searchHint: "Find the flying insect",
                color: "blue"
            },
            {
                name: "pink rose",
                description: "a small pink rose",
                size: "small",
                difficulty: "hard",
                searchHint: "Look for the romantic flower",
                color: "pink"
            },
            {
                name: "orange carrot",
                description: "a small orange carrot",
                size: "small",
                difficulty: "hard",
                searchHint: "Find the healthy vegetable",
                color: "orange"
            },
            {
                name: "green leaf",
                description: "a small green leaf",
                size: "small",
                difficulty: "hard",
                searchHint: "Spot the piece of nature",
                color: "green"
            },
            {
                name: "purple grape bunch",
                description: "a small bunch of purple grapes",
                size: "small",
                difficulty: "hard",
                searchHint: "Find the fruit cluster",
                color: "purple"
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
            console.log(`${emoji} [${timestamp}] WALDO_PUZZLE: ${message}`);
        }
    }

    // 🎨 ENHANCED: Better scene selection with category variety
    selectSceneByDifficulty(difficulty) {
        const filteredScenes = this.sceneTemplates.filter(scene => {
            switch (difficulty.toLowerCase()) {
                case 'easy': 
                    return scene.complexity === 'easy' || scene.complexity === 'medium';
                case 'medium': 
                    return scene.complexity === 'medium';
                case 'hard': 
                    return scene.complexity === 'high' || scene.complexity === 'medium';
                default: 
                    return scene.complexity === 'medium';
            }
        });

        // Add some randomness by category to ensure variety
        const categories = [...new Set(filteredScenes.map(scene => scene.category))];
        const selectedCategory = categories[Math.floor(Math.random() * categories.length)];
        const categoryScenes = filteredScenes.filter(scene => scene.category === selectedCategory);
        
        // Fallback to all filtered scenes if category has no scenes
        const finalScenes = categoryScenes.length > 0 ? categoryScenes : filteredScenes;
        
        return finalScenes[Math.floor(Math.random() * finalScenes.length)] || this.sceneTemplates[0];
    }

    // 🎯 ENHANCED: Better object selection with color variety
    selectObjectsByDifficulty(difficulty, count = 3) {
        const filteredObjects = this.hiddenObjects.filter(obj => {
            switch (difficulty.toLowerCase()) {
                case 'easy':
                    return obj.difficulty === 'easy' || obj.difficulty === 'medium';
                case 'medium':
                    return obj.difficulty === 'medium';
                case 'hard':
                    return obj.difficulty === 'hard' || obj.difficulty === 'medium';
                default:
                    return obj.difficulty === 'medium';
            }
        });

        // Ensure color variety - no duplicate colors
        const selectedObjects = [];
        const usedColors = new Set();
        const shuffled = filteredObjects.sort(() => Math.random() - 0.5);
        
        for (const obj of shuffled) {
            if (selectedObjects.length >= count) break;
            
            // For first few objects, ensure color variety
            if (selectedObjects.length < 3 && usedColors.has(obj.color)) {
                continue; // Skip if color already used for first 3 objects
            }
            
            selectedObjects.push(obj);
            usedColors.add(obj.color);
        }
        
        // If we don't have enough objects, fill with remaining ones
        while (selectedObjects.length < count && selectedObjects.length < filteredObjects.length) {
            for (const obj of shuffled) {
                if (!selectedObjects.includes(obj)) {
                    selectedObjects.push(obj);
                    break;
                }
            }
        }

        return selectedObjects.slice(0, count);
    }

    getObjectCountForDifficulty(difficulty) {
        switch (difficulty.toLowerCase()) {
            case 'easy': return 2;
            case 'medium': return 3;
            case 'hard': return 4;
            default: return 3;
        }
    }

    // ✅ Convert percentage position to grid cell
    percentageToGridCell(xPercent, yPercent) {
        // Clamp values to 0-100
        const x = Math.max(0, Math.min(100, xPercent));
        const y = Math.max(0, Math.min(100, yPercent));
        
        // Convert to grid coordinates (0-based)
        const col = Math.floor((x / 100) * this.gridConfig.cols);
        const row = Math.floor((y / 100) * this.gridConfig.rows);
        
        // Clamp to valid grid bounds
        const clampedCol = Math.max(0, Math.min(this.gridConfig.cols - 1, col));
        const clampedRow = Math.max(0, Math.min(this.gridConfig.rows - 1, row));
        
        // Convert to cell number (1-based)
        const cellNumber = (clampedRow * this.gridConfig.cols) + clampedCol + 1;
        
        this.debugLog(`Position conversion: (${x}%, ${y}%) → Row ${clampedRow + 1}, Col ${clampedCol + 1} → Cell ${cellNumber}`);
        
        return {
            cellNumber,
            row: clampedRow + 1, // 1-based for display
            col: clampedCol + 1, // 1-based for display
            xPercent: x,
            yPercent: y
        };
    }

    async generateWaldoPuzzle(difficulty = 'medium', maxRetries = 3) {
        this.debugLog(`🚀 Starting Waldo puzzle generation (${difficulty})`);

        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            this.debugLog(`🔄 Attempt ${attempt}/${maxRetries}`);
            
            try {
                if (!this.apiKey) {
                    throw new Error('OPENAI_API_KEY environment variable not set (needed for DALL-E)');
                }

                // Select scene and objects based on difficulty
                const sceneTemplate = this.selectSceneByDifficulty(difficulty);
                const objectCount = this.getObjectCountForDifficulty(difficulty);
                const hiddenObjects = this.selectObjectsByDifficulty(difficulty, objectCount);
                
                this.debugLog(`🎨 Selected scene: ${sceneTemplate.theme} (${sceneTemplate.complexity}) - Category: ${sceneTemplate.category}`);
                this.debugLog(`🎯 Selected objects: ${hiddenObjects.map(obj => `${obj.name} (${obj.color})`).join(', ')}`);

                // Generate the puzzle
                const puzzleResult = await this.generateSceneAndDetectObjects(sceneTemplate, hiddenObjects, difficulty);
                
                if (puzzleResult.success) {
                    const formattedData = this.formatForPuzzleSystem(puzzleResult.data);
                    
                    this.debugLog(`✅ Successfully generated Waldo puzzle on attempt ${attempt}`, 'success');
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
                            message: puzzleResult.error || 'Failed to generate Waldo puzzle',
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
                        message: `Waldo puzzle generation error after ${maxRetries} attempts: ${error.message}`,
                        attempts: attempt
                    };
                }
                
                await new Promise(resolve => setTimeout(resolve, 2000));
            }
        }
    }

    async generateSceneAndDetectObjects(sceneTemplate, hiddenObjects, difficulty) {
        this.debugLog('🎨 Generating scene and detecting objects...');
        
        const puzzleId = `waldo_${Date.now()}_${performance.now().toString().replace('.', '')}_${Math.random().toString(36).substr(2, 9)}`;
        
        try {
            // Step 1: Generate optimized scene prompt
            const imagePrompt = this.createOptimizedScenePrompt(sceneTemplate, hiddenObjects, difficulty);
            
            // Step 2: Generate image with DALL-E
            this.debugLog('🎨 Generating scene with DALL-E...');
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

            // Step 4: ✅ Use improved Vision API detection with percentage positions
            this.debugLog('👁️ Detecting object locations with improved Vision API...');
            const detectionResults = await this.detectObjectLocationsWithPercentages(imageResponse.data.data[0].b64_json, hiddenObjects);

            if (!detectionResults.success || detectionResults.objects.length === 0) {
                this.debugLog(`⚠️ ${detectionResults.error || 'No objects detected'}`, 'warning');
                return { success: false, error: detectionResults.error || 'No objects detected' };
            }

            // Step 5: Create puzzle data structure with validated image URL
            const puzzleData = {
                puzzleId: puzzleId,
                type: 'waldopuzzle',
                theme: sceneTemplate.theme,
                category: sceneTemplate.category,
                description: `Find ${detectionResults.objects.length} hidden objects in this ${sceneTemplate.theme.toLowerCase()}`,
                imageUrl: imageUrl,  // ✅ Supabase public URL
                imageWidth: 1024,
                imageHeight: 1024,
                imagePrompt: imagePrompt,
                gridConfig: this.gridConfig,
                hiddenObjects: detectionResults.objects,
                totalObjects: detectionResults.objects.length,
                difficulty: difficulty,
                timeLimit: this.getTimeLimitForDifficulty(difficulty, detectionResults.objects.length),
                gameSettings: {
                    allowHints: true,
                    showProgress: true,
                    highlightFound: true,
                    gridInteraction: true
                },
                imageStatus: 'uploaded', // ✅ Indicates successful Supabase upload
                imageStorageProvider: 'supabase',
                imageGeneratedAt: new Date().toISOString(),
                imageUploadedAt: new Date().toISOString(), // ✅ Track upload time
                objectsDetectedAt: new Date().toISOString(),
                generationMethod: 'dalle_with_percentage_detection', // ✅ Updated method name
                sceneComplexity: sceneTemplate.complexity
            };

            // ✅ Validate that all required image data is present
            if (!puzzleData.imageUrl || !puzzleData.imageUrl.startsWith('http')) {
                throw new Error(`Invalid image URL in puzzle data: ${puzzleData.imageUrl}`);
            }

            this.debugLog(`✅ Generated Waldo puzzle with ${detectionResults.objects.length} objects`);
            this.debugLog(`🖼️ Image URL: ${imageUrl}`);
            this.debugLog(`📁 Storage location: waldo-puzzles/${puzzleId}.jpg`);
            detectionResults.objects.forEach((obj, index) => {
                this.debugLog(`   ${index + 1}. ${obj.name} at (${obj.xPercent}%, ${obj.yPercent}%) → Cell ${obj.gridCell} (confidence: ${obj.confidence})`);
            });

            return { success: true, data: puzzleData };

        } catch (error) {
            this.debugLog(`❌ Error generating Waldo puzzle: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    // 🎨 ENHANCED: Better scene prompts with more detailed instructions
    createOptimizedScenePrompt(sceneTemplate, hiddenObjects, difficulty) {
        const complexityInstructions = {
            easy: "moderately busy scene with clear visual separation between elements, easy to navigate visually",
            medium: "busy scene with good detail and multiple activities happening, moderate visual complexity", 
            hard: "very busy and complex scene with many overlapping elements, high visual density, and challenging navigation"
        };

        const objectInstructions = hiddenObjects.map((obj, index) => 
            `- Include ${obj.description} (bright ${obj.color} color) naturally placed in the scene where it fits but requires searching to find`
        ).join('\n');

        // Add category-specific enhancements
        const categoryEnhancements = {
            outdoor: "Include natural lighting, outdoor atmosphere, and environmental elements",
            indoor: "Include indoor lighting, architectural details, and interior atmosphere", 
            event: "Include festive atmosphere, people engaged in celebration, and event-specific decorations",
            transport: "Include vehicles, travel-related elements, and transit infrastructure",
            seasonal: "Include season-appropriate weather, decorations, and atmospheric elements",
            cultural: "Include cultural elements, traditional items, and diverse representation",
            nature: "Include natural elements, wildlife, and organic environmental features"
        };

        return `Create a detailed, vibrant "Where's Waldo" style illustration: ${sceneTemplate.description}

SCENE COMPLEXITY: ${complexityInstructions[difficulty] || complexityInstructions.medium}
CATEGORY FOCUS: ${categoryEnhancements[sceneTemplate.category] || ''}

HIDDEN OBJECTS TO INCLUDE (CRITICAL - ALL MUST BE PRESENT):
${objectInstructions}

VISUAL REQUIREMENTS:
- Each hidden object must be clearly identifiable with its specified color
- Objects should be small enough to require careful searching but large enough to be found
- Use bright, distinct colors for hidden objects exactly as specified (red, blue, green, etc.)
- Create visual complexity through overlapping elements and busy activities
- Ensure excellent contrast between objects and backgrounds
- Include many people, activities, and scene elements to create search challenge
- Make sure every object maintains its distinctive color and shape

SPATIAL ORGANIZATION:
- Distribute hidden objects across different areas of the image (top, middle, bottom, left, right)
- Mix easy-to-spot and challenging placements based on difficulty level
- Avoid clustering all objects in one area
- Create depth with foreground, middle ground, and background elements
- Ensure objects are not completely obscured but naturally integrated

QUALITY STANDARDS:
- High detail and clarity throughout the image
- Excellent color saturation and contrast
- Professional "Where's Waldo" illustration quality
- Engaging and fun visual experience
- All objects clearly distinguishable when found

CRITICAL SUCCESS FACTORS:
1. ALL ${hiddenObjects.length} specified objects MUST be included
2. Each object MUST maintain its specified color
3. Objects must be findable but challenging
4. Scene must match the ${sceneTemplate.theme} theme perfectly
5. Overall complexity should match ${difficulty} difficulty level

Style: Professional "Where's Waldo" style illustration with excellent clarity, bright colors, and engaging visual complexity perfect for hidden object searching.`;
    }

    // ✅ Improved object detection using percentage positions
    async detectObjectLocationsWithPercentages(imageBase64, hiddenObjects) {
        try {
            const objectNames = hiddenObjects.map(obj => `${obj.name} (${obj.color})`).join(', ');
            
            const detectionPrompt = `ANALYZE this "Where's Waldo" style image to find these EXACT objects: ${objectNames}

🎯 DETECTION TASK:
Look carefully at this detailed illustration and locate each of these specific objects:
${hiddenObjects.map((obj, i) => `${i+1}. ${obj.name} - ${obj.description} (must be ${obj.color} colored)`).join('\n')}

🔍 SEARCH STRATEGY:
- Scan the entire image systematically (left to right, top to bottom)
- Look for the exact color specified for each object
- Objects are small but clearly visible when found
- They blend naturally into the scene but maintain their distinctive appearance

📍 POSITION REPORTING:
For EACH object you find, report its position as a percentage of the image:
- X_PERCENT: horizontal position from left edge (0% = far left, 50% = center, 100% = far right)
- Y_PERCENT: vertical position from top edge (0% = top, 50% = center, 100% = bottom)

🎯 For EACH object you find, respond EXACTLY like this:
OBJECT: [exact object name]
FOUND: yes
X_PERCENT: [number 0-100]
Y_PERCENT: [number 0-100]
CONFIDENCE: [high/medium/low]
COLOR_VERIFIED: [yes/no - confirm the object has the correct color]
DESCRIPTION: [brief description of what you see and where it's located]

❌ If you cannot find an object, respond:
OBJECT: [exact object name]
FOUND: no
DESCRIPTION: not visible in the image or color doesn't match

🔍 QUALITY CHECKS:
- Verify each object has the correct color before marking as found
- Be precise with percentage positions
- Only mark objects as found if you can clearly see them
- Take your time to examine the entire image carefully

Please analyze the image now and report your findings for each object.`;

            this.debugLog('👁️ Sending image to Vision API for enhanced detection...');

            const response = await axios.post('https://api.anthropic.com/v1/messages', {
                model: "claude-sonnet-4-6",
                max_tokens: 2500,
                messages: [{
                    role: "user",
                    content: [
                        {
                            type: "image",
                            source: {
                                type: "base64",
                                media_type: "image/jpeg",
                                data: imageBase64
                            }
                        },
                        {
                            type: "text",
                            text: detectionPrompt
                        }
                    ]
                }]
            }, {
                headers: {
                    'x-api-key': this.anthropicKey,
                    'Content-Type': 'application/json',
                    'anthropic-version': '2023-06-01'
                },
                timeout: 90000
            });

            const content = response.data.content?.[0]?.type === 'text' ? response.data.content[0].text : '';
            this.debugLog(`👁️ Vision response preview: ${content.substring(0, 200)}...`);

            // Parse the response to extract object locations
            const detectedObjects = this.parsePercentageDetections(content, hiddenObjects);

            if (detectedObjects.length === 0) {
                return { success: false, error: 'No objects detected in the image' };
            }

            this.debugLog(`✅ Detected ${detectedObjects.length} objects with percentage positions`);
            return { success: true, objects: detectedObjects };

        } catch (error) {
            this.debugLog(`❌ Error detecting objects: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    // ✅ Enhanced parsing with better validation
    parsePercentageDetections(visionResponse, hiddenObjects) {
        const detectedObjects = [];
        
        // Split response into sections for each object
        const sections = visionResponse.split(/OBJECT:/);
        
        sections.forEach((section, index) => {
            if (index === 0) return; // Skip the first empty section
            
            try {
                // Extract object data
                const nameMatch = section.match(/^([^\n]+)/);
                const foundMatch = section.match(/FOUND:\s*(yes|no)/i);
                const xPercentMatch = section.match(/X_PERCENT:\s*(\d+(?:\.\d+)?)/i);
                const yPercentMatch = section.match(/Y_PERCENT:\s*(\d+(?:\.\d+)?)/i);
                const confidenceMatch = section.match(/CONFIDENCE:\s*(high|medium|low)/i);
                const colorVerifiedMatch = section.match(/COLOR_VERIFIED:\s*(yes|no)/i);
                const descriptionMatch = section.match(/DESCRIPTION:\s*([^\n]+)/i);
                
                const objectName = nameMatch ? nameMatch[1].trim() : '';
                const isFound = foundMatch?.[1]?.toLowerCase() === 'yes';
                const colorVerified = colorVerifiedMatch?.[1]?.toLowerCase() === 'yes';
                const xPercent = xPercentMatch ? parseFloat(xPercentMatch[1]) : null;
                const yPercent = yPercentMatch ? parseFloat(yPercentMatch[1]) : null;
                
                // Check if this object was in our search list
                const originalObject = hiddenObjects.find(obj => 
                    objectName.toLowerCase().includes(obj.name.toLowerCase()) ||
                    obj.name.toLowerCase().includes(objectName.toLowerCase().replace(/\([^)]*\)/, '').trim())
                );
                
                if (!originalObject) {
                    this.debugLog(`⚠️ Unrecognized object in response: "${objectName}"`, 'warning');
                    return;
                }
                
                // Enhanced validation: require found, correct color, and valid positions
                if (isFound && colorVerified && xPercent !== null && yPercent !== null && 
                    xPercent >= 0 && xPercent <= 100 && yPercent >= 0 && yPercent <= 100) {
                    
                    // ✅ Convert percentage position to grid cell
                    const gridInfo = this.percentageToGridCell(xPercent, yPercent);
                    
                    detectedObjects.push({
                        id: detectedObjects.length + 1,
                        name: originalObject.name,
                        description: originalObject.description,
                        color: originalObject.color,
                        xPercent: xPercent,
                        yPercent: yPercent,
                        gridCell: gridInfo.cellNumber,
                        gridRow: gridInfo.row,
                        gridCol: gridInfo.col,
                        confidence: confidenceMatch?.[1] || 'medium',
                        location: descriptionMatch?.[1]?.trim() || '',
                        hint: originalObject.searchHint,
                        difficulty: originalObject.difficulty,
                        found: false, // Will be updated during gameplay
                        foundAt: null,
                        detectionMethod: 'percentage_conversion_enhanced', // ✅ Track enhanced detection method
                        colorVerified: true
                    });
                    
                    this.debugLog(`✅ Successfully detected ${originalObject.name} (${originalObject.color}): (${xPercent}%, ${yPercent}%) → Cell ${gridInfo.cellNumber}`);
                } else {
                    this.debugLog(`❌ Invalid detection for ${originalObject.name}: Found=${isFound}, ColorOK=${colorVerified}, X=${xPercent}%, Y=${yPercent}%`, 'warning');
                }
            } catch (parseError) {
                this.debugLog(`⚠️ Error parsing object section: ${parseError.message}`, 'warning');
            }
        });
        
        return detectedObjects;
    }

    async uploadImage(puzzleId, imageBuffer) {
        try {
            this.debugLog('☁️ Importing Supabase client...');
            const { supabase } = await import('../config/database.js');
            
            // Create organized path for Waldo puzzles
            const fileName = `waldo-puzzles/${puzzleId}.jpg`;
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

    getTimeLimitForDifficulty(difficulty, objectCount) {
        const baseTimePerObject = {
            easy: 45000,    // 45 seconds per object
            medium: 60000,  // 60 seconds per object  
            hard: 75000     // 75 seconds per object
        };

        const timePerObject = baseTimePerObject[difficulty] || 60000;
        return timePerObject * objectCount;
    }

    formatForPuzzleSystem(puzzleData) {
        const questionData = {
            puzzleId: puzzleData.puzzleId,
            theme: puzzleData.theme,
            category: puzzleData.category,
            description: puzzleData.description,
            imageUrl: puzzleData.imageUrl,
            imageWidth: puzzleData.imageWidth,
            imageHeight: puzzleData.imageHeight,
            gridConfig: puzzleData.gridConfig,
            hiddenObjects: puzzleData.hiddenObjects,
            totalObjects: puzzleData.totalObjects,
            timeLimit: puzzleData.timeLimit,
            gameSettings: puzzleData.gameSettings,
            imageStatus: puzzleData.imageStatus,
            generationMethod: puzzleData.generationMethod
        };

        const answerData = {
            hiddenObjects: puzzleData.hiddenObjects,
            totalObjects: puzzleData.totalObjects,
            maxScore: puzzleData.totalObjects * 10,
            passingScore: Math.ceil(puzzleData.totalObjects * 0.6) * 10, // 60% to pass
            gridConfig: puzzleData.gridConfig
        };

        return {
            question: JSON.stringify(questionData),
            answer: JSON.stringify(answerData),
            hint: `Find ${puzzleData.totalObjects} hidden objects in this ${puzzleData.theme.toLowerCase()}`,
            difficulty: puzzleData.difficulty,
            metadata: {
                theme: puzzleData.theme,
                category: puzzleData.category,
                objectCount: puzzleData.totalObjects,
                imageGenerated: true,
                generatedAt: puzzleData.imageGeneratedAt,
                puzzleType: 'waldopuzzle',
                requiresImageGeneration: true,
                hasGridInteraction: true,
                imageUrl: puzzleData.imageUrl,
                generationMethod: puzzleData.generationMethod,
                sceneComplexity: puzzleData.sceneComplexity,
                timeLimit: puzzleData.timeLimit,
                gridRows: puzzleData.gridConfig.rows,
                gridCols: puzzleData.gridConfig.cols,
                detectionMethod: 'percentage_based_enhanced',
                objectColors: puzzleData.hiddenObjects.map(obj => obj.color),
                sceneDiversity: 'high' // ✅ Indicates expanded scene variety
            }
        };
    }
}

// Export for use in puzzle generation system
export const waldoPuzzleGenerator = new WaldoPuzzleGenerator();
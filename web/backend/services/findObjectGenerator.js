// services/findObjectGenerator.js - Simplified Natural Discovery Approach

import axios from 'axios';
import { generateImage } from '../utils/aiClient.js';

export class FindObjectGenerator {
    constructor() {
        this.debugMode = false;
        this.anthropicKey = process.env.ANTHROPIC_API_KEY;
        
        // Grid configuration for mobile-friendly object detection
        this.gridConfig = {
            rows: 6,      // 6 rows
            cols: 8,      // 8 columns
            totalCells: 48 // 6 * 8 = 48 grid cells
        };
        
        // Simplified scene templates - focused on cleaner, less cluttered scenes
        this.sceneTemplates = [
            {
                theme: "Tidy Kitchen Counter",
                description: "A clean, organized kitchen counter with a few cooking items neatly arranged on a wooden surface",
                expectedObjects: ["spoon", "knife", "bowl", "cup", "bottle", "cutting board", "timer", "salt shaker"],
                simplicity: "clean countertop with 6-8 distinct items, bright lighting, minimal background clutter"
            },
            {
                theme: "Simple Living Room",
                description: "A minimalist living room with a few furniture pieces and decorative items on a coffee table and side table",
                expectedObjects: ["remote", "book", "mug", "plant", "lamp", "pillow", "picture frame", "candle"],
                simplicity: "modern furniture with 6-8 items on surfaces, clean walls, uncluttered space"
            },
            {
                theme: "Organized Desk Setup",
                description: "A neat office desk with essential work items arranged on a clean desktop surface",
                expectedObjects: ["pen", "notebook", "mug", "calculator", "stapler", "phone", "lamp", "glasses"],
                simplicity: "clear desk surface with 6-8 office items, minimal papers, clean background"
            },
            {
                theme: "Craft Table",
                description: "A simple craft workspace with art supplies neatly laid out on a table",
                expectedObjects: ["scissors", "pencil", "ruler", "brush", "eraser", "glue", "marker", "paper"],
                simplicity: "organized craft supplies on clean table, good spacing between items"
            },
            {
                theme: "Garden Potting Bench",
                description: "A simple outdoor potting bench with gardening tools and a few plants arranged on wooden shelves",
                expectedObjects: ["watering can", "shovel", "pot", "gloves", "seeds", "rake", "bucket", "scissors"],
                simplicity: "wooden bench with 6-8 garden items, natural lighting, minimal background"
            },
            {
                theme: "Breakfast Table",
                description: "A simple breakfast setting on a wooden table with morning items neatly arranged",
                expectedObjects: ["plate", "fork", "cup", "spoon", "napkin", "juice glass", "butter knife", "bowl"],
                simplicity: "clean table setting with 6-8 breakfast items, natural morning light"
            },
            {
                theme: "Study Corner",
                description: "A quiet study space with books and school supplies organized on a simple desk",
                expectedObjects: ["book", "pencil", "eraser", "ruler", "calculator", "notebook", "highlighter", "paperclip"],
                simplicity: "neat desk with 6-8 study items, minimal background distractions"
            },
            {
                theme: "Picnic Setup",
                description: "A simple outdoor picnic blanket with a few items spread out for a meal",
                expectedObjects: ["plate", "cup", "fork", "napkin", "bottle", "apple", "sandwich", "basket"],
                simplicity: "checkered blanket with 6-8 picnic items, outdoor setting, clear spacing"
            },
            // === NEW KITCHEN VARIANTS ===
            {
                theme: "Baker's Counter",
                description: "A clean baking station with essential baking tools and ingredients neatly arranged on a marble countertop",
                expectedObjects: ["whisk", "measuring cup", "rolling pin", "mixing bowl", "spatula", "flour bag", "timer", "oven mitt"],
                simplicity: "organized baking workspace with 6-8 baking items, bright kitchen lighting, minimal clutter",
                category: "kitchen"
            },
            {
                theme: "Coffee Station",
                description: "A simple coffee preparation area with brewing equipment and accessories on a clean counter",
                expectedObjects: ["coffee mug", "coffee beans", "grinder", "filter", "sugar bowl", "milk pitcher", "spoon", "coffee pot"],
                simplicity: "dedicated coffee area with 6-8 coffee-related items, warm lighting, organized layout",
                category: "kitchen"
            },
            {
                theme: "Tea Time Setup",
                description: "An elegant tea service arrangement on a simple wooden tray with afternoon tea essentials",
                expectedObjects: ["teapot", "teacup", "saucer", "sugar bowl", "milk jug", "tea strainer", "spoon", "cookie plate"],
                simplicity: "formal tea setting with 6-8 tea service items, soft lighting, refined arrangement",
                category: "dining"
            },

            // === NEW BEDROOM/PERSONAL CARE ===
            {
                theme: "Bedside Table",
                description: "A neat nightstand with evening essentials arranged on a simple wooden surface",
                expectedObjects: ["alarm clock", "book", "water glass", "lamp", "phone", "glasses", "tissue box", "candle"],
                simplicity: "organized bedside setup with 6-8 nighttime items, soft bedroom lighting",
                category: "bedroom"
            },
            {
                theme: "Bathroom Counter",
                description: "A clean bathroom vanity with personal care items neatly organized on a marble countertop",
                expectedObjects: ["toothbrush", "soap dispenser", "towel", "mirror", "comb", "lotion bottle", "razor", "cup"],
                simplicity: "tidy bathroom setup with 6-8 grooming items, bright vanity lighting, minimal clutter",
                category: "bathroom"
            },
            {
                theme: "Dressing Table",
                description: "A simple vanity table with beauty and grooming essentials arranged on a clean surface",
                expectedObjects: ["hairbrush", "perfume bottle", "lipstick", "mirror", "jewelry box", "cotton pads", "nail file", "powder compact"],
                simplicity: "organized beauty station with 6-8 cosmetic items, good lighting, elegant arrangement",
                category: "bedroom"
            },

            // === NEW OUTDOOR/SEASONAL ===
            {
                theme: "Beach Day Setup",
                description: "A simple beach scene with vacation essentials laid out on a colorful beach towel",
                expectedObjects: ["sunglasses", "sunscreen bottle", "beach ball", "water bottle", "sandals", "book", "hat", "shell"],
                simplicity: "beach towel with 6-8 summer items, bright outdoor lighting, sandy background",
                category: "outdoor"
            },
            {
                theme: "Camping Gear",
                description: "Essential camping equipment neatly arranged on a wooden picnic table at a campsite",
                expectedObjects: ["flashlight", "compass", "water bottle", "rope", "knife", "map", "matches", "backpack"],
                simplicity: "outdoor table with 6-8 camping items, natural lighting, forest background",
                category: "outdoor"
            },
            {
                theme: "Winter Cabin",
                description: "A cozy cabin interior with winter essentials arranged on a rustic wooden table near a fireplace",
                expectedObjects: ["mug", "blanket", "candle", "book", "mittens", "pine cone", "hot chocolate", "marshmallow"],
                simplicity: "warm cabin setting with 6-8 winter items, fireplace lighting, cozy atmosphere",
                category: "seasonal"
            },

            // === NEW HOBBY/ACTIVITY THEMES ===
            {
                theme: "Artist's Easel",
                description: "A clean art studio setup with painting supplies organized around an easel and palette",
                expectedObjects: ["paintbrush", "palette", "paint tube", "canvas", "water jar", "cloth", "pencil", "eraser"],
                simplicity: "art studio with 6-8 painting supplies, good natural lighting, organized workspace",
                category: "craft"
            },
            {
                theme: "Music Practice",
                description: "A simple music room with instruments and sheet music neatly arranged on stands and tables",
                expectedObjects: ["sheet music", "metronome", "guitar pick", "tuner", "pencil", "music stand", "capo", "violin bow"],
                simplicity: "music room with 6-8 musical items, acoustic panels, organized practice space",
                category: "music"
            },
            {
                theme: "Reading Nook",
                description: "A comfortable reading corner with books and cozy accessories arranged on side tables and shelves",
                expectedObjects: ["book", "bookmark", "reading glasses", "tea cup", "blanket", "cushion", "lamp", "notepad"],
                simplicity: "cozy reading area with 6-8 literary items, warm lighting, comfortable seating",
                category: "living"
            },

            // === NEW WORK/PROFESSIONAL ===
            {
                theme: "Doctor's Desk",
                description: "A medical professional's clean desk with essential medical tools and references neatly organized",
                expectedObjects: ["stethoscope", "clipboard", "pen", "medical chart", "prescription pad", "calculator", "phone", "coffee mug"],
                simplicity: "professional medical desk with 6-8 clinical items, bright office lighting, organized layout",
                category: "professional"
            },
            {
                theme: "Teacher's Classroom",
                description: "A neat classroom desk with educational supplies and teaching materials arranged for the day",
                expectedObjects: ["apple", "whiteboard marker", "grade book", "ruler", "stapler", "pencil holder", "bell", "eraser"],
                simplicity: "teacher's desk with 6-8 educational items, classroom lighting, academic atmosphere",
                category: "education"
            },
            {
                theme: "Chef's Station",
                description: "A professional kitchen prep station with cooking tools and ingredients organized for meal preparation",
                expectedObjects: ["chef knife", "cutting board", "measuring spoons", "tongs", "salt grinder", "oil bottle", "towel", "timer"],
                simplicity: "commercial kitchen setup with 6-8 professional cooking items, bright kitchen lighting",
                category: "professional"
            },

            // === NEW CHILDREN/FAMILY ===
            {
                theme: "Toy Box Corner",
                description: "A clean children's play area with favorite toys neatly arranged on a colorful rug",
                expectedObjects: ["teddy bear", "toy car", "building blocks", "ball", "doll", "crayon", "puzzle piece", "book"],
                simplicity: "organized play area with 6-8 children's toys, bright playroom lighting, colorful setting",
                category: "children"
            },
            {
                theme: "Baby's Nursery",
                description: "A peaceful nursery with baby care essentials organized on a changing table and nearby shelves",
                expectedObjects: ["baby bottle", "diaper", "rattle", "pacifier", "baby lotion", "soft toy", "bib", "blanket"],
                simplicity: "serene nursery with 6-8 baby items, soft lighting, pastel colors",
                category: "children"
            },
            {
                theme: "Family Game Night",
                description: "A living room table set up for family game time with board games and snacks neatly arranged",
                expectedObjects: ["dice", "game piece", "playing cards", "score pad", "pencil", "snack bowl", "drink cup", "timer"],
                simplicity: "game table with 6-8 gaming items, warm family room lighting, organized for play",
                category: "family"
            },

            // === NEW SEASONAL/HOLIDAY ===
            {
                theme: "Spring Garden",
                description: "A fresh spring potting area with gardening supplies and young plants arranged on a garden bench",
                expectedObjects: ["seed packet", "small shovel", "watering can", "plant pot", "gardening gloves", "soil bag", "plant markers", "pruning shears"],
                simplicity: "spring garden setup with 6-8 planting items, natural outdoor lighting, fresh green background",
                category: "seasonal"
            },
            {
                theme: "Summer Patio",
                description: "A clean outdoor patio table with summer entertaining essentials arranged for a gathering",
                expectedObjects: ["citronella candle", "drink pitcher", "napkins", "ice bucket", "serving spoon", "patio cushion", "fan", "fruit bowl"],
                simplicity: "summer patio with 6-8 entertaining items, bright daylight, outdoor furniture setting",
                category: "seasonal"
            },
            {
                theme: "Fall Harvest",
                description: "A rustic autumn display with seasonal items arranged on a wooden farmhouse table",
                expectedObjects: ["pumpkin", "apple", "cinnamon stick", "acorn", "corn", "wheat bundle", "gourd", "autumn leaf"],
                simplicity: "harvest table with 6-8 fall items, warm autumn lighting, natural wood setting",
                category: "seasonal"
            },

            // === NEW SPECIALIZED ACTIVITIES ===
            {
                theme: "Fitness Corner",
                description: "A home gym area with exercise equipment and wellness items organized on a mat and nearby shelf",
                expectedObjects: ["water bottle", "towel", "dumbbell", "yoga mat", "resistance band", "timer", "protein shaker", "fitness tracker"],
                simplicity: "workout space with 6-8 fitness items, bright lighting, motivational setting",
                category: "fitness"
            },
            {
                theme: "Meditation Space",
                description: "A peaceful meditation corner with mindfulness accessories arranged on a simple cushion and low table",
                expectedObjects: ["meditation cushion", "candle", "incense", "singing bowl", "journal", "pen", "crystals", "prayer beads"],
                simplicity: "serene meditation area with 6-8 mindfulness items, soft lighting, minimalist design",
                category: "wellness"
            },
            {
                theme: "Mechanic's Workbench",
                description: "A clean garage workbench with essential tools and automotive supplies neatly organized",
                expectedObjects: ["wrench", "screwdriver", "oil can", "rag", "bolt", "measuring tape", "flashlight", "work gloves"],
                simplicity: "organized workbench with 6-8 automotive tools, bright workshop lighting, industrial setting",
                category: "professional"
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
            console.log(`${emoji} [${timestamp}] FIND_OBJECT: ${message}`);
        }
    }

    // Convert pixel coordinates to grid index
    coordinatesToGridIndex(x, y) {
        const col = Math.floor((x / 100) * this.gridConfig.cols);
        const row = Math.floor((y / 100) * this.gridConfig.rows);
        
        // Ensure we stay within grid bounds
        const boundedCol = Math.max(0, Math.min(col, this.gridConfig.cols - 1));
        const boundedRow = Math.max(0, Math.min(row, this.gridConfig.rows - 1));
        
        return boundedRow * this.gridConfig.cols + boundedCol;
    }

    // Convert grid index back to center coordinates of that cell
    gridIndexToCoordinates(gridIndex) {
        const row = Math.floor(gridIndex / this.gridConfig.cols);
        const col = gridIndex % this.gridConfig.cols;
        
        const cellWidth = 100 / this.gridConfig.cols;
        const cellHeight = 100 / this.gridConfig.rows;
        
        return {
            x: (col * cellWidth) + (cellWidth / 2),
            y: (row * cellHeight) + (cellHeight / 2),
            row,
            col
        };
    }

    // Generate visual grid overlay description for AI prompts
    generateGridDescription() {
        return `The image should be mentally divided into a ${this.gridConfig.cols}x${this.gridConfig.rows} grid (${this.gridConfig.totalCells} cells total). 
Grid cells are numbered from 0 to ${this.gridConfig.totalCells - 1}, starting from top-left (0) and going left-to-right, top-to-bottom.
Each cell is ${(100/this.gridConfig.cols).toFixed(1)}% wide and ${(100/this.gridConfig.rows).toFixed(1)}% tall.`;
    }

    selectRandomTemplate() {
        return this.sceneTemplates[Math.floor(Math.random() * this.sceneTemplates.length)];
    }

    async generateFindObjectPuzzle(difficulty = 'medium', maxRetries = 2) {
        this.debugLog(`🚀 Starting simplified find object puzzle generation (${difficulty})`);

        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            this.debugLog(`🔄 Attempt ${attempt}/${maxRetries}`);
            
            try {
                if (!this.anthropicKey) {
                    throw new Error('ANTHROPIC_API_KEY environment variable not set (needed for vision analysis)');
                }

                // Select random scene template
                const template = this.selectRandomTemplate();
                this.debugLog(`🎨 Selected scene: ${template.theme}`);

                // Generate the puzzle with simplified discovery
                const puzzleResult = await this.generateSimplifiedPuzzleData(template, difficulty);
                
                if (puzzleResult.success) {
                    // Success! Format and return
                    const formattedData = this.formatForPuzzleSystem(puzzleResult.data);
                    
                    this.debugLog(`✅ Successfully generated simplified puzzle on attempt ${attempt}`, 'success');
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
                            message: puzzleResult.error || 'Failed to generate puzzle data',
                            attempts: attempt,
                            lastError: puzzleResult
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
                        message: `Find object generation error after ${maxRetries} attempts: ${error.message}`,
                        attempts: attempt
                    };
                }
                
                // Wait before retry
                await new Promise(resolve => setTimeout(resolve, 2000));
            }
        }
    }

    async generateSimplifiedPuzzleData(template, difficulty) {
        this.debugLog('🎨 Generating simplified scene and discovering objects...');
        
        const puzzleId = `find_object_${Date.now()}_${performance.now().toString().replace('.', '')}_${Math.random().toString(36).substr(2, 9)}`;        
        // Get expected objects for this template
        const expectedObjects = template.expectedObjects.slice(0, 8); // Limit to 8 objects max
        const objectDescriptions = expectedObjects.join(', ');
        
        // SIMPLIFIED image prompt - much cleaner and less cluttered
        const imagePrompt = `Create a SIMPLE, CLEAN illustration of: ${template.description}

CRITICAL SIMPLICITY REQUIREMENTS:
- MINIMAL CLUTTER: Only include 6-8 distinct objects total
- CLEAN BACKGROUND: Simple, uncluttered background (solid color or very simple pattern)
- CLEAR SPACING: Objects should have clear space around them, not overlapping
- BRIGHT LIGHTING: Excellent, even lighting with no shadows that obscure objects
- CENTRAL PLACEMENT: Keep all objects in the center 80% of the image

REQUIRED OBJECTS (include exactly these, one of each):
${objectDescriptions}

OBJECT PLACEMENT RULES:
- Each object should be clearly visible and well-separated from others
- Objects should be medium to large size (easily recognizable)
- Use classic, traditional styles for each object (no abstract or stylized versions)
- Place objects logically within the scene but with clear spacing
- Ensure strong contrast between objects and background
- NO tiny details or decorative elements that could be confused for target objects

SCENE STYLE:
- Clean, minimalist ${template.theme.toLowerCase()}
- ${template.simplicity}
- Cartoon/illustration style with clear, bold lines
- Bright, vibrant colors with excellent contrast
- NO busy patterns, excessive decorations, or background clutter
- Focus on CLARITY and SIMPLICITY over complexity

Style: Clean, bright, minimalist cartoon illustration. Think "children's book illustration" - simple, clear, and easy to understand. Each object should stand out clearly against the background.`;

        try {
            // Generate simplified scene image. Was DALL-E (OPENAI_API_KEY),
            // which was never actually configured on this Fly app — every
            // generation failed immediately, which was the root cause of
            // Find Object's blank-screen bug (2026-08-04). Switched to the
            // same generateImage() (Pollinations, no API key needed)
            // already used by other puzzle generators in this codebase
            // (e.g. generateSplitScreenImage for find-differences).
            this.debugLog('🎨 Generating simplified scene...');
            const imageBuffer = await generateImage(imagePrompt, {
                size: "1024x1024",
                puzzleType: 'find_object'
            });
            this.debugLog(`📦 Generated simplified image: ${imageBuffer.length} bytes`);

            // Upload image to storage
            this.debugLog('☁️ Uploading image...');
            const imageUrl = await this.uploadImage(puzzleId, imageBuffer);

            // Discover objects in the simplified scene
            this.debugLog('🔍 Discovering objects in simplified scene...');
            const discoveredObjects = await this.discoverObjectsInSimplifiedScene(imageBuffer, template, expectedObjects);

            if (!discoveredObjects.success || discoveredObjects.objects.length === 0) {
                this.debugLog(`⚠️ ${discoveredObjects.error || 'No objects discovered in simplified scene'}`, 'warning');
                return { success: false, error: discoveredObjects.error || 'No objects discovered in simplified scene' };
            }

            // Select objects for puzzle based on difficulty
            const selectedObjects = this.selectPuzzleObjectsSimplified(discoveredObjects.objects, difficulty);
            
            if (selectedObjects.length === 0) {
                this.debugLog(`⚠️ No suitable objects selected for puzzle`, 'warning');
                return { success: false, error: 'No suitable objects found for puzzle' };
            }

            // Create the puzzle data structure
            const puzzleData = {
                puzzleId: puzzleId,
                type: 'find_object',
                theme: template.theme,
                description: `Find the hidden objects in this ${template.theme.toLowerCase()}`,
                imageUrl: imageUrl,
                imageWidth: 1024,
                imageHeight: 512,
                instructions: `Find ${selectedObjects.length === 1 ? 'the' : 'all'} ${selectedObjects.length} hidden object${selectedObjects.length === 1 ? '' : 's'}: ${selectedObjects.map(obj => obj.name).join(', ')}`,
                difficulty: this.calculateOverallDifficulty(selectedObjects),
                timeLimit: this.getTimeLimitForDifficulty(difficulty),
                totalObjects: selectedObjects.length,
                targetObject: selectedObjects[0],
                objectsToFind: selectedObjects,
                allFoundInstances: discoveredObjects.objects,
                gridConfig: this.gridConfig,
                gameSettings: {
                    clickTolerance: 20.0, // More forgiving for simplified scenes
                    maxWrongClicks: 10,
                    hintSystem: true,
                    scoringSystem: "per_object",
                    gridBasedValidation: true
                },
                imageStatus: 'generated',
                imageGenerated: true,
                imageGeneratedAt: new Date().toISOString(),
                objectsAnalyzedAt: new Date().toISOString(),
                discoveryMethod: 'simplified_scene_analysis',
                sceneAnalysis: discoveredObjects.sceneAnalysis,
                simplificationApplied: true
            };

            this.debugLog(`✅ Generated simplified puzzle with ${selectedObjects.length} objects (from ${discoveredObjects.objects.length} discovered)`);
            selectedObjects.forEach((obj, index) => {
                this.debugLog(`   ${index + 1}. ${obj.name} at grid cell ${obj.gridIndex} (${obj.x}%, ${obj.y}%) - confidence: ${obj.confidence}`);
            });

            return { success: true, data: puzzleData };

        } catch (error) {
            if (error.code === 'ECONNABORTED') {
                return { success: false, error: 'Request timeout - try again' };
            }
            if (error.response?.status === 502) {
                return { success: false, error: 'AI servers temporarily unavailable - try again in a few minutes' };
            }
            this.debugLog(`❌ Error generating simplified puzzle: ${error.message}`, 'error');
            return { success: false, error: error.message };
        }
    }

    async discoverObjectsInSimplifiedScene(imageBuffer, template, expectedObjects) {
        const maxRetries = 3;
        let lastError;
     
        for (let attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    const delay = Math.pow(2, attempt) * 1000; // 1s, 2s, 4s
                    await new Promise(resolve => setTimeout(resolve, delay));
                    this.debugLog(`Retrying object discovery (attempt ${attempt + 1}/${maxRetries}) after ${delay}ms delay`);
                }
                
                const base64Image = imageBuffer.toString('base64');
     
                const response = await axios.post('https://api.anthropic.com/v1/messages', {
                    model: "claude-sonnet-4-6",
                    max_tokens: 2000,
                    messages: [
                        {
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
                                {
                                    type: "text",
                                    text: `Analyze this SIMPLIFIED ${template.theme.toLowerCase()} scene and locate the specific objects for a "find the hidden object" game.

     ${this.generateGridDescription()}

     SIMPLIFIED SCENE ANALYSIS:
     This image was designed to be SIMPLE and CLEAN with minimal clutter. You should expect to find:
     EXPECTED OBJECTS: ${expectedObjects.join(', ')}

     DISCOVERY RULES FOR SIMPLIFIED SCENES:
     Look for these specific objects that should be clearly visible:
     ${expectedObjects.map(obj => `   - ${obj} (should be clearly recognizable and well-spaced)`).join('\n')}

     SIMPLIFIED DETECTION CRITERIA:
     - Objects should be clearly visible and well-separated
     - Each object should be medium to large size
     - Objects should have clear contrast against the background
     - No overlapping or hidden objects in this simplified scene
     - Focus on the EXACT objects from the expected list

     POSITIONING REQUIREMENTS:
     - Objects should be in the central 80% of the image (avoid edges)
     - Each object should have clear space around it
     - Look for traditional/classic versions of each object type

     For each object you can clearly identify, provide:
     - Object name (matching expected objects list exactly)
     - X,Y coordinates as percentages
     - Grid cell index (0-47)
     - Confidence level (excellent/good/fair)
     - Size assessment (large/medium/small)
     - Visibility assessment (clear/partial/unclear)
     
     IMPORTANT: Only report objects you can clearly see and identify. In this simplified scene, objects should be obvious and well-placed.
     
     Return as JSON:
     {
     "objects": [
        {
        "name": "spoon",
        "x": 45.2,
        "y": 23.8,
        "gridIndex": 12,
        "confidence": "excellent",
        "size": "medium",
        "visibility": "clear",
        "description": "Metal spoon on counter",
        "matches_expected": true
        }
     ],
     "scene_analysis": "Clean, simplified scene with well-spaced objects",
     "total_objects_found": 6,
     "expected_objects_found": ["spoon", "bowl", "cup"],
     "missing_objects": ["knife", "bottle"],
     "scene_simplicity": "high"
     }

Respond with ONLY valid JSON.`
                                }
                            ]
                        }
                    ]
                }, {
                    headers: {
                        'x-api-key': this.anthropicKey,
                        'Content-Type': 'application/json',
                        'anthropic-version': '2023-06-01'
                    },
                    timeout: 180000,
                    validateStatus: function (status) {
                        return status < 500;
                    }
                });
     
                if (response.status === 502) {
                    throw new Error('API server temporarily unavailable (502)');
                }

                const content = response.data.content?.[0]?.type === 'text' ? response.data.content[0].text : '';
                this.debugLog(`📝 Simplified discovery response preview: ${content.substring(0, 200)}...`);
     
                // Parse JSON response
                let discovery;
                try {
                    const codeBlockMatch = content.match(/```json\s*([\s\S]*?)\s*```/);
                    if (codeBlockMatch) {
                        discovery = JSON.parse(codeBlockMatch[1]);
                    } else {
                        const jsonMatch = content.match(/\{[\s\S]*\}/);
                        if (jsonMatch) {
                            discovery = JSON.parse(jsonMatch[0]);
                        } else {
                            throw new Error('No JSON found in response');
                        }
                    }
                } catch (parseError) {
                    this.debugLog(`❌ JSON parsing failed: ${parseError.message}`, 'error');
                    return { 
                        success: false, 
                        error: `Failed to parse discovery analysis: ${parseError.message}`,
                        rawResponse: content
                    };
                }
     
                if (!discovery.objects || discovery.objects.length === 0) {
                    return { 
                        success: false, 
                        error: 'No objects discovered in simplified scene'
                    };
                }
     
                // Filter for clear, well-positioned objects
                const validObjects = discovery.objects.filter(obj => {
                    const hasGoodVisibility = obj.visibility === 'clear';
                    const hasGoodConfidence = obj.confidence === 'excellent' || obj.confidence === 'good';
                    const isInSafeZone = obj.x >= 10 && obj.x <= 90 && obj.y >= 15 && obj.y <= 85;
                    const matchesExpected = expectedObjects.includes(obj.name.toLowerCase());
                    
                    if (!hasGoodVisibility || !hasGoodConfidence || !isInSafeZone) {
                        this.debugLog(`⚠️ Filtering out ${obj.name}: visibility=${obj.visibility}, confidence=${obj.confidence}, safe=${isInSafeZone}`, 'warning');
                        return false;
                    }
                    
                    if (!matchesExpected) {
                        this.debugLog(`⚠️ Object ${obj.name} not in expected list: ${expectedObjects.join(', ')}`, 'warning');
                        return false;
                    }
                    
                    return true;
                });
     
                if (validObjects.length === 0) {
                    this.debugLog('⚠️ No valid objects found after filtering', 'warning');
                    return { success: false, error: 'No valid objects found in simplified scene' };
                }
     
                // Enhance discovered objects with puzzle-ready data
                const enhancedObjects = validObjects.map((obj, index) => {
                    // Validate and correct grid index if needed
                    const calculatedGridIndex = this.coordinatesToGridIndex(obj.x, obj.y);
                    if (Math.abs(calculatedGridIndex - obj.gridIndex) > 1) {
                        this.debugLog(`⚠️ Grid index mismatch for ${obj.name}: calculated ${calculatedGridIndex}, reported ${obj.gridIndex}`, 'warning');
                        obj.gridIndex = calculatedGridIndex;
                    }
     
                    // Assign difficulty and tolerance for simplified objects
                    const difficulty = this.assignSimplifiedObjectDifficulty(obj);
                    const tolerance = this.calculateSimplifiedObjectTolerance(obj);
     
                    return {
                        id: Math.random().toString(36).substr(2, 9),
                        name: obj.name,
                        x: obj.x,
                        y: obj.y,
                        gridIndex: obj.gridIndex,
                        gridRow: Math.floor(obj.gridIndex / this.gridConfig.cols),
                        gridCol: obj.gridIndex % this.gridConfig.cols,
                        confidence: obj.confidence,
                        size: obj.size,
                        visibility: obj.visibility,
                        description: obj.description || `A ${obj.name} in the scene`,
                        difficulty: difficulty,
                        tolerance: tolerance,
                        hint: `Look for the ${obj.name}`,
                        gridValidated: true,
                        discoveryMethod: 'simplified_scene_analysis',
                        matchesExpected: obj.matches_expected || true,
                        isSimplified: true
                    };
                });
     
                this.debugLog(`🔍 Discovered ${enhancedObjects.length} objects in simplified scene:`);
                enhancedObjects.forEach((obj, index) => {
                    this.debugLog(`   ${index + 1}. ${obj.name} at grid ${obj.gridIndex} - ${obj.confidence} confidence`);
                });
     
                // Log expected vs found
                const foundNames = enhancedObjects.map(obj => obj.name.toLowerCase());
                const expectedFound = expectedObjects.filter(exp => foundNames.includes(exp.toLowerCase()));
                const missing = expectedObjects.filter(exp => !foundNames.includes(exp.toLowerCase()));
                
                this.debugLog(`✅ Expected objects found: ${expectedFound.join(', ')}`);
                if (missing.length > 0) {
                    this.debugLog(`⚠️ Missing expected objects: ${missing.join(', ')}`, 'warning');
                }
     
                return { 
                    success: true, 
                    objects: enhancedObjects,
                    sceneAnalysis: discovery.scene_analysis,
                    totalFound: discovery.total_objects_found || enhancedObjects.length,
                    expectedObjectsFound: expectedFound,
                    missingObjects: missing,
                    sceneSimplicity: discovery.scene_simplicity || 'medium'
                };
     
            } catch (error) {
                lastError = error;
                
                if (error.response?.status === 502 || error.message.includes('502')) {
                    this.debugLog(`502 error on object discovery attempt ${attempt + 1}, retrying...`, 'warning');
                    continue;
                } else if (error.response?.status === 429) {
                    this.debugLog(`Rate limited on object discovery, waiting longer...`, 'warning');
                    console.log('Rate limit headers:', {
                        'x-ratelimit-limit-requests': error.response.headers['x-ratelimit-limit-requests'],
                        'x-ratelimit-remaining-requests': error.response.headers['x-ratelimit-remaining-requests'],
                        'x-ratelimit-reset-requests': error.response.headers['x-ratelimit-reset-requests'],
                        'retry-after': error.response.headers['retry-after']
                    });
                    await new Promise(resolve => setTimeout(resolve, 5000));
                    continue;
                } else if (error.code === 'ECONNABORTED') {
                    this.debugLog(`Timeout on object discovery attempt ${attempt + 1}, retrying...`, 'warning');
                    continue;
                } else {
                    this.debugLog(`Non-retryable error: ${error.message}`, 'error');
                    break;
                }
            }
        }
     
        return { 
            success: false, 
            error: `Object discovery failed after ${maxRetries} attempts. Last error: ${lastError?.message || 'Unknown error'}` 
        };
     }

    selectPuzzleObjectsSimplified(discoveredObjects, difficulty) {
        // For simplified scenes, we can be more straightforward
        let targetCount;
        switch (difficulty.toLowerCase()) {
            case 'easy': 
                targetCount = Math.min(3, discoveredObjects.length);
                break;
            case 'medium': 
                targetCount = Math.min(4, discoveredObjects.length);
                break;
            case 'hard': 
                targetCount = Math.min(5, discoveredObjects.length);
                break;
            default: 
                targetCount = Math.min(4, discoveredObjects.length);
        }

        // Ensure we have at least 2 objects for any puzzle
        if (targetCount < 2 && discoveredObjects.length >= 2) {
            targetCount = 2;
        }

        // Sort by quality for simplified scenes
        const sortedObjects = discoveredObjects.sort((a, b) => {
            // Prioritize excellent confidence
            if (a.confidence !== b.confidence) {
                const confScore = (conf) => conf === 'excellent' ? 3 : conf === 'good' ? 2 : 1;
                return confScore(b.confidence) - confScore(a.confidence);
            }
            
            // Then by size (medium is ideal)
            const sizeScore = (size) => size === 'medium' ? 3 : size === 'large' ? 2 : 1;
            if (a.size !== b.size) {
                return sizeScore(b.size) - sizeScore(a.size);
            }
            
            // Finally by central position
            const centerScore = (obj) => {
                const distanceFromCenter = Math.abs(obj.x - 50) + Math.abs(obj.y - 50);
                return -distanceFromCenter; // Negative because we want smaller distances (closer to center) to score higher
            };
            
            return centerScore(b) - centerScore(a);
        });

        const selectedObjects = sortedObjects.slice(0, targetCount);

        this.debugLog(`🎯 Selected ${selectedObjects.length} objects for ${difficulty} simplified puzzle:`);
        selectedObjects.forEach((obj, index) => {
            this.debugLog(`   ${index + 1}. ${obj.name} at grid ${obj.gridIndex} - ${obj.confidence} confidence, ${obj.size} size`);
        });

        return selectedObjects;
    }

    assignSimplifiedObjectDifficulty(obj) {
        // Simplified difficulty assignment for cleaner scenes
        const sizeScore = obj.size === 'large' ? 1 : obj.size === 'medium' ? 1.5 : 2.5;
        const visibilityScore = obj.visibility === 'clear' ? 1 : obj.visibility === 'partial' ? 2 : 3;
        const confidenceScore = obj.confidence === 'excellent' ? 1 : obj.confidence === 'good' ? 1.5 : 2.5;
        
        const avgScore = (sizeScore + visibilityScore + confidenceScore) / 3;
        
        if (avgScore <= 1.3) return 'easy';
        if (avgScore <= 2.0) return 'medium';
        return 'hard';
    }

    calculateSimplifiedObjectTolerance(obj) {
        // More generous tolerance for simplified scenes since objects should be clearer
        let baseTolerance = 20.0;
        
        // Adjust based on size
        if (obj.size === 'large') baseTolerance += 5.0;
        if (obj.size === 'small') baseTolerance -= 5.0;
        
        // Adjust based on confidence
        if (obj.confidence === 'excellent') baseTolerance += 3.0;
        if (obj.confidence === 'fair') baseTolerance -= 5.0;
        
        return Math.max(15.0, Math.min(35.0, baseTolerance));
    }

    async uploadImage(puzzleId, imageBuffer) {
        const { supabase } = await import('../config/database.js');
        const fileName = `find-object/${puzzleId}.jpg`;
        
        const { data, error } = await supabase.storage
            .from('puzzle-images')
            .upload(fileName, imageBuffer, { 
                contentType: 'image/jpeg', 
                upsert: true 
            });

        if (error) throw new Error(`Upload failed: ${error.message}`);

        const { data: { publicUrl } } = supabase.storage
            .from('puzzle-images')
            .getPublicUrl(fileName);

        return publicUrl;
    }

    formatForPuzzleSystem(puzzleData) {
        const questionData = {
            puzzleId: puzzleData.puzzleId,
            theme: puzzleData.theme,
            description: puzzleData.description,
            imageUrl: puzzleData.imageUrl,
            imageWidth: puzzleData.imageWidth,
            imageHeight: puzzleData.imageHeight,
            instructions: puzzleData.instructions,
            timeLimit: puzzleData.timeLimit,
            targetObject: puzzleData.targetObject,
            totalObjects: puzzleData.totalObjects,
            objectsToFind: puzzleData.objectsToFind,
            allFoundInstances: puzzleData.allFoundInstances,
            gridConfig: puzzleData.gridConfig,
            gameSettings: puzzleData.gameSettings,
            imageStatus: puzzleData.imageStatus,
            discoveryMethod: puzzleData.discoveryMethod,
            isSimplified: puzzleData.simplificationApplied
        };

        const answerData = {
            targetObject: puzzleData.targetObject,
            allObjects: puzzleData.objectsToFind,
            allFoundInstances: puzzleData.allFoundInstances,
            totalObjects: puzzleData.totalObjects,
            gridConfig: puzzleData.gridConfig,
            completionCriteria: {
                requireAllObjects: true,
                allowHints: true,
                maxTime: puzzleData.timeLimit,
                gridBasedValidation: true
            },
            discoveryMethod: puzzleData.discoveryMethod,
            sceneAnalysis: puzzleData.sceneAnalysis,
            isSimplified: puzzleData.simplificationApplied
        };

        return {
            question: JSON.stringify(questionData),
            answer: JSON.stringify(answerData),
            hint: `Find and tap these ${puzzleData.totalObjects} objects: ${puzzleData.objectsToFind.map(o => o.name).join(', ')}`,
            difficulty: puzzleData.difficulty,
            metadata: {
                theme: puzzleData.theme,
                objectCount: puzzleData.totalObjects,
                imageGenerated: puzzleData.imageGenerated,
                generatedAt: puzzleData.imageGeneratedAt,
                puzzleType: 'visual_interactive',
                requiresImageGeneration: true,
                clickValidation: true,
                coordinateSystem: 'percentage',
                gridSystem: {
                    enabled: true,
                    rows: puzzleData.gridConfig.rows,
                    cols: puzzleData.gridConfig.cols,
                    totalCells: puzzleData.gridConfig.totalCells
                },
                discoveryMethod: puzzleData.discoveryMethod,
                simplifiedGeneration: puzzleData.simplificationApplied,
                naturalGeneration: true
            }
        };
    }

    // Helper functions
    shuffleArray(array) {
        const shuffled = [...array];
        for (let i = shuffled.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
        }
        return shuffled;
    }

    calculateOverallDifficulty(objects) {
        const difficulties = objects.map(obj => obj.difficulty);
        const avgDifficulty = difficulties.reduce((sum, diff) => {
            const score = diff === 'easy' ? 1 : diff === 'medium' ? 2 : 3;
            return sum + score;
        }, 0) / difficulties.length;

        if (avgDifficulty <= 1.5) return 'Easy';
        if (avgDifficulty <= 2.5) return 'Medium';
        return 'Hard';
    }

    getTimeLimitForDifficulty(difficulty) {
        switch (difficulty.toLowerCase()) {
            case 'easy': return 120000;    // 2 minutes (shorter for simplified scenes)
            case 'medium': return 180000;  // 3 minutes
            case 'hard': return 240000;    // 4 minutes
            default: return 180000;
        }
    }
}

// Export for use in puzzle generation system
export const findObjectGenerator = new FindObjectGenerator();
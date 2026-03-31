import express from 'express';
import { supabase } from '../config/database.js';
import multer from 'multer';
import fs from 'fs';

const router = express.Router();

const upload = multer({ dest: 'uploads/' });

router.post('/upload-screenshot', upload.single('screenshot'), async (req, res) => {
    try {
        const file = req.file;
        if (!file) {
            return res.status(400).json({ error: 'No file uploaded' });
        }

        // Upload to Supabase Storage
        const fileName = `screenshots/${Date.now()}_${file.originalname}`;
        const { data, error } = await supabase.storage
            .from('game-screenshots')
            .upload(fileName, fs.readFileSync(file.path), {
                contentType: file.mimetype
            });

        if (error) throw error;

        // Get public URL
        const { data: urlData } = supabase.storage
            .from('game-screenshots')
            .getPublicUrl(fileName);

        // Clean up temp file
        fs.unlinkSync(file.path);

        res.json({
            url: urlData.publicUrl,
            message: 'Screenshot uploaded successfully'
        });
    } catch (error) {
        console.error('Upload error:', error);
        res.status(500).json({ error: 'Failed to upload screenshot' });
    }
});

// Screenshot Library endpoints
router.get('/screenshots', async (req, res) => {
    try {
        const { gameType, featured } = req.query;
        
        let query = supabase
            .from('screenshot_library')
            .select('*')
            .eq('is_approved', true)
            .order('created_at', { ascending: false });
        
        if (gameType) {
            query = query.eq('game_type', gameType);
        }
        
        if (featured === 'true') {
            query = query.eq('is_featured', true);
        }
        
        const { data, error } = await query;
        
        if (error) throw error;
        
        res.json({
            screenshots: data.map(s => ({
                id: s.id,
                title: s.title,
                description: s.description,
                imageUrl: s.image_url,
                thumbnailUrl: s.thumbnail_url,
                gameType: s.game_type,
                uploadedByUserId: s.uploaded_by_user_id,
                uploadedByName: s.uploaded_by_name,
                isFeatured: s.is_featured,
                useCount: s.use_count,
                createdAt: s.created_at
            })),
            total: data.length
        });
    } catch (error) {
        console.error('Error fetching screenshots:', error);
        res.status(500).json({ error: 'Failed to fetch screenshots' });
    }
});

router.post('/screenshots', upload.single('screenshot'), async (req, res) => {
    try {
        const file = req.file;
        const { title, description, gameType, uploadedByUserId, uploadedByName } = req.body;
        
        if (!file || !title) {
            return res.status(400).json({ error: 'File and title required' });
        }
        
        // Upload to Supabase Storage
        const fileName = `screenshots/${Date.now()}_${file.originalname}`;
        const { data: uploadData, error: uploadError } = await supabase.storage
            .from('game-screenshots')
            .upload(fileName, fs.readFileSync(file.path), {
                contentType: file.mimetype
            });
        
        if (uploadError) throw uploadError;
        
        // Get public URL
        const { data: urlData } = supabase.storage
            .from('game-screenshots')
            .getPublicUrl(fileName);
        
        // Save to database
        const { data: dbData, error: dbError } = await supabase
            .from('screenshot_library')
            .insert([{
                title,
                description,
                image_url: urlData.publicUrl,
                game_type: gameType,
                uploaded_by_user_id: uploadedByUserId,
                uploaded_by_name: uploadedByName
            }])
            .select()
            .single();
        
        if (dbError) throw dbError;
        
        // Clean up temp file
        fs.unlinkSync(file.path);
        
        res.json({
            success: true,
            screenshotId: dbData.id,
            imageUrl: urlData.publicUrl,
            message: 'Screenshot uploaded successfully'
        });
    } catch (error) {
        console.error('Upload error:', error);
        res.status(500).json({ error: 'Failed to upload screenshot' });
    }
});

router.post('/screenshots/:id/use', async (req, res) => {
    try {
        const { id } = req.params;
        
        await supabase
            .from('screenshot_library')
            .update({ 
                use_count: supabase.sql`use_count + 1`,
                updated_at: new Date().toISOString()
            })
            .eq('id', id);
        
        res.json({ success: true });
    } catch (error) {
        console.error('Error incrementing use count:', error);
        res.status(500).json({ error: 'Failed to update use count' });
    }
});

// Helper function to detect platform from request
function detectClientPlatform(req) {
    const userAgent = req.get('User-Agent') || '';
    const headers = req.headers;
    
    // Check for Android
    if (userAgent.includes('Android') || 
        userAgent.includes('okhttp') || // OkHttp is common in Android apps
        headers['x-platform'] === 'android') {
        return 'android';
    }
    
    // Check for iOS
    if (userAgent.includes('iOS') || 
        userAgent.includes('iPhone') || 
        userAgent.includes('iPad') ||
        headers['x-platform'] === 'ios') {
        return 'ios';
    }
    
    // Default to web
    return 'web';
}

// POST - Create new game
router.post('/', async (req, res) => {
    try {
        const { 
            title, 
            description, 
            htmlContent, 
            sourceCode,
            platformType = 'webview',
            creatorId, 
            creatorName, 
            gameType,
            initialPrompt,  // ADD THIS
            initialScreenshotUrl,  // ADD THIS
            compilationMetadata 
        } = req.body;
        
        if (!title) {
            return res.status(400).json({ error: "Title is required" });
        }
        
        // Validate content based on platform type
        if (platformType === 'webview' && !htmlContent) {
            return res.status(400).json({ error: "HTML content required for webview games" });
        }
        
        if ((platformType === 'android' || platformType === 'ios') && !sourceCode) {
            return res.status(400).json({ error: `Source code required for ${platformType} games` });
        }
        
        // Validate platform type
        const validPlatforms = ['webview', 'android', 'ios'];
        if (!validPlatforms.includes(platformType)) {
            return res.status(400).json({ error: "Invalid platform type. Must be webview, android, or ios" });
        }
        
        const { data, error } = await supabase
            .from('custom_games')
            .insert([{
                title: title,
                description: description || '',
                html_content: htmlContent,
                source_code: sourceCode,
                platform_type: platformType,
                creator_id: creatorId,
                creator_name: creatorName,
                game_type: gameType || 'custom',
                initial_prompt: initialPrompt,  // ADD THIS
                initial_screenshot_url: initialScreenshotUrl,  // ADD THIS
                compilation_metadata: compilationMetadata || {}
            }])
            .select()
            .single();
            
        if (error) {
            console.error('Supabase error:', error);
            return res.status(500).json({ error: "Failed to save game" });
        }
        
        res.json({ 
            success: true, 
            gameId: data.id,
            message: "Game saved successfully" 
        });
        
    } catch (error) {
        console.error("Error saving game:", error);
        res.status(500).json({ error: "Internal server error" });
    }
});

// GET - Get all games with auto platform detection
router.get('/', async (req, res) => {
    try {
        const { 
            page = 1, 
            limit = 10, 
            creatorId, 
            featured, 
            gameType,
            sortBy = 'created_at'
        } = req.query;
        
        // Auto-detect platform
        const clientPlatform = detectClientPlatform(req);
        
        const offset = (page - 1) * limit;
        
        let query = supabase
            .from('custom_games')
            .select(`
                id, title, description, creator_name, creator_id, game_type, 
                platform_type, play_count, rating, created_at
            `)
            .range(offset, offset + limit - 1);
            
        // Apply filters
        if (creatorId) {
            query = query.eq('creator_id', creatorId);
        }
        
        if (featured === 'true') {
            query = query.eq('is_featured', true);
        }
        
        if (gameType) {
            query = query.eq('game_type', gameType);
        }
        
        // Platform filtering based on detected client
        let platformTypes = [];
        switch (clientPlatform) {
            case 'android':
                platformTypes = ['webview', 'android'];
                break;
            case 'ios':
                platformTypes = ['webview', 'ios'];
                break;
            case 'web':
            default:
                platformTypes = ['webview'];
        }
        
        query = query.in('platform_type', platformTypes);
        
        // Apply sorting
        switch (sortBy) {
            case 'popular':
                query = query.order('play_count', { ascending: false });
                break;
            case 'rating':
                query = query.order('rating', { ascending: false });
                break;
            default:
                query = query.order('created_at', { ascending: false });
        }
        
        const { data, error } = await query;
        
        if (error) {
            console.error('Supabase error:', error);
            return res.status(500).json({ error: "Failed to fetch games" });
        }
        
        const games = data.map(game => ({
            id: game.id,
            title: game.title,
            description: game.description,
            creatorName: game.creator_name,
            gameType: game.game_type,
            platformType: game.platform_type,
            playCount: game.play_count,
            rating: game.rating,
            createdAt: game.created_at,
            isOwned: game.creator_id === creatorId
        }));
        
        res.json({
            games: games,
            page: parseInt(page),
            limit: parseInt(limit),
            detectedPlatform: clientPlatform,
            supportedPlatforms: platformTypes
        });
        
    } catch (error) {
        console.error("Error fetching games:", error);
        res.status(500).json({ error: "Internal server error" });
    }
});

// GET - Get specific game by ID
router.get('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        
        const { data, error } = await supabase
            .from('custom_games')
            .select('*')
            .eq('id', id)
            .single();
            
        if (error || !data) {
            return res.status(404).json({ error: "Game not found" });
        }
        
        res.json({
            id: data.id,
            title: data.title,
            description: data.description,
            htmlContent: data.html_content,
            sourceCode: data.source_code,
            platformType: data.platform_type,
            creatorName: data.creator_name,
            gameType: data.game_type,
            playCount: data.play_count,
            rating: data.rating,
            createdAt: data.created_at,
            compilationMetadata: data.compilation_metadata
        });
        
    } catch (error) {
        console.error("Error fetching game:", error);
        res.status(500).json({ error: "Internal server error" });
    }
});

// POST - Record game play
router.post('/:id/play', async (req, res) => {
    try {
        const { id } = req.params;
        const { playerId, score = 0, completionTime } = req.body;
        
        // Record the play session
        const { error: playError } = await supabase
            .from('game_plays')
            .insert([{
                game_id: id,
                player_id: playerId,
                score: score,
                completion_time: completionTime
            }]);
            
        if (playError) {
            console.error('Error recording play:', playError);
        }
        
        // Increment play count
        const { error: updateError } = await supabase
            .from('custom_games')
            .update({ 
                play_count: supabase.sql`play_count + 1`,
                updated_at: new Date().toISOString()
            })
            .eq('id', id);
            
        if (updateError) {
            console.error('Error updating play count:', updateError);
        }
        
        res.json({ success: true, message: "Play recorded successfully" });
        
    } catch (error) {
        console.error("Error recording play:", error);
        res.status(500).json({ error: "Internal server error" });
    }
});

// GET - Check if user has played a game before (server-side, survives reinstalls)
router.get('/:id/has-played', async (req, res) => {
    try {
        const { id } = req.params;
        const { userId } = req.query;

        if (!userId) {
            return res.status(400).json({ error: 'userId is required' });
        }

        const { data, error } = await supabase
            .from('game_plays')
            .select('id')
            .eq('game_id', id)
            .eq('player_id', userId)
            .limit(1);

        if (error) {
            console.error('Error checking has-played:', error);
            return res.status(500).json({ error: 'Failed to check play history' });
        }

        res.json({
            success: true,
            hasPlayed: (data && data.length > 0)
        });

    } catch (error) {
        console.error("Error checking has-played:", error);
        res.status(500).json({ error: "Internal server error" });
    }
});

// GET - Platform statistics
router.get('/stats/platforms', async (req, res) => {
    try {
        const { data, error } = await supabase
            .from('custom_games')
            .select('platform_type')
            .then(result => {
                // Group by platform_type manually since Supabase client doesn't support .group()
                const platformCounts = {};
                result.data.forEach(game => {
                    platformCounts[game.platform_type] = (platformCounts[game.platform_type] || 0) + 1;
                });
                return { data: platformCounts, error: result.error };
            });
        
        if (error) {
            console.error('Supabase error:', error);
            return res.status(500).json({ error: "Failed to fetch platform stats" });
        }
        
        res.json({ platformStats: data });
        
    } catch (error) {
        console.error("Error fetching platform stats:", error);
        res.status(500).json({ error: "Internal server error" });
    }
});

// PUT - Update existing game
router.put('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const { 
            title, 
            description, 
            htmlContent, 
            sourceCode,
            platformType,
            gameType
        } = req.body;
        
        // First check if game exists
        const { data: existingGame, error: fetchError } = await supabase
            .from('custom_games')
            .select('id, creator_id')
            .eq('id', id)
            .single();
            
        if (fetchError || !existingGame) {
            return res.status(404).json({ error: "Game not found" });
        }
        
        // Optional: Verify ownership (uncomment if you want to restrict updates to creators only)
        // const { creatorId } = req.body;
        // if (existingGame.creator_id !== creatorId) {
        //     return res.status(403).json({ error: "Not authorized to update this game" });
        // }
        
        // Build update object with only provided fields
        const updateData = {
            updated_at: new Date().toISOString()
        };
        
        if (title !== undefined) updateData.title = title;
        if (description !== undefined) updateData.description = description;
        if (htmlContent !== undefined) updateData.html_content = htmlContent;
        if (sourceCode !== undefined) updateData.source_code = sourceCode;
        if (platformType !== undefined) updateData.platform_type = platformType;
        if (gameType !== undefined) updateData.game_type = gameType;
        
        // Update the game
        const { data, error } = await supabase
            .from('custom_games')
            .update(updateData)
            .eq('id', id)
            .select()
            .single();
            
        if (error) {
            console.error('Supabase error:', error);
            return res.status(500).json({ error: "Failed to update game" });
        }
        
        res.json({ 
            success: true, 
            game: {
                id: data.id,
                title: data.title,
                description: data.description,
                htmlContent: data.html_content,
                sourceCode: data.source_code,
                platformType: data.platform_type,
                gameType: data.game_type,
                updatedAt: data.updated_at
            },
            message: "Game updated successfully" 
        });
        
    } catch (error) {
        console.error("Error updating game:", error);
        res.status(500).json({ error: "Internal server error" });
    }
});

// PATCH - Partial update (alternative, allows updating specific fields)
router.patch('/:id', async (req, res) => {
    try {
        const { id } = req.params;
        
        // First check if game exists
        const { data: existingGame, error: fetchError } = await supabase
            .from('custom_games')
            .select('id')
            .eq('id', id)
            .single();
            
        if (fetchError || !existingGame) {
            return res.status(404).json({ error: "Game not found" });
        }
        
        // Build update object from request body
        const updateData = {
            updated_at: new Date().toISOString()
        };
        
        // Map client fields to database fields
        const fieldMappings = {
            title: 'title',
            description: 'description',
            htmlContent: 'html_content',
            sourceCode: 'source_code',
            platformType: 'platform_type',
            gameType: 'game_type'
        };
        
        Object.keys(fieldMappings).forEach(clientField => {
            if (req.body[clientField] !== undefined) {
                updateData[fieldMappings[clientField]] = req.body[clientField];
            }
        });
        
        // Update the game
        const { data, error } = await supabase
            .from('custom_games')
            .update(updateData)
            .eq('id', id)
            .select()
            .single();
            
        if (error) {
            console.error('Supabase error:', error);
            return res.status(500).json({ error: "Failed to update game" });
        }
        
        res.json({ 
            success: true,
            message: "Game updated successfully" 
        });
        
    } catch (error) {
        console.error("Error updating game:", error);
        res.status(500).json({ error: "Internal server error" });
    }
});

export default router;
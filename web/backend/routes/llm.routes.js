// routes/llm.routes.js - LLM/AI chat endpoints
import express from 'express';
import axios from 'axios';
import usageTracker, { USAGE_CATEGORIES } from '../utils/usageTracker.js';
import { extractTopicFromPrompt, determineCategory } from '../utils/puzzleUtils.js';

const router = express.Router();

const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
const OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

// Rate limiting configuration
const RATE_LIMIT_CONFIG = {
    windowMs: 60 * 1000,
    maxRequests: 10,
    maxRequestsPerUser: 5
};

const rateLimitStore = new Map();

const WHITELISTED_USERS = [
    'gVh2Q53NzLgu8JeA5zXcLwxQvxF3'
];

function checkRateLimit(req) {
    const userId = req.body.userId || 'anonymous';

    if (WHITELISTED_USERS.includes(userId)) {
        return { allowed: true };
    }

    const now = Date.now();
    const windowStart = now - RATE_LIMIT_CONFIG.windowMs;

    if (!rateLimitStore.has(userId)) {
        rateLimitStore.set(userId, { requests: [], totalRequests: 0 });
    }

    const clientData = rateLimitStore.get(userId);
    clientData.requests = clientData.requests.filter(timestamp => timestamp > windowStart);

    const isUserAuth = userId !== 'anonymous' && userId !== '' && !userId.startsWith('ip_');
    const limit = isUserAuth ? RATE_LIMIT_CONFIG.maxRequestsPerUser : RATE_LIMIT_CONFIG.maxRequests;

    if (clientData.requests.length >= limit) {
        const oldestRequest = Math.min(...clientData.requests);
        const resetTime = oldestRequest + RATE_LIMIT_CONFIG.windowMs;
        const waitTime = Math.ceil((resetTime - now) / 1000);

        return {
            allowed: false,
            resetTime,
            waitTime,
            requestsRemaining: 0,
            totalRequests: clientData.totalRequests
        };
    }

    clientData.requests.push(now);
    clientData.totalRequests += 1;

    return {
        allowed: true,
        requestsRemaining: limit - clientData.requests.length,
        totalRequests: clientData.totalRequests,
        resetTime: windowStart + RATE_LIMIT_CONFIG.windowMs
    };
}

// Clean up old entries periodically
setInterval(() => {
    const now = Date.now();
    const cutoff = now - (RATE_LIMIT_CONFIG.windowMs * 2);

    for (const [clientId, data] of rateLimitStore.entries()) {
        data.requests = data.requests.filter(timestamp => timestamp > cutoff);
        if (data.requests.length === 0) {
            rateLimitStore.delete(clientId);
        }
    }
}, 5 * 60 * 1000);

async function detectGameImageNeeds(prompt) {
    const imageKeywords = [
        'periodic table', 'solar system', 'animal', 'geography', 'flag',
        'landmark', 'planet', 'constellation', 'anatomy', 'diagram',
        'picture', 'image', 'photo', 'visual'
    ];

    const promptLower = prompt.toLowerCase();
    return imageKeywords.some(keyword => promptLower.includes(keyword));
}

async function generateImageForGame(prompt, difficulty = 'medium') {
    try {
        if (prompt.toLowerCase().includes('puzzle') || prompt.toLowerCase().includes('jigsaw')) {
            const { imagePuzzleGenerator } = await import('../services/imagePuzzleGenerator.js');
            const result = await imagePuzzleGenerator.generateImagePuzzle(difficulty);

            if (result.success) {
                return {
                    success: true,
                    url: result.puzzleData.question ? JSON.parse(result.puzzleData.question).imageUrl : null,
                    description: result.selectedTheme?.description || 'Game image'
                };
            }
        }

        const { EnhancedPixabayPuzzleSystem } = await import('../services/enhancedPixabayImageMatch.js');
        const pixabay = new EnhancedPixabayPuzzleSystem({ debug: true });

        const topic = extractTopicFromPrompt(prompt);
        const category = determineCategory(topic);

        const result = await pixabay.generateCompletePuzzle(category, difficulty, 1);

        if (result.success) {
            return {
                success: true,
                url: result.puzzle.primaryImage.url,
                description: result.puzzle.themeItem.name
            };
        }

        return { success: false };

    } catch (error) {
        console.error('Image generation failed:', error);
        return { success: false };
    }
}

/**
 * Streaming LLM endpoint
 */
router.post('/call-llm-stream', async (req, res) => {
    const startTime = Date.now();

    try {
        const rateLimitResult = checkRateLimit(req);

        if (!rateLimitResult.allowed) {
            const userId = req.body.userId || 'anonymous';
            await usageTracker.trackRequest({
                modelName: 'gpt-4o',
                category: 'rate_limited',
                prompt: `Rate limited request from ${userId}`,
                response: '',
                success: false,
                puzzleType: 'rate_limit'
            });
            return res.status(429).json({
                error: "Rate limit exceeded",
                rateLimitInfo: {
                    resetTime: rateLimitResult.resetTime,
                    waitTime: rateLimitResult.waitTime,
                    totalRequests: rateLimitResult.totalRequests
                }
            });
        }

        const { conversation, hasImage, userId = 'anonymous', gameContext } = req.body;

        if (!conversation || !Array.isArray(conversation)) {
            return res.status(400).json({
                error: "Invalid request. 'conversation' must be an array."
            });
        }

        res.setHeader('Content-Type', 'text/event-stream');
        res.setHeader('Cache-Control', 'no-cache');
        res.setHeader('Connection', 'keep-alive');
        res.setHeader('X-Accel-Buffering', 'no');

        const keepAliveInterval = setInterval(() => {
            res.write(': keepalive\n\n');
        }, 15000);

        let enhancedConversation = [...conversation];
        const lastUserMessage = conversation[conversation.length - 1];

        if (lastUserMessage && typeof lastUserMessage.content === 'string') {
            const needsGameImage = await detectGameImageNeeds(lastUserMessage.content);

            if (needsGameImage) {
                res.write(`data: ${JSON.stringify({
                    status: 'generating_image',
                    message: 'Generating game image...'
                })}\n\n`);

                const imageResult = await generateImageForGame(
                    lastUserMessage.content,
                    gameContext?.difficulty || 'medium'
                );

                if (imageResult.success) {
                    const imageInstruction = {
                        role: 'system',
                        content: `AVAILABLE IMAGE: You have access to this image URL: ${imageResult.url}

Image description: ${imageResult.description}
Storage: Supabase (permanent URL)

YOU MUST USE THIS EXACT URL IN YOUR HTML GAME.
Example: <img src="${imageResult.url}" alt="${imageResult.description}">

DO NOT use local file references. DO NOT make up image URLs.`
                    };

                    enhancedConversation.splice(enhancedConversation.length - 1, 0, imageInstruction);
                    res.write(`data: ${JSON.stringify({
                        status: 'image_ready',
                        imageUrl: imageResult.url
                    })}\n\n`);
                }
            }
        }

        const promptForTracking = enhancedConversation
            .map(msg => `${msg.role}: ${typeof msg.content === 'string' ? msg.content : '[multimodal content]'}`)
            .join('\n');

        const model = hasImage ? "gpt-4o" : "gpt-3.5-turbo";

        res.write(`data: ${JSON.stringify({
            status: 'generating',
            message: 'AI is thinking...'
        })}\n\n`);

        const response = await axios.post(OPENAI_API_URL, {
            model: model,
            messages: enhancedConversation,
            max_tokens: hasImage ? 4096 : undefined,
            stream: true
        }, {
            headers: {
                'Authorization': `Bearer ${OPENAI_API_KEY}`,
                'Content-Type': 'application/json'
            },
            responseType: 'stream'
        });

        let fullResponse = '';
        let chunkCount = 0;

        response.data.on('data', (chunk) => {
            const lines = chunk.toString().split('\n').filter(line => line.trim() !== '');

            for (const line of lines) {
                if (line.startsWith('data: ')) {
                    const data = line.slice(6);

                    if (data === '[DONE]') return;

                    try {
                        const parsed = JSON.parse(data);
                        const content = parsed.choices?.[0]?.delta?.content;

                        if (content) {
                            fullResponse += content;
                            chunkCount++;
                            res.write(`data: ${JSON.stringify({ chunk: content, done: false })}\n\n`);
                        }
                    } catch (e) { }
                }
            }
        });

        response.data.on('end', async () => {
            clearInterval(keepAliveInterval);
            const responseTime = Date.now() - startTime;

            await usageTracker.trackRequest({
                modelName: model,
                category: USAGE_CATEGORIES.OTHER,
                prompt: promptForTracking,
                response: fullResponse,
                success: true,
                puzzleType: hasImage ? 'game_with_image_stream' : 'general_chat_stream'
            });

            res.write(`data: ${JSON.stringify({
                message: fullResponse,
                done: true,
                metadata: {
                    chunks: chunkCount,
                    duration: responseTime,
                    rateLimitInfo: {
                        requestsRemaining: rateLimitResult.requestsRemaining,
                        totalRequests: rateLimitResult.totalRequests,
                        resetTime: rateLimitResult.resetTime
                    }
                }
            })}\n\n`);

            res.end();
        });

        response.data.on('error', async (error) => {
            clearInterval(keepAliveInterval);
            console.error('Stream error:', error.message);

            await usageTracker.trackRequest({
                modelName: model,
                category: 'stream_error',
                prompt: promptForTracking,
                response: fullResponse,
                success: false,
                puzzleType: 'stream_error'
            });

            res.write(`data: ${JSON.stringify({ error: error.message, done: true })}\n\n`);
            res.end();
        });

        req.on('close', () => {
            clearInterval(keepAliveInterval);
        });

    } catch (error) {
        console.error("Error in streaming endpoint:", error.response ? error.response.data : error.message);

        if (!res.headersSent) {
            res.setHeader('Content-Type', 'text/event-stream');
            res.write(`data: ${JSON.stringify({ error: error.message, done: true })}\n\n`);
        }
        res.end();
    }
});

/**
 * Non-streaming LLM endpoint
 */
router.post('/call-llm', async (req, res) => {
    const startTime = Date.now();

    try {
        const rateLimitResult = checkRateLimit(req);

        if (!rateLimitResult.allowed) {
            const userId = req.body.userId || 'anonymous';
            await usageTracker.trackRequest({
                modelName: 'gpt-4o',
                category: 'rate_limited',
                prompt: `Rate limited request from ${userId}`,
                response: '',
                success: false,
                puzzleType: 'rate_limit'
            });
            return res.status(429).json({
                error: "Rate limit exceeded",
                message: "Too many requests. Please try again later.",
                rateLimitInfo: {
                    resetTime: rateLimitResult.resetTime,
                    waitTime: rateLimitResult.waitTime,
                    totalRequests: rateLimitResult.totalRequests
                }
            });
        }

        const { conversation, hasImage, userId = 'anonymous', gameContext } = req.body;

        if (!conversation || !Array.isArray(conversation)) {
            return res.status(400).json({
                error: "Invalid request. 'conversation' must be an array.",
                rateLimitInfo: {
                    requestsRemaining: rateLimitResult.requestsRemaining,
                    totalRequests: rateLimitResult.totalRequests
                }
            });
        }

        let enhancedConversation = [...conversation];
        const lastUserMessage = conversation[conversation.length - 1];

        if (lastUserMessage && typeof lastUserMessage.content === 'string') {
            const needsGameImage = await detectGameImageNeeds(lastUserMessage.content);

            if (needsGameImage) {
                const imageResult = await generateImageForGame(
                    lastUserMessage.content,
                    gameContext?.difficulty || 'medium'
                );

                if (imageResult.success) {
                    const imageInstruction = {
                        role: 'system',
                        content: `AVAILABLE IMAGE: You have access to this image URL: ${imageResult.url}

Image description: ${imageResult.description}
Storage: Supabase (permanent URL)

YOU MUST USE THIS EXACT URL IN YOUR HTML GAME.
Example: <img src="${imageResult.url}" alt="${imageResult.description}">

DO NOT use local file references. DO NOT make up image URLs.`
                    };

                    enhancedConversation.splice(enhancedConversation.length - 1, 0, imageInstruction);
                }
            }
        }

        const promptForTracking = enhancedConversation
            .map(msg => `${msg.role}: ${typeof msg.content === 'string' ? msg.content : '[multimodal content]'}`)
            .join('\n');

        const model = hasImage ? "gpt-4o" : "gpt-3.5-turbo";

        const response = await axios.post(OPENAI_API_URL, {
            model: model,
            messages: enhancedConversation,
            max_tokens: hasImage ? 4096 : undefined
        }, {
            headers: {
                'Authorization': `Bearer ${OPENAI_API_KEY}`,
                'Content-Type': 'application/json'
            }
        });

        const aiResponse = response.data.choices?.[0]?.message?.content?.trim() || "Call to OpenAI failed.";

        await usageTracker.trackRequest({
            modelName: model,
            category: USAGE_CATEGORIES.OTHER,
            prompt: promptForTracking,
            response: aiResponse,
            success: true,
            puzzleType: hasImage ? 'game_with_image' : 'general_chat'
        });

        res.json({
            message: aiResponse,
            rateLimitInfo: {
                requestsRemaining: rateLimitResult.requestsRemaining,
                totalRequests: rateLimitResult.totalRequests,
                resetTime: rateLimitResult.resetTime
            }
        });

    } catch (error) {
        console.error("Error calling OpenAI API:", error.response ? error.response.data : error.message);

        if (error.response && error.response.status === 429) {
            return res.status(429).json({
                error: "OpenAI API rate limit exceeded. Please try again later.",
                message: "The AI service is temporarily busy. Please wait a moment and try again."
            });
        }

        res.status(500).json({
            error: "Failed to fetch response from OpenAI API.",
            message: "Sorry, there was a technical issue. Please try again."
        });
    }
});

export default router;

// utils/aiClient.js

import axios from 'axios';
import usageTracker, { USAGE_CATEGORIES } from './usageTracker.js';

/**
 * Enhanced AI client with automatic model rotation and proper error handling
 */

// Model rotation state
let currentModelIndex = 0;
const availableModels = [
    'gpt-3.5-turbo',
    'deepseek',
    'claude-3-sonnet',
    'gemini-pro'];


export function getApiKey(modelName) {
    const apiKeys = {
        deepseek: process.env.DEEPSEEK_API_KEY,
        openai: process.env.OPENAI_API_KEY,
        anthropic: process.env.ANTHROPIC_API_KEY,
        google: process.env.GOOGLE_API_KEY
    };

    // Map models to their API keys
    if (modelName === "deepseek") {
        return apiKeys.deepseek;
    } else if (modelName.startsWith('claude')) {
        return apiKeys.anthropic;
    } else if (modelName.startsWith('gemini')) {
        return apiKeys.google;
    } else {
        return apiKeys.openai;
    }
}

function getApiConfig(modelName) {
    switch (modelName) {
        case 'deepseek':
            return {
                url: "https://api.deepseek.com/chat/completions",
                model: "deepseek-chat"
            };
        
        case 'claude-3-sonnet':
            return {
                url: "https://api.anthropic.com/v1/messages",
                model: "claude-3-5-sonnet-20241022",
                isAnthropic: true
            };
        
        case 'gemini-pro':
            return {
                // ✅ Updated Gemini API endpoint
                url: "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent",
                model: "gemini-1.5-flash-latest", // Updated model
                isGoogle: true
            };
        
        default:
            return {
                url: "https://api.openai.com/v1/chat/completions",
                model: modelName // gpt-4, gpt-4-turbo, gpt-3.5-turbo
            };
    }
}

function getHeaders(modelName, apiKey) {
    switch (modelName) {
        case 'claude-3-sonnet':
            return {
                "x-api-key": apiKey,
                "Content-Type": "application/json",
                "anthropic-version": "2023-06-01"
            };
        
        case 'gemini-pro':
            return {
                "Content-Type": "application/json"
            };
        
        default:
            return {
                "Authorization": `Bearer ${apiKey}`,
                "Content-Type": "application/json"
            };
    }
}

/**
 * Get the next available model in rotation
 * @returns {string} - Next model name
 */
function getNextModel() {
    const model = availableModels[currentModelIndex];
    currentModelIndex = (currentModelIndex + 1) % availableModels.length;
    console.log(`🔄 Rotating to model: ${model} (index: ${currentModelIndex - 1})`);
    return model;
}

/**
 * Reset model rotation to start from the beginning
 */
export function resetModelRotation() {
    currentModelIndex = 0;
    console.log('🔄 Model rotation reset to start');
}

/**
 * Skip current model due to failure and get next one
 * @param {string} failedModel - The model that failed
 * @returns {string} - Next available model
 */
function skipToNextModel(failedModel) {
    console.log(`⏭️ Skipping failed model: ${failedModel}`);
    return getNextModel();
}

/**
 * Enhanced AI client that automatically rotates between models on failure
 * @param {string} prompt - The prompt to send to the AI
 * @param {string} requestedModel - Preferred model (optional, will use rotation if not specified)
 * @param {number} maxRetries - Max retries per model
 * @returns {Promise<string>} - Cleaned AI response
 */
export async function callAI(prompt, requestedModel = null, maxRetries = 2, options = {}) {
    if (!prompt || typeof prompt !== 'string') {
        throw new Error('Valid prompt is required');
    }

    const startTime = Date.now();
    const {
        category = USAGE_CATEGORIES.OTHER,
        puzzleType = null,
        difficulty = null,
        max_tokens = 1500,
        rawResponse = false
    } = options;

    // If no specific model requested, use rotation
    let modelsToTry = [getNextModel()];
    
    // If a specific model was requested but failed before, add rotation models as fallback
    if (requestedModel && !modelsToTry.includes(requestedModel)) {
        modelsToTry = [requestedModel, ...availableModels.filter(m => m !== requestedModel)];
    }

    let lastError;
    let totalAttempts = 0;
    let retryCount = 0;
    const maxTotalAttempts = modelsToTry.length * maxRetries;

    for (const modelName of modelsToTry) {
        console.log(`🤖 Trying model: ${modelName}`);
        
        const apiConfig = getApiConfig(modelName);
        const API_URL = apiConfig.url;
    
        let requestBody;
        if (apiConfig.isAnthropic) {
            requestBody = {
                model: apiConfig.model,
                max_tokens: max_tokens,
                messages: [{ role: "user", content: prompt }]
            };
        } else if (apiConfig.isGoogle) {
            requestBody = {
                contents: [{
                    parts: [{ text: prompt }]
                }]
            };
        } else {
            requestBody = {
                model: apiConfig.model,
                messages: [{ role: "user", content: prompt }],
                max_tokens: max_tokens,
                temperature: 0.7,
            };
        }
    
        const apiKey = getApiKey(modelName);
        
        if (!apiKey) {
            console.warn(`⚠️ No API key for ${modelName}, skipping...`);
            continue;
        }
    
        // Try this model with retries
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            totalAttempts++;
            if (attempt > 1) retryCount++;
            
            try {
                console.log(`🤖 Calling ${modelName} API (attempt ${attempt}/${maxRetries}, total: ${totalAttempts}/${maxTotalAttempts})...`);
                
                const headers = getHeaders(modelName, apiKey);
                const finalUrl = modelName === 'gemini-pro' ? `${API_URL}?key=${apiKey}` : API_URL;
                
                const response = await axios.post(finalUrl, requestBody, {
                    headers,
                    timeout: 90000,
                });
    
                // Parse response based on API type
                let aiResponse;
                if (apiConfig.isAnthropic) {
                    if (!response.data?.content?.[0]?.text) {
                        throw new Error('Invalid response format from Anthropic API');
                    }
                    aiResponse = response.data.content[0].text.trim();
                } else if (apiConfig.isGoogle) {
                    if (!response.data?.candidates?.[0]?.content?.parts?.[0]?.text) {
                        throw new Error('Invalid response format from Google API');
                    }
                    aiResponse = response.data.candidates[0].content.parts[0].text.trim();
                } else {
                    if (!response.data?.choices?.[0]?.message?.content) {
                        throw new Error('Invalid response format from API');
                    }
                    aiResponse = response.data.choices[0].message.content.trim();
                }
    
                console.log(`📥 Raw AI Response from ${modelName} length: ${aiResponse.length} (attempt ${attempt})`);

                if (!rawResponse) {
                    aiResponse = cleanAIResponse(aiResponse);
                    console.log(`✨ Cleaned AI Response from ${modelName} length: ${aiResponse.length}`);
                }
                
                console.log(`✅ ${modelName} API call successful on attempt ${attempt}`);
                
                // Track successful usage with simple tracker
                const trackingData = await usageTracker.trackRequest({
                    modelName,
                    category,
                    prompt,
                    response: aiResponse,
                    success: true,
                    puzzleType,
                    difficulty
                });
                
                console.log(`💰 Cost: ${trackingData.cost.toFixed(4)}, Tokens: ${trackingData.tokens}`);
                
                return aiResponse;
                
            } catch (error) {
                lastError = error;
                console.error(`❌ ${modelName} API Error (attempt ${attempt}/${maxRetries}):`, error.message);
                
                // Track failed attempt
                await usageTracker.trackRequest({
                    modelName,
                    category,
                    prompt,
                    response: '',
                    success: false,
                    puzzleType,
                    difficulty
                });
                
                // ... rest of error handling logic remains the same
                let shouldSkipModel = false;
                let shouldRetryAttempt = true;
                
                if (error.response) {
                    const status = error.response.status;
                    
                    if (status === 401) {
                        console.error(`🔐 Authentication failed for ${modelName}, skipping this model`);
                        shouldSkipModel = true;
                        shouldRetryAttempt = false;
                    } else if (status === 429) {
                        console.warn(`⏰ Rate limit for ${modelName}, will retry with backoff`);
                        shouldRetryAttempt = true;
                    } else if (status === 400) {
                        console.warn(`📝 Bad request to ${modelName}, skipping this model`);
                        shouldSkipModel = true;
                        shouldRetryAttempt = false;
                    } else if (status >= 500) {
                        console.warn(`🔧 Server error for ${modelName}, will retry`);
                        shouldRetryAttempt = true;
                    }
                } else if (error.code === 'ECONNABORTED') {
                    console.warn(`⏰ Timeout for ${modelName}, will retry`);
                    shouldRetryAttempt = true;
                } else if (error.code === 'ENOTFOUND' || error.code === 'ECONNREFUSED') {
                    console.warn(`🌐 Network error for ${modelName}, skipping this model`);
                    shouldSkipModel = true;
                    shouldRetryAttempt = false;
                }
                
                // If we should skip this model entirely, break out of the retry loop
                if (shouldSkipModel) {
                    console.log(`⏭️ Skipping to next model due to ${modelName} failure`);
                    break;
                }
                
                // If this is the last attempt for this model, move to next model
                if (attempt === maxRetries) {
                    console.warn(`💥 ${modelName} failed after ${maxRetries} attempts, trying next model`);
                    break;
                }
                
                // Calculate backoff delay
                if (shouldRetryAttempt) {
                    const baseDelay = Math.pow(2, attempt) * 1000;
                    const jitter = Math.random() * 1000;
                    const delay = baseDelay + jitter;
                    
                    console.log(`⏳ Retrying ${modelName} in ${Math.round(delay/1000)}s...`);
                    await new Promise(resolve => setTimeout(resolve, delay));
                }
            }
        }
        
        console.log(`🔄 Moving to next model after ${modelName} failures`);
    }
    
    // All models failed - track final failure
    await usageTracker.trackRequest({
        modelName: modelsToTry[0], // Track against first attempted model
        category: USAGE_CATEGORIES.ERROR_RETRY,
        prompt,
        response: '',
        success: false,
        puzzleType,
        difficulty
    });
    
    console.error(`💀 All models failed after ${totalAttempts} total attempts`);
    resetModelRotation(); // Reset for next time
    
    throw new Error(`All AI models failed. Last error: ${lastError?.message || 'Unknown error'}. Tried models: ${modelsToTry.join(', ')}`);
}

/**
 * FIXED: Enhanced function to clean AI responses and extract valid JSON
 * @param {string} response - Raw AI response
 * @returns {string} - Cleaned response
 */
function cleanAIResponse(response) {
    if (!response || typeof response !== 'string') {
        console.warn("⚠️ Invalid response type:", typeof response);
        return response;
    }
    
    let cleaned = response.trim();
    console.log(`🔍 Cleaning AI response (length: ${cleaned.length})`);
    console.log(`📝 First 100 chars: "${cleaned.substring(0, 100)}"`);
    
    // ✅ FIXED: More specific simple response detection
    // Only treat VERY specific short responses as simple text
    const definitelySimpleResponses = /^(VALID|INVALID|YES|NO|TRUE|FALSE|OK|ERROR)$/i;
    
    if (definitelySimpleResponses.test(cleaned.trim())) {
        console.log("✅ Definite simple response detected, skipping JSON parsing");
        return cleaned;
    }
    
    // ✅ FIXED: Better JSON detection - look for actual JSON structure
    const hasJsonStructure = (
        (cleaned.includes('{') && cleaned.includes('}')) ||
        (cleaned.includes('[') && cleaned.includes(']')) ||
        cleaned.includes('"word"') ||
        cleaned.includes('"hint"') ||
        cleaned.includes('```json') ||
        cleaned.includes('```JSON')
    );
    
    if (!hasJsonStructure && cleaned.length < 100) {
        console.log("✅ Short non-JSON response detected, returning as-is");
        return cleaned;
    }
    
    console.log("🔍 JSON-like response detected, attempting to extract and clean...");
    
    // Remove markdown code blocks first
    cleaned = cleaned.replace(/^```(?:json|JSON)?\s*/i, '');
    cleaned = cleaned.replace(/\s*```\s*$/i, '');
    cleaned = cleaned.trim();
    
    // ✅ FIXED: Better JSON boundary detection
    let jsonStart = -1;
    let jsonEnd = -1;
    
    // Find the first { or [
    for (let i = 0; i < cleaned.length; i++) {
        if (cleaned[i] === '{' || cleaned[i] === '[') {
            jsonStart = i;
            break;
        }
    }
    
    // Find the matching closing bracket
    if (jsonStart !== -1) {
        const openChar = cleaned[jsonStart];
        const closeChar = openChar === '{' ? '}' : ']';
        let depth = 0;
        
        for (let i = jsonStart; i < cleaned.length; i++) {
            if (cleaned[i] === openChar) depth++;
            if (cleaned[i] === closeChar) {
                depth--;
                if (depth === 0) {
                    jsonEnd = i;
                    break;
                }
            }
        }
    }
    
    // Extract JSON if we found proper boundaries
    if (jsonStart !== -1 && jsonEnd !== -1) {
        const extractedJson = cleaned.substring(jsonStart, jsonEnd + 1);
        console.log(`🎯 Extracted JSON: "${extractedJson.substring(0, 100)}..."`);
        cleaned = extractedJson;
    }
    
    // ✅ FIXED: Try to validate and fix JSON
    try {
        const parsed = JSON.parse(cleaned);
        console.log("✅ Valid JSON extracted successfully");
        return cleaned;
    } catch (parseError) {
        console.warn("🚨 JSON parsing failed, attempting repair...");
        console.warn(`Parse error: ${parseError.message}`);
        
        // Try to fix common JSON issues
        const fixedJson = attemptJsonFix(cleaned);
        
        try {
            JSON.parse(fixedJson);
            console.log("✅ JSON repaired successfully");
            return fixedJson;
        } catch (fixError) {
            console.error("❌ Could not repair JSON");
            console.error(`Original: "${cleaned.substring(0, 200)}"`);
            console.error(`Fixed attempt: "${fixedJson.substring(0, 200)}"`);
            
            // Return original for debugging
            return cleaned;
        }
    }
}

// Add this to your aiClient.js file

/**
 * Generate image using OpenAI DALL-E with same error handling as callAI
 * @param {string} prompt - The image prompt
 * @param {object} options - Generation options
 * @returns {Promise<Buffer>} - Image buffer
 */
export async function generateImage(prompt, options = {}) {
    if (!prompt || typeof prompt !== 'string') {
        throw new Error('Valid prompt is required for image generation');
    }

    const {
        model = "dall-e-3",
        size = "1024x1024",
        quality = "hd",
        style = "natural",
        category = USAGE_CATEGORIES.IMAGE_GENERATION || 'image_generation',
        puzzleType = null
    } = options;

    const startTime = Date.now();
    console.log(`🖼️ Generating image with ${model}...`);
    console.log(`📝 Prompt: ${prompt.substring(0, 100)}...`);

    let lastError;
    const maxRetries = 3;

    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            const apiKey = getApiKey('openai');
            if (!apiKey) {
                throw new Error('OpenAI API key not found');
            }

            console.log(`🎨 Image generation attempt ${attempt}/${maxRetries}...`);

            // ✅ FIXED: Correct endpoint URL
            const response = await axios.post(
                'https://api.openai.com/v1/images/generations',  // Fixed: added 's'
                {
                    model: model,
                    prompt: prompt,
                    size: size,
                    quality: quality,
                    response_format: "b64_json",
                    style: style
                }, 
                {
                    headers: {
                        'Authorization': `Bearer ${apiKey}`,
                        'Content-Type': 'application/json'
                    },
                    timeout: 120000 // 2 minutes for image generation
                }
            );

            if (!response.data?.data?.[0]?.b64_json) {
                throw new Error('Invalid response format from OpenAI Images API');
            }

            const imageBuffer = Buffer.from(response.data.data[0].b64_json, 'base64');
            const generationTime = Date.now() - startTime;
            
            console.log(`✅ Image generated successfully in ${generationTime}ms (${imageBuffer.length} bytes)`);

            // Track successful usage
            await usageTracker.trackRequest({
                modelName: model,
                category: category,
                prompt: prompt,
                response: `Image generated (${imageBuffer.length} bytes)`,
                success: true,
                puzzleType: puzzleType,
                tokens: 0,
                cost: model === 'dall-e-3' ? (size === '1024x1024' && quality === 'hd' ? 0.080 : 0.040) : 0.020
            });

            console.log(`💰 Image generation cost: $${model === 'dall-e-3' ? (quality === 'hd' ? '0.080' : '0.040') : '0.020'}`);
            
            return imageBuffer;

        } catch (error) {
            lastError = error;
            console.error(`❌ Image generation attempt ${attempt}/${maxRetries} failed:`, error.message);
            
            // Enhanced error logging
            if (error.response) {
                console.error(`📊 Response status: ${error.response.status}`);
                console.error(`📊 Response data:`, JSON.stringify(error.response.data, null, 2));
            }

            // Track failed attempt
            await usageTracker.trackRequest({
                modelName: model,
                category: category,
                prompt: prompt,
                response: '',
                success: false,
                puzzleType: puzzleType
            });

            // Handle different error types
            if (error.response?.status === 400) {
                console.error(`🚫 Bad request: ${error.response.data?.error?.message}`);
                // Don't retry policy violations
                break;
            } else if (error.response?.status === 404) {
                console.error(`🚫 Endpoint not found - check API URL`);
                break;
            } else if (error.response?.status === 429) {
                console.warn(`⏰ Rate limit exceeded, retrying with backoff...`);
                if (attempt < maxRetries) {
                    const delay = Math.pow(2, attempt) * 2000;
                    console.log(`⏳ Waiting ${delay}ms before retry...`);
                    await new Promise(resolve => setTimeout(resolve, delay));
                }
            } else if (error.response?.status === 401) {
                console.error(`🔐 Authentication failed - check OpenAI API key`);
                console.error(`Key preview: ${apiKey.substring(0, 10)}...${apiKey.substring(apiKey.length - 4)}`);
                break;
            } else if (error.response?.status >= 500) {
                console.warn(`🔧 Server error, retrying...`);
                if (attempt < maxRetries) {
                    const delay = 1000 * attempt;
                    await new Promise(resolve => setTimeout(resolve, delay));
                }
            } else if (error.code === 'ECONNABORTED') {
                console.warn(`⏰ Timeout, retrying...`);
                if (attempt < maxRetries) {
                    await new Promise(resolve => setTimeout(resolve, 2000));
                }
            } else {
                console.error(`🚨 Unexpected error: ${error.message}`);
                if (attempt < maxRetries) {
                    await new Promise(resolve => setTimeout(resolve, 1000));
                }
            }
        }
    }

    // All attempts failed
    console.error(`💀 Image generation failed after ${maxRetries} attempts`);
    throw new Error(`Image generation failed: ${lastError?.message || 'Unknown error'}`);
}

/**
 * Generate image and create split-screen version for find-differences puzzles
 * @param {string} prompt - The image prompt  
 * @param {object} options - Generation and processing options
 * @returns {Promise<Buffer>} - Split-screen image buffer
 */
export async function generateSplitScreenImage(prompt, options = {}) {
    const {
        width = 1024,
        height = 512,
        ...imageOptions
    } = options;

    console.log(`🖼️ Generating split-screen image (${width}x${height})...`);

    try {
        // Generate the original image
        const originalBuffer = await generateImage(prompt, imageOptions);

        // Create split-screen version
        console.log(`✂️ Creating split-screen version...`);
        const sharp = (await import('sharp')).default;
        
        const leftSideBuffer = await sharp(originalBuffer)
            .resize(width / 2, height, { fit: 'cover' })
            .toBuffer();

        const rightSideBuffer = await sharp(originalBuffer)
            .resize(width / 2, height, { fit: 'cover' })
            .toBuffer();

        const splitScreenImage = await sharp({
            create: {
                width: width,
                height: height,
                channels: 3,
                background: { r: 255, g: 255, b: 255 }
            }
        })
        .composite([
            { input: leftSideBuffer, left: 0, top: 0 },
            { input: rightSideBuffer, left: width / 2, top: 0 }
        ])
        .jpeg({ quality: 90 })
        .toBuffer();

        console.log(`✅ Split-screen image created: ${width}x${height} (${splitScreenImage.length} bytes)`);
        return splitScreenImage;

    } catch (error) {
        console.error(`❌ Split-screen image generation failed: ${error.message}`);
        throw error;
    }
}

/**
 * FIXED: Attempt to fix common JSON formatting issues
 * @param {string} jsonString - Potentially malformed JSON string
 * @returns {string} - Attempted fix of JSON string
 */
function attemptJsonFix(jsonString) {
    let fixed = jsonString;
    
    try {
        // 1. Fix missing quotes around property names
        fixed = fixed.replace(/(\w+)\s*:/g, '"$1":');
        
        // 2. Fix single quotes to double quotes
        fixed = fixed.replace(/'/g, '"');
        
        // 3. Remove trailing commas
        fixed = fixed.replace(/,(\s*[}\]])/g, '$1');
        
        // 4. Fix boolean and null values that got quoted
        fixed = fixed.replace(/"(true|false|null)"/g, '$1');
        
        // 5. Fix escaped quotes
        fixed = fixed.replace(/\\"/g, '"');
        
        // 6. Ensure proper array/object structure
        if (!fixed.startsWith('{') && !fixed.startsWith('[')) {
            fixed = '{' + fixed + '}';
        }
        
        // 7. Fix common word generation format issues
        fixed = fixed.replace(/word\s*:\s*([A-Z]+)/g, '"word": "$1"');
        fixed = fixed.replace(/hint\s*:\s*([^,}]+)/g, '"hint": "$1"');
        
        return fixed;
    } catch (error) {
        console.error("Error during JSON repair:", error);
        return jsonString;
    }
}

// 4. Add rotation statistics
let rotationStats = {
    totalCalls: 0,
    modelUsage: {},
    lastReset: Date.now(),
    successfulRotations: 0
};

export function getRotationStats() {
    return {
        ...rotationStats,
        averageCallsPerModel: rotationStats.totalCalls / Object.keys(rotationStats.modelUsage).length || 0,
        mostUsedModel: Object.keys(rotationStats.modelUsage).reduce((a, b) => 
            rotationStats.modelUsage[a] > rotationStats.modelUsage[b] ? a : b, 
            Object.keys(rotationStats.modelUsage)[0] || 'none'
        ),
        uptime: Date.now() - rotationStats.lastReset
    };
}

function trackModelUsage(modelName, success) {
    rotationStats.totalCalls++;
    rotationStats.modelUsage[modelName] = (rotationStats.modelUsage[modelName] || 0) + 1;
    if (success) {
        rotationStats.successfulRotations++;
    }
}

// 5. Add health check endpoint
export async function quickHealthCheck() {
    console.log('🏥 Quick Health Check');
    
    const workingModels = [];
    const failedModels = [];
    
    for (const model of availableModels.slice(0, 3)) { // Test first 3 models
        try {
            const response = await callAI('Health check', model, 1);
            workingModels.push(model);
            console.log(`✅ ${model}: Working`);
        } catch (error) {
            failedModels.push({ model, error: error.message });
            console.log(`❌ ${model}: ${error.message}`);
        }
    }
    
    return {
        working: workingModels,
        failed: failedModels,
        rotationViable: workingModels.length >= 2
    };
}

// 6. Production-ready rotation logging
export function enableRotationLogging() {
    const originalCallAI = callAI;
    
    return async function loggingCallAI(prompt, requestedModel, maxRetries) {
        const startTime = Date.now();
        const statusBefore = getModelStatus();
        
        try {
            const response = await originalCallAI(prompt, requestedModel, maxRetries);
            const statusAfter = getModelStatus();
            
            // Log rotation info
            console.log(`📊 Rotation: ${statusBefore.currentPreferredModel} → ${statusAfter.currentPreferredModel}`);
            trackModelUsage(statusBefore.currentPreferredModel, true);
            
            return response;
        } catch (error) {
            trackModelUsage(statusBefore.currentPreferredModel, false);
            throw error;
        }
    };
}

// 7. Test all models quickly
export async function testAllModelsQuick() {
    console.log('🚀 Quick All-Models Test');
    
    const results = [];
    
    for (const model of availableModels) {
        try {
            const start = Date.now();
            await callAI('Quick test', model, 1);
            const time = Date.now() - start;
            
            results.push({ model, status: 'working', time });
            console.log(`✅ ${model}: ${time}ms`);
        } catch (error) {
            results.push({ model, status: 'failed', error: error.message });
            console.log(`❌ ${model}: ${error.message}`);
        }
    }
    
    return results;
}

// Declare the voice pool and rotation index
const availableVoices = ['alloy', 'echo', 'fable', 'onyx', 'nova', 'shimmer'];
let currentVoiceIndex = 0;

/**
 * Returns the next voice in rotation
 */
function getNextVoice() {
    const voice = availableVoices[currentVoiceIndex];
    currentVoiceIndex = (currentVoiceIndex + 1) % availableVoices.length;
    console.log(`🔊 Using TTS voice: ${voice}`);
    return voice;
}

function cleanTextForTTS(text) {
    let cleanText = text
      // Remove all emoji characters (comprehensive Unicode ranges)
      .replace(/[\u{1F600}-\u{1F64F}]/gu, '') // Emoticons
      .replace(/[\u{1F300}-\u{1F5FF}]/gu, '') // Misc Symbols and Pictographs
      .replace(/[\u{1F680}-\u{1F6FF}]/gu, '') // Transport and Map
      .replace(/[\u{1F1E0}-\u{1F1FF}]/gu, '') // Regional indicators
      .replace(/[\u{2600}-\u{26FF}]/gu, '')   // Misc symbols
      .replace(/[\u{2700}-\u{27BF}]/gu, '')   // Dingbats
      .replace(/[\u{1F900}-\u{1F9FF}]/gu, '') // Supplemental Symbols and Pictographs
      .replace(/[\u{1F018}-\u{1F270}]/gu, '') // Various symbols
      // Remove any remaining problematic Unicode characters
      .replace(/[\uDC00-\uDFFF]/g, '')        // Remove surrogates
      .replace(/[\uFFF0-\uFFFF]/g, '')        // Remove specials
      // Clean up extra spaces and normalize
      .replace(/\s+/g, ' ')
      .trim()
    
    // Additional fallback: if still has problematic characters, use ASCII only
    if (!/^[\x00-\x7F]*$/.test(cleanText)) {
      cleanText = cleanText.replace(/[^\x00-\x7F]/g, '')
    }
    
    return cleanText
  }

/**
 * Generate TTS Audio using OpenAI, cycling through voices
 * @param {string} text - Text to convert to speech
 * @returns {Promise<object>} - Result with audioBuffer or error
 */
export async function generateTTSAudio(text, voice1 = null) {
    const startTime = Date.now();
    text = cleanTextForTTS(text);
    const voice = getNextVoice();

    try {
        const response = await axios.post("https://api.openai.com/v1/audio/speech", {
            model: "tts-1",
            input: text,
            voice: voice,
            response_format: "mp3",
            speed: 0.9
        }, {
            headers: {
                'Authorization': `Bearer ${getApiKey('openai')}`,
                'Content-Type': 'application/json'
            },
            responseType: 'arraybuffer'
        });

        // Track successful TTS usage
        const trackingData = await usageTracker.trackRequest({
            modelName: 'tts-1',
            category: USAGE_CATEGORIES.TTS_GENERATION,
            prompt: text,
            response: `Audio generated (${response.data.byteLength} bytes)`,
            success: true
        });

        console.log(`🔊 TTS Cost: ${trackingData.cost.toFixed(4)}, Voice: ${voice}`);

        return {
            success: true,
            audioBuffer: response.data,
            voiceUsed: voice
        };

    } catch (error) {
        // Track failed TTS usage
        await usageTracker.trackRequest({
            modelName: 'tts-1',
            category: USAGE_CATEGORIES.TTS_GENERATION,
            prompt: text,
            response: '',
            success: false
        });

        console.error(`OpenAI TTS error (voice: ${voice}):`, error);
        return {
            success: false,
            error: error.message,
            voiceUsed: voice
        };
    }
}


/**
 * Call AI with automatic model rotation for puzzle generation
 * @param {string} prompt - The prompt to send
 * @param {string} preferredModel - Preferred model (will fallback to others)
 * @returns {Promise<string>} - AI response
 */
export async function callAIWithRotation(prompt, preferredModel = null) {
    try {
        return await callAI(prompt, preferredModel);
    } catch (error) {
        console.error(`❌ AI call with rotation failed:`, error.message);
        throw error;
    }
}

/**
 * Get current model status and rotation info
 * @returns {object} - Model status information
 */
export function getModelStatus() {
    return {
        availableModels: [...availableModels],
        currentModelIndex,
        currentPreferredModel: availableModels[currentModelIndex],
        totalModels: availableModels.length
    };
}

/**
 * Manually set preferred model for rotation
 * @param {string} modelName - Model to prefer
 */
export function setPreferredModel(modelName) {
    const index = availableModels.indexOf(modelName);
    if (index !== -1) {
        currentModelIndex = index;
        console.log(`🎯 Manually set preferred model to: ${modelName}`);
    } else {
        console.warn(`⚠️ Model ${modelName} not found in available models`);
    }
}

/**
 * Validate that the AI response is reasonable for puzzle generation
 * @param {string} response - AI response to validate
 * @param {string} expectedType - Expected response type ('json', 'text', etc.)
 * @returns {boolean} - Whether the response is valid
 */
export function validateAIResponse(response, expectedType = 'json') {
    if (!response || typeof response !== 'string') {
        return false;
    }
    
    if (expectedType === 'json') {
        try {
            const parsed = JSON.parse(response);
            return typeof parsed === 'object' && parsed !== null;
        } catch {
            return false;
        }
    }
    
    // For text responses, just check it's not empty and reasonable length
    return response.trim().length > 0 && response.length < 10000;
}

/**
 * Call AI with retry logic for better reliability
 * @param {string} prompt - The prompt to send
 * @param {string} modelName - The model to use
 * @param {number} maxRetries - Maximum number of retries
 * @returns {Promise<string>} - AI response
 */
export async function callAIWithRetry(prompt, modelName = 'gpt-3.5-turbo', maxRetries = 3) {
    let lastError;
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            console.log(`🔄 AI call attempt ${attempt}/${maxRetries}`);
            const response = await callAI(prompt, modelName);
            
            if (validateAIResponse(response)) {
                console.log(`✅ AI call succeeded on attempt ${attempt}`);
                return response;
            } else {
                console.warn(`⚠️ Invalid response on attempt ${attempt}`);
                lastError = new Error('Invalid AI response format');
            }
        } catch (error) {
            console.error(`❌ AI call failed on attempt ${attempt}:`, error.message);
            lastError = error;
            
            // Don't retry on authentication errors
            if (error.message.includes('Authentication failed')) {
                throw error;
            }
            
            // Wait before retrying (exponential backoff)
            if (attempt < maxRetries) {
                const delay = Math.min(1000 * Math.pow(2, attempt - 1), 5000);
                console.log(`⏳ Waiting ${delay}ms before retry...`);
                await new Promise(resolve => setTimeout(resolve, delay));
            }
        }
    }
    
    throw new Error(`AI call failed after ${maxRetries} attempts. Last error: ${lastError.message}`);
}

/**
 * Get available AI models in rotation order
 * @returns {Array<string>} - List of available model names
 */
export function getAvailableModels() {
    return [...availableModels];
}

/**
 * Check if a model is available
 * @param {string} modelName - Model name to check
 * @returns {boolean} - Whether the model is available
 */
export function isModelAvailable(modelName) {
    return availableModels.includes(modelName);
}
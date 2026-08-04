import axios from "axios";
import crypto from "crypto";
import { pipeline, env } from "@xenova/transformers";
import cosineSimilarity from "cosine-similarity";
import fs from 'fs'; // classic, e.g., for fs.createReadStream()
import { promises as fsPromises } from 'fs'; // async/await functions
import { callAI } from "../utils/aiClient.js";
import { fileURLToPath } from "url";
import path from "path";
import { Storage } from "@google-cloud/storage";
import { PuzzleGenerator } from '../services/puzzleGeneration.js';
import { type } from "os";
import { supabase } from "../config/database.js";
import {admin, db} from "../config/firebaseAdmin.js";
import { generateTTSAudio } from '../utils/aiClient.js';
import { deduplicationService } from './deduplicationService.js';
import { wordSearchService } from './wordSearchGeneratorService.js';
import { imagePuzzleGenerator } from './imagePuzzleGenerator.js';
import {musicPuzzleGenerator} from './musicPuzzleGenerator.js';
import {USAGE_CATEGORIES} from '../utils/usageTracker.js';
import { userLimitService } from "./userLimitService.js";
import { enhancedWordSnakeGenerator } from './snakeWordSearch.js';
import { ProgressivePuzzleSystem } from './progressivePuzzleSystem.js';
import { notificationService } from './notificationService.js';
import { maskEmail } from './puzzleLeaderboard.js';




// Load GCS service account from environment variable
function getGCSServiceAccountKey() {
    const gcsServiceAccountJson = process.env.GCS_SERVICE_ACCOUNT_KEY;
    if (!gcsServiceAccountJson) {
        console.warn('Missing GCS_SERVICE_ACCOUNT_KEY - GCS storage will not work');
        return null;
    }
    try {
        return JSON.parse(gcsServiceAccountJson);
    } catch (error) {
        console.error('Invalid GCS_SERVICE_ACCOUNT_KEY - must be valid JSON');
        return null;
    }
}

const gcsServiceAccountKey = getGCSServiceAccountKey();

const PUZZLE_AUTO_GENERATION_CONFIG = {
// Puzzle types that support auto-generation instead of reset
whitelistedTypes: new Set([
    'anagram',
    'wordsearch',
    'math',
    'trivia',
    'crossword',
    'wordsnake',
    'crypto',
    'memorystory',
    'synonyms',
    'antonyms',
    'sentencetransitions'
]),

// Unified threshold - generate when remaining puzzles drop below this
threshold: 10,

// Unified batch size - how many puzzles to generate per batch
batchSize: 20
};

// Track active auto-generation jobs
const autoGenerationJobs = new Map();

const BUCKET_NAME = "aipuzzles";

export const OPTION_CONSTRAINTS = {
    maxLength: 50,      // Maximum characters per option
    maxWords: 8,        // Maximum words per option
    preferredLength: 35 // Preferred target length
};

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const DICTIONARY_FILE = "words.txt";

// Initialize GCS Storage only if credentials are available
export const storage = gcsServiceAccountKey
    ? new Storage({
        credentials: gcsServiceAccountKey,
        projectId: gcsServiceAccountKey.project_id,
    })
    : null;

const debugTracer = {
    addTrace: (step, type, data = {}) => {
        if (process.env.NODE_ENV === 'development' || process.env.DEBUG_PUZZLES === 'true') {
            const timestamp = new Date().toISOString();
            console.log(`[DEBUG] ${timestamp} [${step}] ${type}:`, data);
        }
    }
};

/**
 * Generates a SHA256 hash of the question for exact deduplication.
 */
function hashQuestion(question) {
    debugTracer.addTrace('hashQuestion', 'START', { questionLength: question?.length });
    
    try {
        const hash = crypto.createHash("sha256").update(question.toLowerCase().trim()).digest("hex");
        debugTracer.addTrace('hashQuestion', 'SUCCESS', { hash: hash.substring(0, 16) + '...' });
        return hash;
    } catch (error) {
        debugTracer.addTrace('hashQuestion', 'ERROR', { error: error.message });
        throw error;
    }
}

let embedder = null;

const LOCAL_MODEL_PATH = './model-cache/Xenova/all-MiniLM-L6-v2';

async function ensureModelDownloaded() {
    debugTracer.addTrace('ensureModelDownloaded', 'START', { path: LOCAL_MODEL_PATH });
    
    const localPath = path.resolve(LOCAL_MODEL_PATH);
    try {
        await fsPromises.access(localPath);
        debugTracer.addTrace('ensureModelDownloaded', 'MODEL_FOUND', { localPath });
        console.log('✅ Model found at:', localPath);
    } catch (error) {
        debugTracer.addTrace('ensureModelDownloaded', 'MODEL_MISSING', { localPath, error: error.message });
        console.error('❌ Model missing at', localPath);
        throw new Error('Model files not found locally. Please ensure they are bundled in the container.');
    }
}

export async function loadModel() {
    debugTracer.addTrace('loadModel', 'START', { embedderExists: !!embedder });
    
    if (!embedder) {
        try {
            await ensureModelDownloaded();

            process.env.TRANSFORMERS_CACHE = path.resolve('./model-cache');

            embedder = await pipeline('feature-extraction', 'Xenova/all-MiniLM-L6-v2', {
                quantized: true,
                local_files_only: true
            });

            debugTracer.addTrace('loadModel', 'MODEL_LOADED', { cacheDir: process.env.TRANSFORMERS_CACHE });
            console.log('🧠 Model loaded successfully from local cache');
        } catch (error) {
            debugTracer.addTrace('loadModel', 'LOAD_ERROR', { error: error.message });
            throw error;
        }
    }

    debugTracer.addTrace('loadModel', 'COMPLETE', { embedderReady: !!embedder });
    return embedder;
}

let generator = null;

function getGenerator() {
    debugTracer.addTrace('getGenerator', 'START', { generatorExists: !!generator });
    
    if (!generator) {
        console.log('🔧 Initializing PuzzleGenerator...');
        generator = new PuzzleGenerator();
        
        // ✅ Set the storage function immediately
        generator.setStorageFunction(storePuzzleInSupabase);
        
        debugTracer.addTrace('getGenerator', 'INITIALIZED', { storageSet: true });
        console.log('✅ PuzzleGenerator initialized successfully');
    }
    
    debugTracer.addTrace('getGenerator', 'COMPLETE', { generatorReady: !!generator });
    return generator;
}

export async function generateEmbedding(text) {
    debugTracer.addTrace('generateEmbedding', 'START', { textLength: text?.length });
    
    try {
        if (!embedder) await loadModel();
        const output = await embedder(text, { pooling: 'mean' });
        
        debugTracer.addTrace('generateEmbedding', 'SUCCESS', { 
            outputDimensions: output.data.length,
            sampleValues: output.data.slice(0, 3)
        });
        
        return output.data;  // Should always return 768-dimension vector
    } catch (error) {
        debugTracer.addTrace('generateEmbedding', 'ERROR', { error: error.message });
        throw error;
    }
}

export function adjustEmbeddingSize(embedding) {
    debugTracer.addTrace('adjustEmbeddingSize', 'START', { originalLength: embedding?.length });
    
    if (embedding.length === 384) {
        const adjusted = [...embedding, ...embedding]; // Duplicate to 768 dimensions
        debugTracer.addTrace('adjustEmbeddingSize', 'ADJUSTED', { 
            from: 384, 
            to: adjusted.length 
        });
        return adjusted;
    }
    
    debugTracer.addTrace('adjustEmbeddingSize', 'NO_CHANGE', { length: embedding.length });
    return embedding;
}

async function getExistingQuestionsFromPrefix(puzzleType) {
    debugTracer.addTrace('getExistingQuestionsFromPrefix', 'START', { puzzleType });
    
    console.log(`📌 Fetching existing questions of type: ${puzzleType}`);

    try {
        // Query Supabase for puzzles of the specified type
        const { data: puzzles, error } = await supabase
            .from('puzzles')
            .select('question')
            .eq('type', puzzleType);

        if (error) {
            debugTracer.addTrace('getExistingQuestionsFromPrefix', 'SUPABASE_ERROR', { 
                error: error.message 
            });
            console.error(`❌ Error fetching questions:`, error.message);
            return [];
        }

        const questions = puzzles.map(puzzle => puzzle.question).filter(Boolean);
        
        debugTracer.addTrace('getExistingQuestionsFromPrefix', 'SUCCESS', { 
            questionCount: questions.length,
            sampleQuestions: questions.slice(0, 2)
        });
        
        console.log(`📌 Loaded ${questions.length} existing questions from Supabase.`);
        return questions;

    } catch (error) {
        debugTracer.addTrace('getExistingQuestionsFromPrefix', 'ERROR', { error: error.message });
        console.error(`⚠️ Error processing questions:`, error.message);
        return [];
    }
}

export async function getExistingQuestionsTopic(topic) {
    debugTracer.addTrace('getExistingQuestionsTopic', 'START', { topic });
    
    const result = await getExistingQuestionsFromPrefix(topic);
    
    debugTracer.addTrace('getExistingQuestionsTopic', 'COMPLETE', { 
        resultCount: result.length 
    });
    
    return result;
}

export async function getExistingQuestionsFromSupabase(topic) {
    debugTracer.addTrace('getExistingQuestionsFromSupabase', 'START', { topic });
    
    console.log(`📌 Fetching existing daily questions for topic: ${topic}`);

    try {
        const { data: dailyPuzzles, error } = await supabase
            .from('daily_puzzles')
            .select('puzzle_set')
            .eq('topic', topic)
            .gte('generation_date', new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]); // Last 30 days

        if (error) {
            debugTracer.addTrace('getExistingQuestionsFromSupabase', 'SUPABASE_ERROR', { 
                error: error.message 
            });
            console.error(`❌ Error fetching daily questions:`, error.message);
            return [];
        }

        const questions = [];
        dailyPuzzles.forEach(entry => {
            const puzzleSet = entry.puzzle_set;
            if (puzzleSet && Array.isArray(puzzleSet)) {
                puzzleSet.forEach(puzzle => {
                    if (puzzle.question) {
                        questions.push(puzzle.question);
                    }
                });
            }
        });

        debugTracer.addTrace('getExistingQuestionsFromSupabase', 'SUCCESS', { 
            dailyPuzzleEntries: dailyPuzzles.length,
            extractedQuestions: questions.length,
            sampleQuestions: questions.slice(0, 2)
        });

        console.log(`📌 Loaded ${questions.length} existing questions from last 30 days.`);
        return questions;

    } catch (error) {
        debugTracer.addTrace('getExistingQuestionsFromSupabase', 'ERROR', { error: error.message });
        console.error(`⚠️ Error processing daily questions:`, error.message);
        return [];
    }
}

export async function isDuplicateQuestion(puzzleType, question) {
    debugTracer.addTrace('isDuplicateQuestion', 'START', { 
        puzzleType, 
        questionPreview: question.substring(0, 50) + '...' 
    });
    
    console.log(`📌 Enhanced duplicate check for ${puzzleType}: ${question.substring(0, 50)}...`);
    
    try {
        const isDuplicate = await deduplicationService.isDuplicate(puzzleType, question);
        
        debugTracer.addTrace('isDuplicateQuestion', 'DEDUPLICATION_RESULT', { 
            isDuplicate,
            service: 'deduplicationService'
        });
        
        console.log(`✅ Deduplication service result: ${isDuplicate ? 'DUPLICATE' : 'UNIQUE'}`);
        return isDuplicate;
    } catch (error) {
        debugTracer.addTrace('isDuplicateQuestion', 'ERROR', { error: error.message });
        console.error(`❌ Deduplication service error: ${error.message}`);
        // Return false on error to allow puzzle generation to continue
        return false;
    }
}

export async function storePuzzle(puzzleType, question, answer, hint, difficulty, modelName, validationModel) {
    debugTracer.addTrace('storePuzzle', 'START', { 
        puzzleType, 
        questionLength: question?.length,
        difficulty,
        modelName
    });
    
    console.log("📌 Storing new puzzle in Supabase...");

    const puzzleId = crypto.randomUUID();
    const questionHash = hashQuestion(question);
    difficulty = difficulty.toLowerCase();

    debugTracer.addTrace('storePuzzle', 'PUZZLE_PREPARED', { 
        puzzleId, 
        questionHash: questionHash.substring(0, 16) + '...',
        difficulty
    });

    try {
        const { error } = await supabase
            .from('puzzles')
            .insert([{
                puzzleid: puzzleId,
                type: puzzleType,
                question,
                answer,
                hint,
                difficulty,
                options: [], // No options in this path → insert empty array
                timestamp: new Date().toISOString(),
                source: modelName
            }]);

        if (error) {
            debugTracer.addTrace('storePuzzle', 'SUPABASE_ERROR', { error: error.message });
            console.error(`❌ Error inserting puzzle into Supabase:`, error.message);
            return { success: false, message: error.message };
        }

        debugTracer.addTrace('storePuzzle', 'SUCCESS', { puzzleId });
        console.log(`✅ Unique puzzle stored successfully in Supabase with puzzleId=${puzzleId}`);
        return { success: true, puzzleId };

    } catch (err) {
        debugTracer.addTrace('storePuzzle', 'ERROR', { error: err.message });
        console.error("❌ Error in storePuzzle:", err);
        return { success: false, message: "Unexpected error storing puzzle" };
    }
}

export async function uploadAudioToSupabase(audioBuffer, puzzleId, maxRetries = 3) {
    debugTracer.addTrace('uploadAudioToSupabase', 'START', { 
        puzzleId,
        maxRetries,
        bufferSize: audioBuffer?.byteLength || audioBuffer?.length
    });
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        debugTracer.addTrace('uploadAudioToSupabase', 'ATTEMPT', { 
            attempt, 
            maxRetries 
        });
        
        try {
            console.log(`📄 Upload attempt ${attempt}/${maxRetries} for ${puzzleId}`);
            
            if (!audioBuffer || !puzzleId) {
                throw new Error(`Invalid parameters: audioBuffer=${!!audioBuffer}, puzzleId=${puzzleId}`);
            }

            // Convert to Blob if it's ArrayBuffer (more reliable for uploads)
            let uploadBlob;
            if (audioBuffer instanceof ArrayBuffer) {
                uploadBlob = new Blob([audioBuffer], { type: 'audio/mpeg' });
            } else if (audioBuffer instanceof Uint8Array) {
                uploadBlob = new Blob([audioBuffer.buffer], { type: 'audio/mpeg' });
            } else {
                uploadBlob = audioBuffer; // Assume it's already a Blob
            }

            debugTracer.addTrace('uploadAudioToSupabase', 'BLOB_PREPARED', { 
                blobType: uploadBlob.type,
                blobSize: uploadBlob.size
            });

            // ✅ FIX: Remove .mp3 extension to match your storage format
            const fileName = puzzleId; // Changed from `${puzzleId}.mp3` to just puzzleId
            
            // Add timeout wrapper
            const uploadPromise = supabase.storage
                .from('puzzle-audio')
                .upload(fileName, uploadBlob, {
                    contentType: 'audio/mpeg',
                    upsert: true
                });

            // Wrap in timeout (30 seconds)
            const timeoutPromise = new Promise((_, reject) => 
                setTimeout(() => reject(new Error('Upload timeout')), 30000)
            );

            const { data, error } = await Promise.race([uploadPromise, timeoutPromise]);

            if (error) throw error;
            
            debugTracer.addTrace('uploadAudioToSupabase', 'SUCCESS', { 
                attempt,
                data: data?.path
            });
            
            console.log(`✅ Upload successful on attempt ${attempt}:`, data);
            return true;
            
        } catch (error) {
            debugTracer.addTrace('uploadAudioToSupabase', 'ATTEMPT_FAILED', { 
                attempt,
                error: error.message
            });
            
            console.error(`❌ Upload attempt ${attempt} failed:`, error.message);
            
            if (attempt === maxRetries) {
                debugTracer.addTrace('uploadAudioToSupabase', 'ALL_ATTEMPTS_FAILED', {});
                console.error('💥 All upload attempts failed');
                return false;
            }
            
            // Wait before retry (exponential backoff)
            const delay = Math.min(1000 * Math.pow(2, attempt - 1), 5000);
            debugTracer.addTrace('uploadAudioToSupabase', 'WAITING_RETRY', { delay });
            console.log(`⏳ Waiting ${delay}ms before retry...`);
            await new Promise(resolve => setTimeout(resolve, delay));
        }
    }
    
    return false;
}

export async function storePuzzleInSupabase({
    puzzleType,
    question,
    answer,
    hint,
    difficulty,
    options,
    modelName,
    validationModel
}) {
    debugTracer.addTrace('storePuzzleInSupabase', 'START', { 
        puzzleType,
        difficulty,
        questionType: typeof question,
        answerType: typeof answer,
        optionsCount: options?.length,
        modelName
    });
    
    console.log("📌 Storing puzzle with trigger handling paths...");
    difficulty = difficulty.toLowerCase();

    const newPuzzleId = crypto.randomUUID();
    debugTracer.addTrace('storePuzzleInSupabase', 'PUZZLE_ID_GENERATED', { 
        newPuzzleId 
    });
    console.log(`🆔 Generated new puzzle ID: ${newPuzzleId}`);

    // Keep all existing audio generation logic
    let audioGenerated = false;
    if (puzzleType === 'memorystory') {
        debugTracer.addTrace('storePuzzleInSupabase', 'PROCESSING_MEMORY_STORY', {});
        
        try {
            const questionData = JSON.parse(question);
            const storyCard = questionData.storyCard;
            
            if (storyCard) {
                debugTracer.addTrace('storePuzzleInSupabase', 'GENERATING_TTS', { puzzleId: newPuzzleId });
                console.log(`📊 Generating TTS audio for puzzle: ${newPuzzleId}`);
                
                const ttsResult = await generateTTSAudio(storyCard, 'nova');
                
                if (ttsResult.success) {
                    audioGenerated = await uploadAudioToSupabase(ttsResult.audioBuffer, newPuzzleId);
                    debugTracer.addTrace('storePuzzleInSupabase', 'TTS_RESULT', { 
                        audioGenerated 
                    });
                    console.log(`${audioGenerated ? '✅' : '❌'} TTS audio ${audioGenerated ? 'uploaded' : 'upload failed'}`);
                } else {
                    debugTracer.addTrace('storePuzzleInSupabase', 'TTS_FAILED', { 
                        error: ttsResult.error 
                    });
                    console.warn(`⚠️ TTS generation failed: ${ttsResult.error}`);
                }
            }
        } catch (parseError) {
            debugTracer.addTrace('storePuzzleInSupabase', 'TTS_PARSE_ERROR', { 
                error: parseError.message 
            });
            console.warn(`⚠️ Could not parse question for TTS: ${parseError.message}`);
        }
    } 

    if (puzzleType === 'memorysequencing') {
        debugTracer.addTrace('storePuzzleInSupabase', 'PROCESSING_MEMORY_SEQUENCING', {});
        
        try {
            const questionData = JSON.parse(question);
            const partOneAudioScript = questionData.partOneAudioScript;
            const partTwoAudioScript = questionData.partTwoAudioScript;
            
            if (partOneAudioScript && partTwoAudioScript) {
                console.log(`📊 Generating TTS audio for sequencing puzzle: ${newPuzzleId}`);
                
                const part1TTSResult = await generateTTSAudio(partOneAudioScript, 'nova');
                let part1AudioGenerated = false;
                
                if (part1TTSResult.success) {
                    part1AudioGenerated = await uploadAudioToSupabase(part1TTSResult.audioBuffer, `${newPuzzleId}_part1`);
                    debugTracer.addTrace('storePuzzleInSupabase', 'PART1_TTS', { 
                        part1AudioGenerated 
                    });
                    console.log(`${part1AudioGenerated ? '✅' : '❌'} Part 1 TTS audio ${part1AudioGenerated ? 'uploaded' : 'upload failed'}`);
                } else {
                    console.warn(`⚠️ Part 1 TTS generation failed: ${part1TTSResult.error}`);
                }
                
                const part2TTSResult = await generateTTSAudio(partTwoAudioScript, 'nova');
                let part2AudioGenerated = false;
                
                if (part2TTSResult.success) {
                    part2AudioGenerated = await uploadAudioToSupabase(part2TTSResult.audioBuffer, `${newPuzzleId}_part2`);
                    debugTracer.addTrace('storePuzzleInSupabase', 'PART2_TTS', { 
                        part2AudioGenerated 
                    });
                    console.log(`${part2AudioGenerated ? '✅' : '❌'} Part 2 TTS audio ${part2AudioGenerated ? 'uploaded' : 'upload failed'}`);
                } else {
                    console.warn(`⚠️ Part 2 TTS generation failed: ${part2TTSResult.error}`);
                }
                
                audioGenerated = part1AudioGenerated || part2AudioGenerated;
                
                if (part1AudioGenerated && part2AudioGenerated) {
                    try {
                        const updatedQuestionData = {
                            ...questionData,
                            partOneAudioUrl: `${supabaseUrl}/storage/v1/object/public/puzzle-audio/${newPuzzleId}_part1`,
                            partTwoAudioUrl: `${supabaseUrl}/storage/v1/object/public/puzzle-audio/${newPuzzleId}_part2`
                        };
                        
                        question = JSON.stringify(updatedQuestionData);
                        debugTracer.addTrace('storePuzzleInSupabase', 'AUDIO_URLS_ADDED', {});
                        console.log(`✅ Updated sequencing puzzle with audio URLs`);
                    } catch (updateError) {
                        console.warn(`⚠️ Failed to update puzzle with audio URLs: ${updateError.message}`);
                    }
                }
            }
        } catch (parseError) {
            debugTracer.addTrace('storePuzzleInSupabase', 'SEQUENCING_PARSE_ERROR', { 
                error: parseError.message 
            });
            console.warn(`⚠️ Could not parse sequencing question for TTS: ${parseError.message}`);
        }
    }

    if (puzzleType === 'memoryretention') {
        debugTracer.addTrace('storePuzzleInSupabase', 'PROCESSING_MEMORY_RETENTION', {});
        
        try {
            const questionData = JSON.parse(question);
            const essay = questionData.essay;
            
            if (essay) {
                console.log(`📊 Generating TTS audio for memory retention puzzle: ${newPuzzleId}`);
                
                const ttsResult = await generateTTSAudio(essay, 'nova');
                
                if (ttsResult.success) {
                    audioGenerated = await uploadAudioToSupabase(ttsResult.audioBuffer, newPuzzleId);
                    debugTracer.addTrace('storePuzzleInSupabase', 'MEMORY_RETENTION_TTS', { 
                        audioGenerated 
                    });
                    console.log(`${audioGenerated ? '✅' : '❌'} Memory retention TTS audio ${audioGenerated ? 'uploaded' : 'upload failed'}`);
                    
                    if (audioGenerated) {
                        try {
                            const updatedQuestionData = {
                                ...questionData,
                                audioUrl: `${supabaseUrl}/storage/v1/object/public/puzzle-audio/${newPuzzleId}`
                            };
                            
                            question = JSON.stringify(updatedQuestionData);
                            console.log(`✅ Audio URL prepared for memory retention puzzle`);
                        } catch (updateError) {
                            console.warn(`⚠️ Failed to prepare audio URL for memory retention: ${updateError.message}`);
                        }
                    }
                } else {
                    console.warn(`⚠️ Memory retention TTS generation failed: ${ttsResult.error}`);
                }
            }
        } catch (parseError) {
            console.warn(`⚠️ Could not parse memory retention question for TTS: ${parseError.message}`);
        }
    }

    try {
        // Direct insertion - trigger handles path automatically
        debugTracer.addTrace('storePuzzleInSupabase', 'INSERTING_PUZZLE', { 
            puzzleType,
            difficulty,
            newPuzzleId
        });
        
        const { data: puzzleData, error: puzzleError } = await supabase
            .from('puzzles')
            .insert([{
                puzzleid: newPuzzleId,
                type: puzzleType,
                question,
                answer,
                hint: hint || '',
                difficulty,
                options: options || [],
                timestamp: new Date().toISOString(),
                source: modelName,
                parentSetId: null  // Ensure trigger processes it
            }])
            .select();

        if (puzzleError) {
            debugTracer.addTrace('storePuzzleInSupabase', 'INSERT_ERROR', { 
                error: puzzleError.message 
            });
            throw new Error(`Puzzle insertion failed: ${puzzleError.message}`);
        }

        debugTracer.addTrace('storePuzzleInSupabase', 'SUCCESS', { 
            newPuzzleId,
            audioGenerated
        });
        console.log(`✅ Puzzle stored successfully: ${newPuzzleId} (trigger handling path)`);
        
        return { 
            success: true, 
            puzzleId: newPuzzleId,
            pathStatus: 'trigger_managed',
            message: 'Puzzle stored with automatic path management'
        };

    } catch (error) {
        debugTracer.addTrace('storePuzzleInSupabase', 'ERROR', { 
            error: error.message 
        });
        console.error(`❌ Puzzle storage failed: ${error.message}`);
        return { 
            success: false, 
            message: error.message,
            puzzleId: newPuzzleId,
            error: error.message
        };
    }
}

// Monitor path insertion failures
let pathFailureCount = 0;
let pathFailureLog = [];

export function logPathFailure(puzzleId, puzzleType, difficulty, error) {
    debugTracer.addTrace('logPathFailure', 'LOG_FAILURE', { 
        puzzleId, 
        puzzleType, 
        difficulty,
        error: error?.message || error,
        totalFailures: pathFailureCount + 1
    });
    
    pathFailureCount++;
    pathFailureLog.push({
        puzzleId,
        puzzleType,
        difficulty,
        error,
        timestamp: new Date().toISOString()
    });
    
    // Keep only last 100 failures
    if (pathFailureLog.length > 100) {
        pathFailureLog = pathFailureLog.slice(-100);
    }
    
    // Alert if failure rate is high
    if (pathFailureCount % 10 === 0) {
        console.warn(`⚠️ Path failure alert: ${pathFailureCount} total failures`);
        console.log(`Recent failures:`, pathFailureLog.slice(-5));
    }
}

function constructAudioUrl(puzzleId) {
    debugTracer.addTrace('constructAudioUrl', 'CONSTRUCT', { puzzleId });
    return `https://uujjodxicvifmiwlimob.supabase.co/storage/v1/object/public/puzzle-audio/${puzzleId}`;
}

function addAudioUrlToMemoryPuzzle(puzzleData) {
    debugTracer.addTrace('addAudioUrlToMemoryPuzzle', 'START', { 
        puzzleType: puzzleData.type,
        puzzleId: puzzleData.puzzleid
    });
    
    if (puzzleData.type === 'memorystory') {
        try {
            // Parse the question JSON
            const questionData = JSON.parse(puzzleData.question);
            
            // Add audio URL to the question data
            questionData.audioUrl = constructAudioUrl(puzzleData.puzzleid);
            
            // Update the question field with audio URL included
            puzzleData.question = JSON.stringify(questionData);
            
            debugTracer.addTrace('addAudioUrlToMemoryPuzzle', 'MEMORY_STORY_UPDATED', { 
                audioUrl: questionData.audioUrl 
            });
            console.log(`📊 Added audio URL for memory story: ${questionData.audioUrl}`);
        } catch (error) {
            debugTracer.addTrace('addAudioUrlToMemoryPuzzle', 'PARSE_ERROR', { 
                error: error.message 
            });
            console.warn(`⚠️ Could not add audio URL to memory story: ${error.message}`);
        }
    } else if (puzzleData.type === 'memorysequencing') {
        try {
            // Parse the question JSON
            const questionData = JSON.parse(puzzleData.question);
            
            // Add audio URLs for both parts to the question data
            questionData.partOneAudioUrl = constructAudioUrl(`${puzzleData.puzzleid}_part1`);
            questionData.partTwoAudioUrl = constructAudioUrl(`${puzzleData.puzzleid}_part2`);
            
            // Update the question field with audio URLs included
            puzzleData.question = JSON.stringify(questionData);
            
            debugTracer.addTrace('addAudioUrlToMemoryPuzzle', 'SEQUENCING_UPDATED', { 
                partOneUrl: questionData.partOneAudioUrl,
                partTwoUrl: questionData.partTwoAudioUrl
            });
            console.log(`📊 Added audio URLs for sequencing puzzle - Part 1: ${questionData.partOneAudioUrl}, Part 2: ${questionData.partTwoAudioUrl}`);
        } catch (error) {
            console.warn(`⚠️ Could not add audio URLs to sequencing puzzle: ${error.message}`);
        }
    } else if (puzzleData.type === 'memoryretention') {
        try {
            const questionData = JSON.parse(puzzleData.question);
            
            // Add audio URL to the question data
            questionData.audioUrl = constructAudioUrl(puzzleData.puzzleid);
            
            // Update the question field with audio URL included
            puzzleData.question = JSON.stringify(questionData);
            
            debugTracer.addTrace('addAudioUrlToMemoryPuzzle', 'RETENTION_UPDATED', { 
                audioUrl: questionData.audioUrl 
            });
            console.log(`📊 Added audio URL for memory retention: ${questionData.audioUrl}`);
        } catch (error) {
            console.warn(`⚠️ Could not add audio URL to memory retention: ${error.message}`);
        }
    }
    
    debugTracer.addTrace('addAudioUrlToMemoryPuzzle', 'COMPLETE', {});
    return puzzleData;
}

// Diagnostic function to understand path state
export async function diagnosePuzzlePathState(puzzleType, difficulty) {
    debugTracer.addTrace('diagnosePuzzlePathState', 'START', { 
        puzzleType, 
        difficulty 
    });
    
    console.log(`🔍 Diagnosing puzzle path state for ${puzzleType}/${difficulty}`);
    
    try {
        // Get all puzzles for this type/difficulty
        const { data: allPuzzles, error: puzzleError } = await supabase
            .from('puzzles')
            .select('puzzleid, timestamp')
            .eq('type', puzzleType.toLowerCase())
            .eq('difficulty', difficulty.toLowerCase())
            .order('timestamp', { ascending: true });

        if (puzzleError) {
            debugTracer.addTrace('diagnosePuzzlePathState', 'PUZZLE_QUERY_ERROR', { 
                error: puzzleError.message 
            });
            return { error: `Failed to query puzzles: ${puzzleError.message}` };
        }

        // Get all path entries for this type/difficulty
        const { data: pathEntries, error: pathError } = await supabase
            .from('puzzle_path')
            .select('*')
            .eq('type', puzzleType)
            .eq('difficulty', difficulty.toLowerCase());

        if (pathError) {
            debugTracer.addTrace('diagnosePuzzlePathState', 'PATH_QUERY_ERROR', { 
                error: pathError.message 
            });
            return { error: `Failed to query path: ${pathError.message}` };
        }

        const totalPuzzles = allPuzzles?.length || 0;
        const totalPathEntries = pathEntries?.length || 0;
        
        debugTracer.addTrace('diagnosePuzzlePathState', 'DATA_FETCHED', { 
            totalPuzzles, 
            totalPathEntries 
        });
        
        // Find issues
        const issues = [];
        let needsRepair = false;

        // Issue 1: Missing puzzles from path
        const puzzleIds = new Set(allPuzzles?.map(p => p.puzzleid) || []);
        const pathPuzzleIds = new Set(pathEntries?.map(p => p.puzzleid) || []);
        const missingFromPath = [...puzzleIds].filter(id => !pathPuzzleIds.has(id));
        
        if (missingFromPath.length > 0) {
            issues.push(`${missingFromPath.length} puzzles missing from path`);
            needsRepair = true;
        }

        // Issue 2: Orphaned path entries
        const orphanedPaths = [...pathPuzzleIds].filter(id => !puzzleIds.has(id));
        if (orphanedPaths.length > 0) {
            issues.push(`${orphanedPaths.length} orphaned path entries`);
            needsRepair = true;
        }

        // Issue 3: Multiple or no end nodes
        const endNodes = pathEntries?.filter(p => p.nextpuzzleid === null) || [];
        if (endNodes.length !== 1 && totalPathEntries > 0) {
            issues.push(`${endNodes.length} end nodes (should be 1)`);
            needsRepair = true;
        }

        // Issue 4: Broken chains
        const brokenChains = [];
        if (pathEntries) {
            for (const entry of pathEntries) {
                if (entry.nextpuzzleid && !pathPuzzleIds.has(entry.nextpuzzleid)) {
                    brokenChains.push(entry.puzzleid);
                }
            }
        }
        if (brokenChains.length > 0) {
            issues.push(`${brokenChains.length} broken chain links`);
            needsRepair = true;
        }

        const diagnosis = {
            totalPuzzles,
            totalPathEntries,
            missingFromPath: missingFromPath.length,
            orphanedPaths: orphanedPaths.length,
            endNodes: endNodes.length,
            brokenChains: brokenChains.length,
            issues,
            needsRepair,
            isHealthy: !needsRepair && totalPuzzles === totalPathEntries && totalPuzzles > 0
        };

        debugTracer.addTrace('diagnosePuzzlePathState', 'DIAGNOSIS_COMPLETE', { 
            isHealthy: diagnosis.isHealthy,
            issueCount: issues.length,
            needsRepair
        });

        return diagnosis;

    } catch (error) {
        debugTracer.addTrace('diagnosePuzzlePathState', 'ERROR', { error: error.message });
        return { error: `Diagnosis failed: ${error.message}` };
    }
}

export function truncateOption(option, maxLength = OPTION_CONSTRAINTS.maxLength) {
    debugTracer.addTrace('truncateOption', 'START', { 
        originalLength: option?.length,
        maxLength
    });
    
    if (!option || typeof option !== 'string') return option;
    
    const trimmed = option.trim();
    
    // If it's already short enough, return as-is
    if (trimmed.length <= maxLength) {
        debugTracer.addTrace('truncateOption', 'NO_TRUNCATION_NEEDED', {});
        return trimmed;
    }
    
    // Try to truncate at word boundary
    const words = trimmed.split(' ');
    let truncated = '';
    
    for (const word of words) {
        const testLength = truncated ? truncated.length + 1 + word.length : word.length;
        if (testLength <= maxLength - 3) { // Leave room for "..."
            truncated += truncated ? ' ' + word : word;
        } else {
            break;
        }
    }
    
    // If we couldn't fit any complete words, just cut at character limit
    if (!truncated) {
        truncated = trimmed.substring(0, maxLength - 3);
    }
    
    const result = truncated + '...';
    debugTracer.addTrace('truncateOption', 'TRUNCATED', { 
        originalLength: trimmed.length,
        truncatedLength: result.length
    });
    
    return result;
}

export async function checkAnswer(puzzleId, question, expected_answer, guessed_answer, modelName) {
    console.log("📌 Checking answer similarity...");

    // ✅ Step 1: Summarize long answer if needed
    const expectedLength = expected_answer.length;
    const guessedLength = guessed_answer.length;

    if (expectedLength > guessedLength * 3) {
        console.log("📌 Large answer detected. Summarizing expected answer...");
        const prompt = `Summarize this answer to approximately ${guessedLength * 1.5} characters: "${expected_answer}"`;
        expected_answer = await callAI(prompt, modelName);
        console.log(`📌 Summarized expected answer: "${expected_answer}"`);
    }

    // ✅ Step 2: Use embeddings for similarity check
    const embedding1 = adjustEmbeddingSize(await generateEmbedding(expected_answer));
    const embedding2 = adjustEmbeddingSize(await generateEmbedding(guessed_answer));
    const similarity = cosineSimilarity(embedding1, embedding2);

    console.log(`📌 Embedding similarity score: ${similarity}`);

    // ✅ Immediate decision if similarity is very high or low
    if (similarity >= 0.85) {
        return true;
    }  
    if (similarity <= 0.5) {
        return false;
    }

    // ✅ Step 3: Borderline case? Use LLM check
    console.log("📌 Borderline case detected. Using LLM to verify...");
    let res = await checkAnswerWithLLM(question, expected_answer, guessed_answer);
    
    return res;
}


// ✅ Check if the guessed answer is correct using AI
async function checkAnswerWithLLM(question, expected_answer, guessed_answer, puzzleType, modelName) {
    const prompt = `Compare these answers: 
    - Question: "${question}"
    - Expected Answer: "${expected_answer}"
    - Guessed Answer: "${guessed_answer}"
    - Puzzle Type: "${puzzleType}"
    
    Do they mean the same thing? Respond with "YES" or "NO".`;

    console.log("check answer", prompt);
    const aiResponse = await callAI(prompt, modelName, 2, {
        category: USAGE_CATEGORIES.ANSWER_VALIDATION,
        puzzleType,
        difficulty: 'medium'
    });

    // ✅ Extract and sanitize the response text
    const answer = aiResponse.trim().toUpperCase();
    return answer === "YES";
}

export async function generateAnswer(question, puzzleType, modelName) {
    const prompt = `Answer this ${puzzleType} puzzle question: "${question}" \n Give the answer directly, no reasoning needed.`;

    const aiResponse = await callAI(prompt, modelName);

    // ✅ Extract the answer from the JSON response
    return aiResponse;
}

async function updateUserProgress(email, solved) {
    const userRef = db.collection("users").doc(email);
    const userDoc = await userRef.get();

    if (!userDoc.exists) return;

    const user = userDoc.data();
    const updatedStats = {
        puzzlesAttempted: user.puzzlesAttempted + 1,
        puzzlesSolved: solved ? user.puzzlesSolved + 1 : user.puzzlesSolved
    };

    await userRef.update(updatedStats);
}


// Simple configuration for puzzle reset strategy
export const HIGH_COUNT_PUZZLES = new Set([
    'anagram_medium', 'wordsearch_medium', 'letterset_medium', 'crossword_medium',
    'uniqueobject_medium', 'musicidentification_medium', 'crypto_medium',
    'memorystory_medium', 'musicidentification_easy', 'memoryretention_medium',
    'wordsearch_easy', 'anagram_easy', 'find_object_medium', 'imagematch_medium',
    'imagematch_easy',
    'imagequestion_medium', 'imagepuzzle_medium', 'waldopuzzle_medium',
    'memorysquares_easy', 'find_object_easy', 'crypto_easy', 'imagepuzzle_easy',
    'wordsnake_easy', 'math_easy', 'memorystory_easy', 'math_medium',
    'storypuzzle_easy', 'antonyms_easy', 'imagequestion_easy', 'wordsearch_hard',
    'crossword_easy', 'progressiverevelation_easy', 'memorysequencing_medium',
    'synonyms_medium', 'synonyms_hard', 'connotationwords_medium', 'storypuzzle_medium',
    'antonyms_medium', 'sentencetransitions_medium', 'sentencetransitions_easy',
    'memorysequencing_easy', 'memorysequencing_hard', 'trivia_easy', 'trivia_medium','progressiverevelation_medium',
    'progressiverevelation_hard', 'connotationwords_easy', 'connotationwords_hard',
]);


async function getFirstPuzzleIdFast(puzzleType, difficulty) {
    const { data } = await supabase
        .from('puzzle_first_cache')
        .select('first_puzzleid')
        .eq('type', puzzleType.toLowerCase())
        .eq('difficulty', difficulty.toLowerCase())
        .single();
    
    return data?.first_puzzleid;
}

async function trackPuzzleReset(userId, puzzleType, difficulty, resetReason = 'completion') {
    try {
        const resetEventRef = db.collection("puzzle_reset_events").doc();
        const userRef = db.collection("users").doc(userId);
        
        // Get current user data to update reset count
        const userDoc = await userRef.get();
        const userData = userDoc.data() || {};
        
        // Initialize reset tracking if it doesn't exist
        const resetCounts = userData.resetCounts || {};
        const puzzleKey = `${puzzleType}_${difficulty.toLowerCase()}`;
        
        // Increment reset count for this puzzle type
        resetCounts[puzzleKey] = (resetCounts[puzzleKey] || 0) + 1;
        
        // Calculate total resets across all puzzle types
        const totalResets = Object.values(resetCounts).reduce((sum, count) => sum + count, 0);
        
        // Create reset event document
        await resetEventRef.set({
            userId,
            puzzleType,
            difficulty: difficulty.toLowerCase(),
            resetReason, // 'completion', 'validation_failed', 'manual', etc.
            timestamp: admin.firestore.FieldValue.serverTimestamp(),
            resetCount: resetCounts[puzzleKey], // Reset count for this specific puzzle type
            totalResets, // Total resets for this user across all puzzle types
            userEmail: userData.email || null
        });
        
        // Update user document with new reset counts
        await userRef.update({
            resetCounts,
            totalResets,
            lastResetTimestamp: admin.firestore.FieldValue.serverTimestamp()
        });
        
        console.log(`📊 Reset tracked: User ${userId}, ${puzzleType}/${difficulty}, Count: ${resetCounts[puzzleKey]}, Total: ${totalResets}`);
        
        return {
            success: true,
            resetCount: resetCounts[puzzleKey],
            totalResets
        };
        
    } catch (error) {
        console.error('Error tracking puzzle reset:', error);
        return { success: false, error: error.message };
    }
}

/**
 * Per-user sequential puzzle-position tracking (Supabase, puzzle_progress
 * table). Replaces Firestore's users/{userId}.lastPuzzleProgress for
 * fetchNextPuzzle() specifically — see migration
 * 20260804201500_create_puzzle_progress.sql for why (Firestore quota
 * exhaustion was making sequential progress always "reset" silently,
 * repeating the same puzzle instead of advancing).
 */
async function getPuzzleProgress(userId, puzzleType, difficulty) {
    const { data, error } = await supabase
        .from('puzzle_progress')
        .select('current_puzzle_id')
        .eq('user_id', userId)
        .eq('puzzle_type', puzzleType)
        .eq('difficulty', difficulty)
        .maybeSingle();

    if (error) {
        console.error(`Error reading puzzle_progress for ${userId}/${puzzleType}/${difficulty}:`, error.message);
        return null;
    }
    return data?.current_puzzle_id ?? null;
}

async function setPuzzleProgress(userId, puzzleType, difficulty, puzzleId) {
    const { error } = await supabase
        .from('puzzle_progress')
        .upsert({
            user_id: userId,
            puzzle_type: puzzleType,
            difficulty,
            current_puzzle_id: puzzleId,
            updated_at: new Date().toISOString()
        });

    if (error) {
        console.error(`Error writing puzzle_progress for ${userId}/${puzzleType}/${difficulty}:`, error.message);
    }
}

export async function fetchNextPuzzle(userId, puzzleType, difficulty = "easy") {
    let normalizedDifficulty = difficulty.toLowerCase();
    const puzzleKey = `${puzzleType}_${normalizedDifficulty}`;

    try {
        const progressKey = puzzleKey;
        const currentProgressId = await getPuzzleProgress(userId, puzzleType, normalizedDifficulty);

        // Enhanced puzzle validation and reset
        let currentPuzzle = null;
        let progressWasReset = false;

        if (currentProgressId) {
            const { data: currentPuzzleData, error: currentPuzzleError } = await supabase
                .from('puzzles')
                .select('*')
                .eq('puzzleid', currentProgressId)
                .eq('difficulty', normalizedDifficulty)
                .eq('type', puzzleType.toLowerCase())
                .limit(1);

            if (currentPuzzleError || !currentPuzzleData?.[0]) {
                progressWasReset = true;
                await setPuzzleProgress(userId, puzzleType, normalizedDifficulty, null);

                await trackPuzzleReset(userId, puzzleType, normalizedDifficulty, 'validation_failed');
            } else {
                currentPuzzle = currentPuzzleData[0];
            }
        }

        // Calculate remaining puzzles
        let remainingPuzzles = 0;
        let totalPuzzles = 0;

        if (currentPuzzle && !progressWasReset) {
            const { count } = await supabase
                .from('puzzles')
                .select('*', { count: 'exact', head: true })
                .eq('type', puzzleType.toLowerCase())
                .eq('difficulty', normalizedDifficulty)
                .gt('timestamp', currentPuzzle.timestamp)
                .not('puzzleid', 'is', null);

            remainingPuzzles = count || 0;

            const { count: totalCount } = await supabase
                .from('puzzles')
                .select('*', { count: 'exact', head: true })
                .eq('type', puzzleType.toLowerCase())
                .eq('difficulty', normalizedDifficulty)
                .not('puzzleid', 'is', null);

            totalPuzzles = totalCount || 0;
        } else {
            const { count } = await supabase
                .from('puzzles')
                .select('*', { count: 'exact', head: true })
                .eq('type', puzzleType.toLowerCase())
                .eq('difficulty', normalizedDifficulty)
                .not('puzzleid', 'is', null);

            totalPuzzles = count || 0;
            remainingPuzzles = totalPuzzles;
        }

        // Handle complete puzzle exhaustion
        if (remainingPuzzles === 0 && totalPuzzles === 0) {
            return { 
                success: false, 
                message: `No ${puzzleType}/${difficulty} puzzles found in database`,
                error: "No puzzles available"
            };
        }

        // Check if we should reset
        const needsReset = remainingPuzzles < 3;

        if (needsReset) {
            // Track the completion reset
            const resetTrackingResult = await trackPuzzleReset(userId, puzzleType, normalizedDifficulty, 'completion');

            // Reset current user to the beginning
            await setPuzzleProgress(userId, puzzleType, normalizedDifficulty, null);

            // Try cache lookup first, then validate it exists
            const firstPuzzleId = await getFirstPuzzleIdFast(puzzleType, normalizedDifficulty);

            if (firstPuzzleId) {
                const { data: firstPuzzle, error: firstPuzzleError } = await supabase
                    .from('puzzles')
                    .select('*')
                    .eq('puzzleid', firstPuzzleId)
                    .single();

                if (firstPuzzle) {
                    await setPuzzleProgress(userId, puzzleType, normalizedDifficulty, firstPuzzle.puzzleid);

                    return {
                        success: true,
                        puzzleData: buildOldPuzzleFormat(firstPuzzle),
                        remainingPuzzles: totalPuzzles - 1,
                        resetMessage: `You've completed all ${totalPuzzles} ${puzzleType} puzzles! Starting over from the beginning. (Reset #${resetTrackingResult.resetCount || 1})`,
                        totalPuzzles,
                        wasReset: true,
                        resetCount: resetTrackingResult.resetCount || 1,
                        totalResets: resetTrackingResult.totalResets || 1
                    };
                }
                // If cache returned a puzzle ID that doesn't exist, fall through to expensive query
            }

            // Fallback to expensive query if cache lookup fails or returned invalid puzzle
            const { data: firstPuzzleData, error: firstPuzzleError } = await supabase
                .from('puzzles')
                .select('*')
                .eq('type', puzzleType.toLowerCase())
                .eq('difficulty', normalizedDifficulty)
                .not('puzzleid', 'is', null)
                .order('timestamp', { ascending: true })
                .limit(1);

            if (firstPuzzleData?.[0]) {
                const puzzle = firstPuzzleData[0];

                await setPuzzleProgress(userId, puzzleType, normalizedDifficulty, puzzle.puzzleid);

                return {
                    success: true,
                    puzzleData: buildOldPuzzleFormat(puzzle),
                    remainingPuzzles: totalPuzzles - 1,
                    resetMessage: `You've completed all ${totalPuzzles} ${puzzleType} puzzles! Starting over from the beginning. (Reset #${resetTrackingResult.resetCount || 1})`,
                    totalPuzzles,
                    wasReset: true,
                    resetCount: resetTrackingResult.resetCount || 1,
                    totalResets: resetTrackingResult.totalResets || 1
                };
            } else {
                return { 
                    success: false, 
                    message: `No ${puzzleType}/${difficulty} puzzles found in database`,
                    error: "No puzzles available after reset attempt"
                };
            }
        }

        // Find next puzzle
        let nextPuzzleId = null;

        if (currentProgressId && currentPuzzle && !progressWasReset) {
            const { data: pathData, error: pathError } = await supabase
                .from('puzzle_path')
                .select('nextpuzzleid')
                .eq('puzzleid', currentProgressId)
                .limit(1);

            if (pathData?.[0]?.nextpuzzleid) {
                nextPuzzleId = pathData[0].nextpuzzleid;

                // Validate the next puzzle exists
                const { data: nextPuzzleValidation, error: validationError } = await supabase
                    .from('puzzles')
                    .select('puzzleid')
                    .eq('puzzleid', nextPuzzleId)
                    .eq('difficulty', normalizedDifficulty)
                    .eq('type', puzzleType.toLowerCase())
                    .limit(1);

                if (!nextPuzzleValidation?.[0]) {
                    nextPuzzleId = null;
                }
            }

            // If no valid next puzzle found, find next logical one
            if (!nextPuzzleId && remainingPuzzles > 0) {
                const { data: nextLogicalPuzzle, error: logicalError } = await supabase
                    .from('puzzles')
                    .select('*')
                    .eq('type', puzzleType.toLowerCase())
                    .eq('difficulty', normalizedDifficulty)
                    .gt('timestamp', currentPuzzle.timestamp)
                    .order('timestamp', { ascending: true })
                    .limit(1);

                if (nextLogicalPuzzle?.[0]) {
                    nextPuzzleId = nextLogicalPuzzle[0].puzzleid;
                }
            }
        } else {
            // Try cache lookup first, then validate it exists
            const firstPuzzleId = await getFirstPuzzleIdFast(puzzleType, normalizedDifficulty);

            if (firstPuzzleId) {
                // Validate the cached puzzle ID actually exists
                const { data: validationData, error: validationError } = await supabase
                    .from('puzzles')
                    .select('puzzleid')
                    .eq('puzzleid', firstPuzzleId)
                    .eq('type', puzzleType.toLowerCase())
                    .eq('difficulty', normalizedDifficulty)
                    .limit(1);

                if (validationData?.[0]) {
                    nextPuzzleId = firstPuzzleId;
                }
                // If cache returned invalid puzzle, fall through to expensive query
            }

            // Fallback to expensive query if cache fails or returned invalid puzzle
            if (!nextPuzzleId) {
                const { data: firstPuzzleData, error: firstError } = await supabase
                    .from('puzzles')
                    .select('puzzleid')
                    .eq('type', puzzleType.toLowerCase())
                    .eq('difficulty', normalizedDifficulty)
                    .not('puzzleid', 'is', null)
                    .order('timestamp', { ascending: true })
                    .limit(1);

                if (firstPuzzleData?.[0]) {
                    nextPuzzleId = firstPuzzleData[0].puzzleid;
                }
            }
        }

        // Final puzzle fetch
        if (!nextPuzzleId) {
            return { 
                success: false, 
                message: "No next puzzle found in sequence",
                error: "Puzzle sequence error",
                remainingPuzzles
            };
        }

        const { data: nextPuzzleData, error: nextPuzzleError } = await supabase
            .from('puzzles')
            .select('*')
            .eq('puzzleid', nextPuzzleId)
            .limit(1);

        if (nextPuzzleError || !nextPuzzleData?.[0]) {
            return { 
                success: false, 
                message: "Error fetching puzzle",
                error: nextPuzzleError?.message || "Puzzle not found"
            };
        }

        const puzzle = nextPuzzleData[0];

        // Update progress
        await setPuzzleProgress(userId, puzzleType, normalizedDifficulty, puzzle.puzzleid);

        return {
            success: true,
            puzzleData: buildOldPuzzleFormat(puzzle),
            remainingPuzzles: Math.max(0, remainingPuzzles - 1),
            totalPuzzles
        };

    } catch (err) {
        console.error(`Error in fetchNextPuzzle:`, err);
        return { 
            success: false, 
            message: "Internal server error",
            error: err.message
        };
    }
}


async function notifyPuzzleGenerationComplete(puzzleType, difficulty, triggeringUserEmail = null) {
    try {
        const puzzleDisplayName = getPuzzleDisplayName(puzzleType);
        const title = `🎯 New ${puzzleDisplayName} Puzzles Ready!`;
        const body = `Fresh ${difficulty} difficulty puzzles are now available. Start solving!`;
        
        console.log(`📢 Sending puzzle ready notification for ${puzzleType} (${difficulty})`);
        
        // Send notification to all users
        const response = await fetch('https://puzzleverseai.com/send-notifications', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ 
                title, 
                body,
                puzzleType,
                difficulty
            })
        });
        
        if (response.ok) {
            const result = await response.json();
            console.log(`✅ Sent notifications: ${result.sent} successful, ${result.failed} failed`);
        }
        
    } catch (error) {
        console.error('❌ Failed to send puzzle ready notification:', error);
    }
}

function getPuzzleDisplayName(puzzleType) {
    const displayNames = {
        'sentencetransitions': 'Sentence Transitions',
        'wordassociation': 'Word Association',
        'math': 'Math',
        'anagram': 'Anagram',
        'trivia': 'Trivia',
        'memorysquares': 'Memory Squares',
        'crossword': 'Crossword',
        'crypto': 'Crypto',
        'wordsnake': 'Word Snake',
        'imagepuzzle': 'Image Puzzle',
        'storypuzzle': 'Story Puzzle',
        'synonyms': 'Synonyms',
        'connotationwords': 'Connotation Words',
        'mathestimation': 'Math Estimation',
        'geography_cities': 'Geography Cities',
        'geography_countries': 'Geography Countries',
        'wordsearch': 'Word Search',
        'imagequestion': 'Image Question'
    };
    
    return displayNames[puzzleType] || puzzleType.charAt(0).toUpperCase() + puzzleType.slice(1);
}

/**
 * Send notification when custom puzzle generation completes
 */
async function sendCustomPuzzleCompletionNotification(userEmail, topic, format, numPuzzles, success, error = null) {
    console.log(`📱 [${new Date().toISOString()}] Sending completion notification:`, {
        userEmail,
        topic,
        format,
        numPuzzles,
        success,
        error: error?.substring(0, 100)
    });
    
    try {
        // Step 1: Validate inputs
        if (!userEmail || typeof userEmail !== 'string') {
            throw new Error(`Invalid userEmail: ${userEmail}`);
        }

        // Step 2: Get user's FCM tokens with detailed logging
        console.log(`🔍 Fetching FCM tokens for user: ${userEmail}`);
        
        const { data: tokens, error: tokenError } = await supabase
            .from('user_notification_tokens')
            .select('fcm_token, platform, is_active, created_at')
            .eq('user_email', userEmail)
            .eq('is_active', true);

        if (tokenError) {
            console.error(`❌ Database error fetching tokens:`, tokenError);
            throw new Error(`Failed to fetch tokens: ${tokenError.message}`);
        }

        console.log(`📊 Token query results:`, {
            totalTokensFound: tokens?.length || 0,
            userEmail: userEmail
        });

        if (!tokens || tokens.length === 0) {
            console.warn(`⚠️ No active FCM tokens found for user ${userEmail}`);
            
            // Check if user exists at all
            const { data: allTokens, error: allTokensError } = await supabase
                .from('user_notification_tokens')
                .select('*')
                .eq('user_email', userEmail);
            
            if (!allTokensError && allTokens?.length > 0) {
                console.warn(`ℹ️ User has ${allTokens.length} total tokens, but none are active`);
                allTokens.forEach((token, idx) => {
                    console.warn(`Token ${idx + 1}: active=${token.is_active}, platform=${token.platform}, created=${token.created_at}`);
                });
            }
            
            return { 
                success: false, 
                error: 'No active tokens found',
                details: {
                    userEmail,
                    totalTokens: allTokens?.length || 0,
                    activeTokens: 0
                }
            };
        }

        // Step 3: Prepare notification content
        let notificationData;
        
        if (success) {
            notificationData = {
                title: '🎉 Custom Puzzles Ready!',
                body: `Your "${topic}" puzzle set is ready to play! ${numPuzzles} puzzles generated.`,
                data: {
                    type: 'custom_puzzle_complete',
                    topic: topic,
                    format: format,
                    numPuzzles: numPuzzles.toString(),
                    action: 'open_puzzle',
                    timestamp: new Date().toISOString(),
                    click_action: 'FLUTTER_NOTIFICATION_CLICK',
                    success: 'true'
                }
            };
        } else {
            notificationData = {
                title: '❌ Puzzle Generation Failed',
                body: `Failed to generate "${topic}" puzzles. ${error || 'Please try again.'}`,
                data: {
                    type: 'custom_puzzle_failed',
                    topic: topic,
                    format: format,
                    error: error || 'Unknown error',
                    action: 'retry_generation',
                    timestamp: new Date().toISOString(),
                    click_action: 'FLUTTER_NOTIFICATION_CLICK',
                    success: 'false'
                }
            };
        }

        console.log(`📝 Prepared notification:`, {
            title: notificationData.title,
            bodyLength: notificationData.body.length,
            dataKeys: Object.keys(notificationData.data)
        });

        // Step 4: Use your existing NotificationService to send
        const fcmTokens = tokens.map(token => token.fcm_token).filter(Boolean);
        console.log(`🚀 Sending to ${fcmTokens.length} valid FCM tokens using NotificationService...`);
        
        // 🔥 THIS IS THE KEY FIX - use your existing service
        const result = await notificationService.sendMulticastNotification(fcmTokens, notificationData);
        
        console.log(`📊 NotificationService results:`, {
            success: result.success,
            successCount: result.successCount,
            failureCount: result.failureCount,
            error: result.error
        });

        if (result.success) {
            console.log(`✅ Notification sent successfully to ${result.successCount} devices`);
            return { 
                success: true, 
                sent: result.successCount,
                failed: result.failureCount,
                details: {
                    userEmail,
                    topic,
                    tokensUsed: fcmTokens.length
                }
            };
        } else {
            console.error(`❌ Failed to send notification:`, result.error);
            return { 
                success: false, 
                error: result.error,
                details: result
            };
        }

    } catch (error) {
        console.error(`💥 Error in sendCustomPuzzleCompletionNotification:`, error);
        return { 
            success: false, 
            error: error.message,
            stack: error.stack?.split('\n').slice(0, 3)
        };
    }
}

async function generateCustomProgressivePuzzles(topic, format, numPuzzles, userId, puzzleId) {
    console.log(`🔮 Starting custom progressive revelation generation for topic="${topic}", numPuzzles=${numPuzzles}`);
    const startTime = Date.now();
    
    try {
        // Validate inputs
        if (!topic || !userId || !puzzleId) {
            throw new Error('Missing required parameters: topic, userId, or puzzleId');
        }

        // Limit to 3 puzzles max for quality and API usage
        if (numPuzzles > 3) {
            numPuzzles = 3;
            console.log('Progressive puzzles: Limited to 3 puzzles to ensure quality and respect API limits');
        }

        // Initialize progressive system
        const progressiveSystem = new ProgressivePuzzleSystem({
            debug: true,
            useAIGeneration: true
        });

        const generatedPuzzles = [];
        const maxAttemptsPerPuzzle = 3;

        for (let puzzleIndex = 0; puzzleIndex < numPuzzles; puzzleIndex++) {
            console.log(`🔮 Generating progressive puzzle ${puzzleIndex + 1}/${numPuzzles}...`);
            
            let puzzleGenerated = false;
            let attempts = 0;

            while (!puzzleGenerated && attempts < maxAttemptsPerPuzzle) {
                attempts++;
                
                try {
                    console.log(`🎯 Attempt ${attempts}/${maxAttemptsPerPuzzle} for progressive puzzle ${puzzleIndex + 1}`);
                    
                    // Generate puzzle with topic as category hint
                    const result = await progressiveSystem.generateCompletePuzzle(topic, 'medium');
                    
                    if (!result.success) {
                        console.warn(`⚠️ Progressive puzzle generation failed: ${result.error}`);
                        continue;
                    }

                    const puzzle = result.puzzle;
                    
                    // Validate we got a complete puzzle
                    if (!puzzle.answer || !puzzle.clues || puzzle.clues.length < 4 || !puzzle.image?.url) {
                        console.warn(`⚠️ Incomplete progressive puzzle generated`);
                        continue;
                    }

                    // Format for database storage
                    const formattedPuzzle = {
                        question: JSON.stringify({
                            puzzleId: puzzle.puzzleId,
                            type: puzzle.type,
                            category: puzzle.category,
                            answer: puzzle.answer, // For backend reference
                            clues: puzzle.clues,
                            image: puzzle.image, // Single image with blur levels
                            gameFlow: puzzle.gameFlow,
                            clientConfig: puzzle.clientConfig,
                            instructions: "Guess what's in the image! Each wrong answer reveals a clearer image and an easier clue.",
                            timeLimit: puzzle.gameFlow.maxTime,
                            difficulty: puzzle.metadata.difficulty,
                            customTopic: topic // Mark as custom topic
                        }),
                        answer: JSON.stringify({
                            correctAnswer: puzzle.answer,
                            clues: puzzle.clues,
                            maxScore: puzzle.gameFlow.scoringSystem.correctAtClue1,
                            scoringBreakdown: puzzle.gameFlow.scoringSystem,
                            imageValidation: puzzle.metadata.imageValidation,
                            searchTerms: puzzle.searchTerms,
                            customTopic: topic
                        }),
                        hint: `Progressive revelation about ${topic}: ${puzzle.answer}`,
                        difficulty: 'medium'
                    };
                    
                    // Store in database
                    await supabase
                        .from('puzzles')
                        .insert({
                            parentSetId: puzzleId,
                            puzzleid: crypto.randomUUID(),
                            source: userId,
                            type: "progressiverevelation",
                            status: 'completed',
                            difficulty: 'medium',
                            question: formattedPuzzle.question,
                            answer: formattedPuzzle.answer,
                            options: [], // No multiple choice for progressive puzzles
                            hint: formattedPuzzle.hint,
                            timestamp: new Date().toISOString()
                        });
                    
                    generatedPuzzles.push(result);
                    puzzleGenerated = true;
                    
                    console.log(`✅ Progressive puzzle ${puzzleIndex + 1} generated: ${puzzle.answer} (${puzzle.category})`);
                    
                } catch (error) {
                    console.error(`❌ Error in progressive puzzle generation attempt ${attempts}:`, error.message);
                    
                    if (attempts >= maxAttemptsPerPuzzle) {
                        console.warn(`⚠️ Failed to generate progressive puzzle ${puzzleIndex + 1} after ${attempts} attempts`);
                    }
                }
            }

            // Add delay between puzzles to respect API rate limits
            if (puzzleIndex < numPuzzles - 1) {
                console.log(`⏸️ Waiting 5 seconds before next progressive generation...`);
                await new Promise(resolve => setTimeout(resolve, 5000));
            }
        }

        // Final status update
        const finalStatus = generatedPuzzles.length > 0 ? 'completed' : 'failed';
        
        await Promise.all([
            supabase
                .from('puzzle_sets')
                .update({
                    status: finalStatus,
                    puzzle_count: generatedPuzzles.length,
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: finalStatus,
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);

        const elapsedTime = (Date.now() - startTime) / 1000;
        console.log(`⏱️ Progressive puzzle generation completed in ${elapsedTime.toFixed(1)}s`);

        if (generatedPuzzles.length > 0) {
            console.log(`🎉 Generated ${generatedPuzzles.length}/${numPuzzles} progressive revelation puzzles successfully`);
            
            // Send success notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Progressive Revelation', 
                generatedPuzzles.length, 
                true
            );
            
            return { puzzleId };
        } else {
            console.error(`❌ No progressive puzzles could be generated`);
            
            // Send failure notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Progressive Revelation', 
                numPuzzles, 
                false, 
                "Failed to generate progressive revelation puzzles"
            );
            
            return { error: "Failed to generate any progressive revelation puzzles" };
        }

    } catch (error) {
        console.error(`💥 Fatal error in progressive puzzle generation:`, error);
        
        // Mark as failed
        await Promise.allSettled([
            supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: 'failed',
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);
        
        // Send failure notification
        await sendCustomPuzzleCompletionNotification(
            userId, 
            topic, 
            'Progressive Revelation', 
            numPuzzles, 
            false, 
            error.message
        );
        
        return { error: error.message };
    }
}

export async function resetAllUsersPuzzleProgress(req, res) {
    try {
        const { puzzleType, difficulty, dryRun = true, batchSize = 50, maxUsers = 10000 } = req.body;

        // Validate required puzzleType parameter
        if (!puzzleType || typeof puzzleType !== 'string' || !puzzleType.trim()) {
            return res.status(400).json({
                success: false,
                error: "puzzleType is required and must be a valid string"
            });
        }

        // Validate optional parameters
        if (difficulty && typeof difficulty !== 'string') {
            return res.status(400).json({
                success: false,
                error: "difficulty must be a string if provided"
            });
        }

        // Normalize parameters
        const normalizedPuzzleType = puzzleType.trim().toLowerCase();
        const normalizedDifficulty = difficulty ? difficulty.trim().toLowerCase() : null;

        console.log(`🔄 ${dryRun ? 'Analyzing' : 'Resetting'} ${normalizedPuzzleType}${normalizedDifficulty ? `/${normalizedDifficulty}` : ''} progress for all users...`);

        let results = {
            totalUsers: 0,
            processedUsers: 0,
            resetUsers: 0,
            skippedUsers: 0,
            errors: [],
            batches: 0,
            resetDetails: []
        };

        let lastDoc = null;
        let hasMore = true;

        // Process users in batches to avoid memory issues
        while (hasMore && results.totalUsers < maxUsers) {
            console.log(`📦 Processing batch ${results.batches + 1}...`);

            let query = db.collection('users').limit(batchSize);
            
            if (lastDoc) {
                query = query.startAfter(lastDoc);
            }

            const snapshot = await query.get();

            if (snapshot.empty) {
                hasMore = false;
                break;
            }

            results.batches++;
            results.totalUsers += snapshot.size;

            // Process each user in this batch
            for (const userDoc of snapshot.docs) {
                const userId = userDoc.id;
                const userData = userDoc.data() || {};
                const currentProgress = userData.lastPuzzleProgress || {};
                const currentTimestamps = userData.lastPuzzleTimestamps || {};

                let userResetCount = 0;
                let userResetDetails = [];
                let updatedProgress = { ...currentProgress };
                let updatedTimestamps = { ...currentTimestamps };

                // Apply the reset logic based on parameters
                if (normalizedPuzzleType && !normalizedDifficulty) {
                    // Case: Reset all difficulties for a specific puzzle type
                    let progressCleared = 0;
                    let timestampsCleared = 0;

                    // Clear progress entries (format: puzzleType_difficulty)
                    Object.keys(updatedProgress).forEach(key => {
                        if (key.startsWith(`${normalizedPuzzleType}_`)) {
                            delete updatedProgress[key];
                            progressCleared++;
                            userResetDetails.push({
                                action: "reset_difficulty",
                                key: key,
                                type: "progress"
                            });
                        }
                    });

                    // Clear legacy timestamp entries (format: puzzleType)
                    if (updatedTimestamps[normalizedPuzzleType]) {
                        delete updatedTimestamps[normalizedPuzzleType];
                        timestampsCleared++;
                        userResetDetails.push({
                            action: "reset_legacy",
                            key: normalizedPuzzleType,
                            type: "timestamp"
                        });
                    }

                    userResetCount = progressCleared + timestampsCleared;

                } else if (normalizedPuzzleType && normalizedDifficulty) {
                    // Case: Reset specific puzzle type and difficulty combination
                    const progressKey = `${normalizedPuzzleType}_${normalizedDifficulty}`;
                    let found = false;

                    // Check and remove specific progress entry
                    if (updatedProgress[progressKey]) {
                        delete updatedProgress[progressKey];
                        found = true;
                        userResetCount++;
                        userResetDetails.push({
                            action: "reset_specific",
                            key: progressKey,
                            type: "progress"
                        });
                    }

                    // For backward compatibility, also check legacy timestamp format
                    if (updatedTimestamps[normalizedPuzzleType]) {
                        delete updatedTimestamps[normalizedPuzzleType];
                        found = true;
                        userResetCount++;
                        userResetDetails.push({
                            action: "reset_legacy_specific",
                            key: normalizedPuzzleType,
                            type: "timestamp"
                        });
                    }
                }

                results.processedUsers++;

                if (userResetCount > 0) {
                    results.resetUsers++;
                    
                    // Add to sample details (first 10 users)
                    if (results.resetDetails.length < 10) {
                        results.resetDetails.push({
                            userId,
                            resetCount: userResetCount,
                            details: userResetDetails.slice(0, 3) // Limit details per user
                        });
                    }
                    
                    // Apply changes if not dry run
                    if (!dryRun) {
                        try {
                            await userDoc.ref.update({
                                lastPuzzleProgress: updatedProgress,
                                lastPuzzleTimestamps: updatedTimestamps
                            });
                            console.log(`✅ Reset ${userResetCount} entries for user ${userId}`);
                        } catch (error) {
                            results.errors.push({
                                userId,
                                error: error.message,
                                resetCount: userResetCount
                            });
                        }
                    }
                } else {
                    results.skippedUsers++;
                }
            }

            // Set up for next batch
            lastDoc = snapshot.docs[snapshot.docs.length - 1];
            
            if (snapshot.size < batchSize) {
                hasMore = false;
            }

            // Small delay between batches to be gentle on Firebase
            await new Promise(resolve => setTimeout(resolve, 200));
        }

        // Generate summary message
        const puzzleTypeDisplay = `${normalizedPuzzleType}${normalizedDifficulty ? `/${normalizedDifficulty}` : ''}`;
        const message = dryRun 
            ? `Analysis complete: ${results.resetUsers}/${results.processedUsers} users have ${puzzleTypeDisplay} progress that would be reset`
            : `Reset complete: ${results.resetUsers}/${results.processedUsers} users had their ${puzzleTypeDisplay} progress reset`;

        console.log(`✅ ${message}`);

        return res.json({
            success: true,
            mode: dryRun ? 'analysis' : 'reset',
            message,
            puzzleType: normalizedPuzzleType,
            difficulty: normalizedDifficulty,
            results,
            performance: {
                totalBatches: results.batches,
                avgUsersPerBatch: results.batches > 0 ? Math.round(results.totalUsers / results.batches) : 0,
                errorRate: results.processedUsers > 0 ? ((results.errors.length / results.processedUsers) * 100).toFixed(1) + '%' : '0%'
            },
            nextSteps: dryRun && results.resetUsers > 0 ? [
                'Review the resetDetails to see what would be reset',
                'Run with dryRun=false to perform the actual reset',
                'Consider the impact on user experience'
            ] : [
                'Reset operation completed',
                'Users will now start from the beginning of the puzzle sequence',
                'Monitor user feedback and puzzle fetch patterns'
            ]
        });

    } catch (error) {
        console.error(`💥 Error in resetAllUsersPuzzleProgress:`, error);
        return res.status(500).json({
            success: false,
            error: "Internal server error",
            message: error.message
        });
    }
}

export async function resetPuzzleProgress(req, res) {
    try {
        const { userId, puzzleType, difficulty } = req.body;

        // Validate required userId parameter
        if (!userId || typeof userId !== 'string' || !userId.trim()) {
            return res.status(400).json({
                success: false,
                error: "userId is required and must be a valid string"
            });
        }

        const normalizedUserId = userId.trim();

        // Validate optional parameters
        if (puzzleType && typeof puzzleType !== 'string') {
            return res.status(400).json({
                success: false,
                error: "puzzleType must be a string if provided"
            });
        }

        if (difficulty && typeof difficulty !== 'string') {
            return res.status(400).json({
                success: false,
                error: "difficulty must be a string if provided"
            });
        }

        // Normalize optional parameters
        const normalizedPuzzleType = puzzleType ? puzzleType.trim().toLowerCase() : null;
        const normalizedDifficulty = difficulty ? difficulty.trim().toLowerCase() : null;

        // Validate difficulty is only provided with puzzleType
        if (normalizedDifficulty && !normalizedPuzzleType) {
            return res.status(400).json({
                success: false,
                error: "difficulty can only be specified when puzzleType is also provided"
            });
        }

        console.log(`🔄 Reset request: userId=${normalizedUserId}, type=${normalizedPuzzleType}, difficulty=${normalizedDifficulty}`);

        // Get user document from Firebase
        const userRef = db.collection("users").doc(normalizedUserId);
        const userDoc = await userRef.get();

        if (!userDoc.exists) {
            return res.status(404).json({
                success: false,
                error: "User not found",
                message: "No puzzle progress exists for this userId"
            });
        }

        const userData = userDoc.data() || {};
        const currentProgress = userData.lastPuzzleProgress || {};
        const currentTimestamps = userData.lastPuzzleTimestamps || {};

        let resetCount = 0;
        let resetDetails = [];

        // Case 1: Reset ALL puzzle progress (no puzzleType specified)
        if (!normalizedPuzzleType) {
            console.log(`🗑️ Resetting ALL puzzle progress for user ${normalizedUserId}`);
            
            // Count current progress entries
            resetCount = Object.keys(currentProgress).length + Object.keys(currentTimestamps).length;
            
            // Reset all progress
            await userRef.update({
                lastPuzzleProgress: {},
                lastPuzzleTimestamps: {}
            });

            resetDetails.push({
                action: "reset_all",
                progressEntriesCleared: Object.keys(currentProgress).length,
                timestampEntriesCleared: Object.keys(currentTimestamps).length
            });

            return res.json({
                success: true,
                message: "All puzzle progress has been reset",
                resetCount,
                resetDetails,
                user: normalizedUserId
            });
        }

        // Case 2: Reset all difficulties for a specific puzzle type
        if (normalizedPuzzleType && !normalizedDifficulty) {
            console.log(`🗑️ Resetting all progress for puzzle type: ${normalizedPuzzleType}`);
            
            const updatedProgress = { ...currentProgress };
            const updatedTimestamps = { ...currentTimestamps };
            
            // Remove all entries matching the puzzle type
            let progressCleared = 0;
            let timestampsCleared = 0;

            // Clear progress entries (format: puzzleType_difficulty)
            Object.keys(updatedProgress).forEach(key => {
                if (key.startsWith(`${normalizedPuzzleType}_`)) {
                    delete updatedProgress[key];
                    progressCleared++;
                    resetDetails.push({
                        action: "reset_difficulty",
                        key: key,
                        type: "progress"
                    });
                }
            });

            // Clear legacy timestamp entries (format: puzzleType)
            if (updatedTimestamps[normalizedPuzzleType]) {
                delete updatedTimestamps[normalizedPuzzleType];
                timestampsCleared++;
                resetDetails.push({
                    action: "reset_legacy",
                    key: normalizedPuzzleType,
                    type: "timestamp"
                });
            }

            resetCount = progressCleared + timestampsCleared;

            if (resetCount === 0) {
                return res.json({
                    success: true,
                    message: `No progress found for puzzle type: ${normalizedPuzzleType}`,
                    resetCount: 0,
                    user: normalizedUserId
                });
            }

            // Update Firebase
            await userRef.update({
                lastPuzzleProgress: updatedProgress,
                lastPuzzleTimestamps: updatedTimestamps
            });

            return res.json({
                success: true,
                message: `All progress for puzzle type '${normalizedPuzzleType}' has been reset`,
                resetCount,
                resetDetails,
                user: normalizedUserId,
                puzzleType: normalizedPuzzleType
            });
        }

        // Case 3: Reset specific puzzle type and difficulty combination
        if (normalizedPuzzleType && normalizedDifficulty) {
            console.log(`🗑️ Resetting progress for ${normalizedPuzzleType}_${normalizedDifficulty}`);
            
            const progressKey = `${normalizedPuzzleType}_${normalizedDifficulty}`;
            const updatedProgress = { ...currentProgress };
            const updatedTimestamps = { ...currentTimestamps };
            
            let found = false;

            // Check and remove specific progress entry
            if (updatedProgress[progressKey]) {
                delete updatedProgress[progressKey];
                found = true;
                resetCount++;
                resetDetails.push({
                    action: "reset_specific",
                    key: progressKey,
                    type: "progress"
                });
            }

            // For backward compatibility, also check legacy timestamp format
            if (updatedTimestamps[normalizedPuzzleType]) {
                delete updatedTimestamps[normalizedPuzzleType];
                found = true;
                resetCount++;
                resetDetails.push({
                    action: "reset_legacy_specific",
                    key: normalizedPuzzleType,
                    type: "timestamp"
                });
            }

            if (!found) {
                return res.json({
                    success: true,
                    message: `No progress found for ${normalizedPuzzleType} (${normalizedDifficulty})`,
                    resetCount: 0,
                    user: normalizedUserId,
                    puzzleType: normalizedPuzzleType,
                    difficulty: normalizedDifficulty
                });
            }

            // Update Firebase
            await userRef.update({
                lastPuzzleProgress: updatedProgress,
                lastPuzzleTimestamps: updatedTimestamps
            });

            return res.json({
                success: true,
                message: `Progress for ${normalizedPuzzleType} (${normalizedDifficulty}) has been reset`,
                resetCount,
                resetDetails,
                user: normalizedUserId,
                puzzleType: normalizedPuzzleType,
                difficulty: normalizedDifficulty
            });
        }

    } catch (error) {
        console.error(`💥 Error in resetPuzzleProgress:`, error);
        return res.status(500).json({
            success: false,
            error: "Internal server error",
            message: error.message
        });
    }
}


// ✅ Helper function fixes
async function findNextLogicalPuzzle(puzzleType, difficulty, currentTimestamp) {
    console.log(`🔍 Finding next logical puzzle for ${puzzleType}/${difficulty} after ${currentTimestamp}`);
    
    if (!currentTimestamp) {
        // If no current timestamp, get the first puzzle
        const { data } = await supabase
            .from('puzzles')
            .select('*')
            .eq('type', puzzleType.toLowerCase())
            .eq('difficulty', difficulty.toLowerCase()) // Already normalized
            .order('timestamp', { ascending: true })
            .limit(1);
        
        console.log(`📍 First puzzle found: ${data?.[0]?.puzzleid || 'none'}`);
        return data?.[0] || null;
    }
    
    // Find the next puzzle after the current timestamp
    const { data, error } = await supabase
        .from('puzzles')
        .select('*')
        .eq('type', puzzleType.toLowerCase())
        .eq('difficulty', difficulty.toLowerCase()) // Already normalized
        .gt('timestamp', currentTimestamp)
        .order('timestamp', { ascending: true })
        .limit(1);
    
    if (error) {
        console.error(`❌ Error finding next logical puzzle:`, error);
        return null;
    }
    
    console.log(`📍 Next logical puzzle found: ${data?.[0]?.puzzleid || 'none'}`);
    return data?.[0] || null;
}

function getGenerationKey(puzzleType, difficulty) {
    return `${puzzleType.toLowerCase()}_${difficulty.toLowerCase()}`;
}

// Add these functions to puzzleService.js

/**
 * Detects ghost puzzles - puzzle IDs in puzzle_path that don't exist in puzzles table
 */
export async function detectGhostPuzzles(puzzleType = null, difficulty = null) {
    console.log(`👻 Detecting ghost puzzles${puzzleType ? ` for ${puzzleType}/${difficulty}` : ' across all types'}`);
    
    try {
        let pathQuery = supabase.from('puzzle_path').select('puzzleid, type, difficulty, nextpuzzleid');
        
        if (puzzleType && difficulty) {
            pathQuery = pathQuery.eq('type', puzzleType).eq('difficulty', difficulty.toLowerCase());
        }
        
        const { data: pathEntries, error: pathError } = await pathQuery;
        
        if (pathError) {
            throw new Error(`Failed to query puzzle_path: ${pathError.message}`);
        }
        
        if (!pathEntries || pathEntries.length === 0) {
            return {
                success: true,
                ghostPuzzles: [],
                summary: {
                    totalPathEntries: 0,
                    ghostCount: 0,
                    affectedChains: 0
                }
            };
        }
        
        // Get all unique puzzle IDs from path entries
        const allPathPuzzleIds = new Set();
        pathEntries.forEach(entry => {
            allPathPuzzleIds.add(entry.puzzleid);
            if (entry.nextpuzzleid) {
                allPathPuzzleIds.add(entry.nextpuzzleid);
            }
        });
        
        console.log(`🔍 Checking ${allPathPuzzleIds.size} unique puzzle IDs from path entries`);
        
        // Check which puzzle IDs actually exist in puzzles table
        const existingPuzzleIds = new Set();
        const puzzleIdArray = Array.from(allPathPuzzleIds);
        
        // Query in batches to avoid URL length limits
        const batchSize = 100;
        for (let i = 0; i < puzzleIdArray.length; i += batchSize) {
            const batch = puzzleIdArray.slice(i, i + batchSize);
            
            const { data: existingPuzzles, error: puzzleError } = await supabase
                .from('puzzles')
                .select('puzzleid')
                .in('puzzleid', batch);
            
            if (puzzleError) {
                throw new Error(`Failed to query puzzles: ${puzzleError.message}`);
            }
            
            existingPuzzles?.forEach(puzzle => {
                existingPuzzleIds.add(puzzle.puzzleid);
            });
        }
        
        // Find ghost puzzles
        const ghostPuzzleIds = Array.from(allPathPuzzleIds).filter(id => !existingPuzzleIds.has(id));
        
        // Analyze affected path entries
        const ghostPuzzles = [];
        const affectedChains = new Set();
        
        pathEntries.forEach(entry => {
            const isCurrentGhost = ghostPuzzleIds.includes(entry.puzzleid);
            const isNextGhost = entry.nextpuzzleid && ghostPuzzleIds.includes(entry.nextpuzzleid);
            
            if (isCurrentGhost || isNextGhost) {
                const chainKey = `${entry.type}/${entry.difficulty}`;
                affectedChains.add(chainKey);
                
                ghostPuzzles.push({
                    pathEntry: entry,
                    currentPuzzleIsGhost: isCurrentGhost,
                    nextPuzzleIsGhost: isNextGhost,
                    issueType: isCurrentGhost ? 'ghost_current' : 'ghost_next',
                    chainKey
                });
            }
        });
        
        console.log(`👻 Found ${ghostPuzzleIds.length} ghost puzzles affecting ${affectedChains.size} chains`);
        
        return {
            success: true,
            ghostPuzzles,
            ghostPuzzleIds,
            summary: {
                totalPathEntries: pathEntries.length,
                totalUniquePuzzleIds: allPathPuzzleIds.size,
                existingPuzzles: existingPuzzleIds.size,
                ghostCount: ghostPuzzleIds.length,
                affectedChains: affectedChains.size,
                affectedChainsList: Array.from(affectedChains)
            }
        };
        
    } catch (error) {
        console.error(`❌ Error detecting ghost puzzles:`, error);
        return {
            success: false,
            error: error.message
        };
    }
}



export async function getDeduplicationStats(req, res) {
    try {
        const { puzzleType } = req.params;
        
        if (!puzzleType) {
            return res.status(400).json({
                success: false,
                error: 'puzzleType parameter is required'
            });
        }

        const stats = await deduplicationService.getDeduplicationStats(puzzleType);
        
        return res.json({
            success: true,
            stats
        });
        
    } catch (error) {
        console.error('❌ Error getting deduplication stats:', error);
        return res.status(500).json({
            success: false,
            error: error.message
        });
    }
}

export async function cleanupDuplicates(req, res) {
    try {
        const { puzzleType } = req.params;
        const { dryRun = true } = req.query;
        
        if (!puzzleType) {
            return res.status(400).json({
                success: false,
                error: 'puzzleType parameter is required'
            });
        }

        const result = await deduplicationService.cleanupDuplicates(puzzleType, dryRun === 'true');
        
        return res.json({
            success: true,
            result
        });
        
    } catch (error) {
        console.error('❌ Error cleaning duplicates:', error);
        return res.status(500).json({
            success: false,
            error: error.message
        });
    }
}

export async function batchDuplicateCheck(req, res) {
    try {
        const { puzzleType, puzzles } = req.body;
        
        if (!puzzleType || !puzzles || !Array.isArray(puzzles)) {
            return res.status(400).json({
                success: false,
                error: 'puzzleType and puzzles array are required'
            });
        }

        const results = await deduplicationService.batchDuplicateCheck(puzzleType, puzzles);
        
        return res.json({
            success: true,
            results
        });
        
    } catch (error) {
        console.error('❌ Error in batch duplicate check:', error);
        return res.status(500).json({
            success: false,
            error: error.message
        });
    }
}

/**
 * Detects circular references in puzzle paths
 */
async function detectCircularReferences(puzzleType, difficulty) {
    try {
        const { data: pathEntries } = await supabase
            .from('puzzle_path')
            .select('puzzleid, nextpuzzleid')
            .eq('type', puzzleType)
            .eq('difficulty', difficulty.toLowerCase());
        
        if (!pathEntries || pathEntries.length === 0) {
            return { hasCircularReferences: false, circularReferences: [] };
        }
        
        const visited = new Set();
        const recursionStack = new Set();
        const circularReferences = [];
        
        // Build adjacency map
        const adjacency = {};
        pathEntries.forEach(entry => {
            if (entry.nextpuzzleid) {
                adjacency[entry.puzzleid] = entry.nextpuzzleid;
            }
        });
        
        // DFS to detect cycles
        function hasCycle(puzzleId, path = []) {
            if (recursionStack.has(puzzleId)) {
                const cycleStart = path.indexOf(puzzleId);
                circularReferences.push(path.slice(cycleStart).concat(puzzleId));
                return true;
            }
            
            if (visited.has(puzzleId)) {
                return false;
            }
            
            visited.add(puzzleId);
            recursionStack.add(puzzleId);
            path.push(puzzleId);
            
            if (adjacency[puzzleId]) {
                if (hasCycle(adjacency[puzzleId], [...path])) {
                    return true;
                }
            }
            
            recursionStack.delete(puzzleId);
            return false;
        }
        
        // Check all nodes
        for (const entry of pathEntries) {
            if (!visited.has(entry.puzzleid)) {
                hasCycle(entry.puzzleid);
            }
        }
        
        return {
            hasCircularReferences: circularReferences.length > 0,
            circularReferences
        };
        
    } catch (error) {
        console.error(`❌ Error detecting circular references:`, error);
        return { hasCircularReferences: false, circularReferences: [], error: error.message };
    }
}

// Helper function to fix puzzle path
async function fixPuzzlePath(currentPuzzleId, nextPuzzleId) {
    try {
        // Update the current puzzle to point to the next puzzle
        const { error } = await supabase
            .from('puzzle_path')
            .update({ nextpuzzleid: nextPuzzleId })
            .eq('puzzleid', currentPuzzleId);
        
        if (error) {
            console.error(`❌ Error updating puzzle path:`, error);
        } else {
            console.log(`✅ Fixed puzzle path: ${currentPuzzleId} → ${nextPuzzleId}`);
        }
    } catch (error) {
        console.error(`❌ Exception fixing puzzle path:`, error);
    }
}

// Helper function to rebuild entire puzzle path for a type/difficulty
async function rebuildPuzzlePath(puzzleType, difficulty) {
    console.log(`🔧 Rebuilding puzzle path for ${puzzleType}/${difficulty}`);
    
    try {
        // Get all puzzles for this type/difficulty, ordered by timestamp
        const { data: allPuzzles } = await supabase
            .from('puzzles')
            .select('puzzleid, timestamp')
            .eq('type', puzzleType.toLowerCase())
            .eq('difficulty', difficulty.toLowerCase())
            .order('timestamp', { ascending: true });
        
        if (!allPuzzles || allPuzzles.length === 0) {
            console.log(`No puzzles found for ${puzzleType}/${difficulty}`);
            return;
        }
        
        console.log(`📋 Rebuilding path for ${allPuzzles.length} puzzles`);
        
        // Delete existing path entries for this type/difficulty
        await supabase
            .from('puzzle_path')
            .delete()
            .eq('type', puzzleType)
            .eq('difficulty', difficulty.toLowerCase());
        
        // Create new path entries
        const pathEntries = [];
        for (let i = 0; i < allPuzzles.length; i++) {
            const currentPuzzle = allPuzzles[i];
            const nextPuzzle = allPuzzles[i + 1];
            
            pathEntries.push({
                puzzleid: currentPuzzle.puzzleid,
                type: puzzleType,
                difficulty: difficulty,
                nextpuzzleid: nextPuzzle?.puzzleid || null
            });
        }
        
        // Insert all path entries at once
        const { error } = await supabase
            .from('puzzle_path')
            .upsert(pathEntries, {
                onConflict: 'puzzleid',
                ignoreDuplicates: false
              });
        
        if (error) {
            console.error(`❌ Error inserting puzzle path entries:`, error);
        } else {
            console.log(`✅ Rebuilt puzzle path with ${pathEntries.length} entries`);
        }
        
    } catch (error) {
        console.error(`❌ Error rebuilding puzzle path:`, error);
        throw error;
    }
}

async function triggerAutoGenerationWithLogging(puzzleType, difficulty, count) {
    const jobId = crypto.randomUUID();
    difficulty = difficulty.toLowerCase(); // Normalize difficulty
    try {
        // Log job start
        await supabase
            .from('generation_jobs')
            .insert({
                id: jobId,
                puzzle_type: puzzleType,
                difficulty: difficulty,
                status: 'started',
                started_at: new Date().toISOString()
            });
            
        const result = await triggerAutoGenerationSafe(puzzleType, difficulty, count);
        
        // Log success
        await supabase
            .from('generation_jobs')
            .update({
                status: 'completed',
                completed_at: new Date().toISOString(),
                puzzles_generated: result.successCount || 0
            })
            .eq('id', jobId);
            
        return result;
        
    } catch (error) {
        // Log failure
        await supabase
            .from('generation_jobs')
            .update({
                status: 'failed',
                completed_at: new Date().toISOString(),
                error_message: error.message
            })
            .eq('id', jobId);
            
        throw error;
    }
}

function findBestOptionMatch(correctAnswer, options) {
    if (!correctAnswer || !options || !Array.isArray(options)) {
        return { match: null, confidence: 0, index: -1 };
    }

    const normalizeText = (text) => {
        return text.toString().trim().toLowerCase()
            .replace(/[^\w\s]/g, '') // Remove punctuation
            .replace(/\s+/g, ' '); // Normalize whitespace
    };

    const normalizedAnswer = normalizeText(correctAnswer);
    let bestMatch = { match: null, confidence: 0, index: -1 };

    options.forEach((option, index) => {
        const normalizedOption = normalizeText(option);
        
        // Exact match after normalization
        if (normalizedAnswer === normalizedOption) {
            bestMatch = { match: option, confidence: 1.0, index };
            return;
        }

        // Substring match
        if (normalizedAnswer.includes(normalizedOption) || normalizedOption.includes(normalizedAnswer)) {
            const confidence = Math.min(normalizedAnswer.length, normalizedOption.length) / 
                            Math.max(normalizedAnswer.length, normalizedOption.length);
            if (confidence > bestMatch.confidence) {
                bestMatch = { match: option, confidence, index };
            }
        }

        // Fuzzy matching using Levenshtein distance
        const distance = calculateLevenshteinDistance(normalizedAnswer, normalizedOption);
        const maxLength = Math.max(normalizedAnswer.length, normalizedOption.length);
        const similarity = 1 - (distance / maxLength);
        
        if (similarity > 0.8 && similarity > bestMatch.confidence) {
            bestMatch = { match: option, confidence: similarity, index };
        }
    });

    return bestMatch;
}

function calculateLevenshteinDistance(str1, str2) {
    const matrix = Array(str2.length + 1).fill(null).map(() => Array(str1.length + 1).fill(null));
    
    for (let i = 0; i <= str1.length; i++) matrix[0][i] = i;
    for (let j = 0; j <= str2.length; j++) matrix[j][0] = j;
    
    for (let j = 1; j <= str2.length; j++) {
        for (let i = 1; i <= str1.length; i++) {
            const indicator = str1[i - 1] === str2[j - 1] ? 0 : 1;
            matrix[j][i] = Math.min(
                matrix[j][i - 1] + 1,     // deletion
                matrix[j - 1][i] + 1,     // insertion
                matrix[j - 1][i - 1] + indicator // substitution
            );
        }
    }
    
    return matrix[str2.length][str1.length];
}

export async function triggerAutoGenerationSafe(puzzleType, difficulty, count = 5) {
    const generationId = crypto.randomUUID();
    
    const puzzleGenerator = getGenerator();
    
    if (!puzzleGenerator) {
        return {
            success: false,
            reason: 'generator_unavailable',
            message: 'Puzzle generator not available'
        };
    }

    // Start async generation
    generatePuzzlesAsyncSimple(puzzleGenerator, puzzleType, difficulty, count, generationId)
        .catch(error => {
            console.error('Async generation error:', error);
        });

    return {
        success: true,
        message: `Auto-generation started for ${puzzleType}/${difficulty}`,
        generationId: generationId,
        targetCount: count
    };
}

// Enhanced generatePuzzlesAsyncSimple with debug context
async function generatePuzzlesAsyncSimple(puzzleGenerator, puzzleType, difficulty, targetCount, generationId, generationKey, debugContext = null) {
    function addTrace(step, data) {
        if (debugContext) {
            debugContext.addTrace(`generatePuzzlesAsyncSimple.${step}`, data);
        }
    }

    addTrace('ASYNC_START', {
        generationId,
        targetCount,
        puzzleType,
        difficulty
    });

    let generatedCount = 0;
    let attempts = 0;
    const maxAttempts = targetCount * 2;
    
    try {
        while (generatedCount < targetCount && attempts < maxAttempts) {
            attempts++;
            
            addTrace('GENERATION_ATTEMPT', {
                attempt: attempts,
                generatedSoFar: generatedCount,
                remaining: targetCount - generatedCount
            });
            
            try {
                // Pass debug context to puzzle generator
                const result = await puzzleGenerator.generatePuzzle(puzzleType, null, difficulty, debugContext);
                
                addTrace('PUZZLE_GENERATION_RESULT', {
                    attempt: attempts,
                    success: result.success,
                    puzzleId: result.puzzleId,
                    message: result.message
                });
                
                if (result.success) {
                    generatedCount++;
                    addTrace('PUZZLE_SUCCESS', {
                        generatedCount,
                        puzzleId: result.puzzleId,
                        progress: `${generatedCount}/${targetCount}`
                    });
                } else {
                    addTrace('PUZZLE_FAILED', {
                        attempt: attempts,
                        reason: result.message
                    });
                }
                
                await new Promise(resolve => setTimeout(resolve, 500));
                
            } catch (error) {
                addTrace('PUZZLE_EXCEPTION', {
                    attempt: attempts,
                    error: error.message
                });
                await new Promise(resolve => setTimeout(resolve, 1000));
            }
        }
        
        const success = generatedCount >= targetCount;
        
        addTrace('ASYNC_COMPLETE', {
            success,
            generatedCount,
            targetCount,
            attempts,
            generationId
        });
        
        if (success) {
            await notifyPuzzleGenerationComplete(puzzleType, difficulty);
            addTrace('NOTIFICATION_SENT', { puzzleType, difficulty });
        }
        
    } catch (error) {
        addTrace('ASYNC_FATAL_ERROR', {
            error: error.message,
            stack: error.stack,
            generationId
        });
    }
}

function buildOldPuzzleFormat(puzzle) {
    console.log(`🛠️ Building response format for puzzle=${puzzle.puzzleid}`);
    
    // Keep ALL puzzle types in their original format
    puzzle = addAudioUrlToMemoryPuzzle(puzzle);
    
    console.log(`✅ Processed ${puzzle.type} puzzle format: question type=${typeof puzzle.question}`);

    return {
        puzzleId: puzzle.puzzleid,
        puzzleType: puzzle.type,
        question: puzzle.question,
        answer: puzzle.answer,     
        hint: puzzle.hint || "",
        difficulty: puzzle.difficulty,
        generatedBy: puzzle.source || "",
        validatedBy: "",
        timestamp: puzzle.timestamp,
        options: puzzle.options || [],
        correct_option: puzzle.correct_option || puzzle.answer
    };
}

const ANONYMOUS_USER_ID = "anon_sequential_user";


export async function fetchRandomPuzzle(puzzleType, difficulty = "easy") {
    try {
        console.log(`🔍 Fetching sequential puzzle for anonymous user`);
        console.log(`🔍 Puzzle type: "${puzzleType}", difficulty: "${difficulty}"`);

        // Use the sequential approach with predefined anonymous ID
        const result = await fetchNextPuzzle(ANONYMOUS_USER_ID, puzzleType, difficulty);
        
        if (!result.success) {
            console.warn(`⚠ Sequential fetch failed, attempting fallback`);
            // Fallback to ensure we have puzzles available
            const { data, error } = await supabase
                .from('puzzles')
                .select('*')
                .eq('type', puzzleType.toLowerCase())
                .eq('difficulty', difficulty.toLowerCase())
                .limit(1);

            if (error || !data || data.length === 0) {
                return { success: false, message: "No puzzles available for this type" };
            }

            // Reset the anonymous user's progress and start from beginning
            await resetAnonymousUserProgress(puzzleType, difficulty);
            return await fetchNextPuzzle(ANONYMOUS_USER_ID, puzzleType, difficulty);
        }

        return result;

    } catch (err) {
        console.error("❌ Error in fetchRandomPuzzle:", err);
        return { success: false, message: "Error fetching puzzle" };
    }
}

async function resetAnonymousUserProgress(puzzleType, difficulty) {
    try {
        console.log(`🔄 Resetting anonymous user progress for ${puzzleType}/${difficulty}`);
        
        const userRef = db.collection("users").doc(ANONYMOUS_USER_ID);
        const progressKey = `${puzzleType.toLowerCase()}_${difficulty.toLowerCase()}`;
        
        // Reset the progress for this specific puzzle type/difficulty combination
        await userRef.update({
            [`puzzleProgress.${progressKey}.currentIndex`]: 0,
            [`puzzleProgress.${progressKey}.completed`]: false,
            [`puzzleProgress.${progressKey}.lastResetTimestamp`]: new Date().toISOString()
        });
        
        console.log(`✅ Reset progress for ${progressKey}`);
    } catch (error) {
        console.error("❌ Error resetting anonymous user progress:", error);
        // If update fails, try to create the document
        try {
            const userRef = db.collection("users").doc(ANONYMOUS_USER_ID);
            await userRef.set({
                email: "anonymous@sequential.user",
                lastPuzzleTimestamp: null,
                puzzlesAttempted: 0,
                puzzlesSolved: 0,
                lastPuzzleTimestamps: {},
                puzzleProgress: {}
            }, { merge: true });
        } catch (createError) {
            console.error("❌ Error creating anonymous user document:", createError);
        }
    }
}

export async function fetchCustomPuzzle(parentSetId) {
    console.log(`🔍 Fetching custom puzzle set parentSetId=${parentSetId} from Supabase...`);

    try {
        // Step 1: Check if puzzle set exists in puzzle_sets table
        const { data: puzzleSetData, error: setError } = await supabase
            .from('puzzle_sets')
            .select('*')
            .eq('id', parentSetId);

        if (setError) {
            console.error("❌ Error querying puzzle_sets:", setError);
            throw new Error("Error fetching puzzle set metadata.");
        }

        const puzzleSet = puzzleSetData?.[0];
        if (!puzzleSet) {
            console.warn(`⚠️ No puzzle set found for parentSetId=${parentSetId}`);
            throw new Error("Custom puzzle set not found.");
        }

        console.log(`📊 Found puzzle set: ${puzzleSet.name} (${puzzleSet.status}, ${puzzleSet.puzzle_count} puzzles)`);

        // Step 2: Fetch actual puzzles from puzzles table
        const { data: puzzleData, error: puzzleError } = await supabase
            .from('puzzles')
            .select('*')
            .eq('parentSetId', parentSetId)
            .not('puzzleid', 'is', null); // Only get actual puzzles, not status rows

        if (puzzleError) {
            console.error("❌ Error fetching puzzles:", puzzleError);
            throw new Error("Error fetching puzzle data.");
        }

        console.log(`📊 Found ${puzzleData?.length || 0} actual puzzles`);

        // Step 3: Handle missing puzzles with auto-regeneration
        if (!puzzleData || puzzleData.length === 0) {
            console.warn(`⚠️ No puzzles found for parentSetId=${parentSetId}`);
            
            // Check if this is a data inconsistency (set exists but no puzzles)
            if (puzzleSet.status === 'completed' && puzzleSet.puzzle_count > 0) {
                console.log(`🔧 Data inconsistency detected - triggering auto-regeneration...`);
                return await handleMissingPuzzlesWithRegeneration(parentSetId, puzzleSet);
            }
            
            // Check if generation is in progress
            if (puzzleSet.status === 'generating') {
                console.log(`⏳ Puzzle generation in progress...`);
                return {
                    status: 'generating',
                    message: 'Puzzle generation in progress. Please try again in a moment.',
                    estimatedTimeRemaining: '30-60 seconds',
                    puzzleSet: {
                        name: puzzleSet.name,
                        creator: puzzleSet.creator,
                        format: puzzleSet.format
                    }
                };
            }
            
            // Check if generation failed
            if (puzzleSet.status === 'failed') {
                console.log(`❌ Previous generation failed - attempting regeneration...`);
                return await handleFailedPuzzleSetRegeneration(parentSetId, puzzleSet);
            }
            
            throw new Error("No puzzles available for this set.");
        }

        // Step 4: Transform puzzles to expected format
        const puzzles = puzzleData.map(row => {
            const optionsArray = row.options || [];

            let puzzleFormat = "Q/A";
            if (optionsArray.length > 0) {
                puzzleFormat = "Multiple Choice";
            } else if (row.type?.toLowerCase().includes('anagram')) {
                puzzleFormat = "Anagram";
            } else if (row.type?.toLowerCase().includes('crossword')) {
                puzzleFormat = "Crossword";
            } else if (row.type?.toLowerCase().includes('wordsearch')) {
                puzzleFormat = "Word Search";
            }
        
            return {
                puzzleId: row.puzzleid,
                userId: row.source,
                topic: row.type,
                format: puzzleFormat,
                question: row.question,
                difficulty: row.difficulty,
                timestamp: row.timestamp,
                answer: row.correct_option || row.answer,
                options: optionsArray,
                hint: row.hint || ""
            };
        });

        // Step 5: Fetch leaderboard
        const { data: leaderboardData, error: leaderboardError } = await supabase
            .from('custom_leaderboard')
            .select('*')
            .eq('parentsetid', parentSetId);

        if (leaderboardError) {
            console.error("❌ Error fetching leaderboard:", leaderboardError);
            // Don't throw - leaderboard is optional
        }

        const leaderboard = (leaderboardData || []).map(entry => ({
            userId: entry.userid,
            timeTaken: entry.timetaken,
            score: entry.score
        }));

        // Step 6: Build response in the nested format the Android client expects
        const response = {
            userId: puzzleSet.creator,
            topic: puzzleSet.name,
            format: puzzleSet.format,
            numPuzzles: puzzles.length,
            createdAt: puzzleSet.created_at,
            updatedAt: puzzleSet.updated_at,
            puzzleData: {
                puzzleData: {  // ✅ Double nested structure to match Android client
                    puzzles,
                    leaderboard
                }
            }
        };

        console.log(`✅ Successfully fetched custom puzzle set with ${puzzles.length} puzzles`);
        console.log(`📋 Response structure:`, {
            hasUserId: !!response.userId,
            hasTopic: !!response.topic,
            hasFormat: !!response.format,
            hasPuzzleData: !!response.puzzleData,
            hasNestedPuzzleData: !!response.puzzleData.puzzleData,
            puzzleDataHasPuzzles: !!response.puzzleData.puzzleData.puzzles,
            puzzleDataHasLeaderboard: !!response.puzzleData.puzzleData.leaderboard,
            puzzleCount: response.puzzleData.puzzleData.puzzles.length
        });

        return response;

    } catch (error) {
        console.error("💥 Error in fetchCustomPuzzle:", error);
        throw error;
    }
}

// Handle data inconsistency with auto-regeneration
async function handleMissingPuzzlesWithRegeneration(parentSetId, puzzleSet) {
    console.log(`🔄 Handling missing puzzles for ${puzzleSet.name}...`);
    
    try {
        // Mark as generating to prevent concurrent regeneration
        await supabase
            .from('puzzle_sets')
            .update({
                status: 'generating',
                updated_at: new Date().toISOString()
            })
            .eq('id', parentSetId);

        console.log(`🚀 Starting background regeneration for ${puzzleSet.name}...`);
        
        // Start regeneration in background (don't await)
        triggerBackgroundRegeneration(parentSetId, puzzleSet)
            .then(result => {
                if (result.success) {
                    console.log(`✅ Background regeneration completed for ${parentSetId}`);
                } else {
                    console.error(`❌ Background regeneration failed for ${parentSetId}:`, result.error);
                }
            })
            .catch(error => {
                console.error(`💥 Background regeneration error for ${parentSetId}:`, error);
            });

        // Return immediate response indicating regeneration is happening
        return {
            status: 'regenerating',
            message: 'Puzzles are being regenerated. Please try again in a moment.',
            estimatedTimeRemaining: '30-60 seconds',
            puzzleSet: {
                name: puzzleSet.name,
                creator: puzzleSet.creator,
                format: puzzleSet.format,
                originalPuzzleCount: puzzleSet.puzzle_count
            },
            action: 'auto_regeneration_started'
        };

    } catch (error) {
        console.error("💥 Error handling missing puzzles:", error);
        
        // Mark as failed
        await supabase
            .from('puzzle_sets')
            .update({
                status: 'failed',
                updated_at: new Date().toISOString()
            })
            .eq('id', parentSetId);
            
        throw new Error("Failed to initiate puzzle regeneration");
    }
}

// Handle failed puzzle sets with regeneration option
async function handleFailedPuzzleSetRegeneration(parentSetId, puzzleSet) {
    console.log(`🔄 Handling failed puzzle set ${puzzleSet.name}...`);
    
    try {
        // Check how recent the failure was
        const failureTime = new Date(puzzleSet.updated_at);
        const now = new Date();
        const timeSinceFailure = now - failureTime;
        const oneHourInMs = 60 * 60 * 1000;
        
        // Only auto-regenerate if failure was recent (within 1 hour)
        if (timeSinceFailure < oneHourInMs) {
            console.log(`🔄 Recent failure detected, attempting regeneration...`);
            
            // Mark as generating
            await supabase
                .from('puzzle_sets')
                .update({
                    status: 'generating',
                    updated_at: new Date().toISOString()
                })
                .eq('id', parentSetId);

            // Start regeneration in background
            triggerBackgroundRegeneration(parentSetId, puzzleSet)
                .then(result => {
                    console.log(`Background regeneration result for ${parentSetId}:`, result);
                })
                .catch(error => {
                    console.error(`Background regeneration error for ${parentSetId}:`, error);
                });

            return {
                status: 'regenerating',
                message: 'Previous generation failed. Attempting to regenerate puzzles...',
                estimatedTimeRemaining: '30-60 seconds',
                puzzleSet: {
                    name: puzzleSet.name,
                    creator: puzzleSet.creator,
                    format: puzzleSet.format
                },
                action: 'auto_regeneration_after_failure'
            };
        } else {
            // Old failure - don't auto-regenerate
            return {
                status: 'failed',
                message: 'Puzzle generation failed. Please try creating the puzzle set again.',
                puzzleSet: {
                    name: puzzleSet.name,
                    creator: puzzleSet.creator,
                    format: puzzleSet.format
                },
                failedAt: puzzleSet.updated_at,
                action: 'manual_regeneration_required'
            };
        }

    } catch (error) {
        console.error("💥 Error handling failed puzzle set:", error);
        throw new Error("Failed to handle puzzle set regeneration");
    }
}

// Background regeneration function
async function triggerBackgroundRegeneration(parentSetId, puzzleSet) {
    console.log(`🚀 Starting background regeneration for ${parentSetId}...`);
    
    try {
        const targetPuzzleCount = Math.max(puzzleSet.puzzle_count || 5, 5); // At least 5 puzzles
        
        // Determine which generation function to use based on format
        let result;
        
        if (puzzleSet.format && puzzleSet.format.toLowerCase().includes('anagram')) {
            // Use anagram generation
            console.log(`🔤 Using anagram generation for ${puzzleSet.name}`);
            result = await generateCustomAnagrams(
                puzzleSet.name, 
                puzzleSet.format, 
                targetPuzzleCount, 
                puzzleSet.creator, 
                parentSetId
            );
        } else {
            // Use regular puzzle generation
            console.log(`🧩 Using regular puzzle generation for ${puzzleSet.name}`);
            result = await generateCustomPuzzles(
                puzzleSet.name, 
                puzzleSet.format, 
                targetPuzzleCount, 
                puzzleSet.creator, 
                parentSetId
            );
        }
        
        if (result && result.puzzleId) {
            console.log(`✅ Background regeneration successful for ${parentSetId}`);
            return { success: true, puzzleId: result.puzzleId };
        } else {
            console.error(`❌ Background regeneration failed for ${parentSetId}:`, result?.error);
            
            // Mark as failed
            await supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', parentSetId);
                
            return { success: false, error: result?.error || 'Unknown error' };
        }
        
    } catch (error) {
        console.error(`💥 Background regeneration failed for ${parentSetId}:`, error);
        
        // Mark as failed
        await supabase
            .from('puzzle_sets')
            .update({
                status: 'failed',
                updated_at: new Date().toISOString()
            })
            .eq('id', parentSetId);
            
        return { success: false, error: error.message };
    }
}

// Helper function to mark puzzle set as failed
async function markPuzzleSetAsFailed(puzzleId, reason) {
    console.log(`❌ Marking puzzle set ${puzzleId} as failed: ${reason}`);
    
    try {
        await Promise.all([
            supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: 'failed',
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);
        
        console.log(`✅ Puzzle set ${puzzleId} marked as failed`);
    } catch (error) {
        console.error(`💥 Error marking puzzle set as failed:`, error);
    }
}

export async function listCustomPuzzles(page = 1, limit = 10, offset = 0, userEmail = null) {
    try {
        console.log(`🔍 Listing custom puzzles (page=${page}, limit=${limit}, offset=${offset})`);

        // Build query against puzzle_sets table
        let query = supabase
            .from('puzzle_sets')
            .select('*', { count: 'exact' })
            .order('updated_at', { ascending: false })
            .range(offset, offset + limit - 1);

        if (userEmail) {
            query = query.eq('creator', userEmail);
        }

        const { data: puzzleSets, error, count } = await query;

        if (error) {
            throw new Error(`Failed to fetch puzzle sets: ${error.message}`);
        }

        if (!puzzleSets || puzzleSets.length === 0) {
            return {
                success: true,
                puzzles: [],
                page: Math.floor(offset / limit) + 1,
                totalPages: 0,
                totalCount: 0
            };
        }

        // Enhanced formatting with theme and metadata
        const formattedPuzzles = puzzleSets.map(set => {
            const theme = generateThemeFromTopic(set.name);
            
            return {
                id: set.id,
                name: set.name || "Unknown Topic",
                creator: set.creator || "Unknown Creator",
                status: set.status || "completed",
                createdAt: set.created_at,
                updatedAt: set.updated_at,
                format: set.format || "Q/A",
                numPuzzles: set.puzzle_count || 0,
                // Enhanced fields for better UI
                topic: extractTopicKeyword(set.name),
                themeColors: theme,
                difficulty: set.difficulty || determineDifficultyFromName(set.name),
                playCount: Math.floor(Math.random() * 100), // Placeholder - implement real tracking later
                averageRating: parseFloat((3.5 + Math.random() * 1.5).toFixed(1)) // Placeholder
            };
        });

        return {
            success: true,
            puzzles: formattedPuzzles,
            page: Math.floor(offset / limit) + 1,
            totalPages: Math.ceil(count / limit),
            totalCount: count
        };

    } catch (error) {
        console.error("❌ Error listing enhanced puzzles:", error);
        return {
            success: false,
            error: error.message,
            puzzles: [],
            page: 1,
            totalPages: 0,
            totalCount: 0
        };
    }
}

/**
 * Get puzzle leaderboard data
 */
export async function getPuzzleLeaderboard(puzzleId, userId = null) {
    try {
        console.log(`🏆 Fetching leaderboard for puzzleId=${puzzleId}`);

        if (!puzzleId) {
            throw new Error("PuzzleId is required");
        }

        // Fetch leaderboard entries
        const { data: leaderboardData, error: leaderboardError } = await supabase
            .from('custom_leaderboard')
            .select('*')
            .eq('parentsetid', puzzleId)
            .order('score', { ascending: false }) // Highest score first
            .order('timetaken', { ascending: true }) // Fastest time for ties
            .limit(50); // Top 50 players

        if (leaderboardError) {
            throw new Error(`Failed to fetch leaderboard: ${leaderboardError.message}`);
        }

        // Get puzzle info
        const { data: puzzleInfo, error: puzzleInfoError } = await supabase
            .from('puzzle_sets')
            .select('name, creator')
            .eq('id', puzzleId)
            .limit(1);

        if (puzzleInfoError) {
            console.warn("⚠️ Could not fetch puzzle info:", puzzleInfoError.message);
        }

        const puzzleName = puzzleInfo?.[0]?.name || "Unknown Puzzle";
        const puzzleCreator = puzzleInfo?.[0]?.creator || "Unknown Creator";

        if (!leaderboardData || leaderboardData.length === 0) {
            return {
                success: true,
                puzzleId,
                puzzleName,
                puzzleCreator,
                topPlayers: [],
                userRank: null,
                userScore: null,
                userTime: null,
                totalPlayers: 0,
                isEmpty: true
            };
        }

        // Resolve display names from Firebase Auth for player-friendly names
        const userIds = leaderboardData.slice(0, 10).map(e => e.userid).filter(Boolean);
        const displayNameMap = {};
        if (userIds.length > 0 && admin) {
            try {
                const result = await admin.auth().getUsers(userIds.map(uid => ({ uid })));
                result.users.forEach(user => {
                    displayNameMap[user.uid] = user.displayName || maskEmail(user.email || user.uid);
                });
            } catch (e) {
                console.warn('Could not resolve Firebase display names:', e.message);
            }
        }

        // Format top players (limit to top 10 for the response)
        const topPlayers = leaderboardData.slice(0, 10).map((entry, index) => ({
            rank: index + 1,
            playerName: displayNameMap[entry.userid] || maskEmail(entry.userid),
            userId: entry.userid, // Keep original for matching current user
            score: entry.score,
            completionTime: entry.timetaken,
            formattedTime: formatTime(entry.timetaken)
        }));

        // Find user's rank and stats if userId provided
        let userRank = null;
        let userScore = null;
        let userTime = null;
        let userPercentile = null;

        if (userId) {
            const userEntryIndex = leaderboardData.findIndex(entry => entry.userid === userId);
            if (userEntryIndex !== -1) {
                userRank = userEntryIndex + 1;
                userScore = leaderboardData[userEntryIndex].score;
                userTime = leaderboardData[userEntryIndex].timetaken;
                
                // Calculate percentile
                const totalPlayers = leaderboardData.length;
                userPercentile = totalPlayers > 1 ? 
                    Math.round(((totalPlayers - userRank + 1) / totalPlayers) * 100) : 100;
            }
        }

        // Calculate leaderboard statistics
        const scores = leaderboardData.map(entry => entry.score);
        const times = leaderboardData.map(entry => entry.timetaken);
        
        const statistics = {
            totalPlayers: leaderboardData.length,
            highestScore: Math.max(...scores),
            lowestScore: Math.min(...scores),
            averageScore: Math.round(scores.reduce((a, b) => a + b, 0) / scores.length),
            fastestTime: Math.min(...times),
            slowestTime: Math.max(...times),
            averageTime: Math.round(times.reduce((a, b) => a + b, 0) / times.length)
        };

        return {
            success: true,
            puzzleId,
            puzzleName,
            puzzleCreator,
            topPlayers,
            userRank,
            userScore,
            userTime,
            userPercentile,
            totalPlayers: leaderboardData.length,
            statistics,
            isEmpty: false
        };

    } catch (error) {
        console.error(`❌ Error fetching leaderboard for ${puzzleId}:`, error);
        return {
            success: false,
            error: error.message,
            puzzleId,
            topPlayers: [],
            userRank: null,
            totalPlayers: 0,
            isEmpty: true
        };
    }
}

/**
 * Get featured/trending puzzles
 */
export async function getFeaturedPuzzles(limit = 5) {
    try {
        console.log(`⭐ Fetching featured puzzles (limit=${limit})`);

        // Get recently created, completed puzzles with good puzzle counts
        const { data: featuredPuzzles, error } = await supabase
            .from('puzzle_sets')
            .select('*')
            .eq('status', 'completed')
            .gte('puzzle_count', 3) // At least 3 puzzles
            .order('created_at', { ascending: false })
            .limit(parseInt(limit));

        if (error) {
            throw new Error(`Failed to fetch featured puzzles: ${error.message}`);
        }

        // Format with enhanced data
        const formattedFeatured = (featuredPuzzles || []).map(set => {
            const theme = generateThemeFromTopic(set.name);
            
            return {
                id: set.id,
                name: set.name,
                creator: set.creator,
                format: set.format,
                numPuzzles: set.puzzle_count,
                createdAt: set.created_at,
                updatedAt: set.updated_at,
                topic: extractTopicKeyword(set.name),
                themeColors: theme,
                difficulty: set.difficulty || determineDifficultyFromName(set.name),
                averageRating: parseFloat((4.0 + Math.random() * 1.0).toFixed(1)), // Featured puzzles have higher ratings
                playCount: Math.floor(Math.random() * 100),
                completionCount: Math.floor(Math.random() * 50),
                isFeatured: true
            };
        });

        return {
            success: true,
            featuredPuzzles: formattedFeatured,
            count: formattedFeatured.length
        };

    } catch (error) {
        console.error("❌ Error fetching featured puzzles:", error);
        return {
            success: false,
            error: error.message,
            featuredPuzzles: [],
            count: 0
        };
    }
}

/**
 * Core function to update puzzle leaderboard in Supabase
 */
async function updatePuzzleLeaderboardCore(puzzleId, userId, timeTaken, score) {
    try {
        console.log(`🏆 Updating leaderboard for ${puzzleId}, user: ${userId}, score: ${score}, time: ${timeTaken}ms`);

        if (!puzzleId || !userId || timeTaken === undefined || score === undefined) {
            throw new Error("Missing required parameters: puzzleId, userId, timeTaken, or score");
        }

        const parsedScore = parseInt(score);
        const parsedTime = parseInt(timeTaken);

        // Check if entry already exists for this user + puzzle
        const { data: existing, error: checkError } = await supabase
            .from('custom_leaderboard')
            .select('*')
            .eq('parentsetid', puzzleId)
            .eq('userid', userId)
            .limit(1);

        if (checkError) {
            throw new Error(`Failed to check existing entry: ${checkError.message}`);
        }

        if (existing && existing.length > 0) {
            // Update only if new score is better (or same score with faster time)
            const current = existing[0];
            const shouldUpdate = parsedScore > current.score ||
                (parsedScore === current.score && parsedTime < current.timetaken);

            if (shouldUpdate) {
                const { error: updateError } = await supabase
                    .from('custom_leaderboard')
                    .update({
                        score: parsedScore,
                        timetaken: parsedTime,
                        createdat: new Date().toISOString()
                    })
                    .eq('parentsetid', puzzleId)
                    .eq('userid', userId);

                if (updateError) {
                    throw new Error(`Failed to update leaderboard: ${updateError.message}`);
                }
            }
        } else {
            // Insert new entry
            const { error: insertError } = await supabase
                .from('custom_leaderboard')
                .insert({
                    parentsetid: puzzleId,
                    userid: userId,
                    timetaken: parsedTime,
                    score: parsedScore,
                    createdat: new Date().toISOString()
                });

            if (insertError) {
                throw new Error(`Failed to insert leaderboard entry: ${insertError.message}`);
            }
        }

        // Fetch updated leaderboard for this puzzle
        const { data: leaderboardData, error: fetchError } = await supabase
            .from('custom_leaderboard')
            .select('*')
            .eq('parentsetid', puzzleId)
            .order('score', { ascending: false })
            .order('timetaken', { ascending: true });

        if (fetchError) {
            throw new Error(`Failed to fetch updated leaderboard: ${fetchError.message}`);
        }

        // Return the leaderboard in the expected format
        return (leaderboardData || []).map(entry => ({
            userId: entry.userid,
            timeTaken: entry.timetaken,
            score: entry.score,
            timestamp: entry.timestamp
        }));

    } catch (error) {
        console.error(`❌ Error in updatePuzzleLeaderboardCore:`, error);
        throw error;
    }
}

/**
 * Enhanced updatePuzzleLeaderboard with better response formatting
 */
export async function updatePuzzleLeaderboard(puzzleId, userId, timeTaken, score) {
    try {
        console.log(`🏆 Updating enhanced leaderboard for ${puzzleId}, user: ${userId}`);

        // Use the base updatePuzzleLeaderboard function
        const leaderboard = await updatePuzzleLeaderboardCore(puzzleId, userId, timeTaken, score);

        // Enhanced formatting with rankings and stats
        const formattedLeaderboard = leaderboard.map((entry, index) => ({
            rank: index + 1,
            userId: entry.userId,
            timeTaken: entry.timeTaken,
            score: entry.score,
            formattedTime: formatTime(entry.timeTaken)
        }));

        // Calculate user's new rank and stats
        const userRank = formattedLeaderboard.findIndex(entry => entry.userId === userId) + 1;
        const totalPlayers = formattedLeaderboard.length;

        // Calculate percentile
        const percentile = totalPlayers > 1 ? 
            Math.round(((totalPlayers - userRank + 1) / totalPlayers) * 100) : 100;

        // Check if this is a new record
        const isNewRecord = userRank === 1 && totalPlayers > 1;
        const isPersonalBest = true; // This would need to be tracked separately

        // Leaderboard statistics
        const leaderboardStats = {
            totalPlayers,
            highestScore: formattedLeaderboard[0]?.score || score,
            averageScore: Math.round(
                formattedLeaderboard.reduce((sum, entry) => sum + entry.score, 0) / totalPlayers
            ),
            fastestTime: Math.min(...formattedLeaderboard.map(entry => entry.timeTaken))
        };

        return {
            success: true,
            leaderboard: formattedLeaderboard,
            userStats: {
                rank: userRank,
                totalPlayers,
                percentile,
                score,
                timeTaken,
                formattedTime: formatTime(timeTaken),
                isNewRecord,
                isPersonalBest,
                improvement: calculateImprovement(userId, score) // Placeholder
            },
            leaderboardStats,
            achievements: generateAchievements(userRank, score, timeTaken, isNewRecord)
        };

    } catch (error) {
        console.error("❌ Error updating enhanced leaderboard:", error);
        return {
            success: false,
            error: error.message,
            leaderboard: [],
            userStats: null,
            leaderboardStats: null
        };
    }
}

// Helper functions for theme generation and topic extraction
function generateThemeFromTopic(topicName) {
    if (!topicName) return { primaryColor: "#667eea", secondaryColor: "#764ba2", emoji: "🧩" };
    
    const topic = topicName.toLowerCase();
    
    const themeMap = {
        'math': { primaryColor: "#667eea", secondaryColor: "#764ba2", emoji: "🔢" },
        'number': { primaryColor: "#667eea", secondaryColor: "#764ba2", emoji: "🔢" },
        'calculation': { primaryColor: "#667eea", secondaryColor: "#764ba2", emoji: "🔢" },
        'science': { primaryColor: "#4ECDC4", secondaryColor: "#44A08D", emoji: "🔬" },
        'physics': { primaryColor: "#4ECDC4", secondaryColor: "#44A08D", emoji: "🔬" },
        'chemistry': { primaryColor: "#4ECDC4", secondaryColor: "#44A08D", emoji: "🔬" },
        'history': { primaryColor: "#FFE066", secondaryColor: "#FF9472", emoji: "🏛️" },
        'ancient': { primaryColor: "#FFE066", secondaryColor: "#FF9472", emoji: "🏛️" },
        'war': { primaryColor: "#FFE066", secondaryColor: "#FF9472", emoji: "🏛️" },
        'geography': { primaryColor: "#74B9FF", secondaryColor: "#0984E3", emoji: "🌍" },
        'country': { primaryColor: "#74B9FF", secondaryColor: "#0984E3", emoji: "🌍" },
        'capital': { primaryColor: "#74B9FF", secondaryColor: "#0984E3", emoji: "🌍" },
        'sport': { primaryColor: "#00B894", secondaryColor: "#00CEC9", emoji: "⚽" },
        'game': { primaryColor: "#00B894", secondaryColor: "#00CEC9", emoji: "⚽" },
        'olympic': { primaryColor: "#00B894", secondaryColor: "#00CEC9", emoji: "⚽" },
        'music': { primaryColor: "#A29BFE", secondaryColor: "#6C5CE7", emoji: "🎵" },
        'song': { primaryColor: "#A29BFE", secondaryColor: "#6C5CE7", emoji: "🎵" },
        'artist': { primaryColor: "#A29BFE", secondaryColor: "#6C5CE7", emoji: "🎵" },
        'movie': { primaryColor: "#FF6B6B", secondaryColor: "#FF8E8E", emoji: "🎬" },
        'film': { primaryColor: "#FF6B6B", secondaryColor: "#FF8E8E", emoji: "🎬" },
        'cinema': { primaryColor: "#FF6B6B", secondaryColor: "#FF8E8E", emoji: "🎬" },
        'book': { primaryColor: "#A8E6CF", secondaryColor: "#7FD8BE", emoji: "📚" },
        'literature': { primaryColor: "#A8E6CF", secondaryColor: "#7FD8BE", emoji: "📚" },
        'author': { primaryColor: "#A8E6CF", secondaryColor: "#7FD8BE", emoji: "📚" },
        'tech': { primaryColor: "#FD79A8", secondaryColor: "#E84393", emoji: "💻" },
        'computer': { primaryColor: "#FD79A8", secondaryColor: "#E84393", emoji: "💻" },
        'ai': { primaryColor: "#FD79A8", secondaryColor: "#E84393", emoji: "💻" },
        'nature': { primaryColor: "#00B894", secondaryColor: "#55A3FF", emoji: "🌿" },
        'animal': { primaryColor: "#00B894", secondaryColor: "#55A3FF", emoji: "🌿" },
        'plant': { primaryColor: "#00B894", secondaryColor: "#55A3FF", emoji: "🌿" },
        'food': { primaryColor: "#FDCB6E", secondaryColor: "#E17055", emoji: "🍕" },
        'cooking': { primaryColor: "#FDCB6E", secondaryColor: "#E17055", emoji: "🍕" },
        'recipe': { primaryColor: "#FDCB6E", secondaryColor: "#E17055", emoji: "🍕" },
        'art': { primaryColor: "#FD79A8", secondaryColor: "#FDCB6E", emoji: "🎨" },
        'paint': { primaryColor: "#FD79A8", secondaryColor: "#FDCB6E", emoji: "🎨" },
        'draw': { primaryColor: "#FD79A8", secondaryColor: "#FDCB6E", emoji: "🎨" }
    };

    // Find matching theme
    for (const [keyword, theme] of Object.entries(themeMap)) {
        if (topic.includes(keyword)) {
            return theme;
        }
    }
    
    // Default theme
    return { primaryColor: "#667eea", secondaryColor: "#764ba2", emoji: "🧩" };
}

function extractTopicKeyword(puzzleName) {
    if (!puzzleName) return null;
    
    const topic = puzzleName.toLowerCase();
    const keywords = [
        'math', 'science', 'history', 'geography', 'sports', 'music', 
        'movies', 'books', 'technology', 'nature', 'food', 'art'
    ];
    
    for (const keyword of keywords) {
        if (topic.includes(keyword)) {
            return keyword;
        }
    }
    
    return null;
}

function determineDifficultyFromName(puzzleName) {
    if (!puzzleName) return 'Medium';
    
    const name = puzzleName.toLowerCase();
    
    if (name.includes('easy') || name.includes('basic') || name.includes('simple')) {
        return 'Easy';
    } else if (name.includes('hard') || name.includes('difficult') || name.includes('advanced')) {
        return 'Hard';
    } else if (name.includes('expert') || name.includes('master') || name.includes('pro')) {
        return 'Expert';
    } else {
        return 'Medium';
    }
}

function formatTime(timeInMs) {
    if (!timeInMs || timeInMs < 0) return "0s";
    
    const seconds = Math.floor(timeInMs / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    
    if (minutes > 0) {
        return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
    } else {
        return `${remainingSeconds}s`;
    }
}

function calculateImprovement(userId, currentScore) {
    // Placeholder for improvement calculation
    // This would need to compare against user's previous scores
    return Math.floor(Math.random() * 20) - 10; // -10 to +10 points
}

function generateAchievements(rank, score, timeTaken, isNewRecord) {
    const achievements = [];
    
    if (isNewRecord) {
        achievements.push({
            id: 'new_record',
            title: '🏆 New Record!',
            description: 'You set a new high score!'
        });
    }
    
    if (rank <= 3) {
        achievements.push({
            id: 'top_3',
            title: '🥉 Top 3!',
            description: `You ranked #${rank}!`
        });
    }
    
    if (score >= 90) {
        achievements.push({
            id: 'high_score',
            title: '⭐ High Scorer',
            description: 'Scored 90% or higher!'
        });
    }
    
    if (timeTaken < 60000) { // Less than 1 minute
        achievements.push({
            id: 'speed_demon',
            title: '⚡ Speed Demon',
            description: 'Completed in under 1 minute!'
        });
    }
    
    return achievements;
}


function toCamelCase(str) {
    return str
      .toLowerCase()
      .replace(/(?:^|\s|_|-)\w/g, match => match.toUpperCase())
      .replace(/[_-]/g, ' ')
      .trim();
  }


export async function deletePuzzleLeaderboardEntry(puzzleId, userId) {
    // 📌 Locate the puzzle file in storage
    const filePath = `riddles/${puzzleId.split("-")[1]}.json`; // Extract timestamp-based filename
    const file = storage.bucket(BUCKET_NAME).file(filePath);

    // ✅ Check if the file exists
    const [exists] = await file.exists();
    if (!exists) {
        console.error(`❌ Puzzle file not found: ${filePath}`);
        return { error: "Puzzle not found." };
    }

    // ✅ Read the existing puzzle data
    const [puzzleData] = await file.download();
    let puzzleJson = JSON.parse(puzzleData.toString());

    // 🔹 Ensure `puzzleData` and `leaderboard` exist
    if (!puzzleJson.puzzleData || !Array.isArray(puzzleJson.puzzleData.leaderboard)) {
        return { error: "Leaderboard not found." };
    }

    // 🔹 Check if user exists in leaderboard
    const initialLength = puzzleJson.puzzleData.leaderboard.length;
    puzzleJson.puzzleData.leaderboard = puzzleJson.puzzleData.leaderboard.filter(entry => entry.userId !== userId);

    // 🔹 If no change was made, user wasn't found
    if (puzzleJson.puzzleData.leaderboard.length === initialLength) {
        return { error: "User not found in leaderboard." };
    }

    // ✅ Save updated puzzle data
    await file.save(JSON.stringify(puzzleJson, null, 2));

    console.log(`✅ Leaderboard entry deleted for ${userId} in ${filePath}`);
    return puzzleJson.puzzleData.leaderboard;
}


async function createPuzzles(topic, batchSize, format) {
    const prompt = `
You are a riddle generator API. Given a topic and format, generate ${batchSize} unique riddles.

1. If the topic or format is unsafe, harmful, illegal, offensive, or unsuitable, reply with:
   { "error": "Invalid or unsafe topic" }

2. Otherwise, respond ONLY with a JSON array of riddles in this format:
[
  {
    "question": "What has keys but can't open locks?",
    "answer": "A piano",
    "options": ["A piano", "A keyboard", "A safe", "A keychain"],
    "hint": "It's a musical instrument",
    "difficulty": "Medium"
  },
  ...
]

For difficulty, assign one of: "Easy", "Medium", "Hard", "Expert" based on the complexity of the riddle.

Topic: "${topic}"
Format: "${format}"
Respond ONLY with valid JSON.`.trim();

    try {
        const riddleData = await retryWithBackoff(() => callAI(prompt, null, 2, {
            category: USAGE_CATEGORIES.CUSTOM_PUZZLE_GENERATION,
            puzzleType: format,
            difficulty: 'medium'
        }));

        // LLM might return either an array or an error object
        const parsed = JSON.parse(riddleData);
        if (Array.isArray(parsed)) {
            // Ensure all puzzles have difficulty set
            return parsed.map(puzzle => ({
                ...puzzle,
                difficulty: puzzle.difficulty || "Medium" // Fallback to Medium
            }));
        } else if (parsed && parsed.error) {
            return { error: parsed.error };
        } else {
            return { error: "Invalid format from AI" };
        }
    } catch (error) {
        console.error("❌ Error calling AI:", error);
        return { error: "AI generation failed" };
    }
}

async function retryWithBackoff(fn, retries = 5, delay = 500) {
    for (let i = 0; i < retries; i++) {
        try {
            return await fn();
        } catch (err) {
            console.warn(`⏳ Retry ${i + 1}/${retries} failed: ${err.message}`);
            if (i < retries - 1) {
                await new Promise(res => setTimeout(res, delay * Math.pow(2, i))); // Exponential backoff
            } else {
                throw err;
            }
        }
    }
}

export async function generateCustomPuzzles(topic, format, numPuzzles, userId, puzzleId, skipLLMValidation = false) {
    topic = toCamelCase(topic);
    console.log(`🚀 Starting generateCustomPuzzles for topic="${topic}", format="${format}", numPuzzles=${numPuzzles}, userId=${userId}, puzzleId=${puzzleId}, skipLLMValidation=${skipLLMValidation}`);

    try {
        // Phase 0: Validate topic and format (conditionally)
        if (!skipLLMValidation) {
            console.log("🛡️ Validating and potentially correcting topic with LLM...");
            const validationPrompt = `
        You are a content safety validator and topic corrector. Your job is to:
        1. Check if topics are SAFE and APPROPRIATE for public riddles
        2. Provide a corrected/clarified version of the topic if needed
        
        ONLY reject topics that contain:
        - Inappropriate, offensive, or harmful content
        - Adult content or violence
        - Discriminatory language or hate speech
        - Illegal activities
        - Personal attacks or harassment
        
        ALWAYS ACCEPT and correct topics that are:
        - Educational (math, science, history, etc.)
        - General knowledge or trivia
        - Hobbies and interests
        - Entertainment topics
        - Topics with minor typos or unclear phrasing
        
        Examples:
        - "About Riddles Mahts" → corrected to "About Riddles Math"
        - "Science Questons" → corrected to "Science Questions"
        - "Histroy Facts" → corrected to "History Facts"
        - "Movies and TV Shows" → no correction needed
        - "Sports Trivia" → no correction needed
        
        Topic: "${topic}"
        Format: "${format}"
        
        Respond ONLY with valid JSON:
        - If safe and appropriate: { "valid": true, "correctedTopic": "corrected version or original if no correction needed" }
        - If unsafe or inappropriate: { "valid": false, "reason": "explain the safety concern" }
            `.trim();
        
            try {
                const validationResult = await callAI(validationPrompt, null, 2, {
                    category: USAGE_CATEGORIES.CONTENT_VALIDATION,
                    puzzleType: format,
                    difficulty: 'medium'
                });
                console.log("🧠 Validation LLM responded:", validationResult);
        
                const parsedValidation = JSON.parse(validationResult);
        
                if (!parsedValidation.valid) {
                    console.warn(`❌ LLM rejected topic/format. Safety reason: ${parsedValidation.reason || "Unknown"}`);
                    
                    // Create/update the puzzle set record with failed status
                    await supabase
                        .from('puzzle_sets')
                        .upsert({
                            id: puzzleId,
                            name: topic,
                            creator: userId,
                            status: 'failed',
                            format: format,
                            puzzle_count: 0,
                            created_at: new Date().toISOString(),
                            updated_at: new Date().toISOString()
                        });
                    
                    // Also store the status row in puzzles table
                    await supabase
                        .from('puzzles')
                        .insert({
                            parentSetId: puzzleId,
                            source: userId,
                            type: topic,
                            status: 'failed',
                            difficulty: 'medium',
                            timestamp: new Date().toISOString()
                        });
        
                    return { error: parsedValidation.reason || "Topic rejected for safety reasons" };
                }
        
                // Use the corrected topic if provided
                const correctedTopic = parsedValidation.correctedTopic || topic;
                if (correctedTopic !== topic) {
                    console.log(`📝 Topic corrected: "${topic}" → "${correctedTopic}"`);
                    topic = correctedTopic; // Update the topic variable to use corrected version
                }
        
                console.log("✅ Topic validated and corrected successfully!");
            } catch (error) {
                console.error("❌ Validation LLM call failed:", error);
                // If validation fails due to technical issues, proceed anyway
                console.log("⚠️ Validation failed due to technical error, proceeding with generation...");
            }
        } else {
            console.log("⚡ Skipping LLM validation as requested");
        }

        // Phase 1: Create initial records in both tables
        console.log("📂 Creating initial records in Supabase...");
        
        // First create/update the puzzle set record
        await supabase
            .from('puzzle_sets')
            .upsert({
                id: puzzleId,
                name: topic,
                creator: userId,
                status: 'generating',
                format: format,
                puzzle_count: 0, // Will be updated later
                created_at: new Date().toISOString(),
                updated_at: new Date().toISOString()
            });
        
        // Then create the status row in puzzles table
        await supabase
            .from('puzzles')
            .insert({
                parentSetId: puzzleId,
                source: userId,
                type: topic,
                status: 'generating',
                difficulty: 'medium',
                timestamp: new Date().toISOString()
            });

        // Phase 2: Route to appropriate generation method based on format
        const formatLower = format.toLowerCase();
        
        if (formatLower.includes('anagram')) {
            console.log("🔤 Using custom anagram generation...");
            return await generateCustomAnagrams(topic, format, numPuzzles, userId, puzzleId);
        }
        
        if (formatLower.includes('crossword')) {
            console.log("🧩 Using custom crossword generation...");
            return await generateCustomCrosswords(topic, format, numPuzzles, userId, puzzleId);
        }

        if (formatLower.includes('word search') || formatLower.includes('wordsearch')) {
            console.log("🔍 Using custom word search generation...");
            return await generateCustomWordSearch(topic, numPuzzles, userId, puzzleId, 'medium');
        }

        if (formatLower.includes('image puzzle') || formatLower.includes('image')) {
            console.log("🖼️ Using image puzzle generation...");
            return await generateCustomImagePuzzles(topic, format, numPuzzles, userId, puzzleId);
        }

        if (formatLower.includes('music') || formatLower.includes('song')) {
            console.log("🎵 Using custom music generation...");
            return await generateCustomMusicPuzzles(topic, format, numPuzzles, userId, puzzleId);
        }

        if (formatLower.includes('word snake') || formatLower.includes('wordsnake')) {
            console.log("🐍 Using existing word snake service for custom generation...");
            return await generateCustomWordSnakePuzzles(topic, format, numPuzzles, userId, puzzleId);
        }

        if (formatLower.includes('progressive') || formatLower.includes('revelation')) {
            console.log('🔮 Using progressive revelation generation...');
            return await generateCustomProgressivePuzzles(topic, format, numPuzzles, userId, puzzleId);
        }        

        // Phase 3: Standard Q&A puzzle generation (existing logic)
        console.log("🧩 Using standard Q&A generation...");
        let generatedPuzzles = [];
        let existingQuestions = new Set();

        console.log("🔁 Starting puzzle generation loop...");
        while (generatedPuzzles.length < numPuzzles) {
            let remaining = numPuzzles - generatedPuzzles.length;
            let batchSize = Math.min(5, remaining);

            console.log(`🧩 Requesting ${batchSize} new puzzles (remaining: ${remaining})...`);

            let newPuzzles = await createPuzzles(topic, batchSize, format);

            if (newPuzzles.error) {
                console.warn(`❌ Puzzle generation failed: ${newPuzzles.error}`);
                
                // Update both tables to failed status
                await Promise.all([
                    supabase
                        .from('puzzle_sets')
                        .update({
                            status: 'failed',
                            updated_at: new Date().toISOString()
                        })
                        .eq('id', puzzleId),
                    
                    supabase
                        .from('puzzles')
                        .update({
                            status: 'failed',
                            timestamp: new Date().toISOString()
                        })
                        .eq('parentSetId', puzzleId)
                        .is('puzzleid', null)
                ]);

                return { error: newPuzzles.error };
            }

            console.log(`🔎 Received ${newPuzzles.length} new puzzles to process.`);

            for (let puzzle of newPuzzles) {
                console.log(`📌 Checking puzzle for duplicates: "${puzzle.question}"`);

                const isDuplicate = await isDuplicateCustomQuestion(format, puzzle.question, existingQuestions);

                if (!isDuplicate) {
                    console.log(`✅ Puzzle accepted: "${puzzle.question}"`);

                    // Insert this puzzle row into puzzles table
                    await supabase
                        .from('puzzles')
                        .insert({
                            parentSetId: puzzleId,
                            puzzleid: crypto.randomUUID(),
                            source: userId,
                            type: topic,
                            status: 'completed',
                            difficulty: (puzzle.difficulty || 'medium').toLowerCase(),
                            question: puzzle.question,
                            answer: puzzle.answer,
                            options: puzzle.options || [],
                            hint: puzzle.hint || "",
                            timestamp: new Date().toISOString()
                        });

                    existingQuestions.add(puzzle.question);
                    generatedPuzzles.push(puzzle);
                } else {
                    console.warn(`⚠️ Duplicate puzzle skipped: "${puzzle.question}"`);
                }
            }

            if (newPuzzles.length === 0 || generatedPuzzles.length >= numPuzzles) {
                console.warn("⚠️ No unique puzzles added in this batch. Ending generation...");
                break;
            }
        }

        // Phase 4: Final updates to both tables
        console.log("📚 Finalizing records...");
        const finalStatus = generatedPuzzles.length > 0 ? 'completed' : 'failed';
        
        await Promise.all([
            // Update puzzle_sets with final status and count
            supabase
                .from('puzzle_sets')
                .update({
                    status: finalStatus,
                    puzzle_count: generatedPuzzles.length,
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            // Update the status row in puzzles table
            supabase
                .from('puzzles')
                .update({
                    status: finalStatus,
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);

        // Send completion notification
        if (generatedPuzzles.length > 0) {
            console.log(`🎉 Puzzles generated and stored successfully: ${generatedPuzzles.length} puzzles.`);
            
            // Send success notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                format, 
                generatedPuzzles.length, 
                true
            );
            
            return { puzzleId };
        } else {
            console.warn("❌ Puzzle generation failed. No puzzles stored.");
            return { error: "No puzzles could be generated" };
        }

    } catch (error) {
        console.error("💥 Fatal error during puzzle generation:", error);
        
        // Ensure we mark as failed in both tables if something crashes
        await Promise.allSettled([
            supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: 'failed',
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);
        
        return { error: error.message };
    }
}

async function generateCustomImagePuzzles(topic, format, numPuzzles, userId, puzzleId) {
    console.log(`🖼️ Starting custom image puzzle generation for topic="${topic}", numPuzzles=${numPuzzles}`);
    const startTime = Date.now();
    
    try {
        // Validate inputs
        if (!topic || !userId || !puzzleId) {
            throw new Error('Missing required parameters: topic, userId, or puzzleId');
        }

        if (numPuzzles < 1 || numPuzzles > 3) {
            numPuzzles = 3;
            console.log('Image puzzles: Number of puzzles must be between 1 and 3');
        }

        // Set debug mode for image generator
        imagePuzzleGenerator.setDebugMode(true);

        // Generate image puzzles
        const generatedPuzzles = [];
        const maxAttemptsPerPuzzle = 3;

        for (let puzzleIndex = 0; puzzleIndex < numPuzzles; puzzleIndex++) {
            console.log(`🖼️ Generating image puzzle ${puzzleIndex + 1}/${numPuzzles}...`);
            
            let puzzleGenerated = false;
            let attempts = 0;

            while (!puzzleGenerated && attempts < maxAttemptsPerPuzzle) {
                attempts++;
                
                try {
                    console.log(`🎯 Attempt ${attempts}/${maxAttemptsPerPuzzle} for image puzzle ${puzzleIndex + 1}`);
                    
                    // Get image puzzle options based on topic
                    const imagePuzzleOptions = getImagePuzzleOptionsForTopic(topic);
                    
                    // ✅ FIXED: The generator already formats the data, just use it directly
                    const result = await imagePuzzleGenerator.generateImagePuzzle('medium', {
                        maxRetries: 2,
                        preferredCategory: imagePuzzleOptions.category,
                        preferredTags: imagePuzzleOptions.tags,
                        customTheme: imagePuzzleOptions.customTheme
                    });
                    
                    // ✅ FIXED: Only check if generation was successful, no validation
                    if (!result.success) {
                        console.warn(`⚠️ Image puzzle generation failed: ${result.message}`);
                        continue; // Try again
                    }

                    // ✅ FIXED: Use the already formatted data directly from the generator
                    const formattedPuzzle = result.puzzleData;
                    
                    // ✅ FIXED: Store directly without additional formatting or validation
                    console.log(`💾 Storing image puzzle in database...`);
                    const insertResult = await supabase
                        .from('puzzles')
                        .insert({
                            parentSetId: puzzleId,
                            puzzleid: crypto.randomUUID(),
                            source: userId,
                            type: "imagepuzzle",
                            status: 'completed',
                            difficulty: formattedPuzzle.difficulty || 'medium',
                            question: formattedPuzzle.question, // Already formatted JSON string
                            answer: formattedPuzzle.answer,     // Already formatted JSON string
                            options: [],
                            hint: formattedPuzzle.hint,
                            timestamp: new Date().toISOString()
                        })
                        .select();

                    if (insertResult.error) {
                        console.error(`❌ Database insert failed:`, insertResult.error);
                        continue; // Try again
                    }

                    // Success!
                    generatedPuzzles.push(result);
                    puzzleGenerated = true;
                    
                    console.log(`✅ Image puzzle ${puzzleIndex + 1} generated and stored successfully`);
                    
                } catch (error) {
                    console.error(`❌ Error in image puzzle generation attempt ${attempts}:`, error.message);
                    
                    if (attempts >= maxAttemptsPerPuzzle) {
                        console.warn(`⚠️ Failed to generate image puzzle ${puzzleIndex + 1} after ${attempts} attempts`);
                    }
                }
            }

            // ✅ Continue to next puzzle instead of failing completely if one puzzle fails
            if (!puzzleGenerated) {
                console.error(`❌ Failed to generate valid image puzzle ${puzzleIndex + 1} after ${maxAttemptsPerPuzzle} attempts`);
            }

            // Add delay between puzzles to be respectful to DALL-E API
            if (puzzleIndex < numPuzzles - 1) {
                console.log(`⏸️ Waiting 3 seconds before next image generation...`);
                await new Promise(resolve => setTimeout(resolve, 3000));
            }
        }

        // Final status update
        const finalStatus = generatedPuzzles.length > 0 ? 'completed' : 'failed';
        
        await Promise.all([
            supabase
                .from('puzzle_sets')
                .update({
                    status: finalStatus,
                    puzzle_count: generatedPuzzles.length,
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: finalStatus,
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);

        const elapsedTime = (Date.now() - startTime) / 1000;
        console.log(`⏱️ Image puzzle generation completed in ${elapsedTime.toFixed(1)}s`);

        if (generatedPuzzles.length > 0) {
            console.log(`🎉 Generated ${generatedPuzzles.length}/${numPuzzles} AI image puzzles successfully`);
            
            // Send success notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Image Puzzle', 
                generatedPuzzles.length, 
                true
            );
            
            return { puzzleId };
        } else {
            console.error(`❌ No valid image puzzles could be generated after all attempts`);
            
            // Send failure notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Image Puzzle', 
                numPuzzles, 
                false, 
                "Failed to generate any valid image puzzles"
            );
            
            return { error: "Failed to generate any valid image puzzles" };
        }

    } catch (error) {
        console.error(`💥 Fatal error in image puzzle generation:`, error);
        
        // Mark as failed
        await Promise.allSettled([
            supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: 'failed',
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);
        
        // Send failure notification
        await sendCustomPuzzleCompletionNotification(
            userId, 
            topic, 
            'Image Puzzle', 
            numPuzzles, 
            false, 
            error.message
        );
        
        return { error: error.message };
    }
}


/**
 * Generate custom music puzzles for specific countries/languages
 */
async function generateCustomMusicPuzzles(topic, format, numPuzzles, userId, puzzleId) {
    console.log(`🎵 Starting custom music puzzle generation for topic="${topic}", numPuzzles=${numPuzzles}`);
    const startTime = Date.now();
    
    try {
        // Validate inputs
        if (!topic || !userId || !puzzleId) {
            throw new Error('Missing required parameters: topic, userId, or puzzleId');
        }

        // Cap at 1 music puzzle for quality and API limits
        if (numPuzzles > 1) {
            numPuzzles = 1;
            console.log('Music puzzles: Limited to 1 puzzle to ensure quality and respect API limits');
        }

        // Set debug mode
        musicPuzzleGenerator.setDebugMode(true);
        
        const generatedPuzzles = [];
        const maxAttempts = 3;

        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            console.log(`🎯 Music generation attempt ${attempt}/${maxAttempts}`);
            
            try {
                // FIXED: Simple call - just pass the topic as the search query
                const result = await musicPuzzleGenerator.generateMusicPuzzle('medium', topic);
                
                if (!result || !result.success) {
                    console.warn(`⚠️ Music generation failed: ${result?.message || 'Unknown error'}`);
                    if (attempt === maxAttempts) {
                        throw new Error(result?.message || 'Music generation failed after all attempts');
                    }
                    continue;
                }

                const puzzleData = result.data;
                
                // Validate we got songs
                if (!puzzleData || !puzzleData.songs || puzzleData.songs.length === 0) {
                    console.warn(`⚠️ No songs in generated puzzle data`);
                    if (attempt === maxAttempts) {
                        throw new Error('No songs found for the given topic');
                    }
                    continue;
                }

                // FIXED: Format for storage using the generator's method
                let formattedPuzzle;
                try {
                    formattedPuzzle = musicPuzzleGenerator.formatForPuzzleSystem(puzzleData);
                } catch (formatError) {
                    console.error(`❌ Formatting failed: ${formatError.message}`);
                    if (attempt === maxAttempts) {
                        throw new Error(`Puzzle formatting failed: ${formatError.message}`);
                    }
                    continue;
                }
                
                // Validate formatted puzzle has required fields
                if (!formattedPuzzle.question || !formattedPuzzle.answer) {
                    console.warn(`⚠️ Invalid formatted puzzle structure`);
                    if (attempt === maxAttempts) {
                        throw new Error('Formatted puzzle missing required fields');
                    }
                    continue;
                }
                
                // Store in database
                const insertResult = await supabase
                    .from('puzzles')
                    .insert({
                        parentSetId: puzzleId,
                        puzzleid: crypto.randomUUID(),
                        source: userId,
                        type: "musicidentification",
                        status: 'completed',
                        difficulty: 'medium',
                        question: formattedPuzzle.question,
                        answer: formattedPuzzle.answer,
                        options: [], // Options embedded in question JSON
                        hint: formattedPuzzle.hint,
                        timestamp: new Date().toISOString()
                    });

                if (insertResult.error) {
                    console.error(`❌ Database insert failed:`, insertResult.error);
                    if (attempt === maxAttempts) {
                        throw new Error(`Database insert failed: ${insertResult.error.message}`);
                    }
                    continue;
                }
                
                generatedPuzzles.push(result);
                console.log(`✅ Music puzzle generated successfully with ${puzzleData.songs.length} songs`);
                break; // Success - exit retry loop
                
            } catch (error) {
                console.error(`❌ Music generation attempt ${attempt} failed:`, error.message);
                
                if (attempt === maxAttempts) {
                    throw error; // Re-throw on final attempt
                }
                
                // Wait before retry
                await new Promise(resolve => setTimeout(resolve, 2000));
            }
        }

        // Final status update
        const finalStatus = generatedPuzzles.length > 0 ? 'completed' : 'failed';
        
        await Promise.all([
            supabase
                .from('puzzle_sets')
                .update({
                    status: finalStatus,
                    puzzle_count: generatedPuzzles.length,
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: finalStatus,
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);

        const elapsedTime = (Date.now() - startTime) / 1000;
        console.log(`⏱️ Music puzzle generation completed in ${elapsedTime.toFixed(1)}s`);

        if (generatedPuzzles.length > 0) {
            console.log(`🎉 Generated ${generatedPuzzles.length} music identification puzzle successfully`);
            
            // Send success notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Music Identification', 
                generatedPuzzles.length, 
                true
            );
            
            return { puzzleId };
        } else {
            console.error(`❌ No music puzzles could be generated`);
            
            // Send failure notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Music Identification', 
                numPuzzles, 
                false, 
                "Failed to find music with audio previews for this topic"
            );
            
            return { error: "Failed to generate any music puzzles" };
        }

    } catch (error) {
        console.error(`💥 Fatal error in music puzzle generation:`, error);
        
        // Mark as failed
        await Promise.allSettled([
            supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: 'failed',
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);
        
        // Send failure notification
        await sendCustomPuzzleCompletionNotification(
            userId, 
            topic, 
            'Music Identification', 
            numPuzzles, 
            false, 
            error.message
        );
        
        return { error: error.message };
    }
}


function getImagePuzzleOptionsForTopic(topic) {
    const topicLower = topic.toLowerCase();
    
    // Map topics to image categories and tags
    const topicMappings = {
        // Nature topics
        nature: { category: 'nature', tags: ['landscape', 'peaceful'] },
        landscape: { category: 'nature', tags: ['landscape', 'mountains'] },
        ocean: { category: 'nature', tags: ['ocean', 'water'] },
        mountain: { category: 'nature', tags: ['mountains', 'landscape'] },
        forest: { category: 'nature', tags: ['forest', 'trees'] },
        desert: { category: 'nature', tags: ['desert', 'sand'] },
        beach: { category: 'seasonal', tags: ['summer', 'beach'] },
        
        // Animals
        animals: { category: 'wildlife', tags: ['safari', 'animals'] },
        wildlife: { category: 'wildlife', tags: ['safari', 'animals'] },
        safari: { category: 'wildlife', tags: ['safari', 'elephants'] },
        birds: { category: 'wildlife', tags: ['birds', 'tropical'] },
        dolphins: { category: 'wildlife', tags: ['dolphins', 'ocean'] },
        
        // Cities and architecture
        city: { category: 'urban', tags: ['city', 'modern'] },
        architecture: { category: 'urban', tags: ['architecture', 'buildings'] },
        skyline: { category: 'urban', tags: ['city', 'lights'] },
        bridge: { category: 'urban', tags: ['bridge', 'architecture'] },
        
        // Fantasy and magical
        fantasy: { category: 'fantasy', tags: ['magical', 'fairy tale'] },
        magic: { category: 'fantasy', tags: ['magical', 'spells'] },
        castle: { category: 'fantasy', tags: ['castle', 'fairy tale'] },
        dragon: { category: 'fantasy', tags: ['dragons', 'mystical'] },
        unicorn: { category: 'fantasy', tags: ['unicorns', 'magical'] },
        
        // Space
        space: { category: 'space', tags: ['planets', 'cosmos'] },
        planets: { category: 'space', tags: ['planets', 'solar system'] },
        galaxy: { category: 'space', tags: ['galaxy', 'stars'] },
        astronaut: { category: 'space', tags: ['space station', 'astronauts'] },
        
        // Seasonal
        winter: { category: 'seasonal', tags: ['winter', 'snow'] },
        spring: { category: 'seasonal', tags: ['spring', 'flowers'] },
        summer: { category: 'seasonal', tags: ['summer', 'beach'] },
        autumn: { category: 'seasonal', tags: ['autumn', 'harvest'] },
        christmas: { category: 'seasonal', tags: ['winter', 'cozy'] },
        
        // Underwater
        underwater: { category: 'underwater', tags: ['coral', 'underwater'] },
        coral: { category: 'underwater', tags: ['coral', 'tropical fish'] },
        submarine: { category: 'underwater', tags: ['submarine', 'adventure'] }
    };
    
    // Check for direct matches
    for (const [keyword, options] of Object.entries(topicMappings)) {
        if (topicLower.includes(keyword)) {
            return options;
        }
    }
    
    // If no specific match, create a custom theme
    return {
        category: null,
        tags: [],
        customTheme: imagePuzzleGenerator.generateCustomTheme(topic, 'medium')
    };
}

/**
 * Generate custom word search puzzles
 */
async function generateCustomWordSearch(topic, numPuzzles, userId, puzzleId, difficulty = 'medium') {
    console.log(`🔍 Starting FIXED custom word search generation for topic="${topic}", numPuzzles=${numPuzzles}`);
    const startTime = Date.now();
    const maxGenerationTimeMs = 180000; // 3 minutes
    
    try {
        // Validate inputs
        if (!topic || !userId || !puzzleId) {
            throw new Error('Missing required parameters: topic, userId, or puzzleId');
        }

        if (numPuzzles < 1 || numPuzzles > 10) {
            throw new Error('Number of puzzles must be between 1 and 10');
        }

        // Generate word searches using the FIXED service
        const generatedPuzzles = [];
        const maxAttemptsPerPuzzle = 3;

        for (let puzzleIndex = 0; puzzleIndex < numPuzzles; puzzleIndex++) {
            console.log(`🔍 Generating word search ${puzzleIndex + 1}/${numPuzzles}...`);
            
            let puzzleGenerated = false;
            let attempts = 0;

            while (!puzzleGenerated && attempts < maxAttemptsPerPuzzle) {
                attempts++;
                
                // Check timeout
                if (Date.now() - startTime > maxGenerationTimeMs) {
                    console.warn(`⏰ Generation timeout reached after ${(Date.now() - startTime)/1000}s`);
                    break;
                }

                try {
                    console.log(`🎯 Attempt ${attempts}/${maxAttemptsPerPuzzle} for puzzle ${puzzleIndex + 1}`);
                    
                    // ✅ FIX: Use AI to generate topic-specific words directly
                    const wordList = await wordSearchService.generateTopicWordsWithAI(topic, difficulty, 12);
                    
                    if (!wordList || wordList.length < 5) {
                        console.warn(`⚠️ Insufficient words generated (${wordList?.length || 0}), retrying...`);
                        continue;
                    }

                    console.log(`✅ Generated ${wordList.length} topic-specific words: ${wordList.map(w => w.word).join(', ')}`);

                    // Determine grid size
                    const gridSize = determineOptimalGridSize(wordList, difficulty);
                    
                    // ✅ FIX: Use enhanced generation with deduplication
                    const wordSearch = await wordSearchService.generateWordSearchWithDeduplication(
                        wordList, 
                        difficulty, 
                        gridSize, 
                        topic
                    );
                    
                    if (!wordSearch || wordSearch.words.length < 5) {
                        console.warn(`⚠️ Word search generation failed or insufficient words placed, retrying...`);
                        continue;
                    }

                    // Format for storage using existing service method
                    const formattedPuzzle = wordSearchService.formatForCustomStorage(wordSearch, topic, difficulty);
                    
                    // Store in database
                    await supabase
                        .from('puzzles')
                        .insert({
                            parentSetId: puzzleId,
                            puzzleid: crypto.randomUUID(),
                            source: userId,
                            type: 'wordsearch',
                            status: 'completed',
                            difficulty: difficulty.toLowerCase(),
                            question: formattedPuzzle.question,
                            answer: formattedPuzzle.answer,
                            options: [],
                            hint: formattedPuzzle.hint,
                            timestamp: new Date().toISOString()
                        });
                    
                    generatedPuzzles.push(wordSearch);
                    puzzleGenerated = true;
                    
                    console.log(`✅ Word search ${puzzleIndex + 1} generated successfully with ${wordSearch.words.length} words`);
                    
                } catch (error) {
                    console.error(`❌ Error in word search generation attempt ${attempts}:`, error.message);
                    
                    // If this is the last attempt, continue to next puzzle
                    if (attempts >= maxAttemptsPerPuzzle) {
                        console.warn(`⚠️ Failed to generate word search ${puzzleIndex + 1} after ${attempts} attempts`);
                    }
                }
            }

            // Add small delay between puzzles
            if (puzzleIndex < numPuzzles - 1) {
                await new Promise(resolve => setTimeout(resolve, 1000));
            }
        }

        // Final status update
        const finalStatus = generatedPuzzles.length > 0 ? 'completed' : 'failed';
        
        await Promise.all([
            supabase
                .from('puzzle_sets')
                .update({
                    status: finalStatus,
                    puzzle_count: generatedPuzzles.length,
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: finalStatus,
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);

        const elapsedTime = (Date.now() - startTime) / 1000;
        console.log(`⏱️ Word search generation completed in ${elapsedTime.toFixed(1)}s`);

        if (generatedPuzzles.length > 0) {
            console.log(`🎉 Generated ${generatedPuzzles.length}/${numPuzzles} topic-specific word search puzzles successfully`);
            
            // Send success notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Word Search', 
                generatedPuzzles.length, 
                true
            );
            
            return { puzzleId };
        } else {
            console.error(`❌ No word search puzzles could be generated`);
            
            // Send failure notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Word Search', 
                numPuzzles, 
                false, 
                "Failed to generate word search puzzles"
            );
            
            return { error: "Failed to generate any word search puzzles" };
        }

    } catch (error) {
        console.error(`💥 Fatal error in word search generation:`, error);
        
        // Mark as failed
        await Promise.allSettled([
            supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: 'failed',
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);
        
        // Send failure notification
        await sendCustomPuzzleCompletionNotification(
            userId, 
            topic, 
            'Word Search', 
            numPuzzles, 
            false, 
            error.message
        );
        
        return { error: error.message };
    }
}

/**
 * Determine optimal grid size based on words and difficulty
 */
function determineOptimalGridSize(wordList, difficulty) {
    const maxWordLength = Math.max(...wordList.map(w => w.word.length));
    const wordCount = wordList.length;
    
    let baseSize;
    switch (difficulty.toLowerCase()) {
        case 'easy':
            baseSize = Math.max(10, maxWordLength + 2);
            break;
        case 'medium':
            baseSize = Math.max(12, maxWordLength + 3);
            break;
        case 'hard':
            baseSize = Math.max(15, maxWordLength + 4);
            break;
        default:
            baseSize = 12;
    }

    // Adjust for word count
    if (wordCount > 12) baseSize += 2;
    if (wordCount > 16) baseSize += 2;
    
    // Cap at reasonable sizes
    return Math.min(Math.max(baseSize, 10), 20);
}

async function generateCustomWordSnakePuzzles(topic, format, numPuzzles, userId, puzzleId) {
    console.log(`🐍 Starting custom word snake generation for topic="${topic}", numPuzzles=${numPuzzles}`);
    const startTime = Date.now();
    
    try {
        // Validate inputs
        if (!topic || !userId || !puzzleId) {
            throw new Error('Missing required parameters: topic, userId, or puzzleId');
        }

        if (numPuzzles < 1 || numPuzzles > 5) {
            numPuzzles = Math.min(Math.max(numPuzzles, 1), 5);
            console.log(`Word snake puzzles: Adjusted to ${numPuzzles} puzzles (max 5 for quality)`);
        }

        // Enable debug mode for generation
        enhancedWordSnakeGenerator.setDebugMode(true);

        // Generate word snake puzzles using existing service
        const generatedPuzzles = [];
        const maxAttemptsPerPuzzle = 3;

        for (let puzzleIndex = 0; puzzleIndex < numPuzzles; puzzleIndex++) {
            console.log(`🔗 Generating word snake ${puzzleIndex + 1}/${numPuzzles}...`);
            
            let puzzleGenerated = false;
            let attempts = 0;

            while (!puzzleGenerated && attempts < maxAttemptsPerPuzzle) {
                attempts++;
                
                try {
                    console.log(`🎯 Attempt ${attempts}/${maxAttemptsPerPuzzle} for word snake ${puzzleIndex + 1}`);
                    
                    // Use existing word snake service to generate topic-specific puzzle
                    const result = await enhancedWordSnakeGenerator.generateTopicWordSnakePuzzle(
                        topic, 
                        'medium', 
                        8 // 8x8 grid size
                    );
                    
                    if (!result.success) {
                        console.warn(`⚠️ Word snake generation failed: ${result.message}`);
                        continue;
                    }

                    const puzzleData = result.puzzleData;
                    
                    if (!puzzleData.words || puzzleData.words.length < 3) {
                        console.warn(`⚠️ Insufficient words in puzzle: ${puzzleData.words?.length || 0}`);
                        continue;
                    }

                    // Format for database storage
                    const formattedPuzzle = formatWordSnakeForCustomStorage(puzzleData, topic, puzzleIndex + 1);
                    
                    // Store in database
                    await supabase
                        .from('puzzles')
                        .insert({
                            parentSetId: puzzleId,
                            puzzleid: crypto.randomUUID(),
                            source: userId,
                            type: "wordsnake",
                            status: 'completed',
                            difficulty: 'medium',
                            question: formattedPuzzle.question,
                            answer: formattedPuzzle.answer,
                            options: [],
                            hint: formattedPuzzle.hint,
                            timestamp: new Date().toISOString()
                        });
                    
                    generatedPuzzles.push(result);
                    puzzleGenerated = true;
                    
                    console.log(`✅ Word snake ${puzzleIndex + 1} generated successfully with ${puzzleData.words.length} words`);
                    
                } catch (error) {
                    console.error(`❌ Error in word snake generation attempt ${attempts}:`, error.message);
                    
                    if (attempts >= maxAttemptsPerPuzzle) {
                        console.warn(`⚠️ Failed to generate word snake ${puzzleIndex + 1} after ${attempts} attempts`);
                    }
                }
            }

            // Add delay between puzzles
            if (puzzleIndex < numPuzzles - 1) {
                await new Promise(resolve => setTimeout(resolve, 1500));
            }
        }

        // Disable debug mode
        enhancedWordSnakeGenerator.setDebugMode(false);

        // Final status update
        const finalStatus = generatedPuzzles.length > 0 ? 'completed' : 'failed';
        
        await Promise.all([
            supabase
                .from('puzzle_sets')
                .update({
                    status: finalStatus,
                    puzzle_count: generatedPuzzles.length,
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: finalStatus,
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);

        const elapsedTime = (Date.now() - startTime) / 1000;
        console.log(`⏱️ Word snake generation completed in ${elapsedTime.toFixed(1)}s`);

        if (generatedPuzzles.length > 0) {
            console.log(`🎉 Generated ${generatedPuzzles.length}/${numPuzzles} word snake puzzles successfully`);
            
            // Send success notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Word Snake', 
                generatedPuzzles.length, 
                true
            );
            
            return { puzzleId };
        } else {
            console.error(`❌ No word snake puzzles could be generated`);
            
            // Send failure notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                'Word Snake', 
                numPuzzles, 
                false, 
                "Failed to generate word snake puzzles"
            );
            
            return { error: "Failed to generate any word snake puzzles" };
        }

    } catch (error) {
        console.error(`💥 Fatal error in word snake generation:`, error);
        
        // Mark as failed
        await Promise.allSettled([
            supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: 'failed',
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);
        
        // Send failure notification
        await sendCustomPuzzleCompletionNotification(
            userId, 
            topic, 
            'Word Snake', 
            numPuzzles, 
            false, 
            error.message
        );
        
        return { error: error.message };
    }
}

function formatWordSnakeForCustomStorage(puzzleData, topic, puzzleNumber) {
    // The existing service already provides the right format, just adapt it
    const questionData = {
        type: "wordsnake",
        topic: topic,
        puzzleNumber: puzzleNumber,
        grid: puzzleData.grid,
        words: puzzleData.words,
        gridSize: puzzleData.gridSize,
        instruction: puzzleData.instruction || `Find ${puzzleData.words.length} hidden words by tracing snake-like paths`,
        gameSettings: {
            difficulty: puzzleData.difficulty || "medium",
            timeLimit: 300, // 5 minutes
            hintsAllowed: true
        }
    };
    
    // Create the answer JSON (contains the solution)
    const answerData = {
        words: puzzleData.words.map(word => ({
            word: word.word,
            path: word.path,
            clue: word.clue
        })),
        totalWords: puzzleData.words.length,
        gridSize: puzzleData.gridSize
    };
    
    // Create hint text
    const hintText = `Word snake puzzle about ${topic}. Find ${puzzleData.words.length} words by tracing connected paths. Words: ${puzzleData.words.map(w => w.clue).join(', ')}`;
    
    return {
        question: JSON.stringify(questionData),
        answer: JSON.stringify(answerData),
        hint: hintText
    };
}

export async function generateCustomAnagrams(topic, format, numPuzzles, userId, puzzleId) {
    console.log(`🔤 Starting custom anagram generation for topic="${topic}", numPuzzles=${numPuzzles}`);
    
    try {
        // Phase 1: Create initial records in both tables (same as crosswords)
        console.log("📂 Creating initial anagram records in Supabase...");
        
        await supabase
            .from('puzzle_sets')
            .upsert({
                id: puzzleId,
                name: topic,
                creator: userId,
                status: 'generating',
                format: format,
                puzzle_count: 0,
                created_at: new Date().toISOString(),
                updated_at: new Date().toISOString()
            });
        
        await supabase
            .from('puzzles')
            .insert({
                parentSetId: puzzleId,
                source: userId,
                type: topic,
                status: 'generating',
                difficulty: 'medium',
                timestamp: new Date().toISOString()
            });

        // Phase 2: Generate words for anagrams using AI
        console.log("🤖 Generating anagram words with AI...");
        const prompt = `Generate exactly ${numPuzzles} popular words related to "${topic}" with corresponding hints for an anagram puzzle.

REQUIREMENTS:
- Word length: 4-8 letters (good for anagrams)
- All words must be related to the topic: ${topic}
- No proper nouns, abbreviations, or hyphenated words
- Each word needs a clear, descriptive hint

FORMAT:
{
  "words": [
    {"word": "EXAMPLE", "hint": "A sample or illustration"},
    {"word": "PUZZLE", "hint": "A challenging problem to solve"}
  ]
}

Generate exactly ${numPuzzles} words related to "${topic}":`;

        const aiResponse = await callAI(prompt, null);
        
        if (!aiResponse || aiResponse.startsWith("Error")) {
            throw new Error(`AI failed to generate anagram words: ${aiResponse}`);
        }

        let wordData;
        try {
            wordData = JSON.parse(aiResponse);
        } catch (error) {
            throw new Error(`Failed to parse AI anagram response: ${error.message}`);
        }

        if (!wordData.words || !Array.isArray(wordData.words)) {
            throw new Error('Invalid AI anagram response format');
        }

        // Phase 3: Generate anagram puzzles and store in database
        console.log("🔤 Creating anagram puzzles and storing in database...");
        const generatedAnagrams = [];
        const existingQuestions = new Set();

        // Load existing anagram answers from database to prevent global duplicates
        const { data: existingAnagrams } = await supabase
            .from('puzzles')
            .select('answer')
            .ilike('type', '%anagram%')
            .not('answer', 'is', null);

        const existingAnswers = new Set(
            (existingAnagrams || []).map(p => p.answer?.toUpperCase?.() || '')
        );
        console.log(`📊 Found ${existingAnswers.size} existing anagram answers in database`);

        for (const { word, hint } of wordData.words.slice(0, numPuzzles)) {
            if (!word || !hint) {
                console.warn("⚠️ Skipping invalid word/hint pair");
                continue;
            }

            const cleanWord = word.toUpperCase().trim();

            // Check if this word already exists as an anagram answer in the database
            if (existingAnswers.has(cleanWord)) {
                console.warn(`⚠️ Skipping word already used as anagram: ${cleanWord}`);
                continue;
            }

            const shuffledWord = shuffleWord(cleanWord);

            // Ensure the anagram is actually different from the original
            let finalShuffled = shuffledWord;
            let attempts = 0;
            while (finalShuffled === cleanWord && attempts < 5) {
                finalShuffled = shuffleWord(cleanWord);
                attempts++;
            }

            // Check for duplicates within this session
            if (existingQuestions.has(finalShuffled)) {
                console.warn(`⚠️ Duplicate anagram skipped (session): ${finalShuffled}`);
                continue;
            }

            try {
                // Insert anagram puzzle into puzzles table
                await supabase
                    .from('puzzles')
                    .insert({
                        parentSetId: puzzleId,
                        puzzleid: crypto.randomUUID(),
                        source: userId,
                        type: topic,
                        status: 'completed',
                        difficulty: 'medium',
                        question: finalShuffled,    // The scrambled word
                        answer: cleanWord,          // The original word
                        options: [],                // Anagrams don't use options
                        hint: hint.trim(),
                        timestamp: new Date().toISOString()
                    });

                existingQuestions.add(finalShuffled);
                existingAnswers.add(cleanWord); // Prevent duplicates within this batch
                generatedAnagrams.push({
                    question: finalShuffled,
                    answer: cleanWord,
                    hint: hint.trim()
                });

                console.log(`✅ Generated anagram ${generatedAnagrams.length}/${numPuzzles}: "${finalShuffled}" → "${cleanWord}"`);
                
            } catch (dbError) {
                console.error(`❌ Failed to store anagram in database:`, dbError);
                continue;
            }
        }

        // Phase 4: Final status update
        const finalStatus = generatedAnagrams.length > 0 ? 'completed' : 'failed';
        
        await Promise.all([
            supabase
                .from('puzzle_sets')
                .update({
                    status: finalStatus,
                    puzzle_count: generatedAnagrams.length,
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: finalStatus,
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);

        // 🆕 Send completion notification
        if (generatedAnagrams.length > 0) {
            console.log(`🎉 Custom anagrams generated successfully: ${generatedAnagrams.length} anagrams`);
            
            // Send success notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                format, 
                generatedAnagrams.length, 
                true
            );
            
            return { puzzleId };
        } else {
            console.warn("❌ Custom anagram generation failed. No anagrams generated.");
            
            // Send failure notification
            await sendCustomPuzzleCompletionNotification(
                userId, 
                topic, 
                format, 
                numPuzzles, 
                false, 
                "Failed to generate anagrams"
            );
            
            return { error: "Failed to generate anagrams" };
        }

    } catch (error) {
        console.error("💥 Fatal error during custom anagram generation:", error);
        
        // Mark as failed in both tables
        await Promise.allSettled([
            supabase
                .from('puzzle_sets')
                .update({
                    status: 'failed',
                    updated_at: new Date().toISOString()
                })
                .eq('id', puzzleId),
            
            supabase
                .from('puzzles')
                .update({
                    status: 'failed',
                    timestamp: new Date().toISOString()
                })
                .eq('parentSetId', puzzleId)
                .is('puzzleid', null)
        ]);
        
        return { error: error.message };
    }
}

async function generateCrosswordWordsForTopic(topic, wordCount = 20) {
    console.log(`🤖 [${new Date().toISOString()}] Generating ${wordCount} crossword words for topic: ${topic}`);
    
    const createSmartPrompt = (topic, wordCount) => {
        const topicLower = topic.toLowerCase();
        
        // Detect if topic is asking for specific items vs general concepts
        const isSpecificItemTopic = detectSpecificItemTopic(topicLower);
        
        let promptInstructions;
        let examples;
        
        if (isSpecificItemTopic) {
            const itemType = getItemTypeFromTopic(topicLower);
            promptInstructions = `Generate exactly ${wordCount} specific ${itemType} for a 5x5 crossword puzzle.

IMPORTANT: Generate actual ${itemType}, not concepts or ideas related to them.

REQUIREMENTS:
- Word length: 3-5 letters ONLY (essential for crossword fitting)  
- Each word should be a concrete ${itemType.slice(0, -1)} that crossword solvers can easily identify
- Include common crossword letters: A, E, I, O, U, R, S, T, N, L for better intersections
- Each word needs a clear, concise hint
- Avoid abstract concepts, activities, or related ideas - stick to actual ${itemType}`;
            
            examples = getExamplesForCrosswordItemType(itemType, topic);
        } else {
            // For general topics, use enhanced guidance
            promptInstructions = `Generate exactly ${wordCount} words directly related to "${topic}" for a 5x5 crossword puzzle.

REQUIREMENTS:
- Word length: 3-5 letters ONLY (critical for crossword grid placement)
- Words should be concrete and recognizable terms strongly connected to ${topic}
- Include common crossword letters: A, E, I, O, U, R, S, T, N, L for better grid intersections
- Focus on tangible, specific elements rather than abstract concepts
- Each word needs a clear, concise hint
- Prioritize words that crossword solvers would immediately associate with ${topic}`;
            
            examples = getGeneralExamplesForTopic(topic);
        }
        
        return `${promptInstructions}

EXAMPLES for topic "${topic}":
${examples}

FORMAT:
{
  "words": [
    {"word": "HOUSE", "hint": "Place where people live"},
    {"word": "PLANT", "hint": "Green growing organism"}
  ]
}

Generate exactly ${wordCount} words for "${topic}":`;
    };

    const prompt = createSmartPrompt(topic, wordCount);

    try {
        console.log(`🤖 [${new Date().toISOString()}] Calling AI for crossword words...`);
        
        // Let your aiClient handle all the model rotation and retries
        const aiResponse = await callAI(prompt, null, 2, {
            category: USAGE_CATEGORIES.CROSSWORD_GENERATION,
            puzzleType: 'crossword',
            difficulty: 'medium'
        });
        const wordData = JSON.parse(aiResponse);
        
        if (!wordData.words || !Array.isArray(wordData.words)) {
            throw new Error('Invalid AI response format - missing words array');
        }
        
        // Filter and format words for crossword (3-5 characters only)
        const validWords = wordData.words
            .filter(w => w.word && w.hint && w.word.length >= 3 && w.word.length <= 5)
            .map(w => ({
                word: w.word.toUpperCase().trim(),
                hint: w.hint.trim()
            }))
            .filter((w, index, self) => {
                // Remove duplicates and ensure only letters
                return /^[A-Z]+$/.test(w.word) && 
                       self.findIndex(other => other.word === w.word) === index;
            });
        
        if (validWords.length < 8) {
            throw new Error(`Insufficient valid words generated: ${validWords.length} (need at least 8)`);
        }
        
        console.log(`🎉 [${new Date().toISOString()}] SUCCESS! Generated ${validWords.length} valid crossword words for topic: ${topic}`);
        console.log(`📝 [${new Date().toISOString()}] Sample words:`, validWords.slice(0, 3));
        
        return validWords;
        
    } catch (error) {
        console.error(`💥 [${new Date().toISOString()}] Crossword word generation failed:`, error.message);
        return null;
    }
}

/**
 * Detect if a topic is asking for specific items rather than general concepts
 */
function detectSpecificItemTopic(topicLower) {
    const specificItemPatterns = [
        // Geographic entities
        /\b(capital|cities|countries|nations|states|provinces)\b/,
        // Living things  
        /\b(animals|birds|fish|insects|plants|flowers|trees)\b/,
        // Food and drinks
        /\b(food|fruits|vegetables|drinks|beverages|dishes)\b/,
        // Objects and items
        /\b(instruments|tools|vehicles|colors?|colours?|clothing|furniture)\b/,
        // People and roles
        /\b(professions?|jobs|occupations|careers)\b/,
        // Sports and activities
        /\b(sports?|games|exercises|hobbies)\b/,
        // Academic subjects with specific terms
        /\b(elements|compounds|planets|organs|bones)\b/
    ];
    
    return specificItemPatterns.some(pattern => pattern.test(topicLower));
}

/**
 * Get the item type from a topic for more targeted prompting
 */
function getItemTypeFromTopic(topicLower) {
    const itemTypeMap = {
        'capital': 'capital cities',
        'cities': 'cities', 
        'countries': 'countries',
        'nations': 'countries',
        'animals': 'animals',
        'birds': 'birds',
        'fish': 'fish',
        'insects': 'insects',
        'plants': 'plants',
        'flowers': 'flowers',
        'food': 'foods',
        'fruits': 'fruits',
        'vegetables': 'vegetables',
        'drinks': 'beverages',
        'instruments': 'musical instruments',
        'tools': 'tools',
        'vehicles': 'vehicles',
        'colors': 'colors',
        'colours': 'colors',
        'professions': 'professions',
        'jobs': 'jobs',
        'sports': 'sports',
        'games': 'games',
        'elements': 'chemical elements',
        'planets': 'planets'
    };
    
    for (const [key, value] of Object.entries(itemTypeMap)) {
        if (topicLower.includes(key)) {
            return value;
        }
    }
    
    return 'items'; // fallback
}

/**
 * Get targeted examples for crossword specific item types (3-5 letters only)
 */
function getExamplesForCrosswordItemType(itemType, topic) {
    const exampleMap = {
        'capital cities': '- ROME (hint: "Capital of Italy")\n- PARIS (hint: "Capital of France")\n- OSLO (hint: "Capital of Norway")',
        'countries': '- PERU (hint: "South American country")\n- CHAD (hint: "African country")\n- CUBA (hint: "Caribbean island nation")',
        'animals': '- CAT (hint: "Domestic feline")\n- BEAR (hint: "Large omnivore")\n- FISH (hint: "Aquatic vertebrate")',
        'foods': '- RICE (hint: "Asian grain staple")\n- BREAD (hint: "Baked staple food")\n- APPLE (hint: "Common red fruit")',
        'fruits': '- APPLE (hint: "Red orchard fruit")\n- GRAPE (hint: "Wine-making fruit")\n- LEMON (hint: "Sour citrus fruit")',
        'sports': '- GOLF (hint: "Club and ball sport")\n- RUGBY (hint: "Oval ball team sport")\n- YOGA (hint: "Flexibility exercise")',
        'colors': '- RED (hint: "Color of fire")\n- BLUE (hint: "Color of sky")\n- GREEN (hint: "Color of grass")',
        'tools': '- SAW (hint: "Cutting tool")\n- AXE (hint: "Wood chopping tool")\n- RAKE (hint: "Leaf gathering tool")'
    };
    
    return exampleMap[itemType] || getGeneralExamplesForTopic(topic);
}

/**
 * Get general examples for non-specific topics
 */
function getGeneralExamplesForTopic(topic) {
    const topicLower = topic.toLowerCase();
    
    const generalExamples = {
        'science': '- ATOM (hint: "Basic unit of matter")\n- GENE (hint: "Heredity unit")\n- LASER (hint: "Light beam device")',
        'technology': '- WEB (hint: "Internet system")\n- APP (hint: "Phone program")\n- CODE (hint: "Programming text")',
        'music': '- SONG (hint: "Musical composition")\n- BEAT (hint: "Musical rhythm")\n- TUNE (hint: "Musical melody")',
        'nature': '- TREE (hint: "Tall woody plant")\n- LAKE (hint: "Body of water")\n- ROCK (hint: "Solid mineral mass")',
        'space': '- STAR (hint: "Celestial light")\n- MOON (hint: "Earth\'s satellite")\n- ORBIT (hint: "Planetary path")',
        'ocean': '- WAVE (hint: "Water movement")\n- TIDE (hint: "Ocean rise/fall")\n- REEF (hint: "Coral formation")'
    };
    
    for (const [key, value] of Object.entries(generalExamples)) {
        if (topicLower.includes(key)) {
            return value;
        }
    }
    
    return '- WORD (hint: "Unit of language")\n- IDEA (hint: "Mental concept")\n- THEME (hint: "Central topic")';
}

export async function generateCustomCrosswords(topic, format, numPuzzles, userId, puzzleId) {
    console.log(`🧩 [${new Date().toISOString()}] Starting custom crossword generation for topic="${topic}", numPuzzles=${numPuzzles}`);
    
    // Master timeout for entire crossword generation process (5 minutes)
    const masterTimeoutPromise = new Promise((_, reject) => 
        setTimeout(() => reject(new Error('Master crossword generation timeout after 5 minutes')), 300000)
    );
    
    const crosswordGenerationPromise = (async () => {
        try {
            // Phase 1: Create initial records in both tables
            console.log(`📂 [${new Date().toISOString()}] Creating initial crossword records in Supabase...`);
            
            await supabase
                .from('puzzle_sets')
                .upsert({
                    id: puzzleId,
                    name: topic,
                    creator: userId,
                    status: 'generating',
                    format: format,
                    puzzle_count: 0,
                    created_at: new Date().toISOString(),
                    updated_at: new Date().toISOString()
                });
            
            await supabase
                .from('puzzles')
                .insert({
                    parentSetId: puzzleId,
                    source: userId,
                    type: topic,
                    status: 'generating',
                    difficulty: 'medium',
                    timestamp: new Date().toISOString()
                });

            console.log(`✅ [${new Date().toISOString()}] Initial records created successfully`);

            // Phase 2: Generate AI words for crosswords with multi-model retry
            console.log(`🤖 [${new Date().toISOString()}] Generating crossword words with AI (multi-model retry)...`);

            let aiWords;
            let aiAttempts = 0;
            const maxAiRetries = 2; // Retry the entire AI word generation process up to 2 times
            
            while (!aiWords && aiAttempts < maxAiRetries) {
                aiAttempts++;
                console.log(`🔄 [${new Date().toISOString()}] AI word generation attempt ${aiAttempts}/${maxAiRetries}`);
                
                try {
                    aiWords = await generateCrosswordWordsForTopic(topic, Math.max(numPuzzles * 15, 50)); // Get plenty of words, minimum 50
                    
                    if (!aiWords || aiWords.length < 8) {
                        const errorMsg = `Insufficient AI words generated: ${aiWords?.length || 0} (need at least 8)`;
                        console.error(`❌ [${new Date().toISOString()}] ${errorMsg}`);
                        
                        if (aiAttempts < maxAiRetries) {
                            console.log(`🔄 [${new Date().toISOString()}] Retrying AI word generation with different models...`);
                            aiWords = null; // Reset for retry
                            continue;
                        } else {
                            throw new Error(errorMsg);
                        }
                    }
                    
                    console.log(`✅ [${new Date().toISOString()}] Successfully generated ${aiWords.length} AI words for crosswords on attempt ${aiAttempts}`);
                    break; // Success, exit retry loop
                    
                } catch (error) {
                    console.error(`❌ [${new Date().toISOString()}] AI word generation attempt ${aiAttempts} failed:`, error.message);
                    
                    if (aiAttempts < maxAiRetries) {
                        console.log(`🔄 [${new Date().toISOString()}] Will retry AI word generation...`);
                        // Add a brief delay before retrying
                        await new Promise(resolve => setTimeout(resolve, 2000));
                    } else {
                        throw new Error(`All AI word generation attempts failed. Last error: ${error.message}`);
                    }
                }
            }

            // Phase 3: Generate crosswords using BeamSearch generator
            console.log(`🔧 [${new Date().toISOString()}] Generating crosswords with BeamSearch generator...`);
            const { BeamSearch5x5CrosswordGenerator } = await import('./crosswordGeneratorService.js');
            const generator = new BeamSearch5x5CrosswordGenerator();
            
            const generatedCrosswords = [];
            let attempts = 0;
            const maxAttempts = numPuzzles * 3; // Try harder for custom puzzles

            while (generatedCrosswords.length < numPuzzles && attempts < maxAttempts) {
                attempts++;
                console.log(`🔄 [${new Date().toISOString()}] Crossword generation attempt ${attempts} (${generatedCrosswords.length}/${numPuzzles} completed)`);
                
                // Select random subset of words for this crossword
                const wordsForCrossword = selectRandomWords(aiWords, 12);
                
                try {
                    const crosswordResult = await generator.generate5x5Crossword(wordsForCrossword);
                    
                    if (crosswordResult && crosswordResult.words && crosswordResult.words.length >= 5) {
                        // Format crossword for storage
                        const formattedCrossword = generator.formatForStorage(crosswordResult, 'medium');
                        
                        // Insert crossword puzzle into puzzles table
                        await supabase
                            .from('puzzles')
                            .insert({
                                parentSetId: puzzleId,
                                puzzleid: crypto.randomUUID(),
                                source: userId,
                                type: topic,
                                status: 'completed',
                                difficulty: 'medium',
                                question: formattedCrossword.question, // JSON string of crossword data
                                answer: formattedCrossword.answer,     // JSON string of words
                                options: [],
                                hint: formattedCrossword.hint,
                                timestamp: new Date().toISOString()
                            });
                        
                        generatedCrosswords.push(crosswordResult);
                        console.log(`✅ [${new Date().toISOString()}] Generated crossword ${generatedCrosswords.length}/${numPuzzles} with ${crosswordResult.words.length} words`);
                    } else {
                        console.warn(`⚠️ [${new Date().toISOString()}] Crossword generation failed - insufficient words placed`);
                    }
                } catch (error) {
                    console.warn(`⚠️ [${new Date().toISOString()}] Crossword generation attempt ${attempts} failed:`, error.message);
                }
                
                // Brief pause between attempts
                await new Promise(resolve => setTimeout(resolve, 500));
            }

            // Phase 4: Final status update
            console.log(`📚 [${new Date().toISOString()}] Finalizing crossword records...`);
            const finalStatus = generatedCrosswords.length > 0 ? 'completed' : 'failed';
            
            await Promise.all([
                supabase
                    .from('puzzle_sets')
                    .update({
                        status: finalStatus,
                        puzzle_count: generatedCrosswords.length,
                        updated_at: new Date().toISOString()
                    })
                    .eq('id', puzzleId),
                
                supabase
                    .from('puzzles')
                    .update({
                        status: finalStatus,
                        timestamp: new Date().toISOString()
                    })
                    .eq('parentSetId', puzzleId)
                    .is('puzzleid', null)
            ]);

            // 🆕 Send completion notification (SUCCESS ONLY)
            if (generatedCrosswords.length > 0) {
                console.log(`🎉 [${new Date().toISOString()}] Custom crosswords generated successfully: ${generatedCrosswords.length} crosswords`);
                
                // Send success notification
                await sendCustomPuzzleCompletionNotification(
                    userId, 
                    topic, 
                    format, 
                    generatedCrosswords.length, 
                    true // success = true
                );
                
                return { puzzleId };
            } else {
                console.warn(`❌ [${new Date().toISOString()}] Custom crossword generation failed. No crosswords generated.`);
                
                // NO notification sent for failure - just log and return error
                return { error: "Failed to generate crosswords with sufficient quality" };
            }

        } catch (error) {
            console.error(`💥 [${new Date().toISOString()}] Fatal error during custom crossword generation:`, error);
            throw error; // Re-throw to be caught by the master timeout handler
        }
    })();
    
    try {
        // Race between the crossword generation and master timeout
        return await Promise.race([crosswordGenerationPromise, masterTimeoutPromise]);
        
    } catch (error) {
        console.error(`💥 [${new Date().toISOString()}] Crossword generation failed or timed out:`, error);
        
        // Mark as failed in both tables
        try {
            await Promise.allSettled([
                supabase
                    .from('puzzle_sets')
                    .update({
                        status: 'failed',
                        updated_at: new Date().toISOString()
                    })
                    .eq('id', puzzleId),
                
                supabase
                    .from('puzzles')
                    .update({
                        status: 'failed',
                        timestamp: new Date().toISOString()
                    })
                    .eq('parentSetId', puzzleId)
                    .is('puzzleid', null)
            ]);
            
            console.log(`✅ [${new Date().toISOString()}] Marked puzzle set ${puzzleId} as failed`);
        } catch (cleanupError) {
            console.error(`❌ [${new Date().toISOString()}] Failed to mark puzzle as failed:`, cleanupError);
        }
        
        // NO notification sent for failure - just return error
        if (error.message.includes('timeout')) {
            return { 
                error: "Crossword generation timed out. Please try again with a simpler topic or fewer puzzles.",
                errorType: 'timeout',
                timeoutDuration: error.message.includes('5 minutes') ? '5 minutes' : '60 seconds'
            };
        } else {
            return { 
                error: error.message,
                errorType: 'generation_failed'
            };
        }
    }
}

/**
 * Select random subset of words for crossword generation
 */
function selectRandomWords(words, count) {
    if (!words || words.length <= count) {
        return words;
    }
    
    const shuffled = [...words].sort(() => Math.random() - 0.5);
    return shuffled.slice(0, count);
}

export async function isDuplicateCustomQuestion(puzzleType, question, existingQuestions) {
    console.log("📌 Checking custom puzzle for duplicates...");

    try {
        // Convert Set to array if needed for the centralized service
        const questionsArray = Array.isArray(existingQuestions) 
            ? existingQuestions 
            : Array.from(existingQuestions);
        
        const isDuplicate = await deduplicationService.isDuplicate(puzzleType, question, questionsArray);
        console.log(`✅ Custom deduplication result: ${isDuplicate ? 'DUPLICATE' : 'UNIQUE'}`);
        return isDuplicate;
    } catch (error) {
        console.error(`❌ Custom deduplication error: ${error.message}`);
        return false;
    }
}

export function shuffleWord(word) {
    return word.split("").sort(() => Math.random() - 0.5).join("");
}
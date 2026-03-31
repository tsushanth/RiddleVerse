// services/puzzleStorage.js - Puzzle storage and retrieval functions
import crypto from "crypto";
import { supabase } from "../config/database.js";

/**
 * Generates a SHA256 hash of the question for exact deduplication.
 */
export function hashQuestion(question) {
    return crypto.createHash("sha256").update(question.toLowerCase().trim()).digest("hex");
}

/**
 * Upload audio to Supabase storage with retry logic
 */
export async function uploadAudioToSupabase(audioBuffer, puzzleId, maxRetries = 3) {
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            console.log(`📄 Upload attempt ${attempt}/${maxRetries} for ${puzzleId}`);

            if (!audioBuffer || !puzzleId) {
                throw new Error(`Invalid parameters: audioBuffer=${!!audioBuffer}, puzzleId=${puzzleId}`);
            }

            let uploadBlob;
            if (audioBuffer instanceof ArrayBuffer) {
                uploadBlob = new Blob([audioBuffer], { type: 'audio/mpeg' });
            } else if (audioBuffer instanceof Uint8Array) {
                uploadBlob = new Blob([audioBuffer.buffer], { type: 'audio/mpeg' });
            } else {
                uploadBlob = audioBuffer;
            }

            const fileName = puzzleId;

            const uploadPromise = supabase.storage
                .from('puzzle-audio')
                .upload(fileName, uploadBlob, {
                    contentType: 'audio/mpeg',
                    upsert: true
                });

            const timeoutPromise = new Promise((_, reject) =>
                setTimeout(() => reject(new Error('Upload timeout')), 30000)
            );

            const { data, error } = await Promise.race([uploadPromise, timeoutPromise]);

            if (error) throw error;

            console.log(`✅ Upload successful on attempt ${attempt}:`, data);
            return true;

        } catch (error) {
            console.error(`❌ Upload attempt ${attempt} failed:`, error.message);

            if (attempt === maxRetries) {
                console.error('💥 All upload attempts failed');
                return false;
            }

            const delay = Math.min(1000 * Math.pow(2, attempt - 1), 5000);
            console.log(`⏳ Waiting ${delay}ms before retry...`);
            await new Promise(resolve => setTimeout(resolve, delay));
        }
    }

    return false;
}

/**
 * Store a puzzle in Supabase
 */
export async function storePuzzle(puzzleType, question, answer, hint, difficulty, modelName, validationModel) {
    console.log("📌 Storing new puzzle in Supabase...");

    const puzzleId = crypto.randomUUID();
    const questionHash = hashQuestion(question);
    difficulty = difficulty.toLowerCase();

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
                options: [],
                timestamp: new Date().toISOString(),
                source: modelName
            }]);

        if (error) {
            console.error(`❌ Error inserting puzzle into Supabase:`, error.message);
            return { success: false, message: error.message };
        }

        console.log(`✅ Unique puzzle stored successfully in Supabase with puzzleId=${puzzleId}`);
        return { success: true, puzzleId };

    } catch (err) {
        console.error("❌ Error in storePuzzle:", err);
        return { success: false, message: "Unexpected error storing puzzle" };
    }
}

/**
 * Get existing questions from a prefix/type
 */
export async function getExistingQuestionsFromPrefix(puzzleType) {
    console.log(`📌 Fetching existing questions of type: ${puzzleType}`);

    try {
        const { data: puzzles, error } = await supabase
            .from('puzzles')
            .select('question')
            .eq('type', puzzleType);

        if (error) {
            console.error(`❌ Error fetching questions:`, error.message);
            return [];
        }

        const questions = puzzles.map(puzzle => puzzle.question).filter(Boolean);
        console.log(`📌 Loaded ${questions.length} existing questions from Supabase.`);
        return questions;

    } catch (error) {
        console.error(`⚠️ Error processing questions:`, error.message);
        return [];
    }
}

/**
 * Get existing questions for a topic
 */
export async function getExistingQuestionsTopic(topic) {
    return await getExistingQuestionsFromPrefix(topic);
}

/**
 * Get existing questions from Supabase daily puzzles
 */
export async function getExistingQuestionsFromSupabase(topic) {
    console.log(`📌 Fetching existing daily questions for topic: ${topic}`);

    try {
        const { data: dailyPuzzles, error } = await supabase
            .from('daily_puzzles')
            .select('puzzle_set')
            .eq('topic', topic)
            .gte('generation_date', new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]);

        if (error) {
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

        console.log(`📌 Loaded ${questions.length} existing questions from last 30 days.`);
        return questions;

    } catch (error) {
        console.error(`⚠️ Error processing daily questions:`, error.message);
        return [];
    }
}

/**
 * Construct audio URL for a puzzle
 */
export function constructAudioUrl(puzzleId) {
    return `https://uujjodxicvifmiwlimob.supabase.co/storage/v1/object/public/puzzle-audio/${puzzleId}`;
}

/**
 * Add audio URL to memory puzzle data
 */
export function addAudioUrlToMemoryPuzzle(puzzleData) {
    if (puzzleData.type === 'memorystory') {
        try {
            const questionData = JSON.parse(puzzleData.question);
            questionData.audioUrl = constructAudioUrl(puzzleData.puzzleid);
            puzzleData.question = JSON.stringify(questionData);
            console.log(`📊 Added audio URL for memory story: ${questionData.audioUrl}`);
        } catch (error) {
            console.warn(`⚠️ Could not add audio URL to memory story: ${error.message}`);
        }
    } else if (puzzleData.type === 'memorysequencing') {
        try {
            const questionData = JSON.parse(puzzleData.question);
            questionData.partOneAudioUrl = constructAudioUrl(`${puzzleData.puzzleid}_part1`);
            questionData.partTwoAudioUrl = constructAudioUrl(`${puzzleData.puzzleid}_part2`);
            puzzleData.question = JSON.stringify(questionData);
            console.log(`📊 Added audio URLs for sequencing puzzle`);
        } catch (error) {
            console.warn(`⚠️ Could not add audio URLs to sequencing puzzle: ${error.message}`);
        }
    } else if (puzzleData.type === 'memoryretention') {
        try {
            const questionData = JSON.parse(puzzleData.question);
            questionData.audioUrl = constructAudioUrl(puzzleData.puzzleid);
            puzzleData.question = JSON.stringify(questionData);
            console.log(`📊 Added audio URL for memory retention: ${questionData.audioUrl}`);
        } catch (error) {
            console.warn(`⚠️ Could not add audio URL to memory retention: ${error.message}`);
        }
    }

    return puzzleData;
}

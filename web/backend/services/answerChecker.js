// services/answerChecker.js - Answer checking and validation
import cosineSimilarity from "cosine-similarity";
import { callAI } from "../utils/aiClient.js";
import { pipeline } from "@xenova/transformers";
import path from "path";
import { promises as fsPromises } from 'fs';

let embedder = null;
const LOCAL_MODEL_PATH = './model-cache/Xenova/all-MiniLM-L6-v2';

/**
 * Ensure model is downloaded
 */
async function ensureModelDownloaded() {
    const localPath = path.resolve(LOCAL_MODEL_PATH);
    try {
        await fsPromises.access(localPath);
        console.log('✅ Model found at:', localPath);
    } catch (error) {
        console.error('❌ Model missing at', localPath);
        throw new Error('Model files not found locally. Please ensure they are bundled in the container.');
    }
}

/**
 * Load the embedding model
 */
export async function loadModel() {
    if (!embedder) {
        try {
            await ensureModelDownloaded();
            process.env.TRANSFORMERS_CACHE = path.resolve('./model-cache');

            embedder = await pipeline('feature-extraction', 'Xenova/all-MiniLM-L6-v2', {
                quantized: true,
                local_files_only: true
            });

            console.log('🧠 Model loaded successfully from local cache');
        } catch (error) {
            throw error;
        }
    }

    return embedder;
}

/**
 * Generate embedding for text
 */
export async function generateEmbedding(text) {
    try {
        if (!embedder) await loadModel();
        const output = await embedder(text, { pooling: 'mean' });
        return output.data;
    } catch (error) {
        throw error;
    }
}

/**
 * Adjust embedding size to 768 dimensions
 */
export function adjustEmbeddingSize(embedding) {
    if (embedding.length === 384) {
        return [...embedding, ...embedding];
    }
    return embedding;
}

/**
 * Check if an answer is correct using embeddings and AI
 */
export async function checkAnswer(puzzleId, question, expected_answer, guessed_answer, modelName) {
    console.log("📌 Checking answer similarity...");

    const expectedLength = expected_answer.length;
    const guessedLength = guessed_answer.length;

    if (expectedLength > guessedLength * 3) {
        console.log("📌 Large answer detected. Summarizing expected answer...");
        const prompt = `Summarize this answer to approximately ${guessedLength * 1.5} characters: "${expected_answer}"`;
        expected_answer = await callAI(prompt, modelName);
        console.log(`📌 Summarized expected answer: "${expected_answer}"`);
    }

    const embedding1 = adjustEmbeddingSize(await generateEmbedding(expected_answer));
    const embedding2 = adjustEmbeddingSize(await generateEmbedding(guessed_answer));
    const similarity = cosineSimilarity(embedding1, embedding2);

    console.log(`📌 Embedding similarity score: ${similarity}`);

    if (similarity >= 0.85) {
        return true;
    }
    if (similarity <= 0.5) {
        return false;
    }

    // For borderline cases, use LLM to decide
    console.log("📌 Using LLM for borderline case verification...");
    const verificationPrompt = `
Question: ${question}
Expected answer: ${expected_answer}
User's answer: ${guessed_answer}

Is the user's answer correct? Consider semantic similarity and partial credit.
Reply with just "YES" or "NO".`;

    const llmResult = await callAI(verificationPrompt, modelName);
    return llmResult.toUpperCase().includes('YES');
}

/**
 * Generate an answer for a question
 */
export async function generateAnswer(question, puzzleType, modelName) {
    console.log(`📌 Generating answer for ${puzzleType}: ${question.substring(0, 50)}...`);

    const prompt = `Answer this ${puzzleType} question concisely: ${question}`;

    try {
        const answer = await callAI(prompt, modelName);
        return answer.trim();
    } catch (error) {
        console.error('Error generating answer:', error);
        return null;
    }
}

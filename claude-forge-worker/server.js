// Claude Forge Worker - Runs Claude Code CLI on Fly.io
// This worker executes actual Claude Code CLI for game/puzzle generation

import express from 'express';
import { spawn } from 'child_process';
import { v4 as uuidv4 } from 'uuid';
import fs from 'fs/promises';
import path from 'path';

const app = express();
app.use(express.json({ limit: '10mb' }));

const PORT = process.env.PORT || 3000;
const SANDBOXES_DIR = '/app/sandboxes';

// In-memory session store
const sessions = new Map();

// System prompts
const GAME_SYSTEM_PROMPT = `You are an expert HTML5 game developer. Create a complete, self-contained HTML5 game.

CRITICAL REQUIREMENTS:
1. Create a SINGLE file called game.html with ALL code inline (CSS in <style>, JS in <script>)
2. NO external dependencies - everything must be inline
3. Games must be touch-friendly for mobile devices
4. Use canvas or DOM-based rendering (no WebGL)
5. Include clear instructions for the player
6. Games should be fun, polished, and bug-free
7. Always include a score display and game over state
8. Use modern ES6+ JavaScript

After creating the game, test it by opening game.html in the browser to verify it works.
Fix any errors you find before completing.`;

const PUZZLE_SYSTEM_PROMPT = `You are an expert puzzle designer and HTML5 developer. Create an engaging, self-contained HTML5 puzzle.

CRITICAL REQUIREMENTS:
1. Create a SINGLE file called game.html with ALL code inline (CSS in <style>, JS in <script>)
2. NO external dependencies - everything must be inline
3. Puzzles must be touch-friendly for mobile devices
4. Include clear instructions and hints system
5. Track and display progress (moves, time, score)
6. Provide satisfying feedback on completion
7. Difficulty should be appropriate and engaging

After creating the puzzle, test it by opening game.html in the browser to verify it works.
Fix any errors you find before completing.`;

// Cleanup old sessions (older than 1 hour)
setInterval(() => {
    const oneHourAgo = Date.now() - 60 * 60 * 1000;
    for (const [id, session] of sessions.entries()) {
        if (session.createdAt < oneHourAgo) {
            cleanupSession(id);
        }
    }
}, 5 * 60 * 1000); // Run every 5 minutes

async function cleanupSession(sessionId) {
    const session = sessions.get(sessionId);
    if (session?.sandboxPath) {
        try {
            await fs.rm(session.sandboxPath, { recursive: true, force: true });
        } catch (err) {
            console.error(`Failed to cleanup sandbox for ${sessionId}:`, err);
        }
    }
    sessions.delete(sessionId);
}

// Health check
app.get('/health', (req, res) => {
    res.json({ status: 'healthy', sessions: sessions.size });
});

// Create a new session
app.post('/session', async (req, res) => {
    try {
        const { type = 'game' } = req.body;
        const sessionId = uuidv4();
        const sandboxPath = path.join(SANDBOXES_DIR, sessionId);

        await fs.mkdir(sandboxPath, { recursive: true });

        const session = {
            sessionId,
            type,
            sandboxPath,
            createdAt: Date.now(),
            status: 'created',
            currentHtml: null
        };

        sessions.set(sessionId, session);

        res.json({ success: true, sessionId });
    } catch (error) {
        console.error('Error creating session:', error);
        res.status(500).json({ success: false, error: error.message });
    }
});

// Generate game from prompt
app.post('/generate', async (req, res) => {
    const { sessionId, prompt } = req.body;

    if (!sessionId || !prompt) {
        return res.status(400).json({ success: false, error: 'sessionId and prompt required' });
    }

    const session = sessions.get(sessionId);
    if (!session) {
        return res.status(404).json({ success: false, error: 'Session not found' });
    }

    try {
        session.status = 'generating';
        const systemPrompt = session.type === 'puzzle' ? PUZZLE_SYSTEM_PROMPT : GAME_SYSTEM_PROMPT;

        const fullPrompt = `${prompt}\n\nCreate a game.html file with the complete implementation.`;

        const result = await runClaudeCode(session.sandboxPath, fullPrompt, systemPrompt);

        if (result.success) {
            // Read the generated game.html
            const gamePath = path.join(session.sandboxPath, 'game.html');
            try {
                const html = await fs.readFile(gamePath, 'utf-8');
                session.currentHtml = html;
                session.status = 'ready';
                res.json({ success: true, html, sessionId });
            } catch (err) {
                // Game file not found - check for other html files
                const files = await fs.readdir(session.sandboxPath);
                const htmlFile = files.find(f => f.endsWith('.html'));
                if (htmlFile) {
                    const html = await fs.readFile(path.join(session.sandboxPath, htmlFile), 'utf-8');
                    session.currentHtml = html;
                    session.status = 'ready';
                    res.json({ success: true, html, sessionId });
                } else {
                    session.status = 'error';
                    res.json({ success: false, error: 'No HTML file generated', output: result.output });
                }
            }
        } else {
            session.status = 'error';
            res.json({ success: false, error: result.error || 'Generation failed', output: result.output });
        }
    } catch (error) {
        console.error('Generate error:', error);
        session.status = 'error';
        res.status(500).json({ success: false, error: error.message });
    }
});

// Iterate on existing game
app.post('/iterate', async (req, res) => {
    const { sessionId, feedback } = req.body;

    if (!sessionId || !feedback) {
        return res.status(400).json({ success: false, error: 'sessionId and feedback required' });
    }

    const session = sessions.get(sessionId);
    if (!session) {
        return res.status(404).json({ success: false, error: 'Session not found' });
    }

    if (!session.currentHtml) {
        return res.status(400).json({ success: false, error: 'No game to iterate on' });
    }

    try {
        session.status = 'iterating';
        const systemPrompt = session.type === 'puzzle' ? PUZZLE_SYSTEM_PROMPT : GAME_SYSTEM_PROMPT;

        const fullPrompt = `Here's the user's feedback on the current game:\n${feedback}\n\nPlease update game.html based on this feedback. Test the changes to ensure they work.`;

        const result = await runClaudeCode(session.sandboxPath, fullPrompt, systemPrompt);

        if (result.success) {
            const gamePath = path.join(session.sandboxPath, 'game.html');
            try {
                const html = await fs.readFile(gamePath, 'utf-8');
                session.currentHtml = html;
                session.status = 'ready';
                res.json({ success: true, html, sessionId });
            } catch (err) {
                session.status = 'error';
                res.json({ success: false, error: 'Failed to read updated game', output: result.output });
            }
        } else {
            session.status = 'error';
            res.json({ success: false, error: result.error || 'Iteration failed', output: result.output });
        }
    } catch (error) {
        console.error('Iterate error:', error);
        session.status = 'error';
        res.status(500).json({ success: false, error: error.message });
    }
});

// Get session status
app.get('/session/:sessionId', (req, res) => {
    const session = sessions.get(req.params.sessionId);
    if (!session) {
        return res.status(404).json({ success: false, error: 'Session not found' });
    }
    res.json({
        success: true,
        sessionId: session.sessionId,
        status: session.status,
        hasGame: !!session.currentHtml,
        type: session.type
    });
});

// Delete session
app.delete('/session/:sessionId', async (req, res) => {
    const sessionId = req.params.sessionId;
    await cleanupSession(sessionId);
    res.json({ success: true });
});

// Run Claude Code CLI
function runClaudeCode(sandboxPath, prompt, systemPrompt) {
    return new Promise((resolve) => {
        let output = '';
        let errorOutput = '';

        const claude = spawn('claude', [
            '-p', prompt,
            '--output-format', 'text',
            '--max-turns', '25',
            '--dangerously-skip-permissions'
        ], {
            cwd: sandboxPath,
            env: {
                ...process.env,
                ANTHROPIC_API_KEY: process.env.ANTHROPIC_API_KEY,
                CLAUDE_CODE_SYSTEM_PROMPT: systemPrompt,
            },
            timeout: 5 * 60 * 1000 // 5 minute timeout
        });

        claude.stdout.on('data', (data) => {
            output += data.toString();
            console.log('[Claude]', data.toString());
        });

        claude.stderr.on('data', (data) => {
            errorOutput += data.toString();
            console.error('[Claude Error]', data.toString());
        });

        claude.on('close', (code) => {
            if (code === 0) {
                resolve({ success: true, output });
            } else {
                resolve({ success: false, error: `Process exited with code ${code}`, output: output + errorOutput });
            }
        });

        claude.on('error', (err) => {
            resolve({ success: false, error: err.message, output });
        });
    });
}

app.listen(PORT, () => {
    console.log(`Claude Forge Worker running on port ${PORT}`);
});

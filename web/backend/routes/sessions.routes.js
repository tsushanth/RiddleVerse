// routes/sessions.routes.js - Host-controlled game session lifecycle
import express from 'express';
import { supabase } from '../config/database.js';

const router = express.Router();

// How long (seconds) a session must last to be billed as a real play
const BILLABLE_THRESHOLD_SECONDS = 15;

// Max session duration before auto-close (seconds)
const MAX_SESSION_SECONDS = 300; // 5 minutes

// Score derivation buckets: if no score reported, infer from duration
function deriveScore(durationSeconds) {
  if (durationSeconds >= 120) return 100;
  if (durationSeconds >= 60)  return 60;
  if (durationSeconds >= 30)  return 30;
  return 10;
}

/**
 * POST /api/sessions/start
 * Creates a new game session and returns session_id + wrapper URL.
 *
 * Body: { game_id, user_id, source, chat_id? }
 */
router.post('/start', async (req, res) => {
  const { game_id, user_id, source, chat_id } = req.body;

  if (!game_id || !user_id || !source) {
    return res.status(400).json({ error: 'Missing required fields: game_id, user_id, source' });
  }

  const validSources = ['ios', 'android', 'telegram', 'web'];
  if (!validSources.includes(source)) {
    return res.status(400).json({ error: `source must be one of: ${validSources.join(', ')}` });
  }

  try {
    const { data, error } = await supabase
      .from('game_sessions')
      .insert({
        game_id,
        user_id,
        source,
        chat_id: chat_id || null,
      })
      .select('id')
      .single();

    if (error) throw error;

    const sessionId = data.id;
    const baseUrl = process.env.FRONTEND_URL || 'https://puzzleverseai.com';
    const wrapperUrl = `${baseUrl}/play?session_id=${sessionId}`;

    return res.json({ session_id: sessionId, wrapper_url: wrapperUrl });
  } catch (err) {
    console.error('❌ Failed to start session:', err);
    return res.status(500).json({ error: 'Failed to start session' });
  }
});

/**
 * POST /api/sessions/event
 * Records a game event (score, interaction, visibility_lost, exit).
 * Appends to the session's events array.
 *
 * Body: { session_id, type, value? }
 * type: 'score' | 'interaction' | 'visibility_lost' | 'exit'
 */
router.post('/event', async (req, res) => {
  const { session_id, type, value } = req.body;

  if (!session_id || !type) {
    return res.status(400).json({ error: 'Missing required fields: session_id, type' });
  }

  const validTypes = ['score', 'interaction', 'visibility_lost', 'exit'];
  if (!validTypes.includes(type)) {
    return res.status(400).json({ error: `type must be one of: ${validTypes.join(', ')}` });
  }

  try {
    // Fetch current events array
    const { data: session, error: fetchError } = await supabase
      .from('game_sessions')
      .select('id, events, ended_at')
      .eq('id', session_id)
      .single();

    if (fetchError || !session) {
      return res.status(404).json({ error: 'Session not found' });
    }

    // Ignore events on already-ended sessions
    if (session.ended_at) {
      return res.json({ ok: true, ignored: true });
    }

    const newEvent = { type, value: value ?? null, ts: new Date().toISOString() };
    const updatedEvents = [...(session.events || []), newEvent];

    const updatePayload = { events: updatedEvents };

    // If this is a score event, optimistically store it
    if (type === 'score' && typeof value === 'number') {
      updatePayload.score = value;
      updatePayload.score_source = 'reported';
    }

    const { error: updateError } = await supabase
      .from('game_sessions')
      .update(updatePayload)
      .eq('id', session_id);

    if (updateError) throw updateError;

    return res.json({ ok: true });
  } catch (err) {
    console.error('❌ Failed to record session event:', err);
    return res.status(500).json({ error: 'Failed to record event' });
  }
});

/**
 * POST /api/sessions/end
 * Closes a session, resolves final score, marks is_billable.
 * Also writes to custom_leaderboard if a score exists.
 *
 * Body: { session_id, score?, forced_by }
 * forced_by: 'user_button' | 'timeout' | 'focus_lost' | 'app_background'
 */
router.post('/end', async (req, res) => {
  const { session_id, score: reportedScore, forced_by } = req.body;

  if (!session_id) {
    return res.status(400).json({ error: 'Missing required field: session_id' });
  }

  try {
    const { data: session, error: fetchError } = await supabase
      .from('game_sessions')
      .select('*')
      .eq('id', session_id)
      .single();

    if (fetchError || !session) {
      return res.status(404).json({ error: 'Session not found' });
    }

    // Already ended — return current state without re-processing
    if (session.ended_at) {
      return res.json({
        ok: true,
        already_ended: true,
        is_billable: session.is_billable,
        score: session.score,
        score_source: session.score_source,
        duration_seconds: session.duration_seconds,
      });
    }

    const endedAt = new Date();
    const startedAt = new Date(session.started_at);
    const durationSeconds = Math.min(
      Math.floor((endedAt - startedAt) / 1000),
      MAX_SESSION_SECONDS
    );
    const isBillable = durationSeconds >= BILLABLE_THRESHOLD_SECONDS;

    // Score resolution: reported > stored from events > derived > default
    let finalScore = null;
    let scoreSource = null;

    if (typeof reportedScore === 'number') {
      finalScore = reportedScore;
      scoreSource = 'reported';
    } else if (typeof session.score === 'number') {
      finalScore = session.score;
      scoreSource = session.score_source || 'reported';
    } else if (isBillable) {
      finalScore = deriveScore(durationSeconds);
      scoreSource = 'derived';
    } else {
      finalScore = 0;
      scoreSource = 'default';
    }

    // Update the session row
    const { error: updateError } = await supabase
      .from('game_sessions')
      .update({
        ended_at: endedAt.toISOString(),
        duration_seconds: durationSeconds,
        is_billable: isBillable,
        score: finalScore,
        score_source: scoreSource,
        forced_by: forced_by || null,
      })
      .eq('id', session_id);

    if (updateError) throw updateError;

    // Write to custom_leaderboard if billable and we have a game_id
    if (isBillable && session.game_id && session.user_id && finalScore > 0) {
      const { error: lbError } = await supabase
        .from('custom_leaderboard')
        .upsert(
          {
            parentsetid: session.game_id,
            userid: session.user_id,
            score: finalScore,
            timetaken: durationSeconds,
          },
          { onConflict: 'parentsetid,userid', ignoreDuplicates: false }
        );

      if (lbError) {
        // Non-fatal — log but don't fail the response
        console.error('⚠️ Failed to upsert leaderboard entry:', lbError);
      }

      // Mirror to chat_scores so the in-app leaderboard tab (which reads
      // chat_scores with platform='app') stays in sync. Keep highest score
      // per user+game, matching the chatScores.routes.js semantics.
      const chatScoresChatId = session.chat_id || '_app_';
      const chatScoresPlatform = session.source === 'telegram' ? 'telegram' : 'app';
      try {
        const { data: existingChatScore, error: csFetchError } = await supabase
          .from('chat_scores')
          .select('id, score')
          .eq('game_id', session.game_id)
          .eq('chat_id', chatScoresChatId)
          .eq('platform', chatScoresPlatform)
          .eq('user_id', session.user_id)
          .single();

        if (csFetchError && csFetchError.code !== 'PGRST116') {
          throw csFetchError;
        }

        if (existingChatScore) {
          if (finalScore > existingChatScore.score) {
            const { error: csUpdateError } = await supabase
              .from('chat_scores')
              .update({
                score: finalScore,
                updated_at: new Date().toISOString(),
              })
              .eq('id', existingChatScore.id);
            if (csUpdateError) throw csUpdateError;
          }
        } else {
          const { error: csInsertError } = await supabase
            .from('chat_scores')
            .insert({
              game_id: session.game_id,
              chat_id: chatScoresChatId,
              platform: chatScoresPlatform,
              user_id: session.user_id,
              username: 'Player',
              score: finalScore,
            });
          if (csInsertError) throw csInsertError;
        }
      } catch (csError) {
        console.error('⚠️ Failed to mirror score to chat_scores:', csError);
      }
    }

    return res.json({
      ok: true,
      is_billable: isBillable,
      score: finalScore,
      score_source: scoreSource,
      duration_seconds: durationSeconds,
    });
  } catch (err) {
    console.error('❌ Failed to end session:', err);
    return res.status(500).json({ error: 'Failed to end session' });
  }
});

/**
 * GET /api/sessions/:session_id
 * Returns current session state (used by wrapper page on load).
 */
router.get('/:session_id', async (req, res) => {
  const { session_id } = req.params;

  try {
    const { data, error } = await supabase
      .from('game_sessions')
      .select('id, game_id, user_id, source, started_at, ended_at, is_billable, score, score_source, duration_seconds')
      .eq('id', session_id)
      .single();

    if (error || !data) {
      return res.status(404).json({ error: 'Session not found' });
    }

    return res.json(data);
  } catch (err) {
    console.error('❌ Failed to fetch session:', err);
    return res.status(500).json({ error: 'Failed to fetch session' });
  }
});

export default router;

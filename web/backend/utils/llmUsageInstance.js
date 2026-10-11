// utils/llmUsageInstance.js
// Claude API spend accounting for RiddleVerse: tokens and dollars per day / model / feature, reported to the central Worker.
// Independent of utils/usageTracker.js (that one keeps in-memory counters with estimated tokens and is left as is).
// Key: LLM_USAGE_KEY, falling back to FAILURE_REPORTER_KEY. With neither set the instance is disabled and every call is a no-op.
//
// utils/llmUsage.cjs is the shared client copied verbatim from the app-failure-reporter repo (node/llmUsage.js); it is CommonJS
// and this package is ESM, hence the .cjs extension.
import llmUsageLib from './llmUsage.cjs';

const { createLlmUsage } = llmUsageLib;

export const llm = createLlmUsage({ app: 'riddleverse', log: (m) => console.warn(`[llm-usage] ${m}`) });

/**
 * Record one finished Anthropic /v1/messages response body (the parsed JSON). A body without a usage block counts as an error call.
 * Never throws.
 */
export function recordAnthropicResponse(body, feature, fallbackModel) {
    try {
        if (body && body.usage) llm.record({ model: body.model || fallbackModel, usage: body.usage, feature });
        else llm.record({ model: fallbackModel, feature, ok: false });
    } catch { /* accounting must never break a request */ }
}

/** Record a call that produced no usable response (HTTP error, timeout, network failure). Never throws. */
export function recordAnthropicFailure(model, feature) {
    try { llm.record({ model, feature, ok: false }); } catch { /* ignore */ }
}

/**
 * Record a failed axios request. Only axios-level errors (the request itself failed) count, so a later parse error in the
 * caller's own code is not recorded a second time.
 */
export function recordAnthropicAxiosError(error, model, feature) {
    if (error && (error.isAxiosError || error.response)) recordAnthropicFailure(model, feature);
}

/**
 * Collects usage from an Anthropic SSE stream. Feed every parsed event to onEvent(); call finish(true) once at the end of the
 * stream or finish(false) on a stream error. Records exactly once.
 */
export function createStreamUsageRecorder(model, feature) {
    const total = {};
    let m = model;
    let done = false;
    return {
        onEvent(ev) {
            if (!ev) return;
            if (ev.type === 'message_start' && ev.message) { m = ev.message.model || m; Object.assign(total, ev.message.usage || {}); }
            else if (ev.type === 'message_delta' && ev.usage) Object.assign(total, ev.usage);
        },
        finish(ok = true) {
            if (done) return;
            done = true;
            try {
                if (ok) llm.record({ model: m, usage: total, feature });
                else llm.record({ model: m, usage: total, feature, ok: false });
            } catch { /* ignore */ }
        },
    };
}

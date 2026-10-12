// Shared OpenAI image generation helper (gpt-image-* models).
//
// Replaces the old dall-e-3 + response_format:"b64_json" calls. The gpt-image
// models always return base64 in data[0].b64_json and REJECT `response_format`
// with HTTP 400 "Unknown parameter". Allowed sizes: 1024x1024, 1024x1536,
// 1536x1024, auto. quality: low | medium | high | auto.
//
// Env (read at call time):
//   OPENAI_IMAGE_MODEL           default 'gpt-image-1-mini'
//   OPENAI_IMAGE_QUALITY         default 'low'
//   OPENAI_IMAGE_QUALITY_DETAIL  default 'medium' (services needing fine detail)

export const IMAGES_URL = 'https://api.openai.com/v1/images/generations';
export const DEFAULT_IMAGE_MODEL = 'gpt-image-1-mini';
export const DEFAULT_IMAGE_QUALITY = 'low';
export const DEFAULT_DETAIL_QUALITY = 'medium';

const VALID_QUALITY = new Set(['low', 'medium', 'high', 'auto']);
const VALID_SIZES = new Set(['1024x1024', '1024x1536', '1536x1024', 'auto']);

// dall-e-3 sizes -> nearest gpt-image size
const SIZE_MAP = {
    '1024x1792': '1024x1536',
    '1792x1024': '1536x1024',
    '512x512': '1024x1024',
    '256x256': '1024x1024'
};

export function getImageModel() {
    return (process.env.OPENAI_IMAGE_MODEL || '').trim() || DEFAULT_IMAGE_MODEL;
}

/** True for any persisted `source` label that denotes an AI-generated image (old and new). */
export function isAIImageSource(source) {
    if (!source) return false;
    const s = String(source).toLowerCase();
    return s === 'dall-e-3' || s === 'dall-e-2' || s.startsWith('gpt-image') || s === getImageModel().toLowerCase();
}

export function mapSize(size) {
    if (!size) return '1024x1024';
    const s = String(size).trim().toLowerCase();
    if (VALID_SIZES.has(s)) return s;
    if (SIZE_MAP[s]) return SIZE_MAP[s];
    // Unknown: pick by orientation, else square
    const m = s.match(/^(\d+)x(\d+)$/);
    if (m) {
        const w = Number(m[1]);
        const h = Number(m[2]);
        if (w > h) return '1536x1024';
        if (h > w) return '1024x1536';
    }
    return '1024x1024';
}

export function resolveQuality(quality) {
    const pick = (v) => {
        const q = (v || '').toString().trim().toLowerCase();
        return VALID_QUALITY.has(q) ? q : null;
    };
    // Legacy dall-e values: 'hd' -> high-ish, 'standard' -> default
    const legacy = { hd: 'high', standard: null };
    const q = (quality || '').toString().trim().toLowerCase();
    if (q in legacy) {
        return legacy[q] || pick(process.env.OPENAI_IMAGE_QUALITY) || DEFAULT_IMAGE_QUALITY;
    }
    return pick(quality) || pick(process.env.OPENAI_IMAGE_QUALITY) || DEFAULT_IMAGE_QUALITY;
}

/** Quality for services that need fine detail (Find Differences, Waldo). */
export function detailQuality() {
    return resolveQuality(process.env.OPENAI_IMAGE_QUALITY_DETAIL || DEFAULT_DETAIL_QUALITY);
}

export class OpenAIImageError extends Error {
    constructor(message, { status = null, code = null, type = null, openaiMessage = null, contentPolicy = false } = {}) {
        super(message);
        this.name = 'OpenAIImageError';
        this.status = status;
        this.code = code;
        this.type = type;
        this.openaiMessage = openaiMessage;
        this.isContentPolicy = contentPolicy;
    }
}

function redact(text) {
    return String(text ?? '').replace(/sk-[A-Za-z0-9_\-]{8,}/g, 'sk-***').replace(/Bearer\s+\S+/gi, 'Bearer ***');
}

function isContentPolicy(status, err) {
    const code = String(err?.code || '').toLowerCase();
    const type = String(err?.type || '').toLowerCase();
    if (code === 'content_policy_violation' || code === 'moderation_blocked') return true;
    if (type === 'content_policy_violation') return true;
    const msg = String(err?.message || '').toLowerCase();
    return status === 400 && /safety system|content policy|moderation/.test(msg);
}

/** Default transport: global fetch (Node 18+). Returns { status, data }. */
export async function defaultHttp({ url, headers, body, timeoutMs }) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body), signal: controller.signal });
        const text = await res.text();
        let data = null;
        try { data = text ? JSON.parse(text) : null; } catch { data = { error: { message: text.slice(0, 300) } }; }
        return { status: res.status, data };
    } catch (e) {
        if (e?.name === 'AbortError') {
            throw new OpenAIImageError(`OpenAI image request timed out after ${Math.round(timeoutMs / 1000)}s`, { code: 'timeout' });
        }
        throw new OpenAIImageError(`OpenAI image request failed: ${redact(e?.message)}`, { code: 'network_error' });
    } finally {
        clearTimeout(timer);
    }
}

/** Build the request body (exported for tests). Never contains response_format. */
export function buildImageRequest({ prompt, size, quality } = {}) {
    return {
        model: getImageModel(),
        prompt,
        n: 1,
        size: mapSize(size),
        quality: resolveQuality(quality)
    };
}

/**
 * Generate one image and return it as a Buffer.
 * @param {object} p
 * @param {string} p.prompt
 * @param {string} [p.size]      dall-e style sizes are mapped to the nearest allowed size
 * @param {string} [p.quality]   low|medium|high|auto (defaults to OPENAI_IMAGE_QUALITY or 'low')
 * @param {string} p.apiKey
 * @param {number} [p.timeoutMs]
 * @param {Function} [p.http]    injectable ({url, headers, body, timeoutMs}) => {status, data}
 */
export async function generateImageB64({ prompt, size, quality, apiKey, timeoutMs = 120000, http = defaultHttp } = {}) {
    if (!prompt || typeof prompt !== 'string') throw new OpenAIImageError('Image prompt is required');
    if (!apiKey) throw new OpenAIImageError('OPENAI_API_KEY not configured for image generation', { code: 'missing_api_key' });

    const body = buildImageRequest({ prompt, size, quality });
    const { status, data } = await http({
        url: IMAGES_URL,
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
        body,
        timeoutMs
    });

    if (status < 200 || status >= 300 || data?.error) {
        const err = data?.error || {};
        const openaiMessage = redact(err.message || `HTTP ${status}`);
        const policy = isContentPolicy(status, err);
        let message;
        if (policy) {
            message = `Image request blocked by OpenAI content policy (${err.code || 'content_policy_violation'}): ${openaiMessage}`;
        } else if (status === 401) {
            message = `OpenAI image generation unauthorized (HTTP 401): ${openaiMessage}`;
        } else if (status === 429) {
            message = `OpenAI image generation rate limited or out of quota (HTTP 429${err.code ? `, ${err.code}` : ''}): ${openaiMessage}`;
        } else {
            message = `OpenAI image generation failed (HTTP ${status}${err.code ? `, ${err.code}` : ''}): ${openaiMessage}`;
        }
        throw new OpenAIImageError(message, { status, code: err.code || null, type: err.type || null, openaiMessage, contentPolicy: policy });
    }

    const b64 = data?.data?.[0]?.b64_json;
    if (!b64 || typeof b64 !== 'string') {
        throw new OpenAIImageError('OpenAI image response contained no data[0].b64_json', { status, code: 'empty_response' });
    }
    const buffer = Buffer.from(b64, 'base64');
    if (buffer.length === 0) throw new OpenAIImageError('OpenAI image response decoded to an empty image', { status, code: 'empty_response' });
    return buffer;
}

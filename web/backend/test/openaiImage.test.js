const KEY = ['sk', 'test', 'fake'].join('-') + '0123456789abcdefghij'; // built at runtime so secret scanners do not flag a fake key
// Tests for utils/openaiImage.js and the request each migrated service builds.
// No network: an injectable http fn (helper) or a stubbed global fetch (services).
import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
    generateImageB64, buildImageRequest, mapSize, resolveQuality, detailQuality,
    getImageModel, isAIImageSource, OpenAIImageError, IMAGES_URL
} from '../utils/openaiImage.js';

// Dummy values so service modules (which build singletons at import) can load. Never real keys.
process.env.OPENAI_API_KEY ||= 'sk-fake';
process.env.ANTHROPIC_API_KEY ||= 'fake-anthropic';
process.env.SUPABASE_URL ||= 'https://fake-project.supabase.co';
process.env.SUPABASE_ANON_KEY ||= 'fake-anon';
process.env.SUPABASE_SERVICE_ROLE_KEY ||= 'fake-service';

const PNG_B64 = Buffer.from('fake-png-bytes').toString('base64');
const okHttp = (calls) => async (req) => { calls.push(req); return { status: 200, data: { data: [{ b64_json: PNG_B64 }] } }; };
const errHttp = (status, error) => async () => ({ status, data: { error } });

const ENV_KEYS = ['OPENAI_IMAGE_MODEL', 'OPENAI_IMAGE_QUALITY', 'OPENAI_IMAGE_QUALITY_DETAIL'];
let saved;
beforeEach(() => { saved = Object.fromEntries(ENV_KEYS.map((k) => [k, process.env[k]])); ENV_KEYS.forEach((k) => delete process.env[k]); });
afterEach(() => { ENV_KEYS.forEach((k) => (saved[k] === undefined ? delete process.env[k] : (process.env[k] = saved[k]))); });

test('request body: model, n, size, quality default, no response_format, bearer header', async () => {
    const calls = [];
    const buf = await generateImageB64({ prompt: 'a cat', apiKey: KEY, http: okHttp(calls) });
    assert.ok(Buffer.isBuffer(buf));
    assert.equal(buf.toString(), 'fake-png-bytes');
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, IMAGES_URL);
    assert.deepEqual(calls[0].body, { model: 'gpt-image-1-mini', prompt: 'a cat', n: 1, size: '1024x1024', quality: 'low' });
    assert.ok(!('response_format' in calls[0].body));
    assert.ok(!('style' in calls[0].body));
    assert.equal(calls[0].headers.Authorization, `Bearer ${KEY}`);
});

test('size mapping', () => {
    assert.equal(mapSize('1024x1024'), '1024x1024');
    assert.equal(mapSize('1792x1024'), '1536x1024');
    assert.equal(mapSize('1024x1792'), '1024x1536');
    assert.equal(mapSize('1536x1024'), '1536x1024');
    assert.equal(mapSize('1024x1536'), '1024x1536');
    assert.equal(mapSize('auto'), 'auto');
    assert.equal(mapSize(undefined), '1024x1024');
    assert.equal(mapSize('2000x1000'), '1536x1024');
    assert.equal(mapSize('garbage'), '1024x1024');
});

test('quality: default low, env override, explicit wins, legacy values, invalid ignored', () => {
    assert.equal(resolveQuality(), 'low');
    process.env.OPENAI_IMAGE_QUALITY = 'medium';
    assert.equal(resolveQuality(), 'medium');
    assert.equal(resolveQuality('high'), 'high');
    assert.equal(resolveQuality('standard'), 'medium'); // legacy dall-e value -> env/default
    assert.equal(resolveQuality('hd'), 'high');
    process.env.OPENAI_IMAGE_QUALITY = 'bogus';
    assert.equal(resolveQuality(), 'low');
    assert.equal(buildImageRequest({ prompt: 'x', quality: 'auto' }).quality, 'auto');
});

test('detail quality: default medium, own env override', () => {
    assert.equal(detailQuality(), 'medium');
    process.env.OPENAI_IMAGE_QUALITY_DETAIL = 'high';
    assert.equal(detailQuality(), 'high');
});

test('model override via env', async () => {
    process.env.OPENAI_IMAGE_MODEL = 'gpt-image-1';
    const calls = [];
    await generateImageB64({ prompt: 'x', apiKey: KEY, http: okHttp(calls) });
    assert.equal(calls[0].body.model, 'gpt-image-1');
    assert.equal(getImageModel(), 'gpt-image-1');
});

test('size is mapped in the outgoing request', async () => {
    const calls = [];
    await generateImageB64({ prompt: 'x', size: '1792x1024', quality: 'medium', apiKey: KEY, http: okHttp(calls) });
    assert.equal(calls[0].body.size, '1536x1024');
    assert.equal(calls[0].body.quality, 'medium');
});

test('content_policy_violation and moderation_blocked map to a distinct error', async () => {
    for (const code of ['content_policy_violation', 'moderation_blocked']) {
        await assert.rejects(
            generateImageB64({ prompt: 'x', apiKey: KEY, http: errHttp(400, { code, message: 'Your request was rejected by the safety system.' }) }),
            (e) => e instanceof OpenAIImageError && e.isContentPolicy === true && e.status === 400 && e.code === code
                && /blocked by OpenAI content policy/.test(e.message) && /safety system/.test(e.message)
        );
    }
});

test('401, 429 and generic 400 map to readable non-policy errors', async () => {
    await assert.rejects(generateImageB64({ prompt: 'x', apiKey: KEY, http: errHttp(401, { message: 'Incorrect API key provided' }) }),
        (e) => e.status === 401 && !e.isContentPolicy && /unauthorized/.test(e.message) && /Incorrect API key/.test(e.message));
    await assert.rejects(generateImageB64({ prompt: 'x', apiKey: KEY, http: errHttp(429, { code: 'rate_limit_exceeded', message: 'Slow down' }) }),
        (e) => e.status === 429 && !e.isContentPolicy && /rate limited/.test(e.message) && /Slow down/.test(e.message));
    await assert.rejects(generateImageB64({ prompt: 'x', apiKey: KEY, http: errHttp(400, { message: "Unknown parameter: 'foo'" }) }),
        (e) => e.status === 400 && !e.isContentPolicy && /Unknown parameter/.test(e.message));
});

test('error messages never contain the api key', async () => {
    await assert.rejects(
        generateImageB64({ prompt: 'x', apiKey: KEY, http: errHttp(401, { message: `Incorrect API key provided: ${KEY}. Bearer ${KEY}` }) }),
        (e) => !e.message.includes(KEY) && !e.message.includes('SECRETSECRET') && !JSON.stringify(e).includes('SECRETSECRET')
    );
});

test('missing b64_json / empty body / missing key / missing prompt are errors', async () => {
    await assert.rejects(generateImageB64({ prompt: 'x', apiKey: KEY, http: async () => ({ status: 200, data: { data: [{ url: 'u' }] } }) }), /no data\[0\]\.b64_json/);
    await assert.rejects(generateImageB64({ prompt: 'x', apiKey: KEY, http: async () => ({ status: 200, data: null }) }), /no data\[0\]\.b64_json/);
    await assert.rejects(generateImageB64({ prompt: 'x', http: okHttp([]) }), /OPENAI_API_KEY not configured/);
    await assert.rejects(generateImageB64({ apiKey: KEY, http: okHttp([]) }), /prompt is required/);
});

test('isAIImageSource accepts old and new labels', () => {
    assert.ok(isAIImageSource('dall-e-3'));
    assert.ok(isAIImageSource('gpt-image-1-mini'));
    assert.ok(isAIImageSource('gpt-image-1'));
    assert.ok(!isAIImageSource('pixabay'));
    assert.ok(!isAIImageSource(undefined));
});

test('no service sends response_format or dall-e-3 to OpenAI any more', () => {
    const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'services');
    for (const f of readdirSync(dir).filter((n) => n.endsWith('.js'))) {
        const src = readFileSync(path.join(dir, f), 'utf8');
        assert.ok(!src.includes('images/generations'), `${f} still calls images/generations directly`);
        assert.ok(!/response_format/.test(src), `${f} still references response_format`);
    }
});

// ---- service-level request construction via stubbed global fetch ----
const realFetch = globalThis.fetch;
function stubFetch(captured, response) {
    globalThis.fetch = async (url, init) => {
        captured.push({ url, body: JSON.parse(init.body), headers: init.headers });
        return { status: response.status, text: async () => JSON.stringify(response.json) };
    };
}
const OK = { status: 200, json: { data: [{ b64_json: PNG_B64 }] } };
const assertReq = (c, { size, quality }) => {
    assert.equal(c.length, 1);
    assert.equal(c[0].url, IMAGES_URL);
    assert.equal(c[0].body.model, 'gpt-image-1-mini');
    assert.equal(c[0].body.size, size);
    assert.equal(c[0].body.quality, quality);
    assert.ok(!('response_format' in c[0].body));
    assert.ok(!('style' in c[0].body));
    assert.equal(c[0].headers.Authorization, 'Bearer sk-fake');
};
const noop = () => {};

test('service: WhichIsRealGenerator.generateAIVariation', async (t) => {
    t.after(() => { globalThis.fetch = realFetch; });
    const { WhichIsRealGenerator } = await import('../services/whichIsRealGenerator.js');
    const g = Object.create(WhichIsRealGenerator.prototype);
    Object.assign(g, { openaiKey: 'sk-fake', debugLog: noop });
    const c = []; stubFetch(c, OK);
    const buf = await g.generateAIVariation(Buffer.alloc(1), 'a red barn');
    assert.ok(Buffer.isBuffer(buf));
    assertReq(c, { size: '1024x1024', quality: 'medium' });
    // content policy surfaces readably
    stubFetch([], { status: 400, json: { error: { code: 'content_policy_violation', message: 'blocked' } } });
    await assert.rejects(g.generateAIVariation(Buffer.alloc(1), 'x'), /content policy.*blocked/);
});

test('service: ImageQuestionGenerator.generateImage', async (t) => {
    t.after(() => { globalThis.fetch = realFetch; });
    const { ImageQuestionGenerator } = await import('../services/imageQuestionGenerator.js');
    const g = Object.create(ImageQuestionGenerator.prototype);
    g.openaiKey = 'sk-fake';
    const c = []; stubFetch(c, OK);
    assert.ok(Buffer.isBuffer(await g.generateImage('three apples')));
    assertReq(c, { size: '1024x1024', quality: 'low' });
});

test('service: FindDifferences.generateSplitScreenImage (wide size mapped, detail quality)', async (t) => {
    t.after(() => { globalThis.fetch = realFetch; });
    const { FindDifferencesGeneratorService } = await import('../services/findDifferencesGeneratorService.js');
    const g = Object.create(FindDifferencesGeneratorService.prototype);
    Object.assign(g, { apiKey: 'sk-fake', debugLog: noop, scenePrompts: ['a kitchen table'] });
    const c = []; stubFetch(c, OK);
    const r = await g.generateSplitScreenImage({ requestedDifferences: 5 });
    assert.ok(Buffer.isBuffer(r.imageBuffer));
    assertReq(c, { size: '1536x1024', quality: 'medium' });
});

test('service: ImagePuzzleGenerator.generateImageAndMetadata request', async (t) => {
    t.after(() => { globalThis.fetch = realFetch; });
    const { ImagePuzzleGenerator } = await import('../services/imagePuzzleGenerator.js');
    const g = Object.create(ImagePuzzleGenerator.prototype);
    Object.assign(g, { apiKey: 'sk-fake', debugLog: noop, createOptimizedImagePrompt: () => 'p', uploadImage: async () => null });
    const c = []; stubFetch(c, OK);
    await g.generateImageAndMetadata('t', 'easy').catch(() => {}); // stops at the stubbed upload
    assertReq(c, { size: '1024x1024', quality: 'low' });
});

test('service: WaldoPuzzleGenerator.generateSceneAndDetectObjects request', async (t) => {
    t.after(() => { globalThis.fetch = realFetch; });
    const { WaldoPuzzleGenerator } = await import('../services/waldoPuzzleGeneratorService.js');
    const g = Object.create(WaldoPuzzleGenerator.prototype);
    Object.assign(g, { apiKey: 'sk-fake', debugLog: noop, createOptimizedScenePrompt: () => 'p', uploadImage: async () => null });
    const c = []; stubFetch(c, OK);
    await g.generateSceneAndDetectObjects({}, [], 'easy').catch(() => {}); // stops at the stubbed upload
    assertReq(c, { size: '1024x1024', quality: 'medium' });
});

test('service: EnhancedPixabay.generateAIImage returns buffer and records block reason', async (t) => {
    t.after(() => { globalThis.fetch = realFetch; });
    const { EnhancedPixabayPuzzleSystem } = await import('../services/enhancedPixabayImageMatch.js');
    const g = Object.create(EnhancedPixabayPuzzleSystem.prototype);
    Object.assign(g, { openaiKey: 'sk-fake', useAIFallback: true, log: noop });
    const c = []; stubFetch(c, OK);
    const r = await g.generateAIImage('Eiffel Tower');
    assert.ok(Buffer.isBuffer(r.buffer));
    assert.equal(r.model, 'gpt-image-1-mini');
    assertReq(c, { size: '1024x1024', quality: 'low' });
    stubFetch([], { status: 400, json: { error: { code: 'content_policy_violation', message: 'nope' } } });
    assert.equal(await g.generateAIImage('x'), null);
    assert.match(g.lastAIImageError, /content policy/);
});

test('service: ProgressivePuzzleSystem.generateAIImage', async (t) => {
    t.after(() => { globalThis.fetch = realFetch; });
    const { ProgressivePuzzleSystem } = await import('../services/progressivePuzzleSystem.js');
    const g = Object.create(ProgressivePuzzleSystem.prototype);
    Object.assign(g, { openaiKey: 'sk-fake', debugLog: noop, log: noop });
    const c = []; stubFetch(c, OK);
    const r = await g.generateAIImage('Mount Fuji', []);
    assert.ok(Buffer.isBuffer(r.buffer));
    assert.equal(r.source, 'gpt-image-1-mini');
    assert.equal(r.metadata.model, 'gpt-image-1-mini');
    assertReq(c, { size: '1024x1024', quality: 'low' });
    stubFetch([], { status: 429, json: { error: { message: 'quota' } } });
    await assert.rejects(g.generateAIImage('x', []), /rate limited.*quota/);
});

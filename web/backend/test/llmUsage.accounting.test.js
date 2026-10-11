import { test, before, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import axios from 'axios';

const backendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// The shared client captures global fetch when it is created, so stub it BEFORE importing the instance.
const sent = [];
globalThis.fetch = async (url, init) => { sent.push({ url, headers: init.headers, body: JSON.parse(init.body) }); return { ok: true, status: 200 }; };
process.env.LLM_USAGE_KEY = 'test-key-not-real';
process.env.ANTHROPIC_API_KEY = 'anthropic-not-real';
for (const k of ['DEEPSEEK_API_KEY', 'GOOGLE_API_KEY', 'OPENAI_API_KEY']) delete process.env[k];

let ai, inst;
before(async () => {
  inst = await import('../utils/llmUsageInstance.js');
  ai = await import('../utils/aiClient.js');
});

async function rows() {
  sent.length = 0;
  await inst.llm.flush();
  return sent.flatMap((s) => s.body.items);
}
beforeEach(async () => { await rows(); });

const okBody = (text, usage = { input_tokens: 1000, output_tokens: 500 }) => ({ data: { model: 'claude-haiku-4-5-20251001', content: [{ type: 'text', text }], usage } });

test('instance is enabled with a key and reports to /v1/llm with the key header', async () => {
  assert.equal(inst.llm.enabled, true);
  const orig = axios.post;
  axios.post = async () => okBody('OK');
  try { await ai.callAI('hi', 'claude-3-sonnet', 2, { feature: 'health_check' }); } finally { axios.post = orig; }
  sent.length = 0;
  await inst.llm.flush();
  assert.equal(sent.length, 1);
  assert.match(sent[0].url, /\/v1\/llm$/);
  assert.equal(sent[0].headers['X-Report-Key'], 'test-key-not-real');
});

test('callAI records tokens, cost, model and the explicit feature label', async () => {
  const orig = axios.post;
  axios.post = async () => okBody('OK');
  try { await ai.callAI('hi', 'claude-3-sonnet', 2, { feature: 'daily_puzzle' }); } finally { axios.post = orig; }
  const r = await rows();
  assert.equal(r.length, 1);
  assert.equal(r[0].feature, 'daily_puzzle');
  assert.equal(r[0].model, 'claude-haiku-4-5');
  assert.equal(r[0].calls, 1);
  assert.equal(r[0].errors, 0);
  assert.equal(r[0].input_tokens, 1000);
  assert.equal(r[0].output_tokens, 500);
  assert.ok(Math.abs(r[0].cost_usd - (1000 * 1 + 500 * 5) / 1e6) < 1e-9);
});

test('feature falls back to the usageTracker category, then ai_other', async () => {
  const orig = axios.post;
  axios.post = async () => okBody('OK');
  try {
    await ai.callAI('hi', 'claude-3-sonnet', 2, { category: 'puzzle_generation' });
    await ai.callAI('hi', 'claude-3-sonnet', 2, {});
  } finally { axios.post = orig; }
  const feats = (await rows()).map((x) => x.feature).sort();
  assert.deepEqual(feats, ['ai_other', 'puzzle_generation']);
});

test('callAIWithRetry records EVERY attempt that returned a usage block', async () => {
  const orig = axios.post;
  let n = 0;
  axios.post = async () => { n++; return okBody('not json at all'); }; // valid HTTP + usage, but fails validateAIResponse -> retried
  try {
    await assert.rejects(ai.callAIWithRetry('hi', 'claude-3-sonnet', 3), /failed after 3 attempts/);
  } finally { axios.post = orig; }
  assert.equal(n, 3);
  const r = await rows();
  const total = r.reduce((a, x) => ({ calls: a.calls + x.calls, input: a.input + x.input_tokens }), { calls: 0, input: 0 });
  assert.equal(total.calls, 3);
  assert.equal(total.input, 3000);
});

test('failed HTTP attempts are recorded as errors with no cost', async () => {
  const orig = axios.post;
  axios.post = async () => { const e = new Error('unauthorized'); e.isAxiosError = true; e.response = { status: 401 }; throw e; };
  try { await assert.rejects(ai.callAI('hi', 'claude-3-sonnet', 2, { feature: 'daily_puzzle' })); } finally { axios.post = orig; }
  const r = await rows();
  assert.equal(r.length, 1);
  assert.equal(r[0].calls, 1);
  assert.equal(r[0].errors, 1);
  assert.equal(r[0].cost_usd, 0);
});

test('non-axios errors (our own parse failures) are not counted as API calls', async () => {
  inst.recordAnthropicAxiosError(new Error('JSON parse'), 'claude-haiku-4-5', 'x_feature');
  assert.equal((await rows()).length, 0);
});

test('recordAnthropicResponse: usage block -> ok call; body without usage -> error call', async () => {
  inst.recordAnthropicResponse({ model: 'claude-sonnet-4-6', usage: { input_tokens: 10, output_tokens: 20 } }, 'waldo_puzzle', 'x');
  inst.recordAnthropicResponse({ error: { message: 'bad' } }, 'waldo_puzzle', 'claude-sonnet-4-6');
  const r = await rows();
  assert.equal(r.length, 1);
  assert.equal(r[0].calls, 2);
  assert.equal(r[0].errors, 1);
  assert.equal(r[0].input_tokens, 10);
});

test('stream recorder merges message_start + message_delta usage and records once', async () => {
  const s = inst.createStreamUsageRecorder('claude-haiku-4-5-20251001', 'llm_route_stream');
  s.onEvent({ type: 'message_start', message: { model: 'claude-haiku-4-5-20251001', usage: { input_tokens: 300, output_tokens: 1 } } });
  s.onEvent({ type: 'message_delta', usage: { output_tokens: 80 } });
  s.finish(true); s.finish(true);
  const r = await rows();
  assert.equal(r.length, 1);
  assert.equal(r[0].calls, 1);
  assert.equal(r[0].input_tokens, 300);
  assert.equal(r[0].output_tokens, 80);
});

test('every call site carries its feature label and uses the shared instance', () => {
  const sites = {
    'routes/llm.routes.js': ['llm_route', 'llm_route_stream'],
    'routes/user.routes.js': ['user_route'],
    'services/waldoPuzzleGeneratorService.js': ['waldo_puzzle'],
    'services/findDifferencesGeneratorService.js': ['find_differences'],
    'services/enhancedPixabayImageMatch.js': ['image_match'],
    'services/progressivePuzzleSystem.js': ['progressive_puzzle'],
    'services/startupHealthCheck.js': ['health_check'],
    'services/dailyPuzzleService.js': ['daily_puzzle'],
    'services/findObjectGenerator.js': ['find_object'],
    'services/imageQuestionGenerator.js': ['image_question'],
    'server.js': ['daily_warm'],
  };
  for (const [file, labels] of Object.entries(sites)) {
    const src = readFileSync(path.join(backendDir, file), 'utf8');
    for (const l of labels) assert.ok(src.includes(`'${l}'`), `${file} missing label ${l}`);
    if (file !== 'services/startupHealthCheck.js' && file !== 'services/dailyPuzzleService.js' && file !== 'server.js') {
      assert.ok(src.includes('llmUsageInstance.js'), `${file} does not use the shared instance`);
    }
  }
});

test('server.js flushes on SIGTERM with a 2.5 s cap', () => {
  const src = readFileSync(path.join(backendDir, 'server.js'), 'utf8');
  assert.match(src, /process\.on\('SIGTERM'/);
  assert.match(src, /llm\.flush\(\)/);
  assert.match(src, /setTimeout\(resolve, 2500\)/);
});

test('nothing is sent when no key is set (disabled and harmless)', () => {
  const script = `
    globalThis.fetch = async () => { console.log('SENT'); return { ok: true, status: 200 }; };
    const inst = await import('./utils/llmUsageInstance.js');
    inst.recordAnthropicResponse({ model: 'claude-haiku-4-5', usage: { input_tokens: 5, output_tokens: 5 } }, 'x', 'y');
    inst.recordAnthropicFailure('claude-haiku-4-5', 'x');
    await inst.llm.flush();
    console.log('enabled=' + inst.llm.enabled + ' pending=' + inst.llm.pending());
  `;
  const env = { ...process.env };
  delete env.LLM_USAGE_KEY; delete env.FAILURE_REPORTER_KEY;
  const r = spawnSync(process.execPath, ['--input-type=module', '-e', script], { cwd: backendDir, env, encoding: 'utf8' });
  assert.equal(r.status, 0, r.stderr);
  assert.ok(!r.stdout.includes('SENT'));
  assert.match(r.stdout, /enabled=false pending=0/);
});

test('FAILURE_REPORTER_KEY is the fallback key', () => {
  const script = `
    const inst = await import('./utils/llmUsageInstance.js');
    console.log('enabled=' + inst.llm.enabled);
  `;
  const env = { ...process.env, FAILURE_REPORTER_KEY: 'fallback-not-real' };
  delete env.LLM_USAGE_KEY;
  const r = spawnSync(process.execPath, ['--input-type=module', '-e', script], { cwd: backendDir, env, encoding: 'utf8' });
  assert.match(r.stdout, /enabled=true/);
});

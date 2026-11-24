import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawnSync } from 'node:child_process';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import {
  buildChildEnv, buildAgentEnv, buildGitEnv, buildAgentCommand, loadSecrets, startAnthropicProxy, listRegularFiles, SECRET_NAMES
} from '../agentSandbox.js';

const REAL_KEY = 'sk-ant-api03-REALKEYREALKEYREALKEY';
const WORKER = 'workersecret-ABCDEFGH12345678';
const PAT = 'ghp_PATPATPATPATPAT';
const dirtyEnv = () => ({
  PATH: '/usr/bin:/bin', HOME: '/home/node', LANG: 'C.UTF-8', TERM: 'xterm', TMPDIR: '/tmp',
  WORKER_SECRET: WORKER, ANTHROPIC_API_KEY: REAL_KEY, GITHUB_PAT: PAT,
  FLY_API_TOKEN: 'fo1_x', AWS_SECRET_ACCESS_KEY: 'aws', SOMETHING_TOKEN: 't', ANTHROPIC_AUTH_TOKEN: 'auth', OPENAI_API_KEY: 'sk-x'
});

test('child env: allowlisted keys only, no secrets, even when a caller tries to add them', () => {
  const env = buildChildEnv(dirtyEnv(), { ANTHROPIC_API_KEY: REAL_KEY, WORKER_SECRET: WORKER, FOO: 'bar' });
  const joined = JSON.stringify(env);
  for (const v of [REAL_KEY, WORKER, PAT, 'fo1_x', 'aws', 'auth']) assert.ok(!joined.includes(v), `leaked ${v}`);
  for (const n of [...SECRET_NAMES, 'FLY_API_TOKEN', 'AWS_SECRET_ACCESS_KEY', 'SOMETHING_TOKEN', 'ANTHROPIC_AUTH_TOKEN', 'OPENAI_API_KEY']) assert.ok(!(n in env), n);
  assert.equal(env.FOO, 'bar');
  assert.equal(env.HOME, '/home/node');
  assert.ok(env.PATH.startsWith('/usr/bin:/bin'));
});

test('agent env: proxy URL and throwaway token replace the real key; git env disables hooks', () => {
  const proxy = { baseUrl: 'http://127.0.0.1:4242', token: 'gw-local-abc' };
  const env = buildAgentEnv({ baseEnv: dirtyEnv(), home: '/home/agent', proxy });
  assert.equal(env.ANTHROPIC_BASE_URL, 'http://127.0.0.1:4242');
  assert.equal(env.ANTHROPIC_API_KEY, 'gw-local-abc');
  assert.equal(env.HOME, '/home/agent');
  assert.ok(!JSON.stringify(env).includes(REAL_KEY));
  assert.ok(!JSON.stringify(env).includes(WORKER));
  const noProxy = buildAgentEnv({ baseEnv: dirtyEnv() });
  assert.ok(!('ANTHROPIC_API_KEY' in noProxy));
  const git = buildGitEnv(dirtyEnv());
  assert.equal(git.GIT_CONFIG_VALUE_0, '/dev/null');
  assert.ok(!JSON.stringify(git).includes(PAT));
});

test('agent command: direct, or via sudo as the agent user with env -i', () => {
  const env = { PATH: '/usr/bin', HOME: '/home/agent', ANTHROPIC_API_KEY: 'gw-local-abc' };
  const direct = buildAgentCommand({ claudePath: '/usr/bin/claude', args: ['-p', 'x'], env });
  assert.equal(direct.cmd, '/usr/bin/claude');
  const viaSudo = buildAgentCommand({ claudePath: '/usr/bin/claude', args: ['-p', 'x'], env, agentUser: 'agent' });
  assert.equal(viaSudo.cmd, 'sudo');
  assert.deepEqual(viaSudo.args.slice(0, 5), ['-n', '-u', 'agent', '--', '/usr/bin/env']);
  assert.ok(viaSudo.args.includes('-i') && viaSudo.args.includes('ANTHROPIC_API_KEY=gw-local-abc'));
  assert.deepEqual(viaSudo.args.slice(-3), ['/usr/bin/claude', '-p', 'x']);
});

test('loadSecrets: from env or from a root-written file; both end up out of process.env, file is deleted', () => {
  const env = dirtyEnv();
  const got = loadSecrets({ env });
  assert.equal(got.WORKER_SECRET, WORKER);
  assert.equal(got.ANTHROPIC_API_KEY, REAL_KEY);
  for (const n of SECRET_NAMES) assert.ok(!(n in env));

  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gw-sec-'));
  const file = path.join(dir, 'secrets.json');
  fs.writeFileSync(file, JSON.stringify({ WORKER_SECRET: 'from-file', ANTHROPIC_API_KEY: 'k-file' }));
  const env2 = { GITHUB_PAT: 'env-pat' };
  const got2 = loadSecrets({ env: env2, secretsFile: file });
  assert.deepEqual(got2, { WORKER_SECRET: 'from-file', ANTHROPIC_API_KEY: 'k-file', GITHUB_PAT: 'env-pat' });
  assert.ok(!fs.existsSync(file));
  assert.deepEqual(env2, {});
  assert.equal(loadSecrets({ env: {} }).WORKER_SECRET, '');
});

// ---- proxy ----

function startUpstream(handler) {
  return new Promise((resolve) => {
    const seen = [];
    const server = http.createServer((req, res) => {
      const chunks = [];
      req.on('data', (c) => chunks.push(c));
      req.on('end', () => { const rec = { method: req.method, url: req.url, headers: req.headers, body: Buffer.concat(chunks).toString() }; seen.push(rec); handler(rec, res); });
    });
    server.listen(0, '127.0.0.1', () => resolve({ server, seen, url: `http://127.0.0.1:${server.address().port}` }));
  });
}

function call(proxy, { method = 'POST', url = '/v1/messages', headers = {}, body = '{"a":1}', auth = true } = {}) {
  return new Promise((resolve, reject) => {
    const h = { ...headers };
    if (auth && !('x-api-key' in h)) h['x-api-key'] = proxy.token;
    const req = http.request({ host: '127.0.0.1', port: proxy.port, method, path: url, headers: h }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push({ t: Date.now(), c }));
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, chunks, body: Buffer.concat(chunks.map(x => x.c)).toString() }));
    });
    req.on('error', reject);
    req.end(body);
  });
}

test('proxy: injects the real key, drops client auth headers, keeps other headers, never exposes the key', async () => {
  const up = await startUpstream((rec, res) => { res.writeHead(200, { 'content-type': 'application/json' }); res.end('{"ok":true}'); });
  const proxy = await startAnthropicProxy({ apiKey: REAL_KEY, upstream: up.url });
  try {
    assert.ok(!JSON.stringify(proxy).includes(REAL_KEY));
    const r = await call(proxy, { url: '/v1/messages?beta=true', headers: { 'content-type': 'application/json', 'anthropic-version': '2023-06-01', authorization: 'Bearer stolen', cookie: 'a=b' } });
    assert.equal(r.status, 200);
    assert.equal(r.body, '{"ok":true}');
    const seen = up.seen[0];
    assert.equal(seen.url, '/v1/messages?beta=true');
    assert.equal(seen.headers['x-api-key'], REAL_KEY);
    assert.equal(seen.headers['anthropic-version'], '2023-06-01');
    assert.equal(seen.headers.authorization, undefined);
    assert.equal(seen.headers.cookie, undefined);
    assert.equal(seen.body, '{"a":1}');
    assert.ok(!r.body.includes(REAL_KEY));
  } finally { await proxy.close(); up.server.close(); }
});

test('proxy: refuses a client without the local token, non-/v1 paths, and never forwards them', async () => {
  const up = await startUpstream((rec, res) => { res.writeHead(200); res.end('ok'); });
  const proxy = await startAnthropicProxy({ apiKey: REAL_KEY, upstream: up.url });
  try {
    assert.equal((await call(proxy, { auth: false })).status, 401);
    assert.equal((await call(proxy, { headers: { 'x-api-key': 'sk-ant-guess' } })).status, 401);
    assert.equal((await call(proxy, { url: '/admin/keys' })).status, 404);
    assert.equal((await call(proxy, { url: '/v1/../admin' })).status, 404);
    assert.equal(up.seen.length, 0);
  } finally { await proxy.close(); up.server.close(); }
});

test('proxy: streams the response as it arrives (does not buffer)', async () => {
  const up = await startUpstream((rec, res) => {
    res.writeHead(200, { 'content-type': 'text/event-stream' });
    res.write('event: a\n\n');
    setTimeout(() => { res.write('event: b\n\n'); res.end(); }, 250);
  });
  const proxy = await startAnthropicProxy({ apiKey: REAL_KEY, upstream: up.url });
  try {
    const r = await call(proxy);
    assert.equal(r.body, 'event: a\n\nevent: b\n\n');
    assert.ok(r.chunks.length >= 2, 'expected the two events in separate chunks');
    assert.ok(r.chunks[r.chunks.length - 1].t - r.chunks[0].t >= 150, 'second event should arrive later');
    assert.equal(r.headers['content-type'], 'text/event-stream');
  } finally { await proxy.close(); up.server.close(); }
});

test('proxy: request size cap and request rate cap', async () => {
  const up = await startUpstream((rec, res) => { res.writeHead(200); res.end('ok'); });
  const proxy = await startAnthropicProxy({ apiKey: REAL_KEY, upstream: up.url, maxBodyBytes: 100, maxRequestsPerMinute: 4 });
  try {
    assert.equal((await call(proxy, { body: 'x'.repeat(101) })).status, 413);
    assert.equal((await call(proxy, { body: 'x'.repeat(100) })).status, 200);
    assert.equal((await call(proxy)).status, 200);
    assert.equal((await call(proxy)).status, 200); // 4th request in the window
    assert.equal((await call(proxy)).status, 429);
    assert.equal(up.seen.length, 3);
  } finally { await proxy.close(); up.server.close(); }
});

test('proxy: upstream failure gives a generic 502 without leaking the key', async () => {
  const proxy = await startAnthropicProxy({ apiKey: REAL_KEY, upstream: 'http://127.0.0.1:1' });
  try {
    const r = await call(proxy);
    assert.equal(r.status, 502);
    assert.ok(!r.body.includes(REAL_KEY));
  } finally { await proxy.close(); }
});

// ---- fake agent ----

test('fake agent child running env, printenv and a shell cannot see any secret', () => {
  const proxy = { baseUrl: 'http://127.0.0.1:4242', token: 'gw-local-abc' };
  const env = buildAgentEnv({ baseEnv: dirtyEnv(), home: os.tmpdir(), proxy });
  const out = [
    spawnSync('/usr/bin/env', [], { env, encoding: 'utf-8' }).stdout,
    spawnSync('/bin/sh', ['-c', 'printenv; echo "$WORKER_SECRET|$GITHUB_PAT|$FLY_API_TOKEN"; set'], { env, encoding: 'utf-8' }).stdout,
    spawnSync(process.execPath, ['-e', 'console.log(JSON.stringify(process.env))'], { env, encoding: 'utf-8' }).stdout
  ].join('\n');
  for (const v of [REAL_KEY, WORKER, WORKER.slice(0, 8), PAT, 'fo1_x', 'aws', 'sk-x']) assert.ok(!out.includes(v), `agent saw ${v}`);
  assert.ok(out.includes('ANTHROPIC_BASE_URL=http://127.0.0.1:4242'));
  assert.ok(!/sk-ant/.test(out));
});

// ---- bundle walker ----

test('listRegularFiles skips symlinks (to a secret file or dir), CLAUDE.md and .claude', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'gw-walk-'));
  const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'gw-outside-'));
  fs.writeFileSync(path.join(outside, 'secret.txt'), REAL_KEY);
  fs.writeFileSync(path.join(root, 'index.html'), '<html>');
  fs.mkdirSync(path.join(root, 'js'));
  fs.writeFileSync(path.join(root, 'js', 'game.js'), '1');
  fs.writeFileSync(path.join(root, 'CLAUDE.md'), 'x');
  fs.mkdirSync(path.join(root, '.claude'));
  fs.writeFileSync(path.join(root, '.claude', 's.json'), '{}');
  fs.symlinkSync(path.join(outside, 'secret.txt'), path.join(root, 'leak.html'));
  fs.symlinkSync(outside, path.join(root, 'leakdir'));
  const rels = listRegularFiles(root).map(f => f.rel).sort();
  assert.deepEqual(rels, ['index.html', 'js/game.js']);
});

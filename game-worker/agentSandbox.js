/**
 * Secret isolation for the game worker.
 *
 * The worker runs the Claude Code CLI with permissions skipped, so a hostile prompt can make the
 * agent run shell commands. Everything here exists so that those commands cannot find a credential:
 *
 *  1. buildAgentEnv:        the CLI (and chromium, git) get an env built from scratch, never process.env.
 *  2. startAnthropicProxy:  the real API key lives only in this in-process proxy; the CLI is pointed at it
 *                           with a throwaway per-process token.
 *  3. loadSecrets:          reads the secrets once (from a root-written file or the env), then removes them
 *                           from process.env.
 *  4. buildAgentCommand:    optionally runs the CLI as another unix user (sudo) so it cannot read the
 *                           worker's /proc/<pid>/mem or files.
 *  5. listRegularFiles:     bundle walkers never follow symlinks (an agent-made link to a secret file).
 */
import http from 'node:http';
import https from 'node:https';
import crypto from 'node:crypto';
import * as fs from 'node:fs';
import * as path from 'node:path';

export const SECRET_NAMES = ['WORKER_SECRET', 'ANTHROPIC_API_KEY', 'GITHUB_PAT'];

// ---------------------------------------------------------------------------
// 1. Child environment allowlist
// ---------------------------------------------------------------------------

const ENV_ALLOWLIST = [
    'PATH', 'HOME', 'LANG', 'LC_ALL', 'TERM', 'TMPDIR', 'TZ',
    // CLI behaviour switches set in the image, and the model choice (not secrets)
    'DISABLE_AUTOUPDATER', 'CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC', 'ANTHROPIC_MODEL', 'ANTHROPIC_SMALL_FAST_MODEL',
    'PUPPETEER_EXECUTABLE_PATH', 'PUPPETEER_SKIP_DOWNLOAD', 'NODE_ENV'
];

/** Env for any child that runs or touches project code. Only allowlisted names, plus explicit extras. */
export function buildChildEnv(baseEnv = process.env, extra = {}) {
    const env = {};
    for (const name of ENV_ALLOWLIST) {
        if (typeof baseEnv[name] === 'string') env[name] = baseEnv[name];
    }
    env.PATH = `${baseEnv.PATH || ''}:/usr/bin:/usr/local/bin:/opt/homebrew/bin`;
    for (const [k, v] of Object.entries(extra)) {
        if (SECRET_NAMES.includes(k)) continue; // a caller can never smuggle a real secret back in
        if (v === undefined || v === null) continue;
        env[k] = String(v);
    }
    return env;
}

/** Env for the Claude CLI: allowlist plus the local proxy as its API endpoint (when there is one). */
export function buildAgentEnv({ baseEnv = process.env, home, proxy } = {}) {
    const env = buildChildEnv(baseEnv, home ? { HOME: home } : {});
    if (proxy) {
        env.ANTHROPIC_BASE_URL = proxy.baseUrl;
        env.ANTHROPIC_API_KEY = proxy.token; // throwaway token, only meaningful to the local proxy
    }
    return env;
}

/** Env for git: allowlist, no repo hooks (a hostile agent can plant .git/hooks in its workdir). */
export function buildGitEnv(baseEnv = process.env) {
    return buildChildEnv(baseEnv, {
        GIT_CONFIG_COUNT: '1',
        GIT_CONFIG_KEY_0: 'core.hooksPath',
        GIT_CONFIG_VALUE_0: '/dev/null',
        GIT_TERMINAL_PROMPT: '0'
    });
}

/**
 * Command line for the agent. With agentUser set, it runs as that unix user through sudo with an
 * `env -i` wrapper (sudo would otherwise merge its own environment) and a group-writable umask so the
 * worker, which shares the group, can read and delete what the agent writes.
 */
export function buildAgentCommand({ claudePath, args, env, agentUser }) {
    if (!agentUser) return { cmd: claudePath, args, env };
    const pairs = Object.entries(env).map(([k, v]) => `${k}=${v}`);
    return {
        cmd: 'sudo',
        args: ['-n', '-u', agentUser, '--', '/usr/bin/env', '-i', ...pairs,
            '/bin/sh', '-c', 'umask 002 && exec "$0" "$@"', claudePath, ...args],
        env: buildChildEnv(env)
    };
}

// ---------------------------------------------------------------------------
// 3. Secrets: read once, then gone from process.env
// ---------------------------------------------------------------------------

/**
 * Returns { WORKER_SECRET, ANTHROPIC_API_KEY, GITHUB_PAT } (empty string when unset).
 * If a secrets file exists (written by the root entrypoint, owned by the worker user) it is read and
 * deleted; otherwise the values come from `env`. Either way the names are removed from `env`, so no
 * child process and no later code can inherit them.
 *
 * Note: deleting from process.env does not scrub /proc/<pid>/environ (the kernel keeps the original
 * block). The Docker entrypoint therefore starts the worker with those variables already unset.
 */
export function loadSecrets({ env = process.env, secretsFile } = {}) {
    const out = {};
    let fromFile = {};
    if (secretsFile && fs.existsSync(secretsFile)) {
        try { fromFile = JSON.parse(fs.readFileSync(secretsFile, 'utf-8')); } catch { fromFile = {}; }
        try { fs.unlinkSync(secretsFile); } catch {}
    }
    for (const name of SECRET_NAMES) {
        out[name] = (typeof fromFile[name] === 'string' && fromFile[name]) || env[name] || '';
        delete env[name];
    }
    return out;
}

// ---------------------------------------------------------------------------
// 2. Local forward proxy that holds the real API key
// ---------------------------------------------------------------------------

const HOP_BY_HOP = new Set(['connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization', 'proxy-connection', 'te', 'trailer', 'transfer-encoding', 'upgrade']);
// Never forwarded from the client: the real key is added by the proxy, the rest is recomputed.
const DROP_REQUEST_HEADERS = new Set([...HOP_BY_HOP, 'host', 'content-length', 'x-api-key', 'authorization', 'cookie']);

function tokensEqual(a, b) {
    const ha = crypto.createHash('sha256').update(String(a)).digest();
    const hb = crypto.createHash('sha256').update(String(b)).digest();
    return crypto.timingSafeEqual(ha, hb);
}

function reply(res, status, message, extra = {}) {
    if (res.headersSent) { res.destroy(); return; }
    const body = JSON.stringify({ type: 'error', error: { type: 'proxy_error', message } });
    res.writeHead(status, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body), ...extra });
    res.end(body);
}

/**
 * apiKey: the real key, held only in this closure (never exposed on the returned object).
 * Returns { baseUrl, token, port, close() }.
 */
export async function startAnthropicProxy({
    apiKey,
    upstream = 'https://api.anthropic.com',
    maxBodyBytes = 16 * 1024 * 1024,
    maxRequestsPerMinute = 120,
    maxConcurrent = 8,
    upstreamTimeoutMs = 10 * 60 * 1000,
    host = '127.0.0.1',
    port = 0,
    tokenPrefix = 'gw-local-',
    now = () => Date.now()
} = {}) {
    if (!apiKey) throw new Error('startAnthropicProxy: apiKey is required');
    const target = new URL(upstream);
    const transport = target.protocol === 'http:' ? http : https;
    const token = `${tokenPrefix}${crypto.randomBytes(24).toString('hex')}`;
    const stamps = [];
    let inflight = 0;

    const server = http.createServer((req, res) => {
        // Rate limit first, so unauthenticated floods are cheap to refuse too.
        const t = now();
        while (stamps.length && t - stamps[0] > 60000) stamps.shift();
        if (stamps.length >= maxRequestsPerMinute) return reply(res, 429, 'rate limit', { 'retry-after': '5' });
        stamps.push(t);
        if (inflight >= maxConcurrent) return reply(res, 429, 'too many concurrent requests', { 'retry-after': '2' });

        if (!tokensEqual(req.headers['x-api-key'] || '', token)) return reply(res, 401, 'unauthorized');
        if (!req.url.startsWith('/v1/') || req.url.includes('..') || req.url.includes('//')) return reply(res, 404, 'not found');
        const declared = Number(req.headers['content-length'] || 0);
        if (declared > maxBodyBytes) { req.resume(); return reply(res, 413, 'request too large', { connection: 'close' }); }

        const chunks = [];
        let size = 0;
        let tooBig = false;
        req.on('data', (c) => {
            if (tooBig) return;
            size += c.length;
            if (size > maxBodyBytes) { tooBig = true; chunks.length = 0; return; }
            chunks.push(c);
        });
        req.on('error', () => {});
        req.on('end', () => {
            if (tooBig) return reply(res, 413, 'request too large', { connection: 'close' });
            const body = Buffer.concat(chunks);
            const headers = {};
            for (const [k, v] of Object.entries(req.headers)) {
                if (!DROP_REQUEST_HEADERS.has(k)) headers[k] = v;
            }
            headers['x-api-key'] = apiKey;
            headers['host'] = target.host;
            headers['content-length'] = String(body.length);

            inflight++;
            let done = false;
            const finish = () => { if (!done) { done = true; inflight--; } };
            const up = transport.request({
                protocol: target.protocol, hostname: target.hostname, port: target.port || undefined,
                path: req.url, method: req.method, headers, timeout: upstreamTimeoutMs
            }, (upRes) => {
                const out = {};
                for (const [k, v] of Object.entries(upRes.headers)) {
                    if (!HOP_BY_HOP.has(k)) out[k] = v;
                }
                res.writeHead(upRes.statusCode || 502, out);
                upRes.pipe(res);
                upRes.on('end', finish);
                upRes.on('error', () => { finish(); res.destroy(); });
            });
            up.on('timeout', () => up.destroy(new Error('upstream timeout')));
            up.on('error', () => { finish(); reply(res, 502, 'upstream error'); });
            res.on('close', () => { finish(); up.destroy(); });
            up.end(body);
        });
    });

    await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(port, host, resolve);
    });
    const boundPort = server.address().port;
    return {
        baseUrl: `http://${host}:${boundPort}`,
        token,
        port: boundPort,
        close: () => new Promise((resolve) => { server.close(() => resolve()); server.closeAllConnections?.(); })
    };
}

// ---------------------------------------------------------------------------
// 5. Bundle walkers: regular files only
// ---------------------------------------------------------------------------

/**
 * Regular files under dir as [{ rel, fullPath }]. Symlinks, sockets, fifos and devices are skipped
 * (Dirent reports the link itself, it is not followed), so an agent cannot plant `index.html ->
 * /some/secret` and have the worker bundle the target.
 */
export function listRegularFiles(dir, skipNames = ['CLAUDE.md', '.claude']) {
    const files = [];
    (function walk(d, prefix) {
        for (const entry of fs.readdirSync(d, { withFileTypes: true })) {
            if (skipNames.includes(entry.name)) continue;
            const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
            const full = path.join(d, entry.name);
            if (entry.isDirectory()) walk(full, rel);
            else if (entry.isFile()) files.push({ rel, fullPath: full });
        }
    })(dir, '');
    return files;
}

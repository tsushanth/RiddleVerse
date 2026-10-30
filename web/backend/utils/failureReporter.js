/**
 * Sends backend failures to the unified app-failure-reporter Worker (dedupes and emails).
 * Fire-and-forget: never throws, never blocks a request. Set FAILURE_REPORTER_DISABLED=1 to silence (tests/dev).
 * Never pass user content (transcripts, note text, emails) as message or context.
 */
const ENDPOINT = process.env.FAILURE_REPORTER_URL || 'https://app-failure-reporter.t-sushanth.workers.dev/v1/report';
const KEY = process.env.FAILURE_REPORTER_KEY || 'afr_2380403b6807a34c833fa412468daee3';
const APP_VERSION = process.env.FLY_IMAGE_REF || process.env.K_REVISION || process.env.npm_package_version || 'unknown';
const DISABLED = process.env.FAILURE_REPORTER_DISABLED === '1' || process.env.NODE_ENV === 'test' || process.env.NODE_ENV === 'development' || !!process.env.NODE_TEST_CONTEXT;

async function post(kind, flow, err, context, timeoutMs) {
  if (DISABLED) return;
  try {
    const e = err instanceof Error ? err : new Error(String(err));
    await fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Report-Key': KEY },
      body: JSON.stringify({ kind, platform: 'backend', version: APP_VERSION, flow, message: e.message || e.name, stack: e.stack ?? '', context }),
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch { /* reporting must never cause a failure of its own */ }
}

/** A handled server error (5xx). Path only: never log query strings, they can carry tokens. */
export function reportBackendError(req, err, statusCode) {
  if (statusCode < 500) return;
  void post('backend_error', `${req.method} ${req.path ?? ''}`, err, { status: String(statusCode) }, 5000);
}

/** A background job or flow failed (worker, queue, cron). */
export function reportFailure(flow, err, context = {}) {
  void post('failure', flow, err, context, 5000);
}

/** Process is about to die: await this before process.exit so the email actually goes out. */
export function reportCrash(flow, err) {
  return post('crash', flow, err, {}, 2500);
}

// ---------------------------------------------------------------------------------------------
// Classified, stable-message alerts. Messages are fixed strings per class (so the reporter dedupes
// them to one email per hour); context is a fixed provider / flow label, never content or ids.
// ---------------------------------------------------------------------------------------------

/** True for a Firestore/gRPC quota error (code 8 or RESOURCE_EXHAUSTED text). */
export function isFirestoreQuotaError(err) {
  if (!err) return false;
  const msg = String(err.message || err.details || '');
  return err.code === 8 || err.code === 'RESOURCE_EXHAUSTED' || /RESOURCE_EXHAUSTED|Quota exceeded/i.test(msg);
}

/**
 * Classifies an LLM/TTS/image provider failure from an axios-style error ({response:{status,data}}),
 * a fetch-style one ({status}) or a plain Error. Returns 'auth' | 'quota' | null.
 * 429 alone is a rate limit (transient) and is not reported; 429/400/402/403 with a billing or quota
 * body is.
 */
export function classifyProviderError(err) {
  if (!err) return null;
  const status = err.response?.status ?? err.status ?? err.statusCode;
  let body = err.response?.data ?? err.data ?? err.body ?? '';
  if (typeof body !== 'string') { try { body = JSON.stringify(body); } catch { body = ''; } }
  const text = `${body} ${err.message || ''}`.toLowerCase();
  const billing = /credit balance|insufficient[_ ]quota|billing|exceeded your current quota|quota exceeded|out of credits|payment required|insufficient balance|usage limit/.test(text);
  if (status === 401 || status === 403 && !billing) return 'auth';
  if (status === 402 || billing) return 'quota';
  return null;
}

/** One clear alert per provider and class, e.g. "LLM provider quota or credit exhausted" for flow provider.anthropic. */
export function reportProviderError(provider, err, report = reportFailure) {
  const cls = classifyProviderError(err);
  if (!cls) return null;
  const safe = String(provider || 'unknown').replace(/[^a-z0-9_.-]/gi, '').slice(0, 32) || 'unknown';
  const message = cls === 'auth'
    ? 'Provider rejected our API key (401/403): key invalid, revoked or out of permissions'
    : 'Provider quota or credit exhausted: calls are failing until it is topped up';
  report(`provider.${safe}`, new Error(message), { provider: safe, class: cls });
  return cls;
}

let lastQuotaReportAt = 0;
/** Firestore quota exhausted anywhere. At most one outbound report per 10 minutes per process. */
export function reportFirestoreQuota(flow = 'firestore', report = reportFailure, now = Date.now()) {
  if (now - lastQuotaReportAt < 10 * 60 * 1000) return false;
  lastQuotaReportAt = now;
  report('firestore.quota', new Error('Firestore quota exhausted (RESOURCE_EXHAUSTED): reads or writes are failing'), { source: String(flow).slice(0, 48) });
  return true;
}
export function _resetFirestoreQuotaThrottleForTests() { lastQuotaReportAt = 0; }

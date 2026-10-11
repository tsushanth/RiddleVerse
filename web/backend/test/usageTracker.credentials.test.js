import { test } from 'node:test';
import assert from 'node:assert/strict';
import { usageTracker } from '../utils/usageTracker.js';

const KEYS = ['USAGE_ALERT_SMTP_USER', 'USAGE_ALERT_SMTP_PASS', 'USAGE_ALERT_TO'];

test('sendEmailAlert skips without throwing and warns once when SMTP env is unset', async () => {
  const saved = {};
  for (const k of KEYS) { saved[k] = process.env[k]; delete process.env[k]; }
  const warns = [];
  const origWarn = console.warn;
  console.warn = (...a) => warns.push(a.join(' '));
  try {
    const alerts = [{ type: 'DAILY_THRESHOLD', amount: 11, threshold: 10, period: '2026-10-10' }];
    const ctx = { dailyKey: 'd', weeklyKey: 'w', monthlyKey: 'm', hourlyKey: 'h' };
    await usageTracker.sendEmailAlert(alerts, ctx);
    await usageTracker.sendEmailAlert(alerts, ctx);
    assert.equal(warns.filter((w) => w.includes('USAGE_ALERT_SMTP_USER')).length, 1);
  } finally {
    console.warn = origWarn;
    for (const k of KEYS) if (saved[k] !== undefined) process.env[k] = saved[k];
  }
});

test('partially configured env (one var missing) is still treated as unset', async () => {
  process.env.USAGE_ALERT_SMTP_USER = 'x@example.invalid';
  process.env.USAGE_ALERT_SMTP_PASS = 'dummy';
  delete process.env.USAGE_ALERT_TO;
  try {
    await usageTracker.sendEmailAlert([{ type: 'HOURLY_REQUESTS', amount: 1, threshold: 1 }], { dailyKey: 'd', weeklyKey: 'w', monthlyKey: 'm', hourlyKey: 'h' });
  } finally {
    delete process.env.USAGE_ALERT_SMTP_USER; delete process.env.USAGE_ALERT_SMTP_PASS;
  }
});

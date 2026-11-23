import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { statusCodes, errorCode } from '../statusCodes.js';

test('statusCodes: every message the worker sends has a code', () => {
  assert.deepEqual(statusCodes('Building your game', 'Creating game files from your description'), { messageCode: 'build', detailCode: 'creating_files' });
  assert.deepEqual(statusCodes('Checking game quality', 'Running automated quality checks'), { messageCode: 'check_quality', detailCode: 'running_checks' });
  assert.deepEqual(statusCodes('Fixing issues', 'Found 3 issues to resolve'), { messageCode: 'fix_issues', detailCode: 'found_issues', detailArgs: [3] });
  assert.deepEqual(statusCodes('Fixing issues', 'Found 1 issue to resolve'), { messageCode: 'fix_issues', detailCode: 'found_issues', detailArgs: [1] });
  assert.deepEqual(statusCodes('Fixing issues', 'Found 2 issue(s) to resolve').detailCode, undefined, 'unknown text gets no detail code');
  assert.deepEqual(statusCodes('No issues found', 'New level passed all quality checks'), { messageCode: 'no_issues', detailCode: 'passed_checks' });
  assert.deepEqual(statusCodes('Additional fixes needed', '2 critical issues remaining'), { messageCode: 'more_fixes', detailCode: 'critical_remaining', detailArgs: [2] });
  assert.equal(statusCodes('Generating Level 4', 'Reading previous level to extend "Neon Snake"').messageCode, 'build_level');
  assert.equal(statusCodes('Preparing harder challenge', 'Creating level 3 of Neon Snake').messageCode, 'prepare');
  assert.equal(statusCodes('Runtime testing', 'Testing game in headless browser').messageCode, 'runtime_test');
});

test('statusCodes: unknown text yields no codes (the app then falls back to the phase, then to the text)', () => {
  assert.deepEqual(statusCodes('Something new', 'Something else'), {});
  assert.deepEqual(statusCodes(undefined, undefined), {});
});

test('statusCodes: every sendStatus call in server.js is covered by a code', () => {
  const src = fs.readFileSync(new URL('../server.js', import.meta.url), 'utf8');
  const calls = [...src.matchAll(/sendStatus\('(\w+)', ('(?:[^'\\]|\\.)*'|`[^`]*`), ('(?:[^'\\]|\\.)*'|`[^`]*`)/g)];
  assert.ok(calls.length > 30, 'found the sendStatus call sites');
  const uncovered = [];
  for (const [, , msgLit, detailLit] of calls) {
    // Replace ${...} by a number: the dynamic parts of the messages are counts, levels and titles.
    const fill = (lit) => lit.slice(1, -1).replace(/\$\{[^}]*(?:length|issues|Critical|Level|level)[^}]*\}/gi, '2').replace(/\$\{[^}]*\}/g, 'X').replace(/issue\(s\)/g, 'issues');
    const c = statusCodes(fill(msgLit), fill(detailLit));
    if (!c.messageCode) uncovered.push(`message: ${msgLit}`);
  }
  assert.deepEqual(uncovered, [], 'status messages without a code');
});

test('errorCode: stable codes for worker errors, undefined for unknown text', () => {
  assert.equal(errorCode('Unauthorized'), 'unauthorized');
  assert.equal(errorCode('Worker busy. Try again in a moment.'), 'worker_busy');
  assert.equal(errorCode('AI usage limit reached. Service resets at 8am. Please try again after that.'), 'quota_exhausted');
  assert.equal(errorCode('Failed to generate game. Please try again.'), 'generation_failed');
  assert.equal(errorCode('Failed to generate harder version. Please try again.'), 'generation_failed');
  assert.equal(errorCode('No game files were created. Please try again with a different description.'), 'no_game_files');
  assert.equal(errorCode('Internal worker error during tweak.'), 'server_error');
  assert.equal(errorCode('parentBundle is required (base64 ZIP)'), 'missing_fields');
  assert.equal(errorCode('Failed to clone game repo: boom'), 'repo_init_failed');
  assert.equal(errorCode('totally new'), undefined);
});

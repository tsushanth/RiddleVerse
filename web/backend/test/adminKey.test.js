import test from 'node:test';
import assert from 'node:assert/strict';
import { isAdminKey } from '../middleware/adminKey.js';

test('unset or empty ADMIN_KEY rejects everything, including a missing key (the old undefined !== undefined hole)', () => {
    assert.equal(isAdminKey(undefined, undefined), false);
    assert.equal(isAdminKey('', ''), false);
    assert.equal(isAdminKey('anything', undefined), false);
    assert.equal(isAdminKey(undefined, ''), false);
});

test('missing, non-string and wrong keys are rejected', () => {
    assert.equal(isAdminKey(undefined, 'real-key-123'), false);
    assert.equal(isAdminKey(['real-key-123'], 'real-key-123'), false);
    assert.equal(isAdminKey({ toString: () => 'real-key-123' }, 'real-key-123'), false);
    assert.equal(isAdminKey('real-key-124', 'real-key-123'), false);
    assert.equal(isAdminKey('real-key-1234', 'real-key-123'), false);
});

test('the exact key is accepted', () => {
    assert.equal(isAdminKey('real-key-123', 'real-key-123'), true);
});

test('reads ADMIN_KEY from the environment by default', () => {
    const prev = process.env.ADMIN_KEY;
    try {
        delete process.env.ADMIN_KEY;
        assert.equal(isAdminKey(undefined), false);
        process.env.ADMIN_KEY = 'env-key';
        assert.equal(isAdminKey('env-key'), true);
        assert.equal(isAdminKey(undefined), false);
    } finally {
        if (prev === undefined) delete process.env.ADMIN_KEY; else process.env.ADMIN_KEY = prev;
    }
});

test('no inline "!== process.env.ADMIN_KEY" admin checks remain in the backend', async () => {
    const { execSync } = await import('node:child_process');
    const out = execSync(`grep -rn "!== process.env.ADMIN_KEY" server.js routes services || true`, { encoding: 'utf8' });
    assert.equal(out.trim(), '');
});

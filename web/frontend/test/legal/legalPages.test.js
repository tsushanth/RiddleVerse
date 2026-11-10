import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const pub = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'public')
const read = (p) => readFileSync(path.join(pub, p), 'utf8')

for (const slug of ['privacy', 'terms', 'delete-account']) {
  test(`/${slug}/ is a real static page, not the SPA shell`, () => {
    assert.ok(existsSync(path.join(pub, slug, 'index.html')))
    const html = read(`${slug}/index.html`)
    assert.match(html, /<meta name="viewport"/)
    assert.match(html, /<h1>/)
    assert.doesNotMatch(html, /id="root"/)
  })
}

test('legal pages make no payout or earnings promise', () => {
  for (const slug of ['privacy', 'terms', 'delete-account']) {
    const html = read(`${slug}/index.html`).toLowerCase()
    assert.doesNotMatch(html, /you (can )?earn (real )?money|get paid|cash out/)
  }
})

test('assetlinks.json declares the RiddleVerse package with a Play signing fingerprint', () => {
  const links = JSON.parse(read('.well-known/assetlinks.json'))
  const rv = links.find((l) => l.target.package_name === 'com.kreativekoala.riddleverse')
  assert.ok(rv, 'riddleverse package missing')
  for (const fp of rv.target.sha256_cert_fingerprints) assert.match(fp, /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/)
})

test('app-ads.txt uses the real AdMob publisher id, not the placeholder', () => {
  const txt = read('app-ads.txt')
  assert.doesNotMatch(txt, /pub-1234567890123456/)
  assert.match(txt, /^google\.com, pub-\d{16}, DIRECT, f08c47fec0942fa0/)
})

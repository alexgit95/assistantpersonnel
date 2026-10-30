import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';
import { buildQr, QR_MARGIN, qrSvg } from '../../main/resources/static/js/qr-code.js';

const sandbox = {};
sandbox.globalThis = sandbox;
vm.createContext(sandbox);
vm.runInContext(readFileSync(new URL('../../main/resources/static/vendor/qrcode/qrcode.js', import.meta.url), 'utf8'), sandbox);
const library = sandbox.AssistantQr;

test('the bundled library exposes the generator and the correction levels', () => {
  assert.equal(typeof library.QRCode, 'function');
  assert.equal(library.QRErrorCorrectLevel.M, 0);
});

test('a short text gives a version 1 symbol of 21 modules with the three finder patterns', () => {
  const qr = buildQr(library, 'http://a');
  const count = qr.getModuleCount();
  assert.equal(count, 21);
  for (const [row, col] of [[0, 0], [0, count - 7], [count - 7, 0]]) {
    assert.equal(qr.isDark(row, col), true);
    assert.equal(qr.isDark(row + 3, col + 3), true);
    assert.equal(qr.isDark(row + 1, col + 1), false);
  }
});

test('a handoff URL fits in a small symbol and larger texts grow the symbol', () => {
  const url = `http://192.168.1.50:8080/handoff?t=${'a'.repeat(43)}`;
  const qr = buildQr(library, url);
  assert.ok(qr.getModuleCount() >= 33 && qr.getModuleCount() <= 45, String(qr.getModuleCount()));
  assert.ok(buildQr(library, 'x'.repeat(300)).getModuleCount() > qr.getModuleCount());
});

test('the same text always gives the same symbol', () => {
  const first = qrSvg(buildQr(library, 'http://192.168.1.50:8080/mobile?s=abc'));
  const second = qrSvg(buildQr(library, 'http://192.168.1.50:8080/mobile?s=abc'));
  assert.equal(first, second);
});

test('the svg is scalable, has a white quiet zone and an accessible label', () => {
  const qr = buildQr(library, 'http://a');
  const svg = qrSvg(qr, { margin: 2, label: 'QR <test> & "x"' });
  assert.match(svg, /^<svg xmlns="http:\/\/www\.w3\.org\/2000\/svg" viewBox="0 0 25 25"/);
  assert.match(svg, /aria-label="QR &lt;test&gt; &amp; &quot;x&quot;"/);
  assert.match(svg, /<rect width="25" height="25" fill="#fff"\/>/);
  assert.equal((svg.match(/M\d+,\d+h1v1h-1z/g) ?? []).length > 50, true);
  assert.ok(!svg.includes('width="25px"'));
});

test('the first module of the quiet zone is never drawn', () => {
  const svg = qrSvg(buildQr(library, 'http://a'), { margin: 2 });
  assert.ok(!svg.includes('M0,0h1v1h-1z'));
  assert.ok(svg.includes('M2,2h1v1h-1z'));
});

test('the symbol uses error correction L and a one module quiet zone to stay compact', () => {
  const url = `http://192.168.1.50:8080/handoff?t=${'a'.repeat(43)}`;
  const compact = buildQr(library, url);
  const robust = new library.QRCode(5, library.QRErrorCorrectLevel.M);
  robust.addData(url);
  robust.make();
  assert.ok(compact.getModuleCount() < robust.getModuleCount());
  assert.equal(QR_MARGIN, 1);
  const size = compact.getModuleCount() + 2;
  assert.match(qrSvg(compact), new RegExp(`viewBox="0 0 ${size} ${size}"`));
});

test('an oversized text is refused', () => {
  assert.throws(() => buildQr(library, 'x'.repeat(5000)), /trop long/i);
});

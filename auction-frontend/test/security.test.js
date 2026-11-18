import test from 'node:test';
import assert from 'node:assert/strict';
import createDOMPurify from 'dompurify';
import { JSDOM } from 'jsdom';
import { createSvgSanitizer } from '../src/utils/sanitizeSvg.js';
import { reconcileAuctionUpdate } from '../src/features/auction/auctionUpdates.js';

test('SVG logos preserve gradients but remove active content and external references', () => {
  const dom = new JSDOM('');
  const sanitize = createSvgSanitizer(createDOMPurify(dom.window));
  const clean = sanitize(`<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)">
    <script>alert(1)</script><foreignObject><img src="x" onerror="alert(1)"/></foreignObject>
    <defs><linearGradient id="color"><stop stop-color="red"/></linearGradient></defs>
    <path fill="url(#color)" d="M0 0" onclick="alert(1)"/>
    <use href="javascript:alert(1)"/><image href="https://untrusted.example/tracker"/>
  </svg>`);
  assert.doesNotMatch(clean, /onload|onclick|onerror|script|foreignObject|javascript:|untrusted\.example/i);
  assert.match(clean, /linearGradient/);
  assert.match(clean, /url\(#color\)/);
  dom.window.close();
});

test('consecutive bid notices update price without replacing queue or team data', () => {
  const current = { location: 'pune', revision: 2, activePlayer: { id: 10 }, auctionQueue: [{ id: 10 }], cityTeams: [{ id: 1 }] };
  const result = reconcileAuctionUpdate(current, { location: 'pune', revision: 3, eventType: 'BID', activePlayerId: 10,
    started: true, currentBid: 1500, highestBidderTeamId: 1, highestBidderTeam: 'First' });
  assert.equal(result.kind, 'apply');
  assert.equal(result.snapshot.currentBid, 1500);
  assert.equal(result.snapshot.auctionQueue, current.auctionQueue);
  assert.equal(result.snapshot.cityTeams, current.cityTeams);
  assert.equal(reconcileAuctionUpdate(current, { revision: 5, eventType: 'BID', activePlayerId: 10 }).kind, 'resync');
  assert.equal(reconcileAuctionUpdate(current, { revision: 3, eventType: 'STATE' }).kind, 'resync');
  assert.equal(reconcileAuctionUpdate(current, { revision: 1 }).kind, 'ignore');
});

test('unsafe API writes fetch a CSRF token and retry only an explicit CSRF rejection', async t => {
  const calls = [];
  let token = 0;
  let writes = 0;
  const original = globalThis.fetch;
  t.after(() => { globalThis.fetch = original; });
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith('/api/auth/csrf')) return new Response(JSON.stringify({ token: `token-${++token}`, headerName: 'X-XSRF-TOKEN' }), { status: 200 });
    writes++;
    if (writes === 1) return new Response(JSON.stringify({ code: 'CSRF_INVALID' }), { status: 403 });
    return new Response(JSON.stringify({ currentBid: 1500 }), { status: 200 });
  };
  const { apiFetch } = await import(`../src/api/api.js?security-test=${Date.now()}`);
  const result = await apiFetch('/api/pune/auction/bid', { method: 'POST', body: '{}' });
  assert.equal(result.currentBid, 1500);
  assert.equal(calls.length, 4);
  assert.equal(calls[1].options.headers['X-XSRF-TOKEN'], 'token-1');
  assert.equal(calls[3].options.headers['X-XSRF-TOKEN'], 'token-2');
  assert.equal(calls[3].options.credentials, 'include');
  globalThis.fetch = async () => new Response(JSON.stringify({ message: 'Stale bid' }), { status: 409 });
  await assert.rejects(apiFetch('/api/pune/auction/bid', { method: 'POST', body: '{}' }), /Stale bid/);
});

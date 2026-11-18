import test from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import TestRenderer from 'react-test-renderer';
import { useAuthSession } from '../src/features/auth/useAuthSession.js';
import { authApi } from '../src/features/auth/authApi.js';
import { useAuctionActions } from '../src/features/auction/useAuctionActions.js';
import { auctionApi } from '../src/features/auction/auctionApi.js';
import { useCityCatalog } from '../src/features/catalog/useCityCatalog.js';
import { cityCatalog } from '../src/features/catalog/catalogRepository.js';
import { useCatalogActions } from '../src/features/catalog/useCatalogActions.js';
import { catalogApi } from '../src/features/catalog/catalogApi.js';

const { act, create } = TestRenderer;
const deferred = () => {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
};

async function renderHook(hook, input) {
  const result = { current: null };
  function Probe({ value }) {
    result.current = hook(value);
    return null;
  }
  let renderer;
  await act(async () => { renderer = create(React.createElement(Probe, { value: input })); });
  return {
    result,
    update: value => act(async () => { renderer.update(React.createElement(Probe, { value })); }),
    unmount: () => act(() => renderer.unmount()),
  };
}

function replace(t, target, overrides) {
  const original = {};
  for (const key of Object.keys(overrides)) original[key] = target[key];
  Object.assign(target, overrides);
  t.after(() => Object.assign(target, original));
}

test('late initial session response cannot overwrite a successful login', async t => {
  const initial = deferred();
  const owner = { username: 'owner', role: 'owner', teamId: 10 };
  replace(t, authApi, { profile: () => initial.promise, login: async () => owner, logout: async () => {} });
  const hook = await renderHook(useAuthSession);
  t.after(hook.unmount);
  await act(async () => { await hook.result.current.authenticate('owner', 'password'); });
  await act(async () => { initial.resolve({ username: 'old-session', role: 'admin' }); });
  assert.deepEqual(hook.result.current.authUser, owner);
  assert.equal(hook.result.current.authReady, true);
  await act(async () => { await hook.result.current.endSession(); });
  assert.equal(hook.result.current.authUser, null);
});

test('bid commands prevent duplicate clicks and recover the latest snapshot on conflict', async t => {
  const pendingBid = deferred();
  const latest = { revision: 4, activePlayer: { id: 11 }, currentBid: 1500 };
  const applied = [];
  let calls = 0;
  replace(t, auctionApi, {
    bid: async (city, command) => {
      calls++;
      assert.equal(city, 'pune');
      assert.deepEqual(command, { playerId: 11, revision: 3, increment: 500, teamId: 10 });
      return pendingBid.promise;
    },
    state: async () => latest,
  });
  const hook = await renderHook(useAuctionActions, {
    city: 'pune', snapshot: { revision: 3, activePlayer: { id: 11 }, currentBid: 1000 },
    applySnapshot: state => applied.push(state), players: [],
    selectedLot: { skillLevel: 'All', gender: 'All' }, activeLot: { skillLevel: 'All', gender: 'All' },
  });
  t.after(hook.unmount);
  let first;
  let duplicate;
  await act(async () => {
    first = hook.result.current.increaseBid(500, 10);
    duplicate = await hook.result.current.increaseBid(500, 10);
  });
  assert.equal(calls, 1);
  assert.equal(duplicate.success, false);
  assert.equal(hook.result.current.auctionBusy, true);
  await act(async () => { pendingBid.resolve(latest); await first; });
  assert.equal(hook.result.current.auctionBusy, false);
  assert.deepEqual(applied, [latest]);

  auctionApi.bid = async () => { throw new Error('The auction changed.'); };
  let conflict;
  await act(async () => { conflict = await hook.result.current.increaseBid(500, 10); });
  assert.equal(conflict.success, false);
  assert.equal(hook.result.current.auctionError, 'The auction changed.');
  assert.deepEqual(applied, [latest, latest]);
});

test('late catalog responses from the previous city cannot replace the selected city', async t => {
  const pune = deferred();
  const mumbai = deferred();
  replace(t, cityCatalog, { load: city => city === 'pune' ? pune.promise : mumbai.promise });
  const hook = await renderHook(({ city, role }) => useCityCatalog(city, role), { city: 'pune', role: 'guest' });
  t.after(hook.unmount);
  await hook.update({ city: 'mumbai', role: 'guest' });
  await act(async () => { mumbai.resolve({ players: [{ id: 20 }], teams: [{ id: 200 }] }); });
  await act(async () => { pune.resolve({ players: [{ id: 10 }], teams: [{ id: 100 }] }); });
  assert.deepEqual(hook.result.current.players, [{ id: 20 }]);
  assert.deepEqual(hook.result.current.teams, [{ id: 200 }]);
});

test('a live team update survives older cache hydration and an in-flight catalog fetch', async t => {
  const download = deferred();
  let hydrate;
  replace(t, cityCatalog, { load: (_, options) => { hydrate = options.onCached; return download.promise; } });
  const hook = await renderHook(() => useCityCatalog('pune', 'guest'));
  t.after(hook.unmount);
  await act(async () => { hook.result.current.applyLiveTeams([{ id: 10, purseRemaining: 98500 }]); });
  await act(async () => { hydrate({ fromCache: true, players: [], teams: [{ id: 10, purseRemaining: 100000 }] }); });
  assert.equal(hook.result.current.teams[0].purseRemaining, 98500);
  await act(async () => { download.resolve({ players: [], teams: [{ id: 10, purseRemaining: 100000 }] }); });
  assert.equal(hook.result.current.teams[0].purseRemaining, 98500);
});

test('a rejected team deletion never modifies local roster or sale prices', async t => {
  replace(t, catalogApi, { deleteTeam: async () => { throw new Error('Team has owner accounts.'); } });
  const hook = await renderHook(useCatalogActions, {
    city: 'pune',
    setPlayers: () => assert.fail('Failed deletion must preserve players'),
    setTeams: () => assert.fail('Failed deletion must preserve teams'),
    invalidateCache: () => assert.fail('Failed deletion must preserve cache validity'),
  });
  t.after(hook.unmount);
  await assert.rejects(hook.result.current.deleteTeam(10), /owner accounts/);
});

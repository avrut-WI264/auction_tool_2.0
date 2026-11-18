import test from 'node:test';
import assert from 'node:assert/strict';
import { CityCatalogRepository } from '../src/features/catalog/catalogRepository.js';

function storage(initial = {}) {
  const values = new Map(Object.entries(initial));
  return {
    values,
    get: async key => structuredClone(values.get(key)),
    set: async (key, value) => { values.set(key, structuredClone(value)); },
  };
}

const cached = {
  data_players_pune: [{ id: 1, fullName: 'Before' }],
  data_teams_pune: [{ id: 10 }],
  data_hash_pune: '1_0_0_0_1',
  data_timestamp_pune: 'before',
};

test('unchanged version hydrates the cache and avoids full resource requests', async () => {
  const cache = storage(cached);
  const api = {
    version: async () => ({ hash: cached.data_hash_pune, timestamp: 'now' }),
    players: () => assert.fail('Unchanged players should not be downloaded'),
    teams: () => assert.fail('Unchanged teams should not be downloaded'),
  };
  let hydrated;
  const result = await new CityCatalogRepository(api, cache).load('pune', { onCached: value => { hydrated = value; } });
  assert.deepEqual(hydrated.players, cached.data_players_pune);
  assert.deepEqual(result.teams, cached.data_teams_pune);
});

test('an update during fetching is not hidden by a checkpoint taken after the reads', async () => {
  const cache = storage();
  let changedDuringFetch = false;
  let playersSince;
  const api = {
    version: async () => changedDuringFetch
      ? { hash: '1_1_1500_0_1', timestamp: 'after' }
      : { hash: '1_0_0_0_1', timestamp: 'before' },
    players: async (_, since) => {
      playersSince = since;
      if (!changedDuringFetch) {
        changedDuringFetch = true;
        return [{ id: 1, status: 'UNSOLD' }];
      }
      return [{ id: 1, status: 'SOLD', soldPrice: 1500 }];
    },
    teams: async () => [{ id: 10 }],
  };
  const repository = new CityCatalogRepository(api, cache);
  await repository.load('pune');
  assert.equal(cache.values.get('data_timestamp_pune'), 'before');
  const next = await repository.load('pune');
  assert.equal(playersSince, 'before');
  assert.equal(next.players[0].status, 'SOLD');
});

test('deletions use a full fetch so removed records cannot survive a delta merge', async () => {
  const cache = storage(cached);
  const api = {
    version: async () => ({ hash: '0_0_0_0_1', timestamp: 'now' }),
    players: async (_, since) => { assert.equal(since, null); return []; },
    teams: async (_, since) => { assert.equal(since, null); return [{ id: 10 }]; },
  };
  const result = await new CityCatalogRepository(api, cache).load('pune');
  assert.deepEqual(result.players, []);
});

test('failed downloads preserve existing cached data rather than caching an empty success', async () => {
  const cache = storage(cached);
  const api = {
    version: async () => ({ hash: '2_0_0_0_1', timestamp: 'now' }),
    players: async () => { throw new Error('Offline'); },
    teams: async () => [{ id: 10 }],
  };
  await assert.rejects(new CityCatalogRepository(api, cache).load('pune'), /Offline/);
  assert.deepEqual(cache.values.get('data_players_pune'), cached.data_players_pune);
  assert.equal(cache.values.get('data_hash_pune'), cached.data_hash_pune);
});

test('refreshing only players does not certify the cached teams as current', async () => {
  const cache = storage(cached);
  const repository = new CityCatalogRepository({ players: async () => [{ id: 2 }] }, cache);
  await repository.refresh('pune', 'players');
  assert.equal(cache.values.get('data_hash_pune'), null);
  assert.deepEqual(cache.values.get('data_teams_pune'), cached.data_teams_pune);
});

test('unavailable browser storage does not discard successful server reads', async () => {
  const cache = { get: async () => { throw new Error('Storage unavailable'); }, set: async () => { throw new Error('Storage unavailable'); } };
  const api = { version: async () => ({ hash: '1_0_0_0_1' }), players: async () => [{ id: 1 }], teams: async () => [{ id: 10 }] };
  const repository = new CityCatalogRepository(api, cache);
  assert.deepEqual(await repository.load('pune'), { players: [{ id: 1 }], teams: [{ id: 10 }] });
  assert.deepEqual(await repository.refresh('pune', 'players'), [{ id: 1 }]);
});

test('a partial cache write never certifies an incomplete catalog version', async () => {
  const cache = storage(cached);
  const write = cache.set;
  cache.set = async (key, value) => {
    if (key === 'data_teams_pune') throw new Error('Quota exceeded');
    return write(key, value);
  };
  const api = { version: async () => ({ hash: '2_0_0_0_1', timestamp: 'now' }), players: async () => [{ id: 2 }], teams: async () => [{ id: 10 }] };
  const result = await new CityCatalogRepository(api, cache).load('pune');
  assert.deepEqual(result.players, [{ id: 1, fullName: 'Before' }, { id: 2 }]);
  assert.equal(cache.values.get('data_hash_pune'), null);
});

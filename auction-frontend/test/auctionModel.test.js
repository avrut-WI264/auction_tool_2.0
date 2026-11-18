import test from 'node:test';
import assert from 'node:assert/strict';
import { selectAuctionQueue, mergeLiveTeams } from '../src/features/auction/auctionModel.js';
import { removePlayerFromTeam } from '../src/features/catalog/catalogModel.js';

test('queue selection respects city, lot, status, and ordering without changing input', () => {
  const players = [
    { id: 1, fullName: 'Male', gender: 'Male', skillLevel: 'Beginner', location: 'Pune', status: 'UNSOLD' },
    { id: 2, fullName: 'Female', gender: 'Female', skillLevel: 'Beginner', location: 'Pune', status: 'UNSOLD' },
    { id: 3, gender: 'Female', skillLevel: 'Beginner', location: 'Mumbai', status: 'UNSOLD' },
    { id: 4, gender: 'Female', skillLevel: 'Beginner', location: 'Pune', status: 'SOLD' },
  ];
  const result = selectAuctionQueue(players, 'pune', { skillLevel: 'Beginner', gender: 'All' });
  assert.deepEqual(result.map(player => player.id), [2, 1]);
  assert.deepEqual(players.map(player => player.id), [1, 2, 3, 4]);
});

test('live balances and roster replace old values while downloaded logos survive', () => {
  const old = [{ id: 1, purseRemaining: 100000, logoSvg: '<svg/>', players: [] }];
  const updated = mergeLiveTeams(old, [{ id: 1, purseRemaining: 98500, logoSvg: null, players: [{ id: 10 }] }]);
  assert.equal(updated[0].purseRemaining, 98500);
  assert.equal(updated[0].logoSvg, '<svg/>');
  assert.deepEqual(updated[0].players, [{ id: 10 }]);
  assert.equal(old[0].purseRemaining, 100000);
});

test('player removal refunds the actual sale amount, including a valid zero price', () => {
  const team = { id: 1, purseRemaining: 98500, totalPlayers: 2, femalePlayers: 1, beginnerPlayers: 1,
    players: [{ id: 10, soldPrice: 1500, gender: 'Female', skillLevel: 'Beginner' }, { id: 11, soldPrice: 0, basePrice: 1000 }] };
  const result = removePlayerFromTeam(team, 10);
  assert.equal(result.purseRemaining, 100000);
  assert.equal(result.totalPlayers, 1);
  assert.equal(result.femalePlayers, 0);
  assert.equal(removePlayerFromTeam(result, 11).purseRemaining, 100000);
  assert.equal(team.players.length, 2);
});

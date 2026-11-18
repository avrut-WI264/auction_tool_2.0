export function mergeById(existing, changes) {
  const records = new Map(existing.map(record => [record.id, record]));
  changes.forEach(record => records.set(record.id, record));
  return [...records.values()];
}

export function canLoadDelta(cached, serverVersion) {
  if (!cached.timestamp || !cached.hash || !serverVersion?.hash) return false;
  const previous = cached.hash.split('_');
  const current = serverVersion.hash.split('_');
  return Number(current[0]) >= Number(previous[0]) && Number(current[4]) >= Number(previous[4]);
}

export const releasedPlayer = player => ({
  ...player, status: 'UNSOLD', soldPrice: null, soldTeamId: null, soldTeam: null,
});

export function removePlayerFromTeam(team, playerId) {
  const removed = team.players?.find(player => player.id === playerId);
  if (!removed) return team;
  return {
    ...team,
    players: team.players.filter(player => player.id !== playerId),
    totalPlayers: Math.max(0, (team.totalPlayers ?? 0) - 1),
    femalePlayers: Math.max(0, (team.femalePlayers ?? 0) - (removed.gender === 'Female' ? 1 : 0)),
    beginnerPlayers: Math.max(0, (team.beginnerPlayers ?? 0) - (removed.skillLevel === 'Beginner' ? 1 : 0)),
    purseRemaining: (team.purseRemaining ?? 0) + (removed.soldPrice ?? 0),
  };
}

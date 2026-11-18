export const BUSINESS_RULES = Object.freeze({
  teamSizeLimit: 10,
  minBeginners: 2,
  minFemales: 2,
  purseLimit: 100000,
  bidIncrement: 500,
});

const AUCTION_ROLES = new Set(['admin', 'owner', 'guest']);
const SKILL_ORDER = { Beginner: 1, Intermediate: 2, Advanced: 3 };

export const isAuctionRole = (role) => AUCTION_ROLES.has(role);

export function matchesLot(player, city, { skillLevel, gender }) {
  return player.location?.toLowerCase() === city?.toLowerCase()
    && (skillLevel === 'All' || player.skillLevel === skillLevel)
    && (gender === 'All' || player.gender === gender);
}

export function selectAuctionQueue(players, city, lot) {
  return players.filter(player => player.status === 'UNSOLD' && matchesLot(player, city, lot))
    .sort((first, second) => {
      const skillDifference = (SKILL_ORDER[first.skillLevel] ?? 99) - (SKILL_ORDER[second.skillLevel] ?? 99);
      if (skillDifference) return skillDifference;
      if (first.gender !== second.gender) return first.gender === 'Female' ? -1 : 1;
      return (first.fullName ?? '').localeCompare(second.fullName ?? '');
    });
}

export function mergeLiveTeams(previous, incoming) {
  const byId = new Map(previous.map(team => [team.id, team]));
  return incoming.map(team => {
    const cached = byId.get(team.id);
    return { ...cached, ...team, logoSvg: team.logoSvg ?? cached?.logoSvg, logoUrl: team.logoUrl ?? cached?.logoUrl };
  });
}

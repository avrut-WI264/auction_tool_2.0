import { apiFetch, apiUpload } from '../../api/api.js';
import { getCachedItem, setCachedItem } from '../../utils/idb.js';

const resource = (city, name) => `/api/${encodeURIComponent(city)}/${name}`;
const write = (path, method, data) => apiFetch(path, {
  method, ...(data === undefined ? {} : { body: JSON.stringify(data) }),
});
const sinceQuery = since => since ? `?${new URLSearchParams({ since })}` : '';

async function hydrateLogo(city, team) {
  if (!team.logoSvgHash && !team.logoUrlHash) return team;
  const key = `logo_${city}_${team.id}_${team.logoSvgHash ?? 'none'}_${team.logoUrlHash ?? 'none'}`;
  const cached = await getCachedItem(key);
  if (cached) {
    try { return { ...team, ...JSON.parse(cached) }; }
    catch { return { ...team, logoSvg: cached }; }
  }
  try {
    const logo = await apiFetch(`${resource(city, 'teams')}/${team.id}/logo`);
    if (logo.logoSvg || logo.logoUrl) await setCachedItem(key, JSON.stringify(logo));
    return { ...team, ...logo };
  } catch {
    return team;
  }
}

export const catalogApi = {
  version: city => apiFetch(resource(city, 'data-version')),
  players: async (city, since) => {
    const data = await apiFetch(`${resource(city, 'players')}${sinceQuery(since)}`);
    if (!Array.isArray(data)) throw new Error('Invalid player list response.');
    return data;
  },
  teams: async (city, since) => {
    const data = await apiFetch(`${resource(city, 'teams')}${sinceQuery(since)}`);
    if (!Array.isArray(data)) throw new Error('Invalid team list response.');
    return Promise.all(data.map(team => hydrateLogo(city, team)));
  },
  createPlayer: (city, player) => write(resource(city, 'players'), 'POST', player),
  updatePlayer: (city, id, player) => write(`${resource(city, 'players')}/${id}`, 'PUT', player),
  deletePlayer: (city, id) => write(`${resource(city, 'players')}/${id}`, 'DELETE'),
  clearPlayers: city => write(resource(city, 'players'), 'DELETE'),
  importPlayers: (city, file) => {
    const body = new FormData();
    body.append('file', file);
    return apiUpload(`${resource(city, 'players')}/import`, body);
  },
  uploadPhoto: (city, id, file) => {
    const body = new FormData();
    body.append('photo', file);
    return apiUpload(`${resource(city, 'players')}/${id}/upload-photo`, body);
  },
  createTeam: (city, team) => write(resource(city, 'teams'), 'POST', team),
  updateTeam: (city, id, team) => write(`${resource(city, 'teams')}/${id}`, 'PUT', team),
  deleteTeam: (city, id) => write(`${resource(city, 'teams')}/${id}`, 'DELETE'),
  releasePlayer: (city, teamId, playerId) => write(`${resource(city, 'teams')}/${teamId}/release-player`, 'POST', { playerId }),
};

import { catalogApi } from './catalogApi.js';
import { getCachedItem, setCachedItem } from '../../utils/idb.js';
import { canLoadDelta, mergeById } from './catalogModel.js';

const cache = { get: getCachedItem, set: setCachedItem };

/** Cache-aside repository with replaceable HTTP and persistence adapters. */
export class CityCatalogRepository {
  constructor(api, storage) {
    this.api = api;
    this.storage = storage;
  }

  async load(city, { force = false, onCached = () => {} } = {}) {
    const [players, teams, hash, timestamp] = await Promise.all(
      ['players', 'teams', 'hash', 'timestamp'].map(key => this.storage.get(`data_${key}_${city}`).catch(() => null)),
    );
    const cached = { players, teams, hash, timestamp, fromCache: true };
    if (players && teams) onCached(cached);

    // Capture the checkpoint BEFORE reads. A version observed after fetching
    // could otherwise mark data current while omitting an intervening update.
    const version = await this.api.version(city).catch(() => null);
    if (!force && players && teams && hash && hash === version?.hash) return cached;
    const delta = !force && players && teams && canLoadDelta(cached, version);
    const since = delta ? timestamp : null;
    const [changedPlayers, changedTeams] = await Promise.all([this.api.players(city, since), this.api.teams(city, since)]);
    const result = {
      players: delta ? mergeById(players, changedPlayers) : changedPlayers,
      teams: delta ? mergeById(teams, changedTeams) : changedTeams,
    };
    try {
      await this.storage.set(`data_hash_${city}`, null);
      await Promise.all([
        this.storage.set(`data_players_${city}`, result.players),
        this.storage.set(`data_teams_${city}`, result.teams),
        this.storage.set(`data_timestamp_${city}`, version?.timestamp ?? null),
      ]);
      // Certify the checkpoint only after every data write succeeds.
      await this.storage.set(`data_hash_${city}`, version?.hash ?? null);
    } catch {
      // Cache availability must not turn a successful server read into a failure.
    }
    return result;
  }

  async refresh(city, resource) {
    const records = await this.api[resource](city);
    try {
      await this.storage.set(`data_${resource}_${city}`, records);
    } catch {
      // Return fresh records even when browser storage is unavailable.
    }
    // A partial refresh cannot certify that the other cached resource is current.
    await this.invalidate(city);
    return records;
  }

  async invalidate(city) {
    try {
      await this.storage.set(`data_hash_${city}`, null);
    } catch {
      // Cache invalidation is optional when browser storage is unavailable.
    }
  }
}

export const cityCatalog = new CityCatalogRepository(catalogApi, cache);

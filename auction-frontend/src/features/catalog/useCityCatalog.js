import { useCallback, useEffect, useRef, useState } from 'react';
import { cityCatalog } from './catalogRepository.js';
import { isAuctionRole, mergeLiveTeams } from '../auction/auctionModel.js';

/** Coordinates catalog state; the repository owns fetching and cache policy. */
export function useCityCatalog(city, role) {
  const [players, setPlayers] = useState([]);
  const [teams, setTeams] = useState([]);
  const [loading, setLoading] = useState(false);
  const [catalogError, setError] = useState('');
  const generation = useRef(0);
  const liveTeams = useRef({ version: 0, city: '', teams: [] });
  const activeCity = useRef(city);
  activeCity.current = city;

  const loadCity = useCallback(async (targetCity, force = false) => {
    if (!targetCity) return;
    const request = ++generation.current;
    const liveVersion = liveTeams.current.version;
    const current = () => request === generation.current && activeCity.current === targetCity;
    const apply = (result) => {
      if (!current()) return;
      setPlayers(result.players);
      const latest = liveTeams.current;
      setTeams(latest.city === targetCity && (result.fromCache || latest.version !== liveVersion)
        ? mergeLiveTeams(result.teams, latest.teams) : result.teams);
    };
    setLoading(true);
    setError('');
    try {
      apply(await cityCatalog.load(targetCity, { force, onCached: apply }));
    } catch (error) {
      if (current()) setError(error.message);
    } finally {
      if (current()) setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (city && isAuctionRole(role)) loadCity(city);
    else setLoading(false);
    return () => { generation.current++; };
  }, [city, role, loadCity]);

  const refresh = useCallback(async (resource, update) => {
    if (!city) return;
    try {
      const records = await cityCatalog.refresh(city, resource);
      if (activeCity.current === city) update(records);
    } catch (error) {
      if (activeCity.current === city) setError(error.message);
    }
  }, [city]);
  const refreshPlayers = useCallback(() => refresh('players', setPlayers), [refresh]);
  const refreshTeams = useCallback(() => refresh('teams', setTeams), [refresh]);
  const invalidateCache = useCallback(() => city ? cityCatalog.invalidate(city) : Promise.resolve(), [city]);
  const applyLiveTeams = useCallback((incoming) => {
    liveTeams.current = { city, teams: incoming, version: liveTeams.current.version + 1 };
    setTeams(previous => mergeLiveTeams(previous, incoming));
  }, [city]);
  const resetCatalog = useCallback(() => {
    generation.current++;
    setPlayers([]);
    setTeams([]);
    setError('');
    setLoading(false);
  }, []);

  return {
    players, teams, loading, catalogError, loadCity, refreshPlayers, refreshTeams,
    invalidateCache, applyLiveTeams, resetCatalog, setPlayers, setTeams,
  };
}

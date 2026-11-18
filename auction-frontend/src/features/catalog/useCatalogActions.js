import { useRef } from 'react';
import { catalogApi } from './catalogApi.js';
import { releasedPlayer, removePlayerFromTeam } from './catalogModel.js';

/** Apply server-confirmed mutations; failed writes leave local data intact. */
export function useCatalogActions({ city, setPlayers, setTeams, invalidateCache }) {
  const activeCity = useRef(city);
  activeCity.current = city;
  const changed = async (operation, apply) => {
    const result = await operation();
    if (activeCity.current === city) apply(result);
    await invalidateCache();
    return result;
  };

  return {
    addPlayer: data => changed(() => catalogApi.createPlayer(city, data),
      player => setPlayers(previous => [...previous, player])),
    importPlayersFromExcel: file => changed(() => catalogApi.importPlayers(city, file),
      result => setPlayers(previous => [...previous, ...result.importedPlayers])),
    clearAllPlayers: () => changed(() => catalogApi.clearPlayers(city), () => setPlayers([])),
    updatePlayer: (id, data) => changed(() => catalogApi.updatePlayer(city, id, data),
      updated => setPlayers(previous => previous.map(player => player.id === id ? updated : player))),
    uploadPlayerPhoto: (id, file) => changed(() => catalogApi.uploadPhoto(city, id, file),
      updated => setPlayers(previous => previous.map(player => Number(player.id) === Number(id) ? updated : player))),
    deletePlayer: id => changed(() => catalogApi.deletePlayer(city, id), () => {
      setPlayers(previous => previous.filter(player => player.id !== id));
      setTeams(previous => previous.map(team => removePlayerFromTeam(team, id)));
    }),
    createTeam: data => changed(() => catalogApi.createTeam(city, data),
      team => setTeams(previous => [...previous, team])),
    updateTeam: (id, data) => changed(() => catalogApi.updateTeam(city, id, data),
      updated => setTeams(previous => previous.map(team => team.id === id ? updated : team))),
    deleteTeam: id => changed(() => catalogApi.deleteTeam(city, id), () => {
      setTeams(previous => previous.filter(team => team.id !== id));
      setPlayers(previous => previous.map(player => player.soldTeamId === id ? releasedPlayer(player) : player));
    }),
    releasePlayerFromTeam: (teamId, playerId) => changed(() => catalogApi.releasePlayer(city, teamId, playerId), updated => {
      setTeams(previous => previous.map(team => team.id === teamId
        ? { ...team, ...updated, logoSvg: updated.logoSvg ?? team.logoSvg, logoUrl: updated.logoUrl ?? team.logoUrl } : team));
      setPlayers(previous => previous.map(player => Number(player.id) === Number(playerId) ? releasedPlayer(player) : player));
    }),
  };
}

import { useCallback, useRef, useState } from 'react';
import { auctionApi } from './auctionApi.js';
import { matchesLot } from './auctionModel.js';

/** Owns command lifecycle, duplicate-click prevention, and conflict recovery. */
export function useAuctionActions({ city, snapshot, applySnapshot, players, selectedLot, activeLot }) {
  const [auctionError, setError] = useState('');
  const [auctionBusy, setBusy] = useState(false);
  const pending = useRef(false);

  const execute = useCallback(async (operation) => {
    if (pending.current) return { success: false, message: 'An auction action is in progress.' };
    pending.current = true;
    setBusy(true);
    setError('');
    try {
      const command = { playerId: snapshot?.activePlayer?.id, revision: snapshot?.revision };
      applySnapshot(await operation(command));
      return { success: true };
    } catch (error) {
      setError(error.message);
      try {
        applySnapshot(await auctionApi.state(city));
      } catch {
        // The live connection resynchronizes after reconnecting.
      }
      return { success: false, message: error.message };
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }, [city, snapshot, applySnapshot]);

  const startAuction = () => {
    const hasUnsold = players.some(player => player.status === 'UNSOLD' && matchesLot(player, city, selectedLot));
    return execute(command => hasUnsold ? auctionApi.start(city, { ...selectedLot, revision: command.revision })
      : auctionApi.reAuction(city, selectedLot, command.revision));
  };
  const markSold = async (teamId, onFailure) => {
    const result = await execute(command => auctionApi.sell(city, { ...command, teamId, finalPrice: snapshot?.currentBid }));
    if (!result.success) onFailure?.(result.message);
    return result;
  };

  return {
    auctionError,
    auctionBusy,
    startAuction,
    markSold,
    increaseBid: (increment = 500, teamId = null) => execute(command => auctionApi.bid(city, { ...command, increment, teamId })),
    revertLastBid: () => execute(command => auctionApi.revert(city, command)),
    passPlayer: () => execute(command => auctionApi.pass(city, command)),
    nextPlayer: () => execute(command => auctionApi.next(city, command)),
    pauseAuction: () => execute(command => auctionApi.pause(city, { revision: command.revision })),
    resumeAuction: () => execute(command => auctionApi.resume(city, { revision: command.revision })),
    reAuction: () => execute(command => auctionApi.reAuction(city, activeLot, command.revision)),
  };
}

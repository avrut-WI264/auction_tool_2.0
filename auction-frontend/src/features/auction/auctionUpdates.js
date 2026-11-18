/** Merge only consecutive bid deltas. Gaps and state changes require a snapshot. */
export function reconcileAuctionUpdate(current, incoming) {
  if (current && incoming.revision <= current.revision) return { kind: 'ignore' };
  if (Array.isArray(incoming.auctionQueue) && Array.isArray(incoming.cityTeams)) {
    return { kind: 'apply', snapshot: incoming };
  }
  const activeId = incoming.activePlayerId ?? incoming.activePlayer?.id;
  if (current && incoming.eventType === 'BID' && incoming.revision === current.revision + 1
      && activeId === current.activePlayer?.id) {
    return { kind: 'apply', snapshot: {
      ...current,
      revision: incoming.revision,
      started: incoming.started,
      currentBid: incoming.currentBid,
      highestBidderTeamId: incoming.highestBidderTeamId,
      highestBidderTeam: incoming.highestBidderTeam,
      eventType: 'BID',
    } };
  }
  return { kind: 'resync', revision: incoming.revision };
}

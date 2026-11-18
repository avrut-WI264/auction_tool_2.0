import React, { createContext, useState, useEffect, useCallback } from 'react';
import { useLiveAuction } from '../features/auction/useLiveAuction';
import { useAuctionActions } from '../features/auction/useAuctionActions';
import { BUSINESS_RULES as businessRules, isAuctionRole, selectAuctionQueue } from '../features/auction/auctionModel';
import { useAuthSession } from '../features/auth/useAuthSession';
import { clearCache } from '../utils/idb';
import { useCityCatalog } from '../features/catalog/useCityCatalog';
import { useCatalogActions } from '../features/catalog/useCatalogActions';

export const AppContext = createContext();

export const AppProvider = ({ children }) => {
  // Auth state — token is in HttpOnly cookie, never in JS
  const [city, setCity] = useState(() => localStorage.getItem('wbp_city') || '');
  const [role, setRole] = useState(() => localStorage.getItem('wbp_role') || '');
  const [username, setUsername] = useState(() => localStorage.getItem('wbp_username') || '');

  const { authUser, authReady, authenticate, endSession } = useAuthSession();

  const catalog = useCityCatalog(city, role);
  const { players, teams, loading, catalogError, loadCity, refreshPlayers, refreshTeams,
    invalidateCache, applyLiveTeams, resetCatalog } = catalog;
  const catalogActions = useCatalogActions({ city, ...catalog });

  // Auction view state reconciled from committed server snapshots
  const [currentAuctionIndex, setCurrentAuctionIndex] = useState(0);

  // Auction Filters — reset auction index when filters change so we start
  // at the beginning of the newly-filtered queue.
  const [auctionSkillFilter, _setAuctionSkillFilter] = useState(() => localStorage.getItem('wbp_auctionSkillFilter') || 'All');
  const [auctionGenderFilter, _setAuctionGenderFilter] = useState(() => localStorage.getItem('wbp_auctionGenderFilter') || 'All');
  const setAuctionSkillFilter = (val) => { _setAuctionSkillFilter(val); setCurrentAuctionIndex(0); };
  const setAuctionGenderFilter = (val) => { _setAuctionGenderFilter(val); setCurrentAuctionIndex(0); };

  // Lot Selection states (shared to persist across pages)
  const [isLotSelected, setIsLotSelected] = useState(() => localStorage.getItem('wbp_isLotSelected') === 'true');
  const [selectedGender, setSelectedGender] = useState(() => localStorage.getItem('wbp_selectedGender') || 'Female');
  const [selectedSkill, setSelectedSkill] = useState(() => localStorage.getItem('wbp_selectedSkill') || 'Beginner');

  // ---- Persist non-sensitive auth to localStorage (token stays in HttpOnly cookie) ----
  useEffect(() => { localStorage.setItem('wbp_city', city); }, [city]);
  useEffect(() => { localStorage.setItem('wbp_role', role); }, [role]);

  // ---- Persist lot configuration and filters to localStorage ----
  useEffect(() => { localStorage.setItem('wbp_auctionSkillFilter', auctionSkillFilter); }, [auctionSkillFilter]);
  useEffect(() => { localStorage.setItem('wbp_auctionGenderFilter', auctionGenderFilter); }, [auctionGenderFilter]);
  useEffect(() => { localStorage.setItem('wbp_isLotSelected', isLotSelected); }, [isLotSelected]);
  useEffect(() => { localStorage.setItem('wbp_selectedGender', selectedGender); }, [selectedGender]);
  useEffect(() => { localStorage.setItem('wbp_selectedSkill', selectedSkill); }, [selectedSkill]);

  // ---- Auth actions ----
  // selectCity is used by the guest flow — also fetches data immediately
  const selectCity = useCallback(async (selectedCity, selectedRole) => {
    setCity(selectedCity);
    setCurrentAuctionIndex(0);
    setIsLotSelected(false);
    _setAuctionSkillFilter('All');
    _setAuctionGenderFilter('All');
    setSelectedGender('Female');
    setSelectedSkill('Beginner');

    // Fetch data immediately using the new city — force a full fetch since it's a new city
    if (selectedCity && isAuctionRole(selectedRole)) {
      await loadCity(selectedCity, true);
    }
  }, [loadCity]);

  const selectRole = useCallback((selectedRole) => {
    setRole(selectedRole);
  }, []);

  /**
   * Authenticate through the session hook, then update route preferences.
   * Data fetch is handled by the useEffect that watches city/role changes.
   */
  const login = async (usernameInput, password) => {
    const resp = await authenticate(usernameInput, password);
    // Clear cache on login so it fetches fresh data each time
    await clearCache();
    // Setting city and role will trigger the useEffect to fetch players/teams
    setRole(resp.role);
    setCity(resp.city);
    setUsername(resp.username);
    localStorage.setItem('wbp_role', resp.role);
    localStorage.setItem('wbp_city', resp.city);
    localStorage.setItem('wbp_username', resp.username);
    return resp;
  };

  const logout = async () => {
    await endSession();

    // Remove stored data on exit
    await clearCache();

    setRole('');
    setCity('');
    setUsername('');
    resetCatalog();
    localStorage.removeItem('wbp_role');
    localStorage.removeItem('wbp_city');
    localStorage.removeItem('wbp_username');
  };

  // Live snapshots own auction values; the provider only coordinates shared data.
  const { snapshot: liveState, status: liveStatus, applySnapshot } = useLiveAuction(city, isAuctionRole(role));
  const activePlayer = liveState?.activePlayer || null;
  const currentBid = liveState?.currentBid ?? 0;
  const highestBidderTeam = liveState?.highestBidderTeam ?? null;
  const highestBidderTeamId = liveState?.highestBidderTeamId ?? null;
  const auctionQueue = liveState?.auctionQueue ?? selectAuctionQueue(players, city, {
    skillLevel: auctionSkillFilter, gender: auctionGenderFilter,
  });
  const {
    auctionError, auctionBusy, startAuction, increaseBid, revertLastBid,
    passPlayer, nextPlayer, markSold, reAuction, pauseAuction, resumeAuction,
  } = useAuctionActions({
    city, snapshot: liveState, applySnapshot, players,
    selectedLot: { skillLevel: selectedSkill, gender: selectedGender },
    activeLot: { skillLevel: auctionSkillFilter, gender: auctionGenderFilter },
  });

  useEffect(() => {
    if (!liveState) return;
    setIsLotSelected(liveState.started);
    _setAuctionSkillFilter(liveState.skillLevel);
    _setAuctionGenderFilter(liveState.gender);
    setCurrentAuctionIndex(Math.max(0, liveState.auctionQueue.findIndex(p => p.id === liveState.activePlayer?.id)));
    // Keep downloaded logos while updating live balances and rosters.
    applyLiveTeams(liveState.cityTeams);
    if (liveState.eventType !== 'BID') {
      refreshPlayers();
      invalidateCache();
    }
  }, [liveState, refreshPlayers, invalidateCache, applyLiveTeams]);


  return (
    <AppContext.Provider value={{
      // Auth — token is in HttpOnly cookie, not exposed to JS
      city, role, username, authUser, authReady,
      login, logout,
      // Data
      players, teams,
      refreshPlayers, refreshTeams,
      businessRules,
      // Auction
      activePlayer,
      currentBid,
      highestBidderTeam,
      highestBidderTeamId,
      auctionQueue,
      currentAuctionIndex,
      auctionSkillFilter,
      setAuctionSkillFilter,
      auctionGenderFilter,
      setAuctionGenderFilter,
      isLotSelected,
      setIsLotSelected,
      selectedGender,
      setSelectedGender,
      selectedSkill,
      setSelectedSkill,
      // Server-confirmed player and team mutations
      ...catalogActions,
      // Auction actions
      startAuction, liveStatus, auctionError, auctionBusy, pauseAuction, resumeAuction,
      auctionPaused: !liveState?.started,
      increaseBid,
      revertLastBid,
      passPlayer,
      markSold,
      nextPlayer,
      reAuction,
      selectCity,
      selectRole,
      // UI
      loading, catalogError,
    }}>
      {children}
    </AppContext.Provider>
  );
};

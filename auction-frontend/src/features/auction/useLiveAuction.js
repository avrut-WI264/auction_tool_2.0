import { useCallback, useEffect, useRef, useState } from 'react';
import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import { API_BASE_URL } from '../../api/api.js';
import { auctionApi } from './auctionApi.js';
import { reconcileAuctionUpdate } from './auctionUpdates.js';

export function useLiveAuction(city, enabled) {
  const [snapshot, setSnapshot] = useState(null);
  const [status, setStatus] = useState('connecting');
  const current = useRef({ city: '', snapshot: null });
  const reconcile = useRef(() => {});
  const key = city?.toLowerCase() || '';

  const applySnapshot = useCallback(incoming => {
    if (incoming.location !== current.current.city) return;
    const result = reconcileAuctionUpdate(current.current.snapshot, incoming);
    if (result.kind === 'apply') {
      current.current.snapshot = result.snapshot;
      setSnapshot(result.snapshot);
    } else if (result.kind === 'resync') {
      reconcile.current(result.revision);
    }
  }, []);

  useEffect(() => {
    current.current = { city: key, snapshot: null };
    setSnapshot(null);
    if (!key || !enabled) return;
    let disposed = false;
    let running;
    let wantedRevision = 0;
    const resync = (revision = 0) => {
      wantedRevision = Math.max(wantedRevision, revision);
      if (running) return running;
      running = (async () => {
        try {
          for (let attempt = 0; attempt < 3; attempt++) {
            const next = await auctionApi.state(key);
            if (disposed) return;
            applySnapshot(next);
            if (client.connected) setStatus('live');
            if ((current.current.snapshot?.revision ?? -1) >= wantedRevision) break;
          }
        } catch {
          if (!disposed) setStatus('reconnecting');
        } finally { running = null; }
      })();
      return running;
    };
    reconcile.current = resync;
    const socketUrl = new URL('/ws-auction', API_BASE_URL || window.location.origin);
    socketUrl.protocol = socketUrl.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new Client({
      brokerURL: socketUrl.toString(), reconnectDelay: 1000, maxReconnectDelay: 30000,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
      heartbeatIncoming: 10000, heartbeatOutgoing: 10000,
      onConnect: () => {
        if (disposed) return;
        client.subscribe(`/topic/auction/${key}`, message => {
          if (disposed) return;
          try { applySnapshot(JSON.parse(message.body)); }
          catch { resync(); }
        });
        resync();
      },
      onWebSocketClose: () => { if (!disposed) setStatus('reconnecting'); },
      onStompError: () => { if (!disposed) setStatus('reconnecting'); },
    });
    setStatus('connecting');
    resync();
    client.activate();
    const interval = setInterval(async () => {
      try {
        const notice = await auctionApi.revision(key);
        if (!disposed) applySnapshot(notice);
      } catch { if (!disposed) setStatus('reconnecting'); }
    }, 30000 + Math.random() * 5000);
    const onVisible = () => { if (document.visibilityState === 'visible') resync(); };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      disposed = true;
      reconcile.current = () => {};
      clearInterval(interval);
      client.deactivate();
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [key, enabled, applySnapshot]);

  return { snapshot: snapshot?.location === key ? snapshot : null, status, applySnapshot };
}

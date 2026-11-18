// Read-only smoke test. Run only against an environment you own/are authorized to test.
import { Client } from '@stomp/stompjs';
import WebSocket from 'ws';

const base = new URL(process.env.BASE_URL || 'http://localhost:8080');
const city = process.env.CITY || 'pune';
const users = Number(process.env.VUS || 20);
const total = Number(process.env.REQUESTS || 100);
if (base.username || base.password || !/^[a-z]+$/.test(city) || !Number.isInteger(users) || users < 1 || users > 1000
    || !Number.isInteger(total) || total < 1 || total > 100000) throw new Error('Invalid smoke-test configuration.');
const socket = new URL('/ws-auction', base);
socket.protocol = socket.protocol === 'https:' ? 'wss:' : 'ws:';
const clients = [];
let readyCount = 0;
let socketErrors = 0;
let readErrors = 0;
let nextRequest = 0;
const timings = [];
const started = performance.now();

try {
  await Promise.all(Array.from({ length: users }, () => new Promise((resolve, reject) => {
    let ready = false;
    const timeout = setTimeout(() => reject(new Error('WebSocket readiness timeout')), 15000);
    const failed = () => { socketErrors++; if (!ready) { clearTimeout(timeout); reject(new Error('WebSocket connection failed')); } };
    const client = new Client({
      webSocketFactory: () => new WebSocket(socket), reconnectDelay: 0,
      heartbeatIncoming: 10000, heartbeatOutgoing: 10000,
      onConnect: () => client.subscribe(`/topic/auction/${city}`, () => {
        if (!ready) { ready = true; readyCount++; clearTimeout(timeout); resolve(); }
      }),
      onWebSocketError: failed, onStompError: failed,
    });
    clients.push(client);
    client.activate();
  })));
  await Promise.all(Array.from({ length: users }, async () => {
    while (nextRequest++ < total) {
      const start = performance.now();
      try {
        const response = await fetch(new URL(`/api/${city}/auction/state`, base), { signal: AbortSignal.timeout(15000) });
        if (!response.ok) throw new Error('Read failed');
        const state = await response.json();
        if (state.location !== city || !Array.isArray(state.auctionQueue)) throw new Error('Invalid state');
      } catch { readErrors++; }
      timings.push(performance.now() - start);
    }
  }));
  timings.sort((first, second) => first - second);
  console.log(JSON.stringify({
    websocketClients: users, websocketReady: readyCount, websocketErrors: socketErrors,
    requests: timings.length, readErrors, p95Ms: Math.round(timings[Math.ceil(timings.length * 0.95) - 1]),
    durationMs: Math.round(performance.now() - started),
  }, null, 2));
  if (readErrors || socketErrors) process.exitCode = 1;
} finally {
  await Promise.all(clients.map(client => client.deactivate({ force: true })));
}
